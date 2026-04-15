package com.life.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.life.dto.Result;
import com.life.dto.ScrollResult;
import com.life.dto.UserDTO;
import com.life.entity.Blog;
import com.life.entity.Follow;
import com.life.entity.User;
import com.life.mapper.BlogMapper;
import com.life.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.service.IFollowService;
import com.life.service.IUserService;
import com.life.utils.SystemConstants;
import com.life.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.life.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.life.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        for (Blog blog : records) {
            queryBlogUser(blog);
        }
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("点评不存在");
        }
        //查询blog有关的用户
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        //判断当前用户有无点赞 , 不加这个的话前端没效果的 , 相当于把isLiked的值给前端
        isBlogLiked(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = UserHolder.getUser().getId();
        //2 判断当前登录用户是否点过赞, score为null说明没点过赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        //点过赞
        if (score != null) {
            blog.setIsLike(true);
        }
    }

    /*
    添加进点赞排行榜
    * */
    @Override
    public Result likeBlog(Long id) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2 判断当前登录用户是否点过赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        //3 score为null说明没点过赞
        if (score == null) {
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //添加用户进redis
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //点过赞,取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            //用户从redis中移除
            if (isSuccess) {
                //此处不能用delete(), 会把整个key都删掉的....
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }

    /*
    查询排行榜
    * */
    @Override
    public Result queryBlogLikes(Long blogId) {
        String key = BLOG_LIKED_KEY + blogId;
        //1.查询最新点赞前五名
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //没人点赞,空
        if (top5 == null || top5.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>();
        //2.根据查到的用户id,查用户信息
        for (String s : top5) {
            Long id = Long.valueOf(s);
            ids.add(id);
        }
//        List<User> users = userService.listByIds(ids);
        String idStr = StrUtil.join(",", ids);
        List<User> users = userService.query()
                .in("id", top5)
                //固定排序,最新点赞在前
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();
        List<UserDTO> userDTOS = new ArrayList<>();

        for (User u : users) {
            //需要将user的内容复制到userDTO中
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(u, userDTO);
            userDTOS.add(userDTO);
        }

        //3.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        //推送粉丝收件箱
        if (isSuccess) {
            //查询当前博主所有粉丝
            List<Follow> follow = followService.query().eq("follow_user_id", user.getId()).list();
            //遍历粉丝集合
            for (Follow f : follow) {
                Long userId = f.getUserId();
                stringRedisTemplate.opsForZSet().add(FEED_KEY + userId,
                        String.valueOf(blog.getId()), System.currentTimeMillis());
            }
        }
        return Result.ok(blog.getId());
    }

    /**
     * 滚动分页查询关注的博客
     *
     * @param max    当前最大时间戳
     * @param offset 偏移量
     * @return
     */
    @Override
    public Result queryBlogFollow(Long max, Integer offset) {
        //1.查询当前用户id
        Long userId = UserHolder.getUser().getId();
        //2.取出用户收件箱的blog 这里填什么??? ZREVERSERANGEBYSCORE key max min limit offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(
                        FEED_KEY + userId.toString(), //"feed:2013"
                        0, //min: 最小值 , 时间最旧的值 , 无论多旧都可以
                        max, //最大时间戳 , 时间新值
                        offset, //偏移量
                        2 //1页显示条数
                );
        if (typedTuples == null | typedTuples .isEmpty()){
            return Result.ok(false);
        }
        //3.解析blogId , max , offset
        Long minTime = 0L;
        int of = 0;
        List<String> ids = new ArrayList<>(typedTuples.size()); //防止list扩容带来性能损耗
        for (ZSetOperations.TypedTuple<String> t : typedTuples) {
            String blogId = t.getValue();
            ids.add(blogId);

            Long time = t.getScore().longValue();
            if (minTime == time) {
                of++;
            } else {
                minTime = time;
                of = 0;
            }
        }
        //4.根据blogId去数据库查
        String idStr = StrUtil.join(",", ids);
        List<Blog> blog = query()
                        .in("id", ids) //不用eq , 查集合in能够一次查完
                        .last("ORDER BY FIELD(id," + idStr + ")")
                        .list();
        //5.封装
        ScrollResult s = new ScrollResult();
        s.setList(blog);
        s.setOffset(of);
        s.setMinTime(minTime);
        return Result.ok(s);
    }


    /*
    isBlogLiked(Blog blog)
    * */
    /*    private void isBlogLiked(Blog blog) {
        // 1. 获取登录用户
        if (UserHolder.getUser() == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = UserHolder.getUser().getId();
        //2 判断当前登录用户是否点过赞, 为true说明有点赞, 为false说明没点赞
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        //点过赞
        blog.setIsLike(BooleanUtil.isTrue(isLiked));
    }*/

    /*
    用户点赞功能(Set)
    * */
    /*@Override
    public Result likeBlog(Long id) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2 判断当前登录用户是否点过赞, 为true说明有点赞, 为false说明没点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());
        //3 未点赞, 可以点赞
        if (BooleanUtil.isFalse(isMember)) {
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //添加用户进redis
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(BLOG_LIKED_KEY + id, userId.toString());
            }
        } else {
            //点过赞,取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            //用户从redis中移除
            if (isSuccess) {
                //此处不能用delete(), 会把整个key都删掉的....
                stringRedisTemplate.opsForSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }*/
}

