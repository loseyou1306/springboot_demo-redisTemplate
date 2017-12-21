package com.test.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.test.data.Result;
import com.test.service.RedisService;

@RestController
@RequestMapping("/redis")
public class DemoController {
	//启动服务后在浏览器使用http://127.0.0.1:10001/redis/get/33		访问获取key为33的value值
    private final RedisService redisServiceImpl;

    @Autowired
    public DemoController(RedisService redisServiceImpl) {
        this.redisServiceImpl = redisServiceImpl;
    }

    @RequestMapping(value = "get/{key}", method = RequestMethod.GET)
    public Result<String> find(@PathVariable("key") String key) {
        String value = redisServiceImpl.getValue(key);
        return new Result<>(value);
    }

    @RequestMapping(value = "add/{key}/{value}", method = RequestMethod.GET)
    public Result<Boolean> add(@PathVariable("value") String value, @PathVariable("key") String key) {
        return new Result<>(redisServiceImpl.cacheValue(key, value));
    }

    @RequestMapping(value = "del/{key}", method = RequestMethod.GET)
    public Result<Boolean> del(@PathVariable("key") String key) {
        return new Result<>(redisServiceImpl.removeValue(key));
        
    }

    @RequestMapping(value = "count/{key}", method = RequestMethod.GET)
    public Result<Long> count(@PathVariable("key") String key) {
        return new Result<>(redisServiceImpl.getListSize(key));
    }


}
