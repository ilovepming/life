package com.life;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//暴露代理对象
//@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.life.mapper")
@SpringBootApplication
public class LifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeApplication.class, args);
    }

}
