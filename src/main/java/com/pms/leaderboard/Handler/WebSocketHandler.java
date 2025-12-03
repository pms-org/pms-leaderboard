package com.pms.leaderboard.Handler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Autowired
    ObjectMapper mapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("Client Connected: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    // public void broadcast(String message) throws Exception {
    //     for (WebSocketSession session : sessions) {
    //         session.sendMessage(new TextMessage(message));
    //     }
    // }

    // Broadcast a POJO or a string
    public void broadcast(Object payload) {
        try {
            String text = (payload instanceof String) ? (String) payload : mapper.writeValueAsString(payload);
            TextMessage msg = new TextMessage(text);
            sessions.forEach(s -> {
                try {
                    if (s.isOpen()) s.sendMessage(msg);
                } catch (Exception e) {
                    // ignore individual send failures
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
