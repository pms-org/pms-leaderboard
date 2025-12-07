package com.pms.leaderboard.services;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.MessageDTO;

@Service
public class MessageQueueService {

    private final ConcurrentLinkedQueue<MessageDTO> pending = new ConcurrentLinkedQueue<>();
    private final Map<UUID, MessageDTO> latestMap = new ConcurrentHashMap<>();

    public void enqueue(MessageDTO ev) {
        if (ev == null || ev.getPortfolioId() == null) return;
        pending.add(ev);
        latestMap.put(ev.getPortfolioId(), ev);
    }

    public Map<UUID, MessageDTO> flushLatestBatch() {
        pending.clear();
        Map<UUID, MessageDTO> batch = new HashMap<>(latestMap);
        latestMap.clear();
        return batch;
    }

    public boolean isEmpty() {
        return latestMap.isEmpty();
    }
    
}
