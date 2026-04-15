package com.life.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.life.dto.LoginFormDTO;
import com.life.dto.Result;
import com.life.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
