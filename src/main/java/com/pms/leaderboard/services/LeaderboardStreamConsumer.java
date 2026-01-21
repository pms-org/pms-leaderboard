package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;

import jakarta.annotation.PostConstruct;

@Service
public class LeaderboardStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardStreamConsumer.class);

    private static final String STREAM_KEY = "leaderboard:stream";
    private static final String GROUP = "leaderboard-db-group";
    private static final String CONSUMER = "db-writer-" + UUID.randomUUID();
    private static final String RETRY_HASH = "leaderboard:retry";
    private static final String DLQ_KEY = "leaderboard:dlq";

    private static final int MAX_RETRIES = 5;
    private static final Duration VISIBILITY_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PersistSnapshot persist;

    @PostConstruct
    public void initGroup() {
        try {
            redis.opsForStream().createGroup(STREAM_KEY, GROUP);
            log.info("Created Redis consumer group {}", GROUP);
        } catch (Exception e) {
            log.info("Group already exists");
        }
    }

    /**
     * Poll NEW messages (XREADGROUP >)
     */
    @Scheduled(fixedDelay = 1000)
    public void pollNew() {

        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(GROUP, CONSUMER),
                StreamReadOptions.empty().count(20),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> msg : records) {
            process(msg);
        }
    }

    /**
     * Process new or claimed message, handle retries + DLQ
     */
    private void process(MapRecord<String, Object, Object> msg) {
        String id = msg.getId().toString();

        // Get retry count safely (without getOrDefault)
        Object retryObj = redis.opsForHash().get(RETRY_HASH, id);
        int retry = retryObj == null ? 0 : Integer.parseInt(retryObj.toString());

        try {
            
            if (true) {
                throw new RuntimeException("FORCED ERROR → DLQ TEST");
            }

            // persist.persistSnapshot(List.of(map(msg)));
            // ACK + clear retry counter
            redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
            redis.opsForHash().delete(RETRY_HASH, id);

        } catch (Exception ex) {

            log.error("Error processing {} retry={}", id, retry);

            if (retry + 1 >= MAX_RETRIES) {
                moveToDLQ(msg);
                redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
                redis.opsForHash().delete(RETRY_HASH, id);
            } else {
                redis.opsForHash().increment(RETRY_HASH, id, 1);
            }
        }
    }

    /**
     * Claim stuck messages (PEL)
     */
    @Scheduled(fixedDelay = 5000)
    public void reclaimStuck() {
        PendingMessages pending = redis.opsForStream().pending(
                STREAM_KEY,
                GROUP,
                Range.unbounded(),
                50
        );

        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (PendingMessage p : pending) {
            long idle = p.getElapsedTimeSinceLastDelivery().toMillis();

            if (idle > VISIBILITY_TIMEOUT.toMillis()) {
                List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(
                        STREAM_KEY,
                        GROUP,
                        CONSUMER,
                        XClaimOptions.minIdle(VISIBILITY_TIMEOUT).ids(p.getId())
                );

                if (claimed != null) {
                    for (MapRecord<String, Object, Object> msg : claimed) {
                        process(msg);
                    }
                }
            }
        }
    }

    /**
     * Move failed message to DLQ stream
     */
    private void moveToDLQ(MapRecord<String, Object, Object> msg) {
        log.error("❌ MOVING {} → DLQ", msg.getId());

        redis.opsForStream().add(
                StreamRecords.newRecord()
                        .in(DLQ_KEY)
                        .ofMap(msg.getValue())
        );
    }

    /**
     * Map Redis stream values to your DTO
     */
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
}
