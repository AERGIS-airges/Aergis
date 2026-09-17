package com.airgesture.control

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Allows content hosted by the shared MetallicViewport helper to request a
 * position without depending on a BoxScope receiver at the call site.
 */
fun Modifier.align(alignment: Alignment): Modifier =
    fillMaxSize().wrapContentSize(alignment)
