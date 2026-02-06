package com.pms.leaderboard.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    /**
     * Generic bounded pool creator. Forces deterministic capacity.
     */
    private ExecutorService boundedExecutor(
            int threads,
            int queueSize,
            String threadName
    ) {

        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                r -> {
                    Thread t = new Thread(r);
                    t.setName(threadName + "-" + t.getId());
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Realtime → latency sensitive Keep queue SMALL.
     */
    @Bean(name = "realtimeExecutor")
    public ExecutorService realtimeExecutor() {
        return boundedExecutor(
                4, // CPU aligned
                500, // small queue → protects latency
                "realtime"
        );
    }

    /**
     * Redis → network IO Larger queue allowed because spikes are common.
     */
    @Bean(name = "redisExecutor")
    public ExecutorService redisExecutor() {
        return boundedExecutor(
                8, // IO threads > CPU is OK
                5000, // shock absorber
                "redis"
        );
    }

    /**
     * CPU deterministic work. NEVER allow infinite waiting here.
     */
    @Bean(name = "processExecutor")
    public ExecutorService processExecutor() {
        return boundedExecutor(
                Runtime.getRuntime().availableProcessors(),
                1000,
                "process"
        );
    }

    /**
     * DB → limited connections anyway. Keep queue controlled.
     */
    @Bean(name = "dbExecutor")
    public ExecutorService dbExecutor() {
        return boundedExecutor(
                2,
                500,
                "db"
        );
    }
}
