# PMS Leaderboard - Comprehensive Test Guide

## 1. BASIC SYSTEM TEST (5 minutes)

### 1.1 Check All Services are Healthy
```bash
docker ps  # Verify 12 containers running
```

### 1.2 Check App is Running
```bash
curl http://localhost:8000/api/leaderboard/top?top=5
```
**Expected:** JSON with top 5 portfolios

### 1.3 Check WebSocket Connection
Open browser: `http://localhost:8000`
**Expected:** Live leaderboard table with scores updating every 250ms

### 1.4 Check Kafka is Producing
```bash
docker logs pms-leaderboard-backend 2>&1 | grep -i "Produced message"
```
**Expected:** See "Produced message at" logs every 1 second

### 1.5 Check Redis is Working
```bash
docker exec redis-master redis-cli ZCARD leaderboard:global:daily
```
**Expected:** Number > 0 (portfolios in leaderboard)

---

## 2. REDIS HEARTBEAT TEST (2 minutes)

### Test: Verify Redis Health Monitoring
```bash
docker logs pms-leaderboard-backend 2>&1 | grep -i "Pinging Redis"
```
**Expected:** See every 3 seconds:
```
Pinging Redis to check health...
Redis ping successful
```

---

## 3. REDIS DOWN RECOVERY TEST (10 minutes)

### 3.1 Stop Redis Cluster
```bash
docker stop redis-master redis-replica-1 redis-replica-2 redis-sentinel-1 redis-sentinel-2 redis-sentinel-3
```

### 3.2 Watch for DOWN Event (3-5 seconds)
```bash
docker logs pms-leaderboard-backend --tail 50 2>&1 | grep -i "MARKED DOWN"
```
**Expected:**
```
🟥🟥🟥🟥 REDIS MARKED DOWN
Redis reported DOWN — stopping Kafka listeners
```

### 3.3 Verify Kafka Stopped (messages buffering)
```bash
docker logs pms-leaderboard-backend --tail 20 2>&1 | grep -i "stopping Kafka"
```
**Expected:** See recovery service stopping listeners

### 3.4 Check EventBuffer is Buffering
- Kafka should NOT be processing (no "Kafka batch received" logs)
- Messages stack up in EventBuffer memory queue

### 3.5 Restart Redis
```bash
docker start redis-master redis-replica-1 redis-replica-2 redis-sentinel-1 redis-sentinel-2 redis-sentinel-3
```

### 3.6 Watch for UP Event (5-10 seconds)
```bash
docker logs pms-leaderboard-backend --tail 50 2>&1 | grep -i "MARKED UP"
```
**Expected:**
```
🟩🟩🟩🟩 REDIS MARKED UP
Redis reported UP — triggering Kafka replay
```

### 3.7 Verify Recovery Complete
```bash
docker logs pms-leaderboard-backend --tail 30 2>&1 | grep -i "REDIS WRITE OK"
```
**Expected:** Buffered messages being replayed to Redis

### 3.8 Check Leaderboard Updated
```bash
curl http://localhost:8000/api/leaderboard/top?top=5
```
**Expected:** Latest portfolio data (timestamps recent)

---

## 4. DATABASE DOWN RECOVERY TEST (5 minutes)

### 4.1 Stop PostgreSQL
```bash
docker stop pms-leaderboard-postgres
```

### 4.2 Check DB Health Down (10 seconds)
```bash
docker logs pms-leaderboard-backend --tail 20 2>&1 | grep -i "DATABASE MARKED DOWN"
```
**Expected:** DB health marked as down

### 4.3 Verify App Still Running
```bash
curl http://localhost:8000/api/leaderboard/top?top=5
```
**Expected:** Still returns leaderboard (from Redis cache)

### 4.4 Restart PostgreSQL
```bash
docker start pms-leaderboard-postgres
```

### 4.5 Check DB Recovered
```bash
docker logs pms-leaderboard-backend --tail 20 2>&1 | grep -i "DATABASE MARKED UP"
```
**Expected:** DB health restored

---

## 5. KAFKA CONSUMER TEST (5 minutes)

