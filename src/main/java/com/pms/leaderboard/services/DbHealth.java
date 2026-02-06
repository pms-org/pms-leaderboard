package com.pms.leaderboard.services;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DbHealth {

    private static final Logger log
            = LoggerFactory.getLogger(DbHealth.class);

    private final AtomicBoolean available = new AtomicBoolean(true);

    public boolean isAvailable() {
        return available.get();
    }

    public void down() {
        log.warn("  Database connection lost, pausing operations");
       if (available.compareAndSet(true, false)) {
            log.error(" DATABASE MARKED DOWN");
        }
    }

    public String status() {
        return available.get() ? "UP" : "DOWN";
    }

    public void up() {
        log.warn(" Database connection restored, resuming operations");
        if (available.compareAndSet(false, true)) {
            log.info(" DATABASE MARKED UP");
        }
    }
}
