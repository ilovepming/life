package com.life.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.dto.LoginFormDTO;
import com.life.dto.Result;
import com.life.dto.UserDTO;
import com.life.entity.User;
import com.life.mapper.UserMapper;
import com.life.service.IUserService;
import com.life.utils.RegexUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.life.utils.RedisConstants.*;
import static com.life.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /*
    发送短信验证码
    **/
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 验证手机号合法,无效为true,有效为false
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式有问题");
        }

        //2. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3. 保存验证码
        session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.发送验证码
        log.debug("验证码已发送" + code);

        return Result.ok();
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 验证手机号码
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式有问题");
        }
        //2. 校验验证码
//        Object cacheCode = session.getAttribute("code");
        //从redis中获取
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (code == null || !cacheCode.equals(code)) {
            //3. 不一致,报错
            return Result.fail("验证码错误");
        }
        //4. 一致,查询用户
        User user = query().eq("phone", phone).one();
        //5. 判断用户是否存在
        if (user == null) {
            //6. 不存在,创建新用户,保存到数据库
            user = createUserWithPhone(phone);
        }
        //7. 保存用户信息到redis中
        //7.1 生成token
        String token = UUID.randomUUID().toString();
        //7.2 将dto转为 Hashmap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        /*
        直接转成map集合不行,hash类型的key和value都要是string类型的,id字段是long类型的
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        方法1 如下:
        Map<String,String> userMap = new HashMap<>();
        这里将long类型转成string
        userMap.put("id",String.valueOf(userDTO.getId()));
        userMap.put("nickName",userDTO.getNickName());
        userMap.put("icon",userDTO.getIcon());
        */
        //方法2
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),CopyOptions.create()
                .setIgnoreNullValue(true) //忽略空值
                .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()) // 将值的类型改变
        );

        //7.3 存,设置时间
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //记得save
        save(user);
        return user;

    }


}
