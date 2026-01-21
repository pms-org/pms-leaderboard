package com.pms.leaderboard.Handler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.pms.leaderboard.dto.LeaderboardDTO;
import com.pms.leaderboard.services.LeaderboardService;
import tools.jackson.databind.ObjectMapper;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Autowired
    ObjectMapper mapper;

    @Autowired
    LeaderboardService leaderboardService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("Client Connected: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    public void broadcastSnapshot(List<LeaderboardDTO> list) {

        Map<String, Object> payload = Map.of(
                "event", "leaderboardSnapshot",
                "timestamp", Instant.now().toEpochMilli(),
                "top", list
        );

        sessions.forEach(session -> {
            try {
                session.sendMessage(
                        new TextMessage(mapper.writeValueAsString(payload))
                );
            } catch (Exception e) {
                log.warn("WS send failed", e);
            }
        });
    }

}
