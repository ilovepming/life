package com.life.utils;

import com.life.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    //登录校验拦截器:
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        //取出用户,判断是否为空
        UserDTO user = UserHolder.getUser();
        if (user == null){
            response.setStatus(401);
         return false;
        }
        //6. threadLocal里面有用户,放行
        return true;
    }


}
