package com.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.life.dto.Result;
import com.life.entity.VoucherOrder;
import com.life.mapper.VoucherOrderMapper;
import com.life.service.ISeckillVoucherService;
import com.life.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.utils.RedisIdWorker;
import com.life.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    //注入这个类,这个类关联到那个我们需要的表了
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    //自我注入,代理对象
    @Autowired
    private VoucherOrderServiceImpl self;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //告诉redis脚本在哪里
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //初始化完类就立刻处理队列中的任务
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHolder());
    }


    public class VoucherOrderHolder implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    //获取消息队列中的订单信息 XREADGROUP group g1 c1 count 1 block 2000 streams stream.orders
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()) // > 从下一个未消费的信息开始读
                    );
                    //判断消息获取是否成功
                    //失败->没有消息,下一次循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    //成功->可以下单
                    //设置了count(1) , 所以一次拿一条
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    //把map对象里的几个id填充到voucherOrder里面
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    creatVoucherOrder(voucherOrder);
                    //ACK确认 xack stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //确认时, 抛出异常 ?
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    //获取pending中的订单信息 XREADGROUP group g1 c1 count 1 streams stream.orders
                    //为什么不用阻塞队列了? 定时扫描, 有就有, 没有就返回了
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            //ReadOffset.from("0") : pending-list中未确认的消息开始读
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        break;
                    }

                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                    creatVoucherOrder(voucherOrder);
                    //ACK确认 xack stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }

            }

        }
    }

    //在这返回给前端
    @Override
    public Result seckillVoucher(Long voucherId) {
        //只有主线程才能通过userHolder拿到id, userHolder是拦截器中的
        Long userId = UserHolder.getUser().getId();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        //调用lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                //没有key的值
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        int r = result.intValue();
        //判断是否等于0, 不等于0说明不能下单
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        return Result.ok(orderId);
    }

    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        //3 一人一单
        Long userId = voucherOrder.getUserId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();

        if (count > 0) {
            log.error("仅限1单");
        }

        //3.1 充足扣库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                //乐观锁
                //.eq("voucher_id", voucherId).eq("stock",voucher.getStock()) //等于上面查询到的stock才能扣 , 出现少卖问题
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存不足");
        }

        //3.3 写入数据库
        save(voucherOrder);
    }
     /*
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<VoucherOrder>(1024*1024);
    //特定写法, 继承Runnable, 实现任务
    public class VoucherOrderHolder implements Runnable{
        @Override
        public void run() {
            try {
                VoucherOrder voucherOrder = orderTasks.take();
                self.creatVoucherOrder(voucherOrder);
            } catch (Exception e) {
                log.error("处理失败",e);
            }

        }
    }*/
    /*
    阻塞队列实现
    @Override
    public Result seckillVoucher(Long voucherId) {
        //只有主线程才能通过userHolder拿到id, userHolder是拦截器中的
        Long userId = UserHolder.getUser().getId();
        //调用lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                //没有key的值
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        int r = result.intValue();
        //判断是否等于0, 不等于0说明不能下单
        if (r != 0 ){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        //阻塞队列============
        // 订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //添加进阻塞队列
        orderTasks.add(voucherOrder);
        //异步下单: 线程池SeckillOrderExecutor+线程任务

        return Result.ok(orderId);
    }
    */
/*@Override
    下单秒杀券, 无脚本
    public Result seckillVoucher(Long voucherId) {
        //1. 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2. 判断秒杀是否开始,是否结束
        //A.isAfter(B) --> A的时间在B的后面, 不要搞反了
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())
                || voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("不在活动时间内!!");
        }

        //3. 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        //Redisson锁入门
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean isLock = lock.tryLock();

        if (!isLock) {
            return Result.fail("不允许重复下单");
        }

        try {
            // 内部锁，保证事务生效
            synchronized (userId.toString().intern()) {
                return self.creatVoucherOrder(voucherId);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {

            lock.unlock();
        }
    }*/
/*
没用lua脚本
@Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //3 一人一单
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        if (count > 0) {
            return Result.fail("一人仅限购买一单");
        }

        //3.1 充足扣库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                //乐观锁
                //.eq("voucher_id", voucherId).eq("stock",voucher.getStock()) //等于上面查询到的stock才能扣 , 出现少卖问题
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存不足");
        }

        //3.2 订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);

        //3.3 写入数据库
        save(voucherOrder);

        //3.3 返回订单id
        return Result.ok(orderId);
    }*/

}