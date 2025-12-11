package com.pms.leaderboard.exceptions;

public class DatabaseWriteException extends RuntimeException {
    public DatabaseWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
    
