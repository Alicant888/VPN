package com.trueroute.app.data

import com.trueroute.app.model.LogEntry
import com.trueroute.app.model.LogLevel
import com.trueroute.app.model.TunnelPhase
import com.trueroute.app.model.TunnelStats
import com.trueroute.app.model.TunnelStatus
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TunnelSessionRepository {
    private val _status = MutableStateFlow(TunnelStatus())
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())

    val status: StateFlow<TunnelStatus> = _status.asStateFlow()
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun reset() {
        _status.value = TunnelStatus()
        _logs.value = emptyList()
    }

    fun updatePhase(phase: TunnelPhase, detail: String? = _status.value.detail) {
        _status.update { current -> current.copy(phase = phase, detail = detail) }
    }

    fun updateStats(stats: TunnelStats) {
        _status.update { current -> current.copy(stats = stats) }
    }

    fun appendLog(level: LogLevel, message: String) {
        _logs.update { current ->
            (current + LogEntry(Instant.now(), level, message)).takeLast(MAX_LOG_LINES)
        }
    }

    private companion object {
        private const val MAX_LOG_LINES = 250
    }
}
