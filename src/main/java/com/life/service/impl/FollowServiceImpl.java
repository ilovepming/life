package com.life.service.impl;

import com.life.dto.Result;
import com.life.dto.UserDTO;
import com.life.entity.Follow;
import com.life.entity.User;
import com.life.mapper.FollowMapper;
import com.life.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.service.IUserService;
import com.life.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.life.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *

 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    public FollowMapper followMapper;

    @Autowired
    public IUserService userService;

    @Autowired
    public StringRedisTemplate stringRedisTemplate;

    /**
     *
     * @param followUserId 博主id
     * @param isFollow 关注/取关
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //粉丝id
        Long userId = UserHolder.getUser().getId();

        if(isFollow){
            //isFolow为True-->关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess){
                //粉丝(用户)关注列表
                stringRedisTemplate.opsForSet().add(FOLLOW_KEY+userId,followUserId.toString());
            }
        }else{
            //已关注-->取关
            followMapper.removeFollowId(userId,followUserId);
            stringRedisTemplate.opsForSet().remove(FOLLOW_KEY+userId,followUserId.toString());
        }

        return Result.ok();
    }

    /**
     *
     * @param id 博主id
     * @return
     */
    @Override
    public Result follow(Long id) {
        Long userId = UserHolder.getUser().getId();
        //查询用户有无关注
        Integer count = query().eq("follow_user_id", id).eq("user_id", userId).count();
        if (count == null || !count.equals(1)){
            return Result.ok(false);
        }
        else{
            return Result.ok(true);
        }
    }

    /**
     *
     * @param id 博主id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        //粉丝id,当前用户
        String userKey = FOLLOW_KEY + userId;
        //博主id
        String followKey = FOLLOW_KEY + id;
        Set<String> commons = stringRedisTemplate.opsForSet().intersect(userKey, followKey);
        if (commons == null || commons.isEmpty()){
            return Result.ok(null);
        }
        List<Long> ids = new ArrayList<>();
        for (String s : commons) {
            ids.add(Long.valueOf(s));
        }
        List<User> users = userService.listByIds(ids);

        List<UserDTO> userDTOList = new ArrayList<>();

        for (User u : users) {
            UserDTO userDTO  = new UserDTO();
            BeanUtils.copyProperties(u,userDTO);
            userDTOList.add(userDTO);
        }

        return Result.ok(userDTOList);
    }
}
