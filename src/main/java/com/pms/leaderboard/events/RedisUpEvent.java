package com.pms.leaderboard.events;

import org.springframework.context.ApplicationEvent;

public class RedisUpEvent extends ApplicationEvent {

    public RedisUpEvent(Object source) {
        super(source);
    }
}
