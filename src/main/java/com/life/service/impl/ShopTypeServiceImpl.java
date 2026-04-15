package com.life.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life.dto.Result;
import com.life.entity.ShopType;
import com.life.mapper.ShopTypeMapper;
import com.life.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
//        String key = "shop:type:";
//        List<ShopType> shopTypeBeanList = new ArrayList<>();
//
//        //1. 在redis中查询
//        List<String> shopType = stringRedisTemplate.opsForList().range(key, 0, -1);
//        //2. 查到,返回对象
//        if(!shopType.isEmpty()){
//
//            for (String s : shopType) {
//                //取出来的是json,转换成bean
//                ShopType shopTypeBean = JSONUtil.toBean(s, ShopType.class);
//                //添加进新的集合
//                shopTypeBeanList.add(shopTypeBean);
//            }
//            return Result.ok(shopTypeBeanList);
//        }
//        //3. 查不到,查数据库
//        shopTypeBeanList = list();
//        //4. 数据库查
//        if (shopTypeBeanList.isEmpty()){
//            //5. 数据库查不到,报错
//            return Result.fail("类型不存在");
//        }
//        //6.数据库查到了,添加进redis , 要用循环把集合里面一个个元素取出来
//        for (ShopType type : shopTypeBeanList ){
//            String shopTypeJson = JSONUtil.toJsonStr(type);
//            stringRedisTemplate.opsForList().rightPush(key, shopTypeJson);
//        }
//        return Result.ok(shopTypeBeanList);

        String key = "shop:type:";
        String shopType = stringRedisTemplate.opsForValue().get(key);
        //redis查到数据直接返回
        if(StrUtil.isNotBlank(shopType)){
            return Result.ok(JSONUtil.toList(shopType,ShopType.class));
        }
        //没找到去数据库找 (shopType) -> shopType.getSort() 简化的lambda表达式
        List<ShopType> shopTypeList = this.list(new LambdaQueryWrapper<ShopType>()
                .orderByAsc(ShopType::getSort));

        if(shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail("未找到");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypeList),36000L, TimeUnit.MINUTES);
        return Result.ok(shopTypeList);
    }




}
