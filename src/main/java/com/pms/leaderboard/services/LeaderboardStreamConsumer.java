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
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.exceptions.DataValidationException;
import com.pms.leaderboard.exceptions.TransientDbException;

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
                    } catch (DataValidationException e) {
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

    private int getRetryCount(String id) {
        Object retryObj = redis.opsForHash().get(RETRY_HASH, id);
        return retryObj == null ? 0 : Integer.parseInt(retryObj.toString());
    }

    private void process(MapRecord<String, Object, Object> msg) {

        String id = msg.getId().toString();
        int retry = getRetryCount(id);

        // ---- HARD BACKPRESSURE ----
        if (!dbHealth.isAvailable()) {
            log.warn("⏸ DB DOWN → message kept in PEL");
            return;
        }

        try {
            persist.persistSnapshot(List.of(map(msg)));

            redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
            redis.opsForHash().delete(RETRY_HASH, id);

        } // ---------- DATA ERROR → DLQ IMMEDIATELY ----------
        catch (DataValidationException e) {
            log.error("❌ Data error → DLQ id={}", id);

            moveToDLQ(msg);
            redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
            redis.opsForHash().delete(RETRY_HASH, id);

        } // ---------- TRANSIENT ERROR → RETRY ----------
        catch (TransientDbException e) {

            if (retry + 1 >= MAX_RETRIES) {
                log.error("❌ Retries exhausted → DLQ id={}", id);

                moveToDLQ(msg);
                redis.opsForStream().acknowledge(STREAM_KEY, GROUP, id);
                redis.opsForHash().delete(RETRY_HASH, id);

            } else {
                log.warn("🔁 Retry {} for id={}", retry + 1, id);

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
