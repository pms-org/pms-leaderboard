package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;

import jakarta.annotation.PostConstruct;

@Service
public class LeaderboardStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardStreamConsumer.class);

    private static final String STREAM_KEY = "leaderboard:stream";
    private static final String RETRY_STREAM = "leaderboard:stream:retry";
    private static final String DLQ_KEY = "leaderboard:dlq";
    private static final String GROUP = "leaderboard-db-group";
    private static final String CONSUMER = "db-writer-" + UUID.randomUUID();
    private static final String RETRY_HASH = "leaderboard:retry";

    private static final int MAX_RETRIES = 5;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PersistSnapshot persist;

    @Autowired
    private DbHealth dbHealth;

    @Autowired
    @Qualifier("dbExecutor")
    private ExecutorService dbExecutor;

    // ---------- INIT ----------
    @PostConstruct
    public void init() {

        try {
            redis.opsForStream().createGroup(STREAM_KEY, GROUP);
        } catch (Exception ignored) {
        }
        try {
            redis.opsForStream().createGroup(RETRY_STREAM, GROUP);
        } catch (Exception ignored) {
        }

        dbExecutor.submit(this::mainLoop);
        dbExecutor.submit(this::retryLoop);

        log.info("✅ Stream consumers started");
    }

    // ---------- MAIN STREAM LOOP ----------
    private void mainLoop() {
        while (true) {
            try {

                if (!dbHealth.isAvailable()) {
                    Thread.sleep(1000);
                    continue;
                }

                List<MapRecord<String, Object, Object>> records
                        = redis.opsForStream().read(
                                Consumer.from(GROUP, CONSUMER),
                                StreamReadOptions.empty().count(20).block(Duration.ofSeconds(2)),
                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                        );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (var msg : records) {
                    process(msg);
                }

            } catch (Exception e) {
                log.error("Main consumer loop error", e);
                sleep(1000);
            }
        }
    }

    // ---------- RETRY LOOP ----------
    private void retryLoop() {
        while (true) {
            try {

                if (!dbHealth.isAvailable()) {
                    Thread.sleep(2000);
                    continue;
                }

                List<MapRecord<String, Object, Object>> records
                        = redis.opsForStream().read(
                                Consumer.from(GROUP, CONSUMER),
                                StreamReadOptions.empty().count(10).block(Duration.ofSeconds(3)),
                                StreamOffset.create(RETRY_STREAM, ReadOffset.lastConsumed())
                        );

                if (records == null || records.isEmpty()) {
                    continue;
                }

                for (var msg : records) {
                    try {
                        persist.persistSnapshot(List.of(map(msg)));
                        redis.opsForStream().acknowledge(RETRY_STREAM, GROUP, msg.getId());
                    } catch (Exception e) {
                        moveToDLQ(msg);
                        redis.opsForStream().acknowledge(RETRY_STREAM, GROUP, msg.getId());
                    }
                }

            } catch (Exception e) {
                log.error("Retry loop error", e);
                sleep(2000);
            }
        }
    }

    // ---------- PROCESS ----------
    private void process(MapRecord<String, Object, Object> msg) {

        String id = msg.getId().toString();
        int retry = redis.opsForHash().get(RETRY_HASH, id) == null ? 0
                : Integer.parseInt(redis.opsForHash().get(RETRY_HASH, id).toString());

        try {

            if (!dbHealth.isAvailable()) {
                log.warn("⏸ DB DOWN → message kept in PEL");
                return; // no ack, no retry
            }

            persist.persistSnapshot(List.of(map(msg)));

            redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
            redis.opsForHash().delete(RETRY_HASH, id);

        } catch (Exception ex) {

            if (!dbHealth.isAvailable()) {
                log.warn("⏸ DB DOWN → leave pending");
                return;
            }

            if (retry + 1 >= MAX_RETRIES) {
                moveToDLQ(msg);
                redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
                redis.opsForHash().delete(RETRY_HASH, id);
            } else {
                redis.opsForHash().increment(RETRY_HASH, id, 1);
                redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
                redis.opsForStream().add(RETRY_STREAM, msg.getValue());
            }
        }
    }

    // ---------- DLQ ----------
    private void moveToDLQ(MapRecord<String, Object, Object> msg) {
        log.error("❌ MOVING TO DLQ id={}", msg.getId());
        redis.opsForStream().add(DLQ_KEY, msg.getValue());
    }

    // ---------- MAP ----------
    private BatchDTO map(MapRecord<String, Object, Object> r) {
        Map<Object, Object> v = r.getValue();
        return new BatchDTO(
                UUID.fromString(v.get("portfolioId").toString()),
                new BigDecimal(v.get("score").toString()),
                Long.parseLong(v.get("rank").toString()),
                new BigDecimal(v.get("avgRateOfReturn").toString()),
                new BigDecimal(v.get("sharpeRatio").toString()),
                new BigDecimal(v.get("sortinoRatio").toString())
        );
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignored) {
        }
    }
}

