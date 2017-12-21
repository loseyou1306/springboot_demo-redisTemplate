package com.test.locker;

public interface RedisLocker {
    Boolean acquireLockWithTimeout(int lockKeyExpireSecond, String lockKey, Boolean isWait) throws Exception;
    Boolean releaseLockWithTimeout(String lockKey) throws Exception;
}
