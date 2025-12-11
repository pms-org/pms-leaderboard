package com.pms.leaderboard.exceptions;

public class RedisUnavailableException extends RuntimeException {
    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

