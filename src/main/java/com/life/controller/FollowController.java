package com.life.controller;


import com.life.dto.Result;
import com.life.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    public IFollowService iFollowService;

    //关注&取关 id:博主的id
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,
                         @PathVariable("isFollow") Boolean isFollow) {
        return iFollowService.follow(id,isFollow);
    }

    //查询关注
    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long id) {
        return iFollowService.follow(id);
    }

    //共同关注
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long followId){
        return iFollowService.followCommons(followId);
    }

}
