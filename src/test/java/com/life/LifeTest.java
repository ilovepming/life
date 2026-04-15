package com.life;

import com.life.entity.Shop;
import com.life.service.impl.ShopServiceImpl;
import com.life.utils.CacheClient;
import com.life.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.life.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
public class LifeTest {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;
    @Test
    public void testShopSave() {
        Shop shop = shopService.getById(1);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
    }

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException{
        // CountDownLatch 是主线程等待所有子线程完成之后才结束 300个线程
        CountDownLatch latch = new CountDownLatch(300);

        // 定义任务：每个线程生成100个ID
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        // 记录开始时间
        long begin = System.currentTimeMillis();

        // 提交300个任务到线程池
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 等待所有任务完成
        latch.await();

        // 记录结束时间
        long end = System.currentTimeMillis();

        // 输出总耗时
        System.out.println("time = " + (end - begin));

        // 关闭线程池
        es.shutdown();
    }



}
