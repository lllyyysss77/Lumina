#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="${APP_NAME:-lumina}"
APP_PORT="${APP_PORT:-8080}"
MYSQL_HOST="${LUMINA_MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${LUMINA_MYSQL_PORT:-3306}"
MYSQL_DB="${LUMINA_MYSQL_DB:-lumina}"
MYSQL_USER="${LUMINA_MYSQL_USER:-root}"
MYSQL_PASSWORD="${LUMINA_MYSQL_PASSWORD:-root123}"
REDIS_HOST="${LUMINA_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${LUMINA_REDIS_PORT:-6379}"
DATA_DIR="${LUMINA_DATA_DIR:-/home/jojo/docker-data/lumina}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

cd "$(dirname "$0")"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  printf '[ERROR] %s\n' "$*" >&2
  exit 1
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" "$@"
  elif has_cmd docker-compose; then
    docker-compose -f "$COMPOSE_FILE" "$@"
  else
    fail "未找到 docker compose 或 docker-compose"
  fi
}

check_docker() {
  has_cmd docker || fail "未安装 Docker"

  if ! docker info >/dev/null 2>&1; then
    fail "Docker 未运行，先启动 Docker 服务后再执行部署脚本"
  fi

  if has_cmd systemctl && systemctl list-unit-files docker.service >/dev/null 2>&1; then
    if [ "$(id -u)" -eq 0 ]; then
      systemctl enable --now docker >/dev/null 2>&1 || true
    fi

    if systemctl is-enabled docker >/dev/null 2>&1; then
      log "Docker 服务已设置开机自启"
    else
      log "提示：Docker 服务未确认开机自启，可执行：sudo systemctl enable --now docker"
    fi
  fi
}

check_tcp() {
  local host="$1"
  local port="$2"
  timeout 3 bash -c ":</dev/tcp/${host}/${port}" >/dev/null 2>&1
}

prepare_directories() {
  mkdir -p "$DATA_DIR/logs"
  log "已准备数据目录：$DATA_DIR"
}

prepare_mysql() {
  log "检查 MySQL：${MYSQL_USER}@${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}"

  if has_cmd mysql; then
    MYSQL_PWD="$MYSQL_PASSWORD" mysql \
      -h "$MYSQL_HOST" \
      -P "$MYSQL_PORT" \
      -u "$MYSQL_USER" \
      -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
      >/dev/null
    log "MySQL 连接正常，数据库已确认存在"
    return
  fi

  if check_tcp "$MYSQL_HOST" "$MYSQL_PORT"; then
    log "MySQL 端口可连接；本机未安装 mysql 客户端，跳过自动建库"
    return
  fi

  fail "MySQL 无法连接：${MYSQL_HOST}:${MYSQL_PORT}。请确认本地 MySQL 已启动并已创建数据库 ${MYSQL_DB}"
}

check_redis() {
  log "检查 Redis：${REDIS_HOST}:${REDIS_PORT}"

  if has_cmd redis-cli && redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null | grep -q PONG; then
    log "Redis 连接正常"
    return
  fi

  if check_tcp "$REDIS_HOST" "$REDIS_PORT"; then
    log "Redis 端口可连接"
    return
  fi

  log "提示：宿主机 Redis 暂不可连接。容器启动脚本会尝试在容器内启动 Redis。"
}

deploy_app() {
  log "开始构建并启动 ${APP_NAME}"
  export LUMINA_MYSQL_HOST="$MYSQL_HOST"
  export LUMINA_MYSQL_PORT="$MYSQL_PORT"
  export LUMINA_MYSQL_DB="$MYSQL_DB"
  export LUMINA_MYSQL_USER="$MYSQL_USER"
  export LUMINA_MYSQL_PASSWORD="$MYSQL_PASSWORD"
  export LUMINA_REDIS_HOST="$REDIS_HOST"
  export LUMINA_REDIS_PORT="$REDIS_PORT"
  export LUMINA_DATA_DIR="$DATA_DIR"

  compose up -d --build
  log "Docker Compose 启动完成"
}

wait_for_app() {
  log "等待应用端口 ${APP_PORT} 就绪"

  for i in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${APP_PORT}/" >/dev/null 2>&1; then
      log "Lumina 已就绪：http://127.0.0.1:${APP_PORT}"
      return
    fi
    sleep 2
  done

  log "应用端口未在预期时间内就绪，输出最近容器日志："
  docker logs --tail 80 "$APP_NAME" || true
  fail "部署后健康检查失败"
}

show_status() {
  log "容器状态："
  docker ps --filter "name=${APP_NAME}" --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'

  log "运行配置："
  docker inspect "$APP_NAME" \
    --format 'Restart={{.HostConfig.RestartPolicy.Name}} LogDriver={{.HostConfig.LogConfig.Type}} LogOpts={{json .HostConfig.LogConfig.Config}} Network={{.HostConfig.NetworkMode}}' \
    || true
}

main() {
  check_docker
  prepare_directories
  prepare_mysql
  check_redis
  deploy_app
  wait_for_app
  show_status

  log "部署完成"
  log "后台地址：http://127.0.0.1:${APP_PORT}"
  log "默认账号：admin / admin123"
}

main "$@"
