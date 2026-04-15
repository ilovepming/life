package com.life.mapper;

import com.life.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 */
public interface FollowMapper extends BaseMapper<Follow> {
    /**
     *
     * @param userId 粉丝
     * @param followUserId 博主
     */
    void removeFollowId(Long userId, Long followUserId);


}
