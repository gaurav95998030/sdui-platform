package com.sdui.core

/**
 * The SDK never throws across a module boundary; every fallible operation returns
 * this. Rendering must survive a bad payload, so failures are values.
 */
public sealed interface SduiResult<out T> {
    public data class Success<T>(val value: T, val fromCache: Boolean = false) : SduiResult<T>
    public data class Failure(val error: SduiError) : SduiResult<Nothing>

    public val isSuccess: Boolean get() = this is Success

    public fun getOrNull(): T? = (this as? Success)?.value

    public fun errorOrNull(): SduiError? = (this as? Failure)?.error

    public companion object {
        public fun <T> success(value: T, fromCache: Boolean = false): SduiResult<T> =
            Success(value, fromCache)

        public fun failure(error: SduiError): SduiResult<Nothing> = Failure(error)

        /** Wraps a throwing block, classifying the throwable into an [SduiError]. */
        public inline fun <T> catching(block: () -> T): SduiResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Failure(SduiError.from(t))
        }
    }
}

public inline fun <T, R> SduiResult<T>.map(transform: (T) -> R): SduiResult<R> = when (this) {
    is SduiResult.Success -> SduiResult.Success(transform(value), fromCache)
    is SduiResult.Failure -> this
}

public inline fun <T, R> SduiResult<T>.flatMap(transform: (T) -> SduiResult<R>): SduiResult<R> =
    when (this) {
        is SduiResult.Success -> transform(value)
        is SduiResult.Failure -> this
    }

public inline fun <T> SduiResult<T>.onSuccess(action: (T) -> Unit): SduiResult<T> = apply {
    if (this is SduiResult.Success) action(value)
}

public inline fun <T> SduiResult<T>.onFailure(action: (SduiError) -> Unit): SduiResult<T> = apply {
    if (this is SduiResult.Failure) action(error)
}

/** Every failure the SDK can surface, in a shape screens can react to. */
public sealed class SduiError(
    public val code: String,
    public override val message: String,
    public override val cause: Throwable? = null,
) : Throwable(message, cause) {

    public class Network(message: String, cause: Throwable? = null) :
        SduiError("network", message, cause)

    public class Timeout(message: String = "Request timed out", cause: Throwable? = null) :
        SduiError("timeout", message, cause)

    public class Offline(message: String = "No network connection") :
        SduiError("offline", message)

    public class Http(public val status: Int, public val body: String?, message: String) :
        SduiError("http_$status", message)

    public class Unauthorized(message: String = "Unauthorized") :
        SduiError("unauthorized", message)

    public class Parse(message: String, cause: Throwable? = null) :
        SduiError("parse", message, cause)

    public class ScreenNotFound(public val screenId: String) :
        SduiError("screen_not_found", "No screen published for id '$screenId'")

    public class UnknownComponent(public val type: String) :
        SduiError("unknown_component", "No renderer registered for component '$type'")

    public class UnknownAction(public val type: String) :
        SduiError("unknown_action", "No handler registered for action '$type'")

    public class ActionFailed(public val type: String, message: String, cause: Throwable? = null) :
        SduiError("action_failed", message, cause)

    public class Validation(public val failures: Map<String, String>) :
        SduiError("validation", "Validation failed for ${failures.keys.joinToString()}")

    public class PermissionDenied(public val permission: String) :
        SduiError("permission_denied", "Permission denied: $permission")

    public class Unknown(message: String, cause: Throwable? = null) :
        SduiError("unknown", message, cause)

    public companion object {
        public fun from(t: Throwable): SduiError = when (t) {
            is SduiError -> t
            is java.net.SocketTimeoutException -> Timeout(t.message ?: "Request timed out", t)
            is java.net.UnknownHostException -> Offline()
            is java.io.IOException -> Network(t.message ?: "Network error", t)
            is kotlinx.serialization.SerializationException -> Parse(t.message ?: "Malformed payload", t)
            else -> Unknown(t.message ?: t::class.java.simpleName, t)
        }
    }
}