### 5.1 Check Message Processing Rate
```bash
docker logs pms-leaderboard-backend 2>&1 | grep "Kafka batch received" | tail -10
```
**Expected:** ~1 batch per second

### 5.2 Check Batch Processing Time
```bash
docker logs pms-leaderboard-backend 2>&1 | grep "Processing batch size" | head -5
```
**Expected:** Batch sizes around 25-50 messages

### 5.3 Monitor EventBuffer Size
```bash
docker logs pms-leaderboard-backend 2>&1 | grep "Buffered.*events" | tail -5
```
**Expected:** Should show buffer managing incoming events

---

## 6. LEADERBOARD RANKING TEST (3 minutes)

### 6.1 Get Top 10
```bash
curl http://localhost:8000/api/leaderboard/top?top=10 | jq '.top[] | {rank, portfolioId, compositeScore}'
```
**Expected:** 
- Ranks 1-10
- Composite scores decreasing
- All valid UUIDs

### 6.2 Get Portfolio Around Rank
```bash
curl "http://localhost:8000/api/leaderboard/around?portfolioId=b3a1f250-0d4f-4b53-b0c9-651be64225f9&range=5" | jq '.'
```
**Expected:**
- centerRank and surrounding portfolios
- Rank range = centerRank ± range

### 6.3 Check Ranking Consistency
```bash
# Run this 3 times (should be same or higher rank for winners)
curl http://localhost:8000/api/leaderboard/top?top=1 | jq '.top[0] | {rank, portfolioId, compositeScore}'
```
**Expected:** Rank changes as scores change, but top portfolios consistent

---

## 7. REAL-TIME BROADCAST TEST (3 minutes)

### 7.1 Open WebSocket Console
```javascript
// In browser DevTools Console:
ws = new WebSocket("ws://localhost:8000/ws/updates");
ws.onmessage = (evt) => {
  msg = JSON.parse(evt.data);
  console.log(`${msg.top[0].rank}. ${msg.top[0].portfolioId.slice(0,8)}... Score: ${msg.top[0].compositeScore.toFixed(2)}`);
};
```

### 7.2 Verify Updates Every 250ms
**Expected:** Console logs every ~250ms showing top portfolio changing

### 7.3 Check Multiple Clients
Open leaderboard in 2 browser tabs
**Expected:** Both update simultaneously

---

## 8. RETRY LOGIC TEST (5 minutes)

### 8.1 Induce Transient Error (Docker restart Redis master)
```bash
docker restart redis-master
```

### 8.2 Check Retry Logs
```bash
docker logs pms-leaderboard-backend --tail 50 2>&1 | grep -i "retry\|attempt"
```
**Expected:**
```
Retry 1 for id=...
Retry 2 for id=...
REDIS WRITE OK (with retry)
```

### 8.3 Verify Data Not Lost
```bash
curl http://localhost:8000/api/leaderboard/top?top=5
```
**Expected:** All portfolios still in leaderboard

---

## 9. METRICS TEST (2 minutes)

### 9.1 Check Recovery Metrics
```bash
docker logs pms-leaderboard-backend 2>&1 | grep "METRIC:"
```
**Expected:** 
```
METRIC: redis.down.events = 1
METRIC: redis.up.events = 1
METRIC: redis.io.failures = X
```

---

## 10. LOAD TEST (Optional, 10 minutes)

### 10.1 Simulate High Throughput
```bash
# In app logs, monitor:
docker logs pms-leaderboard-backend -f 2>&1 | grep "Processing batch\|REDIS WRITE OK"
```
**Expected:** Sustained throughput without errors

### 10.2 Check Resource Usage
```bash
docker stats pms-leaderboard-backend
```
**Expected:**
- Memory stable < 512MB
- CPU varies based on load
- No OOMKilled messages

---

## 11. DATA PERSISTENCE TEST (5 minutes)

### 11.1 Check PostgreSQL Snapshots
```bash
docker exec pms-leaderboard-postgres psql -U leaderboard -d leaderboard_db -c "SELECT COUNT(*) FROM leaderboard_snapshot;"
```
**Expected:** Row count > 100 (continuously written)

