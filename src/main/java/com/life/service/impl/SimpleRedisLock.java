package com.life.service.impl;

import com.life.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/*分布式锁----版本1*/
public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //key 的前缀
    public static final String KEY_PREFIX = "lock:";
    //value 线程id的前缀
    public static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    //DefaultRedisScript 脚本执行器, 返回Long结果的Redis脚本
    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //告诉redis脚本在哪里, 从项目的目录里面找
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程id 拼接 随机id前缀
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.MINUTES);
        //不会发生拆箱的空指针问题
        return Boolean.TRUE.equals(isLock);
    }

    @Override
    public void unlock() {
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (threadId.equals(id)) {
        //有可能在这里发生线程阻塞!!!!!已经判断过
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//   }
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                //锁的key值, 要求集合类型, 用方法转成单个元素的集合
                Collections.singletonList(KEY_PREFIX + name),
                //普通参数值 当前线程id
                ID_PREFIX + Thread.currentThread().getId()
        );

    }

}
