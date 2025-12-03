package com.pms.leaderboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // defaults to localhost:6379 (overridden by spring.redis properties if present)
        return new LettuceConnectionFactory();
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory connFactory) {
        RedisTemplate<String, String> rt = new RedisTemplate<>();
        rt.setConnectionFactory(connFactory);
        rt.afterPropertiesSet();
        return rt;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connFactory) {
        return new StringRedisTemplate(connFactory);
    }
}
