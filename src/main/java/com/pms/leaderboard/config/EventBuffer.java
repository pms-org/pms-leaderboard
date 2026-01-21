package com.pms.leaderboard.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;

@Component
public class EventBuffer {

    private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);

    private final List<MessageDTO> buffer = new ArrayList<>();

    private static final int BUFFER_LIMIT = 50; // bigger batch
    private static final long FLUSH_INTERVAL_MS = 20;

    @Autowired
    LeaderboardService leaderboardService;

    // public synchronized void add(MessageDTO dto) {
    //     buffer.add(dto);
    //     flush();
    //     if (buffer.size() >= BUFFER_LIMIT) {
    //         flush();
    //     }
    // }
    public synchronized void addAll(List<MessageDTO> list) {
        buffer.addAll(list);
        log.info("Buffer size after add: {}", buffer.size());
        System.out.println("Buffer size after add: {}" + buffer.size());
        // flush();
        if (buffer.size() >= BUFFER_LIMIT) {
            log.info("BUFFER FULL ({}). Triggering flush.", buffer.size());
            System.out.println("BUFFER FULL ({}). Triggering flush." + buffer.size());
            flush();
        }
    }

    // @Scheduled(fixedRate = 1000)
    // public synchronized void timeFlush() {
    //     log.info("Scheduled Flush Trigger at {}", System.currentTimeMillis());
    //     System.out.println("Scheduled Flush Trigger at {}" + System.currentTimeMillis());
    //     flush();
    // }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        log.info("FLUSHING {} items at {}", buffer.size(), System.currentTimeMillis());
        System.out.println("Buffer flushed at {}" + System.currentTimeMillis());

        List<MessageDTO> batch = new ArrayList<>(buffer);
        buffer.clear();

        log.debug("Flushing {} events", batch.size());
        System.out.println("Flushing {} events" + batch.size());

        leaderboardService.processBatch(batch);

        // try {
        //     wsHandler.broadcast(batch);
        //     log.info("WS sent at {}", System.currentTimeMillis());
        //     System.out.println("WS sent at {}" + System.currentTimeMillis());
        // } catch (Exception e) {
        //     log.warn("WebSocket broadcast failed", e);
        // }
    }
}
