package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PixelFormat
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

class AirAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var cursorView: CursorOverlayView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var cursorAttached = false
    private var pointerDispatchGeneration = 0L
    private var pointerTouchGeneration = 0L
    private val touchActionCoordinator =
        TouchActionCoordinator()

    private data class PointerTouchSession(
        val generation: Long,
        val ticket: TouchActionTicket,
        val dragGate: PointerTouchDragGate,
        var stroke: GestureDescription.StrokeDescription,
        var endpointX: Float,
        var endpointY: Float,
        var desiredX: Float,
        var desiredY: Float,
        var segmentInFlight: Boolean = false,
        var releaseRequested: Boolean = false,
        var releaseKind: String = "touch"
    )

    private var pointerTouchSession: PointerTouchSession? = null

    private data class PendingPointerUpdate(
        val decision: PointerDecision,
        val enabled: Boolean,
        val sourceTimestampUptimeMs: Long?,
        val resultQueuedAtUptimeMs: Long?,
        val actionDeadlineUptimeMs: Long?,
        val shouldApply: () -> Boolean
    )

    private enum class OwnedGestureOutcome {
        COMPLETED,
        CANCELLED,
        REJECTED_BUSY,
        REJECTED_EXPIRED,
        UNAVAILABLE
    }

    private val pointerUpdateLock = Any()
    private var latestPointerUpdate: PendingPointerUpdate? = null
    private var pointerUpdatePosted = false

    private enum class ScrollDirection {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        TrackingMirrorBridge.attach(this)
        AirRuntime.update {
            it.copy(
                accessibilityEnabled = true,
                lastMessage = "Accessibility control ready"
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        invalidatePointerDispatches()
        clearPendingPointerUpdates()
        abandonPointerTouchSession()
        touchActionCoordinator.clearOwnership()
        removeCursorOverlay()
        TrackingMirrorBridge.detach(this)
        if (instance === this) instance = null

        // Camera control has no useful or privacy-justified reason to
        // continue after the execution layer is disabled.
        stopService(
            Intent(
                this,
                GestureCaptureService::class.java
            )
        )

        AirRuntime.update {
            it.copy(
                accessibilityEnabled = false,
                sessionActive = false,
                cameraReady = false,
                recognizerReady = false,
                pointerVisible = false,
                pointerState = "OFF",
                lastMessage = "Accessibility service disconnected"
            )
        }
        super.onDestroy()
    }

    private fun applyPointer(
        decision: PointerDecision,
        enabled: Boolean,
        sourceTimestampUptimeMs: Long? = null,
        resultQueuedAtUptimeMs: Long? = null,
        actionDeadlineUptimeMs: Long? = null
    ) {
        val applyNow =
            SystemClock.uptimeMillis()
        AirRuntime.recordVisionInteractionLatency(
            resultToPointerApplyMs =
                resultQueuedAtUptimeMs
                    ?.let {
                        applyNow - it
                    },
            sourceToPointerApplyMs =
                sourceTimestampUptimeMs
                    ?.let {
                        applyNow - it
                    }
        )
        if (!enabled || !decision.visible) {
            requestPointerTouchRelease(
                kind = "tracking release"
            )
            hideCursor()
            return
        }

        val wm = windowManager ?: return
        val bounds = displayBounds()
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            requestPointerTouchRelease(
                kind = "display release"
            )
            hideCursor()
            return
        }

        val view = cursorView ?: CursorOverlayView(this).also {
            cursorView = it
        }

        val params = cursorParams
            ?: WindowManager.LayoutParams(
                bounds.width(),
                bounds.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bounds.left
                y = bounds.top
                cursorParams = this
            }

        // The overlay window itself is stationary. Only resize/reposition it
        // when Android's actual display bounds change (for example rotation).
        // Normal pointer frames redraw inside the View and avoid WindowManager
        // relayout/IPC entirely.
        var overlayGeometryChanged = false
        if (params.width != bounds.width()) {
            params.width = bounds.width()
            overlayGeometryChanged = true
        }
        if (params.height != bounds.height()) {
            params.height = bounds.height()
            overlayGeometryChanged = true
        }
        if (params.x != bounds.left) {
            params.x = bounds.left
            overlayGeometryChanged = true
        }
        if (params.y != bounds.top) {
            params.y = bounds.top
            overlayGeometryChanged = true
        }

        if (!cursorAttached) {
            val added = runCatching {
                wm.addView(view, params)
            }.isSuccess

            if (!added) {
                cursorView = null
                cursorParams = null
                AirRuntime.update {
                    it.copy(
                        pointerVisible = false,
                        pointerState = "ERROR",
                        lastMessage = "Air pointer overlay could not be shown"
                    )
                }
                return
            }

            cursorAttached = true
        } else if (overlayGeometryChanged) {
            val resized = runCatching {
                wm.updateViewLayout(view, params)
            }.isSuccess

            if (!resized) {
                removeCursorOverlay()
                AirRuntime.update {
                    it.copy(
                        pointerVisible = false,
                        pointerState = "ERROR",
                        lastMessage = "Air pointer overlay bounds could not update"
                    )
                }
                return
            }
        }

        if (!view.setCursorPosition(decision.x, decision.y)) {
            hideCursor()
            return
        }

        view.visibility = View.VISIBLE

        val touchHandled =
            handlePointerTouchDecision(
                decision = decision,
                bounds = bounds,
                sourceTimestampUptimeMs =
                    sourceTimestampUptimeMs,
                resultQueuedAtUptimeMs =
                    resultQueuedAtUptimeMs,
                actionDeadlineUptimeMs =
                    actionDeadlineUptimeMs
                        ?: Long.MAX_VALUE
            )

        val virtualFingerDown =
            pointerTouchSession != null ||
                decision.state == "PRESSING" ||
                decision.state == "LONG_PRESS" ||
                decision.state ==
                    "LONG_PRESS_WAIT_RELEASE" ||
                (
                    decision.state == "PRESS_OCCLUDED" &&
                        pointerTouchSession != null
                    )

        view.setPressState(
            pressed =
                virtualFingerDown ||
                    decision.state == "TAP",
            held =
                decision.state == "LONG_PRESS" ||
                    decision.state ==
                        "LONG_PRESS_WAIT_RELEASE" ||
                    pointerTouchSession
                        ?.dragGate
                        ?.isDragging() == true
        )

        if (!touchHandled && (decision.hold || decision.tap)) {
            if (
                actionDeadlineUptimeMs != null &&
                SystemClock.uptimeMillis() >
                    actionDeadlineUptimeMs
            ) {
                AirRuntime.recordPointerAction(
                    success = false,
                    message =
                        "Air pointer action expired before screen contact"
                )
                return
            }

            val actionPoint =
                PointerScreenCoordinateMapper
                    .actionPoint(
                        normalizedX =
                            decision.actionX
                                ?: decision.x,
                        normalizedY =
                            decision.actionY
                                ?: decision.y,
                        left = bounds.left,
                        top = bounds.top,
                        width = bounds.width(),
                        height = bounds.height()
                    )

            if (actionPoint == null) {
                AirRuntime.recordPointerAction(
                    success = false,
                    message =
                        "Air pointer action blocked • invalid screen coordinate"
                )
                return
            }

            when {
                decision.hold -> {
                    dispatchPointerLongPress(
                        x = actionPoint.x,
                        y = actionPoint.y,
                        sourceTimestampUptimeMs =
                            sourceTimestampUptimeMs,
                        resultQueuedAtUptimeMs =
                            resultQueuedAtUptimeMs,
                        deadlineUptimeMs =
                            actionDeadlineUptimeMs
                                ?: Long.MAX_VALUE
                    )
                }
                decision.tap -> {
                    dispatchPointerTap(
                        x = actionPoint.x,
                        y = actionPoint.y,
                        sourceTimestampUptimeMs =
                            sourceTimestampUptimeMs,
                        resultQueuedAtUptimeMs =
                            resultQueuedAtUptimeMs,
                        deadlineUptimeMs =
                            actionDeadlineUptimeMs
                                ?: Long.MAX_VALUE
                    )
                }
            }
        }
    }

    private fun enqueuePointerUpdate(
        decision: PointerDecision,
        enabled: Boolean,
        sourceTimestampUptimeMs: Long?,
        resultQueuedAtUptimeMs: Long?,
        actionDeadlineUptimeMs: Long?,
        shouldApply: () -> Boolean
    ) {
        // Tap/Hold/release commits are rare and must never be overwritten by
        // a newer visual frame before the main thread drains its queue.
        if (
            decision.tap ||
            decision.hold ||
            decision.state == "LONG_PRESS_RELEASE"
        ) {
            mainHandler.post {
                if (
                    instance === this &&
                    shouldApply()
                ) {
                    applyPointer(
                        decision = decision,
                        enabled = enabled,
                        sourceTimestampUptimeMs =
                            sourceTimestampUptimeMs,
                        resultQueuedAtUptimeMs =
                            resultQueuedAtUptimeMs,
                        actionDeadlineUptimeMs =
                            actionDeadlineUptimeMs
                    )
                }
            }
            return
        }

        var shouldPost = false
        synchronized(pointerUpdateLock) {
            latestPointerUpdate =
                PendingPointerUpdate(
                    decision = decision,
                    enabled = enabled,
                    sourceTimestampUptimeMs =
                        sourceTimestampUptimeMs,
                    resultQueuedAtUptimeMs =
                        resultQueuedAtUptimeMs,
                    actionDeadlineUptimeMs =
                        actionDeadlineUptimeMs,
                    shouldApply = shouldApply
                )
            if (!pointerUpdatePosted) {
                pointerUpdatePosted = true
                shouldPost = true
            }
        }

        if (shouldPost) {
            mainHandler.post(::drainLatestPointerUpdate)
        }
    }

    private fun drainLatestPointerUpdate() {
        val update =
            synchronized(pointerUpdateLock) {
                val latest = latestPointerUpdate
                latestPointerUpdate = null
                pointerUpdatePosted = false
                latest
            }

        if (
            update != null &&
            instance === this &&
            update.shouldApply()
        ) {
            applyPointer(
                decision = update.decision,
                enabled = update.enabled,
                sourceTimestampUptimeMs =
                    update.sourceTimestampUptimeMs,
                resultQueuedAtUptimeMs =
                    update.resultQueuedAtUptimeMs,
                actionDeadlineUptimeMs =
                    update.actionDeadlineUptimeMs
            )
        }

        var repost = false
        synchronized(pointerUpdateLock) {
            if (
                latestPointerUpdate != null &&
                !pointerUpdatePosted
            ) {
                pointerUpdatePosted = true
                repost = true
            }
        }

        if (repost) {
            mainHandler.post(::drainLatestPointerUpdate)
        }
    }

    private fun clearPendingPointerUpdates() {
        synchronized(pointerUpdateLock) {
            latestPointerUpdate = null
            pointerUpdatePosted = false
        }
    }

    private fun hideCursor() {
        cursorView?.clearCursorPosition()
    }

    private fun removeCursorOverlay() {
        val wm = windowManager
        val view = cursorView
        if (wm != null && view != null) {
            runCatching {
                wm.removeViewImmediate(view)
            }
        }
        cursorView = null
        cursorParams = null
        cursorAttached = false
    }

    private fun displayBounds(): Rect =
        if (Build.VERSION.SDK_INT >= 30) {
            windowManager?.currentWindowMetrics?.bounds
                ?: Rect(
                    0,
                    0,
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels
                )
        } else {
            Rect(
                0,
                0,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels
            )
        }

    private fun executeSingleAsync(
        action: AirAction,
        callback: (ActionResult) -> Unit
    ) {
        when (action) {
            AirAction.SELECT ->
                performSelectAsync(callback)

            AirAction.FORWARD ->
                performScrollAsync(
                    action =
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD,
                    label = "Forward",
                    forward = true,
                    callback = callback
                )

            AirAction.SCROLL_BACKWARD ->
                performScrollAsync(
                    action =
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_BACKWARD,
                    label = "Scroll backward",
                    forward = false,
                    callback = callback
                )

            AirAction.SCROLL_UP ->
                performDirectionalScrollAsync(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_UP
                            .id,
                    label = "Scroll up",
                    direction = ScrollDirection.UP,
                    callback = callback
                )

            AirAction.SCROLL_DOWN ->
                performDirectionalScrollAsync(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_DOWN
                            .id,
                    label = "Scroll down",
                    direction = ScrollDirection.DOWN,
                    callback = callback
                )

            AirAction.SCROLL_LEFT ->
                performDirectionalScrollAsync(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_LEFT
                            .id,
                    label = "Scroll left",
                    direction = ScrollDirection.LEFT,
                    callback = callback
                )

            AirAction.SCROLL_RIGHT ->
                performDirectionalScrollAsync(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_RIGHT
                            .id,
                    label = "Scroll right",
                    direction = ScrollDirection.RIGHT,
                    callback = callback
                )

            else ->
                callback(executeSingle(action))
        }
    }

    private fun executeSingle(action: AirAction): ActionResult {
        return when (action) {
            AirAction.NONE -> ActionResult(false, "No action mapped")
            AirAction.BACK -> global(GLOBAL_ACTION_BACK, "Back")
            AirAction.HOME -> global(GLOBAL_ACTION_HOME, "Home")
            AirAction.RECENTS -> global(GLOBAL_ACTION_RECENTS, "Recent apps")
            AirAction.NOTIFICATIONS -> global(
                GLOBAL_ACTION_NOTIFICATIONS,
                "Notifications"
            )
            AirAction.QUICK_SETTINGS -> global(
                GLOBAL_ACTION_QUICK_SETTINGS,
                "Quick settings"
            )
            AirAction.MEDIA_PLAY_PAUSE -> {
                if (Build.VERSION.SDK_INT >= 36) {
                    global(GLOBAL_ACTION_MEDIA_PLAY_PAUSE, "Media play / pause")
                } else {
                    ActionResult(false, "Media play / pause requires Android 16")
                }
            }
            AirAction.SELECT -> performSelect()
            AirAction.COPY -> performOnFocused(
                AccessibilityNodeInfo.ACTION_COPY,
                "Copy"
            )
            AirAction.PASTE -> performOnFocused(
                AccessibilityNodeInfo.ACTION_PASTE,
                "Paste"
            )
            AirAction.FORWARD -> performScroll(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                "Forward",
                forward = true
            )
            AirAction.SCROLL_BACKWARD -> performScroll(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                "Scroll backward",
                forward = false
            )
            AirAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            AirAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            AirAction.SCROLL_UP ->
                performDirectionalScroll(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_UP
                            .id,
                    label = "Scroll up",
                    direction = ScrollDirection.UP
                )
            AirAction.SCROLL_DOWN ->
                performDirectionalScroll(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_DOWN
                            .id,
                    label = "Scroll down",
                    direction = ScrollDirection.DOWN
                )
            AirAction.SCROLL_LEFT ->
                performDirectionalScroll(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_LEFT
                            .id,
                    label = "Scroll left",
                    direction = ScrollDirection.LEFT
                )
            AirAction.SCROLL_RIGHT ->
                performDirectionalScroll(
                    action =
                        AccessibilityNodeInfo
                            .AccessibilityAction
                            .ACTION_SCROLL_RIGHT
                            .id,
                    label = "Scroll right",
                    direction = ScrollDirection.RIGHT
                )
        }
    }

    private fun global(action: Int, label: String): ActionResult {
        val ok = performGlobalAction(action)
        return ActionResult(ok, if (ok) label else "$label unavailable")
    }

    private fun adjustVolume(
        direction: Int
    ): ActionResult {
        val audio =
            getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_MUSIC
        val before =
            audio.getStreamVolume(stream)
        val max =
            audio.getStreamMaxVolume(stream)

        if (
            direction == AudioManager.ADJUST_RAISE &&
            before >= max
        ) {
            return ActionResult(
                false,
                "Volume already at maximum"
            )
        }

        if (
            direction == AudioManager.ADJUST_LOWER &&
            before <= 0
        ) {
            return ActionResult(
                false,
                "Volume already at minimum"
            )
        }

        audio.adjustStreamVolume(
            stream,
            direction,
            AudioManager.FLAG_SHOW_UI
        )

        val after =
            audio.getStreamVolume(stream)
        val changed =
            if (
                direction ==
                AudioManager.ADJUST_RAISE
            ) {
                after > before
            } else {
                after < before
            }

        return ActionResult(
            changed,
            when {
                changed &&
                    direction ==
                    AudioManager.ADJUST_RAISE ->
                    "Volume up"
                changed ->
                    "Volume down"
                else ->
                    "Volume could not be changed"
            }
        )
    }

    private fun performSelect(): ActionResult {
        val focus = bestFocus()
            ?: return ActionResult(false, "Select: no focused item")

        var node: AccessibilityNodeInfo? = focus
        var parentHops = 0
        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            if (
                node.isClickable &&
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return ActionResult(
                    true,
                    "Select • accessibility node"
                )
            }
            node = node.parent
        }

        val bounds = Rect()
        focus.getBoundsInScreen(bounds)
        val display = displayBounds()
        val safeBounds = Rect(bounds)
        val visible =
            !safeBounds.isEmpty &&
                safeBounds.intersect(display)

        if (visible) {
            val accepted = dispatchTap(
                safeBounds.exactCenterX(),
                safeBounds.exactCenterY()
            )
            if (accepted) {
                return ActionResult(
                    true,
                    "Select • screen tap fallback"
                )
            }
        }

        return ActionResult(false, "Select unavailable here")
    }

    private fun performSelectAsync(
        callback: (ActionResult) -> Unit
    ) {
        val focus =
            bestFocus()
                ?: run {
                    callback(
                        ActionResult(
                            false,
                            "Select: no focused item"
                        )
                    )
                    return
                }

        var node: AccessibilityNodeInfo? = focus
        var parentHops = 0
        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            if (
                node.isClickable &&
                node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            ) {
                callback(
                    ActionResult(
                        true,
                        "Select • accessibility node"
                    )
                )
                return
            }
            node = node.parent
        }

        val bounds = Rect()
        focus.getBoundsInScreen(bounds)
        val safeBounds = Rect(bounds)
        val visible =
            !safeBounds.isEmpty &&
                safeBounds.intersect(
                    displayBounds()
                )

        if (!visible) {
            callback(
                ActionResult(
                    false,
                    "Select unavailable here"
                )
            )
            return
        }

        dispatchGestureWithResult(
            gesture = tapGesture(
                safeBounds.exactCenterX(),
                safeBounds.exactCenterY()
            ),
            successMessage =
                "Select • screen tap completed",
            cancelledMessage =
                "Select • screen tap cancelled",
            unavailableMessage =
                "Select • screen tap unavailable",
            callback = callback
        )
    }

    private fun performOnFocused(
        action: Int,
        label: String
    ): ActionResult {
        var node: AccessibilityNodeInfo? =
            bestFocus()
                ?: return ActionResult(
                    false,
                    "$label: no focused item"
                )

        var parentHops = 0
        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            val supportsAction =
                node.actionList.any {
                    it.id == action
                }

            if (
                supportsAction &&
                node.performAction(action)
            ) {
                return ActionResult(
                    true,
                    "$label • focused control"
                )
            }

            node = node.parent
        }

        return ActionResult(
            false,
            "$label unavailable here"
        )
    }

    private fun bestFocus(): AccessibilityNodeInfo? =
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: rootInActiveWindow?.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT
            )
            ?: rootInActiveWindow?.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
            )

    private fun performScroll(
        action: Int,
        label: String,
        forward: Boolean
    ): ActionResult {
        var node: AccessibilityNodeInfo? = bestFocus()
        var parentHops = 0

        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            if (node.isScrollable && node.performAction(action)) {
                return ActionResult(
                    true,
                    "$label • focused scroll container"
                )
            }
            node = node.parent
        }

        val root = rootInActiveWindow
        if (root != null) {
            val scrollable =
                findBestScrollable(
                    root = root,
                    action = action
                )
            if (
                scrollable != null &&
                scrollable.performAction(action)
            ) {
                return ActionResult(
                    true,
                    "$label • window scroll container"
                )
            }
        }

        val accepted = dispatchScrollGesture(forward)
        return ActionResult(
            accepted,
            if (accepted) {
                "$label • touch-gesture fallback"
            } else {
                "$label unavailable here"
            }
        )
    }

    private fun performScrollAsync(
        action: Int,
        label: String,
        forward: Boolean,
        callback: (ActionResult) -> Unit
    ) {
        var node: AccessibilityNodeInfo? =
            bestFocus()
        var parentHops = 0

        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            if (
                node.isScrollable &&
                node.performAction(action)
            ) {
                callback(
                    ActionResult(
                        true,
                        "$label • focused scroll container"
                    )
                )
                return
            }
            node = node.parent
        }

        val root = rootInActiveWindow
        if (root != null) {
            val scrollable =
                findBestScrollable(
                    root = root,
                    action = action
                )
            if (
                scrollable != null &&
                scrollable.performAction(action)
            ) {
                callback(
                    ActionResult(
                        true,
                        "$label • window scroll container"
                    )
                )
                return
            }
        }

        dispatchGestureWithResult(
            gesture = scrollGesture(forward),
            successMessage =
                "$label • touch gesture completed",
            cancelledMessage =
                "$label • touch gesture cancelled",
            unavailableMessage =
                "$label unavailable here",
            callback = callback
        )
    }

    private fun performDirectionalScroll(
        action: Int,
        label: String,
        direction: ScrollDirection
    ): ActionResult {
        var node: AccessibilityNodeInfo? = bestFocus()
        var parentHops = 0

        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            if (
                node.isScrollable &&
                node.performAction(action)
            ) {
                return ActionResult(
                    true,
                    "$label • focused scroll container"
                )
            }
            node = node.parent
        }

        val root = rootInActiveWindow
        if (root != null) {
            val scrollable =
                findBestScrollable(
                    root = root,
                    action = action
                )
            if (
                scrollable != null &&
                scrollable.performAction(action)
            ) {
                return ActionResult(
                    true,
                    "$label • window scroll container"
                )
            }
        }

        val accepted =
            dispatchDirectionalScrollGesture(direction)

        return ActionResult(
            accepted,
            if (accepted) {
                "$label • short touch-step fallback"
            } else {
                "$label unavailable here"
            }
        )
    }

    private fun performDirectionalScrollAsync(
        action: Int,
        label: String,
        direction: ScrollDirection,
        callback: (ActionResult) -> Unit
    ) {
        var node: AccessibilityNodeInfo? =
            bestFocus()
        var parentHops = 0

        while (
            node != null &&
            parentHops++ < MAX_PARENT_HOPS
        ) {
            if (
                node.isScrollable &&
                node.performAction(action)
            ) {
                callback(
                    ActionResult(
                        true,
                        "$label • focused scroll container"
                    )
                )
                return
            }
            node = node.parent
        }

        val root = rootInActiveWindow
        if (root != null) {
            val scrollable =
                findBestScrollable(
                    root = root,
                    action = action
                )
            if (
                scrollable != null &&
                scrollable.performAction(action)
            ) {
                callback(
                    ActionResult(
                        true,
                        "$label • window scroll container"
                    )
                )
                return
            }
        }

        dispatchGestureWithResult(
            gesture =
                directionalScrollGesture(direction),
            successMessage =
                "$label • short touch step completed",
            cancelledMessage =
                "$label • short touch step cancelled",
            unavailableMessage =
                "$label unavailable here",
            callback = callback
        )
    }

    /**
     * Treat confirmed index+middle fingertip contact as a real touchscreen
     * finger. A short contact/release becomes a tap, a stationary sustained
     * contact becomes Android's native long-press, and moving beyond touch
     * slop while contact remains down becomes a continuous drag/scroll.
     *
     * The old fixed tap/long-press dispatch remains only as a fallback if a
     * continuous touch session could not be started.
     */
    private fun handlePointerTouchDecision(
        decision: PointerDecision,
        bounds: Rect,
        sourceTimestampUptimeMs: Long?,
        resultQueuedAtUptimeMs: Long?,
        actionDeadlineUptimeMs: Long
    ): Boolean {
        val activeContact =
            decision.state == "PRESSING" ||
                decision.state == "LONG_PRESS" ||
                decision.state ==
                    "LONG_PRESS_WAIT_RELEASE"
        val bridgedOcclusion =
            decision.state == "PRESS_OCCLUDED"
        val explicitRelease =
            decision.tap ||
                decision.state == "LONG_PRESS_RELEASE"

        val currentPoint =
            PointerScreenCoordinateMapper
                .actionPoint(
                    normalizedX = decision.x,
                    normalizedY = decision.y,
                    left = bounds.left,
                    top = bounds.top,
                    width = bounds.width(),
                    height = bounds.height()
                )

        if (activeContact) {
            if (currentPoint == null) {
                requestPointerTouchRelease(
                    kind = "invalid pointer release"
                )
                return pointerTouchSession != null
            }

            val existing = pointerTouchSession
            if (existing != null) {
                updatePointerTouchTarget(
                    x = currentPoint.x,
                    y = currentPoint.y
                )
                return true
            }

            val downPoint =
                PointerScreenCoordinateMapper
                    .actionPoint(
                        normalizedX =
                            decision.actionX
                                ?: decision.x,
                        normalizedY =
                            decision.actionY
                                ?: decision.y,
                        left = bounds.left,
                        top = bounds.top,
                        width = bounds.width(),
                        height = bounds.height()
                    )
                    ?: return false

            val started =
                startPointerTouchSession(
                    downX = downPoint.x,
                    downY = downPoint.y,
                    desiredX = currentPoint.x,
                    desiredY = currentPoint.y,
                    sourceTimestampUptimeMs =
      sourceTimestampUptimeMs,
                    resultQueuedAtUptimeMs =
                        resultQueuedAtUptimeMs,
                    deadlineUptimeMs =
                        actionDeadlineUptimeMs
                )

            return started
        }

        if (
            bridgedOcclusion &&
            pointerTouchSession != null
        ) {
            // PointerTracker already freezes the cursor during the brief
            // occlusion grace. Keep the virtual finger down at its last
            // valid target, but never start a new touch from an occlusion.
            currentPoint?.let {
                updatePointerTouchTarget(
                    x = it.x,
                    y = it.y
                )
            }
            return true
        }

        if (
            explicitRelease &&
            pointerTouchSession != null
        ) {
            currentPoint?.let {
                updatePointerTouchTarget(
                    x = it.x,
                    y = it.y
                )
            }

            val kind =
                when {
                    pointerTouchSession
                        ?.dragGate
                        ?.isDragging() == true ->
                        "drag"
                    decision.tap ->
                        "tap"
                    else ->
                        "long press"
                }

            requestPointerTouchRelease(
                kind = kind
            )
            return true
        }

        if (pointerTouchSession != null) {
            // Any state outside the confirmed-contact family means the
            // tracker cancelled/re-armed the contact. End the OS touch
            // promptly so the screen can never be left logically pressed.
            requestPointerTouchRelease(
                kind = "contact cancelled"
            )
            return true
        }

        return false
    }

    private fun startPointerTouchSession(
        downX: Float,
        downY: Float,
        desiredX: Float,
        desiredY: Float,
        sourceTimestampUptimeMs: Long?,
        resultQueuedAtUptimeMs: Long?,
        deadlineUptimeMs: Long
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        val acquisition =
            touchActionCoordinator.tryAcquire(
                nowMs = now,
                deadlineMs = deadlineUptimeMs,
                label = "Air pointer touchscreen contact"
            )
        val ticket = acquisition.ticket

        if (ticket == null) {
            AirRuntime.recordPointerAction(
                success = false,
                message =
                    when (acquisition.failure) {
                        TouchAcquireFailure.EXPIRED ->
                            "Air pointer touch expired before screen contact"
                        TouchAcquireFailure.BUSY ->
                            "Air pointer touch blocked • another touch action is in progress"
                        null ->
                            "Air pointer touch unavailable here"
                    }
            )
            return false
        }

        val dragGate =
            PointerTouchDragGate(
                dragStartDistancePx =
                    pointerDragThresholdPx()
            )
        dragGate.start(
            x = downX,
            y = downY
        )

        val path =
            Path().apply {
                moveTo(
                    downX,
                    downY
                )
            }

        val stroke =
            GestureDescription.StrokeDescription(
                path,
                0L,
                POINTER_TOUCH_SEGMENT_MS,
                true
            )

        val session =
            PointerTouchSession(
                generation = ++pointerTouchGeneration,
                ticket = ticket,
                dragGate = dragGate,
                stroke = stroke,
                endpointX = downX,
                endpointY = downY,
                desiredX = desiredX,
                desiredY = desiredY
            )

        pointerTouchSession = session
        AirRuntime.recordPointerActionPending(
            "Air pointer touchscreen contact active"
        )

        val dispatchNow =
            SystemClock.uptimeMillis()
        val accepted =
            dispatchPointerTouchStroke(
                session = session,
                stroke = stroke,
                endX = downX,
                endY = downY,
                finalSegment = false
            )

        if (accepted) {
            AirRuntime.recordVisionInteractionLatency(
                resultToActionDispatchMs =
                    resultQueuedAtUptimeMs
                        ?.let {
                            dispatchNow - it
                        },
                sourceToActionDispatchMs =
                    sourceTimestampUptimeMs
                        ?.let {
                            dispatchNow - it
                        }
            )
        }

        return accepted
    }

    private fun updatePointerTouchTarget(
        x: Float,
        y: Float
    ) {
        val session =
            pointerTouchSession
                ?: return

        if (!x.isFinite() || !y.isFinite()) {
            return
        }

        session.desiredX = x
        session.desiredY = y

        if (!session.segmentInFlight) {
            dispatchNextPointerTouchSegment(
                session
            )
        }
    }

    private fun requestPointerTouchRelease(
        kind: String
    ) {
        val session =
            pointerTouchSession
                ?: return

        if (!session.releaseRequested) {
            session.releaseRequested = true
            session.releaseKind = kind
        }

        if (!session.segmentInFlight) {
            dispatchNextPointerTouchSegment(
                session
            )
        }
    }

    private fun dispatchNextPointerTouchSegment(
        session: PointerTouchSession
    ) {
        if (
            pointerTouchSession !== session ||
            session.generation !=
                pointerTouchGeneration ||
            session.segmentInFlight
        ) {
            return
        }

        val gated =
            session.dragGate.update(
                x = session.desiredX,
                y = session.desiredY
            )
                ?: PointerTouchDragTarget(
                    x = session.endpointX,
                    y = session.endpointY,
                    dragging = false
                )

        val finalSegment =
            session.releaseRequested
        val duration =
            if (finalSegment) {
                POINTER_TOUCH_RELEASE_SEGMENT_MS
            } else {
                POINTER_TOUCH_SEGMENT_MS
            }

        val path =
            Path().apply {
                moveTo(
                    session.endpointX,
                    session.endpointY
                )
                lineTo(
                    gated.x,
                    gated.y
                )
            }

        val continuedStroke =
            runCatching {
                session.stroke.continueStroke(
                    path,
                    0L,
                    duration,
                    !finalSegment
                )
            }.getOrElse {
                failPointerTouchSession(
                    session = session,
                    message =
                        "Air pointer touch continuation could not be created"
                )
                return
            }

        dispatchPointerTouchStroke(
            session = session,
            stroke = continuedStroke,
            endX = gated.x,
            endY = gated.y,
            finalSegment = finalSegment
        )
    }

    private fun dispatchPointerTouchStroke(
        session: PointerTouchSession,
        stroke: GestureDescription.StrokeDescription,
        endX: Float,
        endY: Float,
        finalSegment: Boolean
    ): Boolean {
        if (
            pointerTouchSession !== session ||
            session.generation !=
                pointerTouchGeneration
        ) {
            return false
        }

        val gesture =
            GestureDescription.Builder()
                .addStroke(stroke)
                .build()

        session.segmentInFlight = true

        val accepted =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(
                        gestureDescription:
                            GestureDescription
                    ) {
                        if (
                            pointerTouchSession !== session ||
                            session.generation !=
                                pointerTouchGeneration
                        ) {
                            return
                        }

                        session.segmentInFlight = false
                        session.stroke = stroke
                        session.endpointX = endX
                        session.endpointY = endY

                        if (finalSegment) {
                            completePointerTouchSession(
                                session
                            )
                        } else {
                            dispatchNextPointerTouchSegment(
                                session
                            )
                        }
                    }

                    override fun onCancelled(
                        gestureDescription:
                            GestureDescription
                    ) {
                        failPointerTouchSession(
                            session = session,
                            message =
                                "Air pointer touchscreen contact was cancelled"
                        )
                    }
                },
                mainHandler
            )

        if (!accepted) {
            session.segmentInFlight = false
            failPointerTouchSession(
                session = session,
                message =
                    "Air pointer touchscreen contact unavailable here"
            )
            return false
        }

        return true
    }

    private fun completePointerTouchSession(
        session: PointerTouchSession
    ) {
        if (pointerTouchSession !== session) {
            return
        }

        val dragged =
            session.dragGate.isDragging()
        val kind =
            if (dragged) {
                "drag"
            } else {
                session.releaseKind
            }

        pointerTouchSession = null
        pointerTouchGeneration++
        session.dragGate.reset()
        touchActionCoordinator.release(
            session.ticket
        )

        AirRuntime.recordPointerAction(
            success = true,
            message =
                when (kind) {
                    "drag" ->
                        "Air pointer drag completed"
                    "tap" ->
                        "Air pointer tap completed"
                    "long press" ->
                        "Air pointer long press completed"
                    else ->
                        "Air pointer touch released"
                }
        )
    }

    private fun failPointerTouchSession(
        session: PointerTouchSession,
        message: String
    ) {
        if (pointerTouchSession !== session) {
            return
        }

        pointerTouchSession = null
        pointerTouchGeneration++
        session.dragGate.reset()
        touchActionCoordinator.release(
            session.ticket
        )

        AirRuntime.recordPointerAction(
            success = false,
            message = message
        )
    }

    private fun abandonPointerTouchSession() {
        val session =
            pointerTouchSession
                ?: return

        pointerTouchSession = null
        pointerTouchGeneration++
        session.dragGate.reset()
        touchActionCoordinator.release(
            session.ticket
        )
    }

    private fun pointerDragThresholdPx(): Float {
        val platformTouchSlop =
            ViewConfiguration
                .get(this)
                .scaledTouchSlop
                .toFloat()
        val minimum =
            resources.displayMetrics.density *
                POINTER_TOUCH_MIN_DRAG_DP

        return maxOf(
            platformTouchSlop,
            minimum
        )
    }

    private fun dispatchPointerTap(
        x: Float,
        y: Float,
        sourceTimestampUptimeMs: Long?,
        resultQueuedAtUptimeMs: Long?,
        deadlineUptimeMs: Long
    ) {
        val generation = ++pointerDispatchGeneration
        AirRuntime.recordPointerActionPending(
            "Air pointer tap pending"
        )

        val dispatchNow =
            SystemClock.uptimeMillis()
        val accepted =
            dispatchOwnedGesture(
                gesture = tapGesture(x, y),
                label = "Air pointer tap",
                deadlineUptimeMs = deadlineUptimeMs
            ) { outcome ->
            if (
                generation !=
                    pointerDispatchGeneration
            ) {
                return@dispatchOwnedGesture
            }

            if (
                outcome ==
                    OwnedGestureOutcome.COMPLETED ||
                outcome ==
                    OwnedGestureOutcome.CANCELLED
            ) {
                AirRuntime.recordVisionInteractionLatency(
                    sourceToActionCompleteMs =
                        sourceTimestampUptimeMs
                            ?.let {
                                SystemClock.uptimeMillis() -
                                    it
                            }
                )
            }

            when (outcome) {
                OwnedGestureOutcome.COMPLETED ->
                    AirRuntime.recordPointerAction(
                        success = true,
                        message =
                            "Air pointer tap completed"
                    )
                OwnedGestureOutcome.CANCELLED ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer tap was cancelled"
                    )
                OwnedGestureOutcome.REJECTED_EXPIRED ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer tap expired before screen contact"
                    )
                OwnedGestureOutcome.REJECTED_BUSY ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer tap blocked • another touch action is in progress"
                    )
                OwnedGestureOutcome.UNAVAILABLE ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer tap unavailable here"
                    )
            }
        }

        if (accepted) {
            AirRuntime.recordVisionInteractionLatency(
                resultToActionDispatchMs =
                    resultQueuedAtUptimeMs
                        ?.let {
                            dispatchNow - it
                        },
                sourceToActionDispatchMs =
                    sourceTimestampUptimeMs
                        ?.let {
                            dispatchNow - it
                        }
            )
        }
    }

    private fun dispatchPointerLongPress(
        x: Float,
        y: Float,
        sourceTimestampUptimeMs: Long?,
        resultQueuedAtUptimeMs: Long?,
        deadlineUptimeMs: Long
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        720L
                    )
                )
                .build()

        val generation = ++pointerDispatchGeneration
        AirRuntime.recordPointerActionPending(
            "Air pointer long press pending"
        )

        val dispatchNow =
            SystemClock.uptimeMillis()
        val accepted =
            dispatchOwnedGesture(
                gesture = gesture,
                label = "Air pointer long press",
                deadlineUptimeMs = deadlineUptimeMs
            ) { outcome ->
            if (
                generation !=
                    pointerDispatchGeneration
            ) {
                return@dispatchOwnedGesture
            }

            if (
                outcome ==
                    OwnedGestureOutcome.COMPLETED ||
                outcome ==
                    OwnedGestureOutcome.CANCELLED
            ) {
                AirRuntime.recordVisionInteractionLatency(
                    sourceToActionCompleteMs =
                        sourceTimestampUptimeMs
                            ?.let {
                                SystemClock.uptimeMillis() -
                                    it
                            }
                )
            }

            when (outcome) {
                OwnedGestureOutcome.COMPLETED ->
                    AirRuntime.recordPointerAction(
                        success = true,
                        message =
                            "Air pointer long press completed"
                    )
                OwnedGestureOutcome.CANCELLED ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer long press was cancelled"
                    )
                OwnedGestureOutcome.REJECTED_EXPIRED ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer long press expired before screen contact"
                    )
                OwnedGestureOutcome.REJECTED_BUSY ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer long press blocked • another touch action is in progress"
                    )
                OwnedGestureOutcome.UNAVAILABLE ->
                    AirRuntime.recordPointerAction(
                        success = false,
                        message =
                            "Air pointer long press unavailable here"
                    )
            }
        }

        if (accepted) {
            AirRuntime.recordVisionInteractionLatency(
                resultToActionDispatchMs =
                    resultQueuedAtUptimeMs
                        ?.let {
                            dispatchNow - it
                        },
                sourceToActionDispatchMs =
                    sourceTimestampUptimeMs
                        ?.let {
                            dispatchNow - it
                        }
            )
        }
    }

    private fun invalidatePointerDispatches() {
        pointerDispatchGeneration++
    }

    private fun dispatchOwnedGesture(
        gesture: GestureDescription,
        label: String,
        deadlineUptimeMs: Long = Long.MAX_VALUE,
        callback: (OwnedGestureOutcome) -> Unit = {}
    ): Boolean {
        val acquisition =
            touchActionCoordinator.tryAcquire(
                nowMs = SystemClock.uptimeMillis(),
                deadlineMs = deadlineUptimeMs,
                label = label
            )
        val ticket = acquisition.ticket
        if (ticket == null) {
            callback(
                when (acquisition.failure) {
                    TouchAcquireFailure.EXPIRED ->
                        OwnedGestureOutcome
                            .REJECTED_EXPIRED
                    TouchAcquireFailure.BUSY ->
                        OwnedGestureOutcome
                            .REJECTED_BUSY
                    null ->
                        OwnedGestureOutcome.UNAVAILABLE
                }
            )
            return false
        }

        val accepted =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(
                        gestureDescription:
                            GestureDescription
                    ) {
                        if (
                            touchActionCoordinator
                                .release(ticket)
                        ) {
                            callback(
                                OwnedGestureOutcome
                                    .COMPLETED
                            )
                        }
                    }

                    override fun onCancelled(
                        gestureDescription:
                            GestureDescription
                    ) {
                        if (
                            touchActionCoordinator
                                .release(ticket)
                        ) {
                            callback(
                                OwnedGestureOutcome
                                    .CANCELLED
                            )
                        }
                    }
                },
                mainHandler
            )

        if (!accepted) {
            if (
                touchActionCoordinator
                    .release(ticket)
            ) {
                callback(
                    OwnedGestureOutcome.UNAVAILABLE
                )
            }
            return false
        }

        return true
    }

    private fun tapGesture(
        x: Float,
        y: Float
    ): GestureDescription {
        val path = Path().apply {
            moveTo(x, y)
        }
        return GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    70L
                )
            )
            .build()
    }

    private fun dispatchTap(
        x: Float,
        y: Float
    ): Boolean =
        dispatchOwnedGesture(
            gesture = tapGesture(x, y),
            label = "Select fallback tap"
        )

    private fun scrollGesture(forward: Boolean): GestureDescription {
        val rootBounds = Rect()
        rootInActiveWindow?.getBoundsInScreen(rootBounds)

        val metrics = resources.displayMetrics
        val width =
            if (!rootBounds.isEmpty) {
                rootBounds.width().toFloat()
            } else {
                metrics.widthPixels.toFloat()
            }
        val height =
            if (!rootBounds.isEmpty) {
                rootBounds.height().toFloat()
            } else {
                metrics.heightPixels.toFloat()
            }
        val left =
            if (!rootBounds.isEmpty) rootBounds.left.toFloat() else 0f
        val top =
            if (!rootBounds.isEmpty) rootBounds.top.toFloat() else 0f

        val x = left + width * 0.50f
        val startY =
            top + height * if (forward) 0.72f else 0.30f
        val endY =
            top + height * if (forward) 0.30f else 0.72f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    260L
                )
            )
            .build()

        return gesture
    }

    private fun dispatchScrollGesture(
        forward: Boolean
    ): Boolean =
        dispatchOwnedGesture(
            gesture = scrollGesture(forward),
            label = "Scroll fallback"
        )

    private fun directionalScrollGesture(
        direction: ScrollDirection
    ): GestureDescription {
        val rootBounds = Rect()
        rootInActiveWindow?.getBoundsInScreen(rootBounds)

        val metrics = resources.displayMetrics
        val width =
            if (!rootBounds.isEmpty) {
                rootBounds.width().toFloat()
            } else {
                metrics.widthPixels.toFloat()
            }
        val height =
            if (!rootBounds.isEmpty) {
                rootBounds.height().toFloat()
            } else {
                metrics.heightPixels.toFloat()
            }
        val left =
            if (!rootBounds.isEmpty) {
                rootBounds.left.toFloat()
            } else {
                0f
            }
        val top =
            if (!rootBounds.isEmpty) {
                rootBounds.top.toFloat()
            } else {
                0f
            }

        val centerX = left + width * 0.50f
        val centerY = top + height * 0.50f
        val horizontalStep = width * 0.12f
        val verticalStep = height * 0.12f

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction) {
            ScrollDirection.UP -> {
                startX = centerX
                endX = centerX
                startY = centerY - verticalStep
                endY = centerY + verticalStep
            }
            ScrollDirection.DOWN -> {
                startX = centerX
                endX = centerX
                startY = centerY + verticalStep
                endY = centerY - verticalStep
            }
            ScrollDirection.LEFT -> {
                startY = centerY
                endY = centerY
                startX = centerX - horizontalStep
                endX = centerX + horizontalStep
            }
            ScrollDirection.RIGHT -> {
                startY = centerY
                endY = centerY
                startX = centerX + horizontalStep
                endX = centerX - horizontalStep
            }
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        190L
                    )
                )
                .build()

        return gesture
    }

    private fun dispatchDirectionalScrollGesture(
        direction: ScrollDirection
    ): Boolean =
        dispatchOwnedGesture(
            gesture =
                directionalScrollGesture(direction),
            label = "Directional scroll fallback"
        )

    private fun dispatchGestureWithResult(
        gesture: GestureDescription,
        successMessage: String,
        cancelledMessage: String,
        unavailableMessage: String,
        callback: (ActionResult) -> Unit
    ) {
        var completed = false

        val accepted =
            dispatchOwnedGesture(
                gesture = gesture,
                label = successMessage
            ) { outcome ->
                if (completed) {
                    return@dispatchOwnedGesture
                }
                completed = true
                callback(
                    when (outcome) {
                        OwnedGestureOutcome.COMPLETED ->
                            ActionResult(
                                true,
                                successMessage
                            )
                        OwnedGestureOutcome.CANCELLED ->
                            ActionResult(
                                false,
                                cancelledMessage
                            )
                        OwnedGestureOutcome.REJECTED_EXPIRED ->
                            ActionResult(
                                false,
                                "Action expired before screen contact"
                            )
                        OwnedGestureOutcome.REJECTED_BUSY ->
                            ActionResult(
                                false,
                                "Touch action blocked • another air touch is in progress"
                            )
                        OwnedGestureOutcome.UNAVAILABLE ->
                            ActionResult(
                                false,
                                unavailableMessage
                            )
                    }
                )
            }

        if (!accepted && !completed) {
            completed = true
            callback(
                ActionResult(
                    false,
                    unavailableMessage
                )
            )
        }
    }

    private fun findBestScrollable(
        root: AccessibilityNodeInfo,
        action: Int
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val display = displayBounds()
        val displayCenterX = display.centerX()
        val displayCenterY = display.centerY()

        var best: AccessibilityNodeInfo? = null
        var bestScore = Long.MIN_VALUE
        var visitedNodes = 0

        while (
            queue.isNotEmpty() &&
            visitedNodes < MAX_ACCESSIBILITY_TREE_NODES
        ) {
            val node = queue.removeFirst()
            visitedNodes++

            val supportsRequestedDirection =
                node.actionList.any {
                    it.id == action
                }

            if (
                node.isScrollable &&
                node.isVisibleToUser &&
                supportsRequestedDirection
            ) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                if (!bounds.isEmpty) {
                    val width =
                        bounds.width()
                            .coerceAtLeast(0)
                            .toLong()
                    val height =
                        bounds.height()
                            .coerceAtLeast(0)
                            .toLong()
                    val area = width * height

                    val centerBonus =
                        if (
                            bounds.contains(
                                displayCenterX,
                                displayCenterY
                            )
                        ) {
                            area / 2L
                        } else {
                            0L
                        }

                    val score = area + centerBonus

                    if (score > bestScore) {
                        bestScore = score
                        best = node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(
                    queue::addLast
                )
            }
        }

        return best
    }

    companion object {
        private const val MAX_PARENT_HOPS = 32
        private const val MAX_ACCESSIBILITY_TREE_NODES = 512
        private const val POINTER_TOUCH_SEGMENT_MS = 48L
        private const val POINTER_TOUCH_RELEASE_SEGMENT_MS = 18L
        private const val POINTER_TOUCH_MIN_DRAG_DP = 8f

        @Volatile
        private var instance: AirAccessibilityService? = null

        fun execute(action: AirAction): ActionResult {
            val service = instance
                ?: return ActionResult(
                    false,
                    "Enable Air Gesture accessibility service"
                )
            return service.executeSingle(action)
        }

        fun executeAsync(
            action: AirAction,
            sourceTimestampUptimeMs: Long? = null,
            resultQueuedAtUptimeMs: Long? = null,
            deadlineUptimeMs: Long =
                Long.MAX_VALUE,
            shouldExecute: () -> Boolean = {
                true
            },
            callback: (ActionResult) -> Unit
        ) {
            val service = instance
            if (service == null) {
                callback(
                    ActionResult(
                        false,
                        "Enable Air Gesture accessibility service"
                    )
                )
                return
            }

            service.mainHandler.post {
                if (instance !== service) {
                    callback(
                        ActionResult(
                            false,
                            "Accessibility service disconnected"
                        )
                    )
                    return@post
                }

                if (!shouldExecute()) {
                    callback(
                        ActionResult(
                            false,
                            "Action cancelled because Air Control changed"
                        )
                    )
                    return@post
                }

                if (
                    SystemClock.uptimeMillis() >
                        deadlineUptimeMs
                ) {
                    callback(
                        ActionResult(
                            false,
                            "Action expired before execution"
                        )
                    )
                    return@post
                }

                val dispatchNow =
                    SystemClock.uptimeMillis()
                AirRuntime.recordVisionInteractionLatency(
                    resultToActionDispatchMs =
                        resultQueuedAtUptimeMs
                            ?.let {
                                dispatchNow - it
                            },
                    sourceToActionDispatchMs =
                        sourceTimestampUptimeMs
                            ?.let {
                                dispatchNow - it
                            }
                )

                service.executeSingleAsync(
                    action = action
                ) { result ->
                    AirRuntime.recordVisionInteractionLatency(
                        sourceToActionCompleteMs =
                            sourceTimestampUptimeMs
                                ?.let {
                                    SystemClock.uptimeMillis() -
                                        it
                                }
                    )
                    callback(result)
                }
            }
        }

        fun updatePointer(
            decision: PointerDecision,
            enabled: Boolean,
            sourceTimestampUptimeMs: Long? = null,
            resultQueuedAtUptimeMs: Long? = null,
            actionDeadlineUptimeMs: Long? = null,
            shouldApply: () -> Boolean = {
                true
            }
        ) {
            val service = instance ?: return
            service.enqueuePointerUpdate(
                decision = decision,
                enabled = enabled,
                sourceTimestampUptimeMs =
                    sourceTimestampUptimeMs,
                resultQueuedAtUptimeMs =
                    resultQueuedAtUptimeMs,
                actionDeadlineUptimeMs =
                    actionDeadlineUptimeMs,
                shouldApply = shouldApply
            )
        }

        fun setPointerEnabled(enabled: Boolean) {
            val service = instance ?: return
            service.mainHandler.post {
                if (!enabled && instance === service) {
                    service.invalidatePointerDispatches()
                    service.clearPendingPointerUpdates()
                    service.requestPointerTouchRelease(
                        kind = "pointer disabled"
                    )
                    service.hideCursor()
                }
            }
        }

        fun isReady(): Boolean = instance != null
    }
}

data class ActionResult(
    val success: Boolean,
    val message: String
)
