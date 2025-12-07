package com.pms.leaderboard.services;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.Handler.WebSocketHandler;

@Service
public class StreamingService {

    @Autowired WebSocketHandler handler;

    public void broadcast(List<Map<String,Object>> rows) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "leaderboardSnapshot");
        payload.put("timestamp", Instant.now().toEpochMilli());
        payload.put("top", rows);
        handler.broadcast(payload);
    }
    
}
