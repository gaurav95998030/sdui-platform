package com.sdui.core

/**
 * The four-state contract every asynchronous surface in the SDK exposes:
 * screen loading, API calls and paged lists all report through this type, which
 * is what lets a single `SduiHost` render skeletons, errors and content uniformly.
 */
public sealed interface UiState<out T> {
    public data object Idle : UiState<Nothing>

    public data class Loading<T>(
        /** Previous value kept during a refresh so the UI need not flash empty. */
        val previous: T? = null,
    ) : UiState<T>

    public data class Success<T>(
        val data: T,
        val fromCache: Boolean = false,
        val stale: Boolean = false,
    ) : UiState<T>

    public data class Error<T>(
        val error: SduiError,
        val previous: T? = null,
    ) : UiState<T>

    public val dataOrNull: T?
        get() = when (this) {
            is Success -> data
            is Loading -> previous
            is Error -> previous
            Idle -> null
        }

    public val isLoading: Boolean get() = this is Loading

    public companion object {
        public fun <T> of(result: SduiResult<T>): UiState<T> = when (result) {
            is SduiResult.Success -> Success(result.value, fromCache = result.fromCache)
            is SduiResult.Failure -> Error(result.error)
        }
    }
}