### 11.2 Verify Latest Snapshot
```bash
docker exec pms-leaderboard-postgres psql -U leaderboard -d leaderboard_db -c "SELECT portfolio_id, leaderboard_ranking, portfolio_score, updated_at FROM leaderboard_snapshot ORDER BY updated_at DESC LIMIT 5;"
```
**Expected:** Recent timestamps, scores matching Redis

---

## 12. FAILURE SCENARIOS MATRIX

| Scenario | Action | Expected Result | Test Time |
|----------|--------|-----------------|-----------|
| Redis Down | Stop all Redis | DOWN event, Kafka pauses | 5 min |
| Redis Up | Start all Redis | UP event, Kafka resumes, replay | 5 min |
| DB Down | Stop PostgreSQL | Leaderboard still works (cached) | 5 min |
| DB Up | Start PostgreSQL | Snapshots resume writing | 2 min |
| Kafka Down | Stop Kafka | App logs error, circuit break | 3 min |
| Single Redis Node | Stop 1 replica | No impact (sentinel handles) | 2 min |
| Network Delay | Slow network* | Retries kick in | 5 min |

*Use `tc` (traffic control) on Linux or NetLimiter on Windows

---

## 13. QUICK TEST SCRIPT

```bash
#!/bin/bash

echo "=== 1. Checking all services ==="
docker ps -q | wc -l  # Should be 12

echo "=== 2. Testing API ==="
curl -s http://localhost:8000/api/leaderboard/top?top=5 | jq '.count'

echo "=== 3. Testing WebSocket ==="
docker logs pms-leaderboard-backend --tail 5 2>&1 | grep "WS broadcast"

echo "=== 4. Testing Kafka ==="
docker logs pms-leaderboard-backend --tail 5 2>&1 | grep "Kafka batch"

echo "=== 5. Testing Redis Heartbeat ==="
docker logs pms-leaderboard-backend --tail 5 2>&1 | grep "Pinging"

echo "=== 6. Testing Data Persistence ==="
docker exec pms-leaderboard-postgres psql -U leaderboard -d leaderboard_db -c "SELECT COUNT(*) FROM leaderboard_snapshot;"

echo "✅ All basic tests passed!"
```

---

## 14. TROUBLESHOOTING

### Issue: Leaderboard Not Updating
```bash
# Check if Kafka is receiving:
docker logs pms-leaderboard-backend 2>&1 | grep "Produced message"
# Check if Redis is available:
docker logs pms-leaderboard-backend 2>&1 | grep "MARKED DOWN"
# Check processing:
docker logs pms-leaderboard-backend 2>&1 | grep "Processing batch"
```

### Issue: WebSocket Not Updating
```bash
# Check browser console for errors
# Check if broadcaster is running:
docker logs pms-leaderboard-backend 2>&1 | grep "WS broadcast"
# Check connections:
docker logs pms-leaderboard-backend 2>&1 | grep "Client Connected"
```

### Issue: Recovery Not Triggering
```bash
# Check heartbeat:
docker logs pms-leaderboard-backend 2>&1 | grep "Pinging Redis"
# Check events:
docker logs pms-leaderboard-backend 2>&1 | grep "RedisDownEvent\|RedisUpEvent"
# Check recovery service:
docker logs pms-leaderboard-backend 2>&1 | grep "RedisRecoveryService"
```

---

## Quick Commands Cheat Sheet

```bash
# View app logs
docker logs pms-leaderboard-backend -f

# Check Redis
docker exec redis-master redis-cli KEYS "leaderboard*"
docker exec redis-master redis-cli ZCARD leaderboard:global:daily

# Check Kafka topics
docker exec pms-leaderboard-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check Database
docker exec pms-leaderboard-postgres psql -U leaderboard -d leaderboard_db -c "\dt"

# All in one test
docker logs pms-leaderboard-backend --tail 100 2>&1 | grep -i "redis\|kafka\|WRITE\|DOWN\|UP" | tail -20
```
