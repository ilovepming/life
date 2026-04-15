package com.life.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/*唯一id生成*/
@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L; //2022-1-1 的秒数

    private static final int COUNT_BITS = 32; //移位数

    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        //计算秒数
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        //当前秒数减去旧秒数
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1 获取当天日期,精确到天  MM分钟,大写
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
     /*   3.拼接返回
         这里用到了位运算, COUNT_BITS 指的是位数 32位
         时间戳左移 32 位，空出低 32 位给序列号 , 低位全部都是0
         然后用count运算序列号, 有1就是1
         这样存前面是时间戳,后面是序列号
         */
        return timeStamp << COUNT_BITS | count;
    }


//获取2022-1-1 零点 的具体描秒数
//    public static void main(String[] args) {
//        LocalDateTime localDateTime = LocalDateTime.of
//                (2022,1,1,0,0,0);
//        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("2022-1-1 second :" + epochSecond);
//    }
}
