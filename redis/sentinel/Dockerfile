FROM redis:7
COPY sentinel.conf /etc/redis/sentinel.conf
RUN chown redis:redis /etc/redis/sentinel.conf
USER redis
CMD ["redis-sentinel", "/etc/redis/sentinel.conf"]
