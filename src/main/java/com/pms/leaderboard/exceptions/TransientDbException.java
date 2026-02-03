package com.pms.leaderboard.exceptions;

public class TransientDbException extends RuntimeException {

    public TransientDbException(Throwable cause) {
        super(cause);
    }
}
