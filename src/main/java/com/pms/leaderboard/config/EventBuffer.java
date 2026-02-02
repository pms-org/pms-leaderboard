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

    private final BlockingQueue<MessageDTO> queue =
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    @Qualifier("processExecutor")
    private java.util.concurrent.ExecutorService processExecutor;

    /**
     * Kafka → EventBuffer
     * BLOCKS when full → Kafka backpressure
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

                while (batch.size() < BATCH_SIZE &&
                       (System.currentTimeMillis() - start) < FLUSH_INTERVAL_MS) {

                    MessageDTO next = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        batch.add(next);
                    }
                }

                List<MessageDTO> toProcess = new ArrayList<>(batch);
                batch.clear();

                leaderboardService.processBatch(toProcess);

            } catch (Exception e) {
                log.error("EventBuffer consumer failure", e);
                batch.clear();
            }
        }
    }
}




// package com.pms.leaderboard.config;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.concurrent.BlockingQueue;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.LinkedBlockingQueue;
// import java.util.concurrent.TimeUnit;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.context.annotation.Bean;
// import org.springframework.stereotype.Component;

// import com.pms.leaderboard.dto.MessageDTO;
// import com.pms.leaderboard.services.LeaderboardService;

// import jakarta.annotation.PostConstruct;

// @Component
// public class EventBuffer {

//     private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);

//     private static final int QUEUE_CAPACITY = 10_000;
//     private static final int BATCH_SIZE = 10;
//     private static final long FLUSH_INTERVAL_MS = 3000;

//     private final BlockingQueue<MessageDTO> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

//     @Autowired
//     private LeaderboardService leaderboardService;
    
//     @Autowired
//     @Qualifier("realtimeExecutor")
//     private ExecutorService realtimeExecutor;

//     @Autowired
//     @Qualifier("processExecutor")
//     private ExecutorService processExecutor;

//     @PostConstruct
//     public void start() {
//         processExecutor.submit(this::consumeLoop);
//     }

//     public void addAll(List<MessageDTO> list) {
//         for (MessageDTO msg : list) {
//             boolean offered = queue.offer(msg); // NON-BLOCKING

//             if (!offered) {
//                 log.error("❌ EVENT BUFFER FULL → DROPPING EVENT pid={}", msg.getPortfolioId());
//                 // Optional: send to Redis stream / DLQ here
//             }
//         }
//     }

//     // Consumer
//     private void consumeLoop() {

//         log.info("DEQUEUE thread={} batchSize={} queueRemaining={}",
//                 Thread.currentThread().getName(),
//                 queue.size());

//         List<MessageDTO> batch = new ArrayList<>(BATCH_SIZE);

//         while (true) {
//             try {
//                 // blocks until at least 1 element exists
//                 MessageDTO first = queue.take();
//                 batch.add(first);

//                 // drain more without blocking
//                 queue.drainTo(batch, BATCH_SIZE - 1);

//                 // OR time-based flush
//                 long start = System.currentTimeMillis();
//                 while (batch.size() < BATCH_SIZE
//                         && (System.currentTimeMillis() - start) < FLUSH_INTERVAL_MS) {
//                     MessageDTO next = queue.poll(100, TimeUnit.MILLISECONDS);
//                     if (next != null) {
//                         batch.add(next);
//                     }
//                 }

//                 log.info("Processing batch size={}", batch.size());

//                 List<MessageDTO> copy = new ArrayList<>(batch);
//                 processExecutor.submit(() -> leaderboardService.processBatch(copy));
//                 batch.clear();

//             } catch (Exception e) {
//                 log.error("Batch worker failure", e);
//                 batch.clear();
//             }
//         }
//     }
// }

// // @Component
// // public class EventBuffer {
// //     private static final Logger log = LoggerFactory.getLogger(EventBuffer.class);
// //     private final List<MessageDTO> buffer = new ArrayList<>();
// //     private static final int BUFFER_LIMIT = 10; // bigger batch
// //     private static final long FLUSH_INTERVAL_MS = 3000;
// //     @Autowired
// //     LeaderboardService leaderboardService;
// //     public synchronized void addAll(List<MessageDTO> list) {
// //         buffer.addAll(list);
// //         log.warn(" ADDALL size={} bufferNow={}", list.size(), buffer.size());;
// //         log.info("Buffer size after add: {}", buffer.size());
// //         if (buffer.size() >= BUFFER_LIMIT) {
// //             log.info(" BUFFER FULL ({}). Triggering flush.", buffer.size());
// //             flush();
// //         }
// //     }
// //     @Scheduled(fixedRate = 3000)
// //     public synchronized void timeFlush() {
// //         log.info(" Scheduled Flush Trigger at {}", System.currentTimeMillis());
// //         flush();
// //     }
// //     private synchronized void flush() {
// //         log.warn(" FLUSH THREAD = {}", Thread.currentThread().getName());
// //         if (buffer.isEmpty()) {
// //             return;
// //         }
// //         log.info("FLUSHING {} items at {}", buffer.size(), System.currentTimeMillis());
// //         List<MessageDTO> batch = new ArrayList<>(buffer);
// //         buffer.clear();
// //         log.debug("Flushing {} events", batch.size());
// //         leaderboardService.processBatch(batch);
// //     }
// // }
