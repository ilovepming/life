package com.life.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.life.utils.RedisConstants.*;
import static com.life.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    /*
    封装成工具类
    * */
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*将任意对象转为JSON并存储在String类型的key中,并且可以设置TTL.
    * */
    public void set(String key, Object value, Long time) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time);
    }

    /*将任意对象转为JSON并存储在String类型的key中,并且可以设置逻辑过期时间,用于处理缓存击穿问题.
    * */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /*根据指定的key查询缓存,并反序列化为指定类型,利用缓存空值的方式解决缓存穿透问题.
    * */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //非空,非空白
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        //为空值,去数据库查.
        //数据库查不能简单查得到,用户的查询逻辑不同,传入函数式
        R r = dbFallback.apply(id);
        if (r == null) {
            //数据库为空,设置空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
        return r;
    }

    /*(逻辑时间解决击穿问题)创建一个固定大小为 10 的线程池，
    最多同时有 10 个线程并发执行任务
    * */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    //逻辑过期解决缓存击穿
    public <ID,R> R queryWithLogicalExpire(String keyPrefix,ID id, Class<R> type,
                                           Function<ID, R> dbFallback,
                                           Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.查询redis商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.未命中
        if (StrUtil.isBlank(json)) {
            //3.返回信息
            return null;
        }
        //3 命中
        //获取过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        //然后再转类型
        R r = JSONUtil.toBean(data,type);
        //到期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        //3.1 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //3.1 没过期,返回信息
            return r;
        }
        //3.2 过期,获取锁
        boolean isLock = tryLock(LOCK_SHOP_KEY+id);
        // 判断有无锁
        if (isLock){
            //新线程
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //重建缓存
                    this.setWithLogicalExpire(key, r1, time, unit);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(LOCK_SHOP_KEY+id);
                }
            });
        }
        //返回数据
        return r;
    }

    /*
    加锁
    * */
    public boolean tryLock(String key) {
        //setIfAbsent的作用是判断key是否存在,不存在则设置key的值为value并返回true . 否则不能设置,返回false
        //这样的好处是一旦key存在就不能设置,相当于上了锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //拿到锁true
        return BooleanUtil.isTrue(flag);
    }
    /*
      释放锁
        * */
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


}

