package com.pms.leaderboard.exceptions;

public class WebSocketBroadcastException extends RuntimeException {
    public WebSocketBroadcastException(String message, Throwable cause) {
        super(message, cause);
    }
}
