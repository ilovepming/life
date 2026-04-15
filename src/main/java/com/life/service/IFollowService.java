package com.life.service;

import com.life.dto.Result;
import com.life.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IFollowService extends IService<Follow> {

    //关注&取关
    Result follow(Long id, Boolean isFollow);

    Result follow(Long id);

    Result followCommons(Long followId);
}
