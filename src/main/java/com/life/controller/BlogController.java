package com.life.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.life.dto.Result;
import com.life.dto.UserDTO;
import com.life.entity.Blog;
import com.life.service.IBlogService;
import com.life.service.IUserService;
import com.life.utils.SystemConstants;
import com.life.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {

        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量 这样不行, 会造成无限点赞 update blog set liked = liked + 1 where id = id;

        return blogService.likeBlog(id);
    }

    //TODO 为啥分不了页???
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
     return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id){
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id){
        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "id")Long id ,
                                    @RequestParam(value = "current")Integer current ){
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();

        return Result.ok(records);
    }

    /** TODO 为啥会一直重复???
     * 滚动查询
     * @param max 时间戳
     * @param offset 偏移量
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogFollow(@RequestParam("lastId") Long max
            , @RequestParam(value = "offset" , defaultValue = "0") Integer offset ){
        return blogService.queryBlogFollow(max,offset);
    }

}
