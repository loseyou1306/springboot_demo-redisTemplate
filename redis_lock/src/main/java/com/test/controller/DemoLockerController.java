package com.test.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.test.locker.RedisLocker;
import com.test.locker.impl.RedisLockerImpl;

@RestController
@RequestMapping("/locker")
public class DemoLockerController {
	   @Autowired
	    private RedisLocker redisLockerImpl;


	    @RequestMapping(value = "/lock", produces = "application/json;charset=utf-8", method = RequestMethod.GET)
	    public boolean acrequireLock() {
	        try {
	            return redisLockerImpl.acquireLockWithTimeout(50, "product:10100101:shopping", true);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        return false;
	    }

	    @RequestMapping(value = "/releaseLock", produces = "application/json;charset=utf-8", method = RequestMethod.GET)
	    public boolean releaseLock() {
	        try {
	            return redisLockerImpl.releaseLockWithTimeout("product:10100101:shopping");
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        return false;
	    }


	    @RequestMapping(value = "/getLockIdentity", produces = "application/json;charset=utf-8", method = RequestMethod.GET)
	    public String getCurrentNodeLockIdentity() {
	        return RedisLockerImpl.getRedisIdentityKey();
	    }
}
