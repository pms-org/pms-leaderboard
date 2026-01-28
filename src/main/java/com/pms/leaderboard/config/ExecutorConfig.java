package com.pms.leaderboard.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    @Bean(name = "realtimeExecutor")
    public ExecutorService realtimeExecutor() {
        return Executors.newFixedThreadPool(4);   // realtime only
    }

    @Bean(name = "redisExecutor")
    public ExecutorService redisExecutor() {
        return Executors.newFixedThreadPool(8);   // Redis IO only
    }

    @Bean(name = "processExecutor")
    public ExecutorService processExecutor() {
        return Executors.newFixedThreadPool(4); // CPU processing only
    }

    @Bean(name = "dbExecutor")
    public ExecutorService dbExecutor() {
        return Executors.newFixedThreadPool(2);   // DB only
    }
}
