# springboot_demo-redisTemplate

该项目实现了springboot+redis集成及使用redis构建分布式锁

redisServiceImp cacheValueIfExist command
java object condition queue 条件队列
retrycount 带有重试次数限制
object wait time 带有超时时间的wait
delete lock 删除远程锁
acquire lock 申请lock
release lock 释放lock


redisServiceImp cacheValueIfExist 命令

redisServiceImp cacheValueIfExist 命令特性:实际上底层调用的是ValueOperations对象的setIfAbsent(key, v)方法,返回boolean值

当指定key不存在时才设置。也就是说，如果返回true说明你的命令被执行成功了,同时过期时间设置(防止你的retry lock重复设置这个过期时间，导致永远不过期),
  redis服务器中的key是你之前设置的值。如果返回false，说明你设置的key在redis服务器里已经存在。
           boolean status = false;
	         status = redisServiceImp.cacheValueIfExist(lockKey, redisIdentityKey,lockKeyExpireSecond);/**设置 lock key.*/


java object condition queue 条件队列

这里有一个小窍门，可以尽可能的最大化cpu利用率又可以解决公平性问题。

当你频繁retry的时候，要么while(true)死循环，然后加个Thread.sleep，或者CAS。前者存在一定线程上下文切换开销（Thread.sleep是不会释放出当前内置锁），而CAS在不清楚远程锁被占用多久的情况会浪费很多CPU计算周期，有可能一个任务计算个十几分钟，CPU不可能空转这么久。

这里我尝试使用condition queue条件队列特性来实现（当然肯定还有其他更优的方法）。

