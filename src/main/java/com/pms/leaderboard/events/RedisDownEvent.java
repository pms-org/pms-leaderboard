package com.pms.leaderboard.events;

import org.springframework.context.ApplicationEvent;

public class RedisDownEvent extends ApplicationEvent {

    public RedisDownEvent(Object source) {
        super(source);
    }
}