// @Service
// public class LeaderboardStreamConsumer {
//     private static final Logger log = LoggerFactory.getLogger(LeaderboardStreamConsumer.class);
//     private static final String STREAM_KEY = "leaderboard:stream";
//     private static final String GROUP = "leaderboard-db-group";
//     private static final String CONSUMER = "db-writer-" + UUID.randomUUID();
//     private static final String RETRY_HASH = "leaderboard:retry";
//     private static final String DLQ_KEY = "leaderboard:dlq";
//     private static final int MAX_RETRIES = 5;
//     private static final Duration VISIBILITY_TIMEOUT = Duration.ofSeconds(15);
//     @Autowired
//     private StringRedisTemplate redis;
//     @Autowired
//     private PersistSnapshot persist;
//     @Autowired
//     private DbHealth dbHealth;
//      @Autowired
//     @Qualifier("dbExecutor")
//     private ExecutorService dbExecutor;
//     @PostConstruct
//     public void initGroup() {
//         try {
//             redis.opsForStream().createGroup(STREAM_KEY, GROUP);
//             log.info("Created Redis consumer group {}", GROUP);
//         } catch (Exception e) {
//             log.info("Group already exists");
//         }
//     }
//     @Scheduled(fixedDelay = 1000)
//     public void pollNew() {
//         if (!dbHealth.isAvailable()) {
//             log.warn(" 👎👎👎 DB DOWN → Redis consumer paused");
//             return;
//         }
//         List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
//                 Consumer.from(GROUP, CONSUMER),
//                 StreamReadOptions.empty().count(20),
//                 StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
//         );
//         if (records == null || records.isEmpty()) {
//             return;
//         }
//         for (MapRecord<String, Object, Object> msg : records) {
//             process(msg);
//         }
//     }
//     private void process(MapRecord<String, Object, Object> msg) {
//         String id = msg.getId().toString();
//         Object retryObj = redis.opsForHash().get(RETRY_HASH, id);
//         int retry = retryObj == null ? 0 : Integer.parseInt(retryObj.toString());
//         try {
//             // if (true) {
//             //     throw new RuntimeException("FORCED ERROR → DLQ TEST");
//             // }
//             persist.persistSnapshot(List.of(map(msg)));
//             redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
//             redis.opsForHash().delete(RETRY_HASH, id);
//         } catch (Exception ex) {
//             if (!dbHealth.isAvailable()) {
//                 log.error(" 😒😒😒 DB DOWN → message stays in stream (no retry, no ack)");
//                 return;
//             }
//             log.error("Error processing {} retry={}", id, retry);
//             if (retry + 1 >= MAX_RETRIES) {
//                 moveToDLQ(msg);
//                 redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
//                 redis.opsForHash().delete(RETRY_HASH, id);
//             } else {
//                 redis.opsForHash().increment(RETRY_HASH, id, 1);
//                 // requeue with delay semantics (soft retry)
//                 redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
//                 redis.opsForStream().add(
//                         "leaderboard:stream:retry",
//                         msg.getValue()
//                 );
//             }
//         }
//     }
//     // Claim stuck messages (PEL)
//     @Scheduled(fixedDelay = 5000)
//     public void reclaimStuck() {
//         if (!dbHealth.isAvailable()) {
//             return;
//         }
//         PendingMessages pending = redis.opsForStream().pending(
//                 STREAM_KEY,
//                 GROUP,
//                 Range.unbounded(),
//                 50
//         );
//         if (pending == null || pending.isEmpty()) {
//             return;
//         }
//         for (PendingMessage p : pending) {
//             long idle = p.getElapsedTimeSinceLastDelivery().toMillis();
//             if (idle > VISIBILITY_TIMEOUT.toMillis()) {
//                 List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(
//                         STREAM_KEY,
//                         GROUP,
//                         CONSUMER,
//                         XClaimOptions.minIdle(VISIBILITY_TIMEOUT).ids(p.getId())
//                 );
//                 if (claimed != null) {
//                     for (MapRecord<String, Object, Object> msg : claimed) {
//                         process(msg);
//                     }
//                 }
//             }
//         }
//     }
//     @Scheduled(fixedDelay = 3000)
//     public void pollRetryStream() {
//         if (!dbHealth.isAvailable()) {
//             return;
//         }
//         List<MapRecord<String, Object, Object>> records
//                 = redis.opsForStream().read(
//                         Consumer.from(GROUP, CONSUMER),
//                         StreamReadOptions.empty().count(10),
//                         StreamOffset.create("leaderboard:stream:retry", ReadOffset.lastConsumed())
//                 );
//         if (records == null) {
//             return;
//         }
//         for (var msg : records) {
//             try {
//                 dbExecutor.submit(() -> persist.persistSnapshot(List.of(map(msg))));
//                 redis.opsForStream().acknowledge("leaderboard:stream:retry", GROUP, msg.getId());
//             } catch (Exception e) {
//                 log.error("Retry stream failed again → DLQ");
//                 moveToDLQ(msg);
//             }
//         }
//     }
//     //  Move failed message to DLQ stream
//     private void moveToDLQ(MapRecord<String, Object, Object> msg) {
//         log.error("❌ MOVING {} → DLQ", msg.getId());
//         log.error(
//                 "DLQ: id={} after {} retries payload={}",
//                 msg.getId(),
//                 msg.getValue()
//         );
//         redis.opsForStream().add(
//                 StreamRecords.newRecord()
//                         .in(DLQ_KEY)
//                         .ofMap(msg.getValue())
//         );
//     }
//     // Map Redis stream values to your DTO
//     private BatchDTO map(MapRecord<String, Object, Object> r) {
//         Map<Object, Object> v = r.getValue();
//         return new BatchDTO(
//                 UUID.fromString(v.get("portfolioId").toString()),
//                 new BigDecimal(v.get("score").toString()),
//                 Long.parseLong(v.get("rank").toString()),
//                 new BigDecimal(v.get("avgRateOfReturn").toString()),
//                 new BigDecimal(v.get("sharpeRatio").toString()),
//                 new BigDecimal(v.get("sortinoRatio").toString())
//         );
//     }
// }
