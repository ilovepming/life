package com.life.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.life.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.life.utils.RedisConstants.LOGIN_USER_KEY;
import static com.life.utils.RedisConstants.LOGIN_USER_TTL;
//拦截所有请求,将用户保存到ThreadLocal
public class ReFreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public ReFreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //登录校验拦截器:
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 1. 获取redis中的用户
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // token不存在,用户没登录,直接跳到LoginInterceptor拦截器拦截
            return true;
        }
        //2. 获取userMap
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (userMap.isEmpty()){
            // userMap不存在 : 原因可能是token过期
            // 不存用户,直接跳到下一个拦截器拦截
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);

        //5. 存在,保留用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        //6. 刷新redis有效期 ; expire是有效期,entries是数据条目->entry的复数形式..过期时间25天,单位分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //6. 放行
        return true;
    }
    //防止内存泄漏,需要清理
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }


}
