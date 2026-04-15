package com.life.service;

import com.life.dto.Result;
import com.life.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void creatVoucherOrder(VoucherOrder voucherOrder);
}
