package com.airgesture.control

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Debug-only, read-only view of the R8 production-vs-One-Euro shadow evidence.
 *
 * This activity cannot change filter selection. The real pointer continues to
 * use R7 PointerMotionFilter; OneEuroPointerFilter remains observation-only.
 */
class FilterDiagnosticsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var output: TextView

    private val refresh =
        object : Runnable {
            override fun run() {
                render()
                handler.postDelayed(this, REFRESH_MS)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val padding =
            (20f * resources.displayMetrics.density)
                .toInt()

        output =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(8, 12, 18))
                textSize = 15f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.START
                setPadding(
                    padding,
                    padding,
                    padding,
                    padding
                )
            }

        val scroll =
            ScrollView(this).apply {
                setBackgroundColor(Color.rgb(8, 12, 18))
                addView(output)
            }

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun render() {
        val diagnostic =
            PointerFilterDiagnostics.current()
        val sample = diagnostic.snapshot

        output.text =
            buildString {
                appendLine("AERMOTUS — R8 POINTER FILTER DIAGNOSTICS")
                appendLine("=======================================")
                appendLine()
                appendLine("READ-ONLY SHADOW EVALUATION")
                appendLine("Production: ${diagnostic.productionControl}")
                appendLine("Challenger: ${diagnostic.challenger}")
                appendLine()
                appendLine(
                    "IMPORTANT: One Euro does NOT control the cursor in this build."
                )
                appendLine(
                    "This screen only compares both filters against the same calibrated landmark-8 samples."
                )
                appendLine()

                if (sample == null) {
                    appendLine("No pointer samples collected in this process yet.")
                    appendLine()
                    appendLine("Start Air Control, move the index fingertip, then return here.")
                    appendLine("If Android restarted the app process, the counters begin again.")
                } else {
                    appendLine("SAMPLES")
                    appendLine("-------")
                    appendLine("Total: ${sample.samples}")
                    appendLine("Stationary-classified: ${sample.stationarySamples}")
                    appendLine("Deliberate-motion: ${sample.deliberateMotionSamples}")
                    appendLine()

                    appendLine("STATIONARY RMS STEP — normalized screen units")
                    appendLine("---------------------------------------------")
                    appendLine("Raw landmark 8 : ${format(sample.rawStationaryRmsStep)}")
                    appendLine("R7 production  : ${format(sample.productionStationaryRmsStep)}")
                    appendLine("One Euro shadow: ${format(sample.challengerStationaryRmsStep)}")
                    appendLine()

                    appendLine("MEAN MOTION ERROR TO LATEST MEASUREMENT")
                    appendLine("---------------------------------------")
                    appendLine("R7 production  : ${format(sample.productionMeanMotionError)}")
                    appendLine("One Euro shadow: ${format(sample.challengerMeanMotionError)}")
                    appendLine()

                    appendLine("ACCUMULATED PATH LENGTH")
                    appendLine("-----------------------")
                    appendLine("Raw landmark 8 : ${format(sample.rawPathLength)}")
                    appendLine("R7 production  : ${format(sample.productionPathLength)}")
                    appendLine("One Euro shadow: ${format(sample.challengerPathLength)}")
                    appendLine()

                    appendLine("SUMMARY")
                    appendLine("-------")
                    appendLine(diagnostic.summary)
                    appendLine()
                    appendLine("Last sample timestamp: ${diagnostic.updatedAtMs} ms")
                }

                appendLine()
                appendLine("INTERPRETATION RULE")
                appendLine("-------------------")
                appendLine(
                    "Lower stationary RMS generally means less visible shimmer."
                )
                appendLine(
                    "Lower motion error can mean better responsiveness, but raw landmarks are not ground truth."
                )
                appendLine(
                    "Do not promote the challenger from these numbers alone; Galaxy A54 device behavior remains the gate."
                )
            }
    }

    private fun format(value: Float): String =
        String.format(Locale.US, "%.5f", value)

    private companion object {
        const val REFRESH_MS = 500L
    }
}
