package com.life.service;

import com.life.dto.Result;
import com.life.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopService extends IService<Shop> {
    Result queryShopById(Long id);

    Result update(Shop shop);
}
