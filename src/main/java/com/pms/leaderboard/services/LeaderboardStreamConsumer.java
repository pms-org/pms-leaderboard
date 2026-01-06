package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;

import jakarta.annotation.PostConstruct;

@Service
public class LeaderboardStreamConsumer {

    private static final Logger log
            = LoggerFactory.getLogger(LeaderboardStreamConsumer.class);

    private static final String STREAM_KEY = "leaderboard:stream";
    private static final String GROUP = "leaderboard-db-group";
    private static final String CONSUMER = "db-writer-1";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PersistSnapshot persistSnapshotService;

    @PostConstruct
    public void initGroup() {
        try {
            redis.opsForStream().createGroup(STREAM_KEY, GROUP);
        } catch (Exception e) {
            // group already exists → ignore
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {

        // 1️⃣ Retry pending (un-ACKed) messages first
        consumeInternal(read(ReadOffset.from("0")));

        // 2️⃣ Process new messages
        consumeInternal(read(ReadOffset.lastConsumed()));
    }

    // -----------------------------
    // Read from stream
    // -----------------------------
    private List<MapRecord<String, Object, Object>> read(ReadOffset offset) {

        var records = redis.opsForStream().read(
                Consumer.from(GROUP, CONSUMER),
                StreamReadOptions.empty().count(100),
                StreamOffset.create(STREAM_KEY, offset)
        );

        if (records == null) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<MapRecord<String, Object, Object>> casted
                = (List<MapRecord<String, Object, Object>>) (List<?>) records;

        return casted;
    }

    // -----------------------------
    // Persist + ACK
    // -----------------------------
    private void consumeInternal(List<MapRecord<String, Object, Object>> records) {

        if (records.isEmpty()) {
            return;
        }

        List<BatchDTO> batch = new ArrayList<>();

        for (MapRecord<String, Object, Object> r : records) {

            Map<Object, Object> v = r.getValue();

            BatchDTO dto = new BatchDTO(
                    UUID.fromString(v.get("portfolioId").toString()),
                    new BigDecimal(v.get("score").toString()),
                    Long.parseLong(v.get("rank").toString()),
                    null
            );

            batch.add(dto);
        }

        try {
            persistSnapshotService.persistSnapshot(batch)
                    .thenRun(() -> {
                        String[] ids = records.stream()
                                .map(r -> r.getId().getValue())
                                .toArray(String[]::new);

                        redis.opsForStream().acknowledge(STREAM_KEY, GROUP, ids);

                        log.info("Persisted & ACKed leaderboard batch size={}", batch.size());
                    })
                    .exceptionally(ex -> {
                        log.error("DB failed — will retry via XPENDING", ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error(
                    "Failed to persist leaderboard snapshot. Will retry. batchSize={}",
                    batch.size(),
                    e
            );
        }
    }
}
