FROM redis:7

RUN mkdir -p /data && chown redis:redis /data
COPY sentinel.conf /data/sentinel.conf

USER redis
CMD ["redis-sentinel", "/data/sentinel.conf"]
