package com.lumina.state;

public enum CircuitState {
    CLOSED,     // 正常
    OPEN,       // 熔断中（禁止请求）
    HALF_OPEN   // 试探恢复
}
