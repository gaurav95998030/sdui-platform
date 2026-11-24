package com.sdui.core

import android.util.Log

public enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * Logging seam. Hosts route SDK diagnostics into their own observability stack by
 * supplying an implementation on [SduiConfig]; the SDK never calls `Log` directly.
 */
public interface SduiLogger {
    public fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)

    public fun v(tag: String, message: String): Unit = log(LogLevel.VERBOSE, tag, message)
    public fun d(tag: String, message: String): Unit = log(LogLevel.DEBUG, tag, message)
    public fun i(tag: String, message: String): Unit = log(LogLevel.INFO, tag, message)
    public fun w(tag: String, message: String, throwable: Throwable? = null): Unit =
        log(LogLevel.WARN, tag, message, throwable)

    public fun e(tag: String, message: String, throwable: Throwable? = null): Unit =
        log(LogLevel.ERROR, tag, message, throwable)

    public companion object {
        /** Drops everything — the production default. */
        public val Noop: SduiLogger = object : SduiLogger {
            override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
        }

        /** Routes to Logcat under the `SDUI/<tag>` prefix. */
        public val Android: SduiLogger = object : SduiLogger {
            override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
                val full = "SDUI/$tag"
                when (level) {
                    LogLevel.VERBOSE -> Log.v(full, message, throwable)
                    LogLevel.DEBUG -> Log.d(full, message, throwable)
                    LogLevel.INFO -> Log.i(full, message, throwable)
                    LogLevel.WARN -> Log.w(full, message, throwable)
                    LogLevel.ERROR -> Log.e(full, message, throwable)
                }
            }
        }
    }
}
