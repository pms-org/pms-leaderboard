package com.pms.leaderboard.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> notFound(ResourceNotFoundException ex) {
        log.warn("ResourceNotFound: {}", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Resource not found: " + ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<String> badRequest(BadRequestException ex) {
        log.warn("BadRequest: {}", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Bad request: " + ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> dbError(DataAccessException ex) {
        log.error("DB error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Data access exception occurred");
    }

    @ExceptionHandler(RedisUnavailableException.class)
    public ResponseEntity<String> handleRedisDown(RedisUnavailableException ex) {
        log.warn("Redis Unavailable exception: {}", ex);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Redis unavailable → fallback active");
    }

    @ExceptionHandler(DatabaseWriteException.class)
    public ResponseEntity<String> handleDbError(DatabaseWriteException ex) {
        log.warn("DataBase Write exception : {}", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("❌ Database write failed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex) {
        log.warn("Generic exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected error occurred");
    }

    @ExceptionHandler(WebSocketBroadcastException.class)
    public ResponseEntity<String> handleWsError(WebSocketBroadcastException ex) {
        log.error("WebSocket error", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("WebSocket broadcast failed");
    }

}
