#!/bin/sh

# 启动 Redis 服务
echo "Starting Redis..."
redis-server --bind 127.0.0.1 --protected-mode no --daemonize yes

# 检查 Redis 是否启动成功
sleep 1
if ! redis-cli ping | grep -q "PONG"; then
    echo "Redis failed to start"
    exit 1
fi
echo "Redis started successfully"

# 启动 Spring Boot 应用
echo "Starting Spring Boot application..."
exec java $JAVA_OPTS -jar app.jar