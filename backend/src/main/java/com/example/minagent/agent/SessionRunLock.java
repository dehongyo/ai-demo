package com.example.minagent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class SessionRunLock {

    private static final Logger log = LoggerFactory.getLogger(SessionRunLock.class);

    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public boolean acquire(UUID sessionId) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        boolean acquired = lock.tryLock();
        if (acquired) {
            log.debug("Lock acquired for session {}", sessionId);
        }
        return acquired;
    }

    public void release(UUID sessionId) {
        ReentrantLock lock = locks.get(sessionId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released for session {}", sessionId);
        }
    }
}
