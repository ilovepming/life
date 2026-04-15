package com.life.service;

public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}
