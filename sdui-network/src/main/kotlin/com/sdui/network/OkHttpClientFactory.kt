package com.sdui.network

import com.sdui.core.LogLevel
import com.sdui.core.SduiConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Builds the shared client. Hosts that already own an [OkHttpClient] should pass
 * theirs to [ApiEngine] instead so connection pools, DNS and TLS config are shared.
 */
public object OkHttpClientFactory {

    public fun create(config: SduiConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.network.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.network.readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.network.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (config.network.enableHttpLogging) {
                addInterceptor(
                    HttpLoggingInterceptor { message ->
                        config.logger.log(LogLevel.DEBUG, "Http", message)
                    }.apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    /** Streaming variant with no read timeout, for the live-preview SSE connection. */
    public fun createStreaming(config: SduiConfig): OkHttpClient = create(config).newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
}
