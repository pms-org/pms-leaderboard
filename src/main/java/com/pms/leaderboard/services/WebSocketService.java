package com.pms.leaderboard.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.MessageDTO;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebSocketService {

    @Autowired
    WebSocketHandler handler;

    @Autowired
    ObjectMapper mapper;

    @PostConstruct
    public void init() {
        System.out.println("Update Simulator started...");
    }

    // @Scheduled(fixedRate = 2000)
    public void sendUpdates() throws Exception {
        MessageDTO msg = new MessageDTO("Update Event from server");
        handler.broadcast(mapper.writeValueAsString(msg));
    }
    
}
