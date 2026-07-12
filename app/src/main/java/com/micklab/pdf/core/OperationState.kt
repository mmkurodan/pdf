package com.micklab.pdf.core

/**
 * UI-facing state for a long-running operation. ViewModels expose this as a
 * StateFlow; screens render idle / progress / success / error uniformly.
 */
sealed interface OperationState<out T> {
    data object Idle : OperationState<Nothing>

    /** [fraction] is 0f..1f when known, otherwise null (indeterminate). */
    data class Running(val fraction: Float? = null, val label: String = "") : OperationState<Nothing>

    data class Success<out T>(val data: T) : OperationState<T>

    data class Failure(val message: String, val cause: Throwable? = null) : OperationState<Nothing>
}

/** Progress sink handed to use cases. [fraction] is 0f..1f. */
typealias ProgressCallback = (fraction: Float, label: String) -> Unit

/** A no-op progress sink for callers that don't care. */
val NoProgress: ProgressCallback = { _, _ -> }
