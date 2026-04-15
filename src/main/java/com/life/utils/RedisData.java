package com.life.utils;

import lombok.Data;

import java.time.LocalDateTime;

//封装逻辑过期时间
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
