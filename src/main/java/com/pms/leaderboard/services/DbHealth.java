package com.pms.leaderboard.services;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

@Component
public class DbHealth {

    private static final org.slf4j.Logger log
            = org.slf4j.LoggerFactory.getLogger(DbHealth.class);

    private final AtomicBoolean available = new AtomicBoolean(true);

    public boolean isAvailable() {
        return available.get();
    }

    public void down() {
        System.out.println(" ⚠️⚠️⚠️Database connection lost, pausing operations");
        log.warn("  ⚠️⚠️⚠️Database connection lost, pausing operations");
       if (available.compareAndSet(true, false)) {
            log.error("🟥🟥🟥 DATABASE MARKED DOWN");
        }
    }

    public String status() {
        return available.get() ? "UP" : "DOWN";
    }

    public void up() {
        System.out.println(" 🔥🔥🔥🔥Database connection restored, resuming operations");
        log.warn("  🔥🔥🔥🔥Database connection restored, resuming operations");
        if (available.compareAndSet(false, true)) {
            log.info("🟩🟩🟩 DATABASE MARKED UP");
        }
    }

    // private volatile boolean available = true;
    // public boolean isAvailable() {
    //     return available;
    // }
    // public void down() {
    //     System.out.println(" ⚠️⚠️⚠️Database connection lost, pausing operations");
    //     available = false;
    // }
    // public void up() {
    //     System.out.println(" 🔥🔥🔥🔥Database connection restored, resuming operations");
    //     available = true;
    // }
}
