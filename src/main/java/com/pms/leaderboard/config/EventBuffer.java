package com.pms.leaderboard.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;

@Component
public class EventBuffer {

    private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);

    private final List<MessageDTO> buffer = new ArrayList<>();

    private static final int BUFFER_LIMIT = 10; // bigger batch
    private static final long FLUSH_INTERVAL_MS = 200;

    @Autowired
    LeaderboardService leaderboardService;

    public synchronized void addAll(List<MessageDTO> list) {
        buffer.addAll(list);

        log.warn(" ADDALL size={} bufferNow={}", list.size(), buffer.size());;
        log.info("Buffer size after add: {}", buffer.size());

        if (buffer.size() >= BUFFER_LIMIT) {
            log.info(" BUFFER FULL ({}). Triggering flush.", buffer.size());
            flush();
        }
    }

    @Scheduled(fixedRate = 3000)
    public synchronized void timeFlush() {
        log.info(" Scheduled Flush Trigger at {}", System.currentTimeMillis());
        flush();
    }

    private synchronized void flush() {

        log.warn(" FLUSH THREAD = {}", Thread.currentThread().getName());

        if (buffer.isEmpty()) {
            return;
        }

        log.info("FLUSHING {} items at {}", buffer.size(), System.currentTimeMillis());

        List<MessageDTO> batch = new ArrayList<>(buffer);
        buffer.clear();

        log.debug("Flushing {} events", batch.size());

        leaderboardService.processBatch(batch);

    }
}
