#!/bin/sh

REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"

# 复用已存在的 Redis；连不上时再启动容器内 Redis。
if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping | grep -q "PONG"; then
    echo "Redis already available at $REDIS_HOST:$REDIS_PORT"
else
    echo "Starting Redis..."
    redis-server --bind "$REDIS_HOST" --port "$REDIS_PORT" --protected-mode no --daemonize yes

    sleep 1
    if ! redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping | grep -q "PONG"; then
        echo "Redis failed to start"
        exit 1
    fi
    echo "Redis started successfully"
fi

# 启动 Spring Boot 应用
echo "Starting Spring Boot application..."
exec java $JAVA_OPTS -jar app.jar
