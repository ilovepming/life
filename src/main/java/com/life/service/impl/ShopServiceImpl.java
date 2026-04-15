package com.life.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.life.dto.Result;
import com.life.entity.Shop;
import com.life.mapper.ShopMapper;
import com.life.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.utils.CacheClient;
import com.life.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.life.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;


    @Override
    /*
    根据id在redis查询店铺
    */
    public Result queryShopById(Long id) {
        //1.1缓存穿透, 调用工具类封装后的方法
         Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,
                      this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //2.1互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //2.2逻辑过期解决缓存击穿
        /*
         Shop shop = queryWithLogicalExpire(id);
        调用逻辑过期工具类解决
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
                this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        */
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /*==================================================================================================================
    建立缓存空对象解决缓存穿透
    * */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.查询redis商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2. 命中非空值
        if (StrUtil.isNotBlank(shopJson)) {// isNotBlank判断字符串里面有实际内容才返回true. "" ,null都是false
            //3.返回信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //2' 命中空对象, 返回提示语, 不能往下查数据库
        if (shopJson != null) { //上面判断了字符串里面一定是null或者"" , 所以这里只要判断不是null就一定是"".
            // null是连对象都没有, ""是有对象但是内容为空
            return null;
        }
        //4. 未命中,在数据库中查询
//        Shop one = query("id",id).one();  不用这种,这个是获取查询到第一条数据的意思
        Shop shop = getById(id);
        if (shop == null) {
            //不存在,防止缓存穿透,添加空对象
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //5.存在,写入redis,返回信息
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /*==================================================================================================================
    互斥锁解决缓存击穿方法
    * */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.查询redis商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2. 命中非空值
        if (StrUtil.isNotBlank(shopJson)) {
            //3.返回信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //2' (缓存穿透)命中空对象
        if (shopJson != null) {
            return null;
        }
        //2'' (缓存击穿)未命中, 加锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //拿到锁是true
            if (!isLock) {

                //休眠一会才重新递归
                Thread.sleep(20);

                return queryWithMutex(id);
            }
            //4. 拿到锁, 未命中, 在数据库中查询
            shop = getById(id);
            if (shop == null) {
                //不存在,防止缓存穿透,添加空对象
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //5.存在,写入redis,返回信息
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6. 释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    /*==================================================================================================================
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
//======================================================================================================================
    //(逻辑时间解决击穿问题)创建一个固定大小为 10 的线程池，最多同时有 10 个线程并发执行任务
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //缓存预热,重建缓存
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //在数据库里面查询
        Shop shop = getById(id);
        Thread.sleep(200L);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        //当前时间加上设置的时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    //逻辑过期解决缓存击穿
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.查询redis商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.未命中
        if (StrUtil.isBlank(shopJson)) {
            //3.返回信息
            return null;
        }
        //3 命中
        //判断过期时间,需要先把json反序列化为对象??/
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //这里data的类型没有写死,所以视为JSONObject
        JSONObject data = (JSONObject) redisData.getData();
        //然后再转成shop类型的
        Shop shop = JSONUtil.toBean(data,Shop.class);
        //到期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        //3.1 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //3.1 没过期,返回信息
            return shop;
        }
        //3.1.2 获取锁
        boolean isLock = tryLock(LOCK_SHOP_KEY+id);
        // 判断有无互斥锁
        if (isLock){
            //有互斥锁,开新线程,更新时间
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,20L);
                }
                 catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁 记得这里的key是锁的key
                    //***释放锁时传错了 key，把缓存数据当成锁删掉了，导致后续请求查不到数据，返回 null。***
                    unLock(LOCK_SHOP_KEY+id);
                }
            });
        }
        //没锁
        return shop;
    }
    /* =================================================================================================================
      更新店铺信息
     **/
    @Override
    @Transactional   //单体项目放在同一个事物处理
    public Result update(Shop shop) {
        //1. 更新数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("没有这个商品");
        }
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }






}
