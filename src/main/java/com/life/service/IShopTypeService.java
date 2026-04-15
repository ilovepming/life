package com.life.service;

import com.life.dto.Result;
import com.life.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IShopTypeService extends IService<ShopType> {
    Result queryTypeList();
}
