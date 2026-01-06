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

    // @Value("${spring.application.name}")
    // private String appName;
    // private String consumerName = appName + "-" + UUID.randomUUID();
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
            log.info("Redis stream group created");
        } catch (Exception e) {
            log.info("Redis stream group already exists");
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {

        log.debug("Polling Redis stream");

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
//     private void consumeInternal(List<MapRecord<String, Object, Object>> records) {
//         if (records.isEmpty()) {
//             return;
//         }
//         List<BatchDTO> batch = new ArrayList<>();
//         for (MapRecord<String, Object, Object> r : records) {
//             Map<Object, Object> v = r.getValue();
//             BatchDTO dto = new BatchDTO(
//                     UUID.fromString(v.get("portfolioId").toString()),
//                     new BigDecimal(v.get("score").toString()),
//                     Long.parseLong(v.get("rank").toString()),
//                     new BigDecimal(v.get("avgRateOfReturn").toString()),
//                     new BigDecimal(v.get("sharpeRatio").toString()),
//                     new BigDecimal(v.get("sortinoRatio").toString())
//             );
//             batch.add(dto);
//         }
//         try {
//             persistSnapshotService.persistSnapshot(batch);
//             redis.opsForStream().acknowledge(STREAM_KEY, GROUP, ids);
//             log.info("Persisted & ACKed batch size={}", batch.size());
//         } catch (Exception e) {
//             log.error("DB failed — NOT ACKING, will retry", e);
//         }
//     }
    private void consumeInternal(List<MapRecord<String, Object, Object>> records) {
        if (records.isEmpty()) {
            return;
        }

        log.info("Stream records received count={}", records.size());

        List<BatchDTO> batch = new ArrayList<>();
        List<String> ids = new ArrayList<>(); // ✅ Collect record IDs

        for (MapRecord<String, Object, Object> r : records) {

            log.debug("Stream record id={} value={}", r.getId(), r.getValue());

            ids.add(r.getId().getValue()); // ✅ Save ID for ACK

            Map<Object, Object> v = r.getValue();

            BatchDTO dto = new BatchDTO(
                    UUID.fromString(v.get("portfolioId").toString()),
                    new BigDecimal(v.get("score").toString()),
                    Long.parseLong(v.get("rank").toString()),
                    new BigDecimal(v.get("avgRateOfReturn").toString()),
                    new BigDecimal(v.get("sharpeRatio").toString()),
                    new BigDecimal(v.get("sortinoRatio").toString())
            );

            log.debug("BatchDTO mapped {}", dto);
            batch.add(dto);
        }

        try {
            // ✅ Persist to DB in a single transaction
            persistSnapshotService.persistSnapshot(batch);

            // ✅ Only ACK after DB commit
            redis.opsForStream().acknowledge(
                    STREAM_KEY,
                    GROUP,
                    ids.toArray(String[]::new)
            );

            log.info("Persisted & ACKed batch size={}", batch.size());

        } catch (Exception e) {
            log.error("DB failed — NOT ACKING, will retry", e);
            // no ACK → message stays in stream for retry
        }
    }

}
