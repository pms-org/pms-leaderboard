package com.pms.leaderboard.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;

@Component
public class EventBuffer {

    private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 50;
    private static final long FLUSH_INTERVAL_MS = 2000;

    private final BlockingQueue<MessageDTO> queue
            = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    @Qualifier("processExecutor")
    private java.util.concurrent.ExecutorService processExecutor;

    /**
     * Kafka → EventBuffer BLOCKS when full → Kafka backpressure
     */
    public void addAll(List<MessageDTO> events) {
        for (MessageDTO event : events) {
            try {
                queue.put(event); // blocks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while buffering", e);
            }
        }
        log.debug("Buffered {} events, bufferSize={}", events.size(), queue.size());
    }

    /**
     * Background consumer
     */
    @PostConstruct
    public void startConsumer() {
        processExecutor.submit(this::consumeLoop);
    }

    private void consumeLoop() {

        List<MessageDTO> batch = new ArrayList<>(BATCH_SIZE);

        while (true) {
            try {
                // wait for first event
                MessageDTO first = queue.take();
                batch.add(first);

                long start = System.currentTimeMillis();

                while (batch.size() < BATCH_SIZE
                        && (System.currentTimeMillis() - start) < FLUSH_INTERVAL_MS) {

                    MessageDTO next = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        batch.add(next);
                    }
                }

                List<MessageDTO> toProcess = new ArrayList<>(batch);
                batch.clear();

                leaderboardService.processBatch(toProcess);

            } catch (Exception e) {

                log.error("EventBuffer consumer failure — requeueing batch", e);

                for (MessageDTO msg : batch) {
                    try {
                        queue.put(msg); // put back
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                batch.clear();

                // small pause prevents hot failure loop
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }

        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

}
