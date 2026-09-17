package com.airgesture.control

data class NativeGraphCloseOutcome(
    val safeToReleaseFrames: Boolean,
    val failureMessage: String? = null
)

/**
 * Encodes the ownership rule for MediaPipe backing frames:
 * submitted frame resources are releasable only after native graph close
 * returns successfully. A thrown close is treated as unresolved native
 * ownership and must fail-stop/quarantine instead of recycling buffers.
 */
object NativeGraphCloseGuard {
    fun attempt(
        closeAction: () -> Unit
    ): NativeGraphCloseOutcome =
        runCatching(closeAction)
            .fold(
                onSuccess = {
                    NativeGraphCloseOutcome(
                        safeToReleaseFrames = true
                    )
                },
                onFailure = { error ->
                    NativeGraphCloseOutcome(
                        safeToReleaseFrames = false,
                        failureMessage =
                            error.message
                                ?.lineSequence()
                                ?.firstOrNull()
                                ?.take(120)
                                ?: error::class.java
                                    .simpleName
                    )
                }
            )
}