if (isWait && retryCounts < RetryCount) {
                    retryCounts++;
                    synchronized (this) {//借助object condition queue 来提高CPU利用率
                        logger.info(String.
                                format("t:%s,当前节点：%s,尝试等待获取锁：%s", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
                        this.wait(WaitLockTimeSecond); //未能获取到lock，进行指定时间的wait再重试.
                    }
                } else if (retryCounts == RetryCount) {
                    logger.info(String.
                            format("t:%s,当前节点：%s,指定时间内获取锁失败：%s", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
                    return false;
                } else {
                    return false;//不需要等待，直接退出。
                }
使用条件队列的好处就是，它虽然释放出了CPU但是也不会持有当前synchronized，这样就可以让其他并发进来的线程也可以获取到当前内置锁，然后形成队列。当wait时间到了被调度唤醒之后才会重新来申请synchronized锁。 简单讲就是不会再锁上等待而是在队列里等待。java object每一个对象都持有一个条件队列，与当前内置锁配合使用。

retrycount 带有重试次数限制

等待远程redis lock肯定是需要一定重试机制，但是这种重试是需要一定的限制。

    /**
     * 重试获取锁的次数,可以根据当前任务的执行时间来设置。
     * 需要时间=RetryCount*(WaitLockTimeSecond/1000)
     */
    private static final int RetryCount = 10;
这种等待是需要用户指定的， if (isWait && retryCounts < RetryCount) ，当isWait为true才会进行重试。

object wait time 带有超时时间的wait

object.wait(timeout),条件队列中的方法wait是需要一个waittime。

    /**
     * 等待获取锁的时间，可以根据当前任务的执行时间来设置。
     * 设置的太短，浪费CPU，设置的太长锁就不太公平。
     */
    private static final long WaitLockTimeSecond = 2000;
默认2000毫秒。

this.wait(WaitLockTimeSecond); //未能获取到lock，进行指定时间的wait再重试.
注意：this.wait虽然会blocking住，但是这里的内置锁是会立即释放出来的。所以，有时候我们可以借助这种特性来优化特殊场景。
delete lock 删除远程锁

释放redis lock比较简单，直接del key就好了

boolean status = redisServiceImp.removeValue(lockKey);
一旦delete 之后，首先wait唤醒的线程将会获得锁。

acquire lock 申请lock

 /**
	     * 带超时时间的redis lock.
	     *
	     * @param lockKeyExpireSecond 锁key在redis中的过去时间
	     * @param lockKey             lock key
	     * @param isWait              当获取不到锁时是否需要等待
	     * @throws Exception lockKey is empty throw exception.
	     */
	    public Boolean acquireLockWithTimeout(int lockKeyExpireSecond, String lockKey, Boolean isWait) throws Exception {
	        if (StringUtils.isEmpty(lockKey)) throw new Exception("lockKey is empty.");

	        int retryCounts = 0;
	        while (true) {
	        	boolean status = false;
	            status = redisServiceImp.cacheValueIfExist(lockKey, redisIdentityKey,lockKeyExpireSecond);/**设置 lock key.*/
	            if (status) {
	                logger.info(String.
	                        format("t:%s,当前节点：%s,获取到锁：%s", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
	                return true;/**获取到lock*/
	            }

	            try {
	                if (isWait && retryCounts < RetryCount) {
	                    retryCounts++;
	                    synchronized (this) {//借助object condition queue 来提高CPU利用率
	                        logger.info(String.
	                                format("t:%s,当前节点：%s,尝试等待获取锁：%s", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
	                        this.wait(WaitLockTimeSecond); //未能获取到lock，进行指定时间的wait再重试.
	                    }
	                } else if (retryCounts == RetryCount) {
	                    logger.info(String.
	                            format("t:%s,当前节点：%s,指定时间内获取锁失败：%s", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
	                    return false;
	                } else {
	                    return false;//不需要等待，直接退出。
	                }
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	    }
release lock 释放lock

 /**
	     * 释放redis lock。
	     *
	     * @param lockKey lock key
	     * @throws Exception lockKey is empty throw exception.
	     */
	    public Boolean releaseLockWithTimeout(String lockKey) throws Exception {
	        if (StringUtils.isEmpty(lockKey)) throw new Exception("lockKey is empty.");

	        boolean status = redisServiceImp.removeValue(lockKey);
	        if (status) {
	            logger.info(String.
	                    format("t:%s,当前节点：%s,释放锁：%s 成功。", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
	            return true;
	        }
	        logger.info(String.
	                format("t:%s,当前节点：%s,释放锁：%s 失败。", Thread.currentThread().getId(), getRedisIdentityKey(), lockKey));
	        return false;
	    }
      
      
      =========demo 演示:=========

本地启动微服务后,通过浏览器访问
http://127.0.0.1:10001/locker/lock 获取锁:(第一次访问获取锁成功,第二次访问则'尝试等待获取锁',直至重试获取锁超过最大重试次数后'指定时间内获取锁失败')


[main] [34mINFO [0;39m [36mc.t.BootRedisApplication[0;39m - [34mStarted BootRedisApplication in 7.647 seconds (JVM running for 8.604)[0;39m 
[http-nio-10001-exec-1] [34mINFO [0;39m [36mo.a.c.c.C.[.[.[/][0;39m - [34mInitializing Spring FrameworkServlet 'dispatcherServlet'[0;39m 
[http-nio-10001-exec-1] [34mINFO [0;39m [36mo.s.w.s.DispatcherServlet[0;39m - [34mFrameworkServlet 'dispatcherServlet': initialization started[0;39m 
[http-nio-10001-exec-1] [34mINFO [0;39m [36mo.s.w.s.DispatcherServlet[0;39m - [34mFrameworkServlet 'dispatcherServlet': initialization completed in 22 ms[0;39m 
[http-nio-10001-exec-1] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:27,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,获取到锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,尝试等待获取锁：product:10100101:shopping[0;39m 
[http-nio-10001-exec-2] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:28,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,指定时间内获取锁失败：product:10100101:shopping[0;39m 



http://127.0.0.1:10001/locker/releaseLock 释放锁(锁被释放后访问http://127.0.0.1:10001/locker/lock 可重新获取到锁):

[http-nio-10001-exec-7] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:33,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,释放锁：product:10100101:shopping 成功。[0;39m 
[http-nio-10001-exec-8] [34mINFO [0;39m [36mc.t.l.RedisLocker[0;39m - [34mt:34,当前节点：92be85c4-a770-40aa-b85b-c4b8a924c5ec,获取到锁：product:10100101:shopping[0;39m 
      
      
