package com.trueroute.app.model

import java.time.Instant

enum class TunnelPhase {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class TunnelStats(
    val txPackets: Long = 0,
    val txBytes: Long = 0,
    val rxPackets: Long = 0,
    val rxBytes: Long = 0,
)

data class TunnelStatus(
    val phase: TunnelPhase = TunnelPhase.IDLE,
    val detail: String? = null,
    val stats: TunnelStats = TunnelStats(),
)

data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val message: String,
)
