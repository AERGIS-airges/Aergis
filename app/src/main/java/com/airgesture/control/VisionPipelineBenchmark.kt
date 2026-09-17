package com.airgesture.control

data class VisionPipelineKey(
    val delegate: VisionDelegateMode,
    val inputMode: VisionInputMode
)

data class VisionPipelineBucket(
    val key: VisionPipelineKey,
    val samples: Long,
    val freshSamples: Long,
    val staleSamples: Long,
    val meanAnalysisToResultMs: Float,
    val stalePercent: Float
)

/**
 * Evidence collector for F17.
 *
 * It does not choose CPU/GPU from assumptions. It records exact-result
 * pipeline identity and observed analyzer-to-result latency/staleness. Final
 * delegate/resolution policy still requires same-scene, same-workload device
 * measurements including thermal/battery evidence.
 */
class VisionPipelineBenchmark {
    private data class MutableBucket(
        var samples: Long = 0L,
        var freshSamples: Long = 0L,
        var staleSamples: Long = 0L,
        var latencyTotalMs: Double = 0.0
    )

    private val buckets =
        mutableMapOf<
            VisionPipelineKey,
            MutableBucket
            >()

    @Synchronized
    fun onResult(
        key: VisionPipelineKey,
        analysisToResultMs: Long,
        stale: Boolean
    ) {
        if (analysisToResultMs < 0L) return

        val bucket =
            buckets.getOrPut(key) {
                MutableBucket()
            }
        bucket.samples++
        bucket.latencyTotalMs +=
            analysisToResultMs.toDouble()
        if (stale) {
            bucket.staleSamples++
        } else {
            bucket.freshSamples++
        }
    }

    @Synchronized
    fun snapshot(): List<VisionPipelineBucket> =
        buckets.map { (key, value) ->
            val count =
                value.samples
                    .coerceAtLeast(1L)
            VisionPipelineBucket(
                key = key,
                samples = value.samples,
                freshSamples =
                    value.freshSamples,
                staleSamples =
                    value.staleSamples,
                meanAnalysisToResultMs =
                    (
                        value.latencyTotalMs /
                            count.toDouble()
                        ).toFloat(),
                stalePercent =
                    (
                        value.staleSamples
                            .toDouble() /
                            count.toDouble() *
                            100.0
                        ).toFloat()
            )
        }.sortedWith(
            compareBy<VisionPipelineBucket>(
                { it.key.inputMode.name },
                { it.key.delegate.name }
            )
        )

    @Synchronized
    fun reset() {
        buckets.clear()
    }

    fun summary(): String {
        val rows = snapshot()
        if (rows.isEmpty()) {
            return "Pipeline benchmark: no samples"
        }

        return rows.joinToString(
            separator = " | ",
            prefix = "Pipeline samples: "
        ) { row ->
            "${row.key.delegate.name}/" +
                "${row.key.inputMode.name} " +
                "n=${row.samples} " +
                "mean=" +
                String.format(
                    java.util.Locale.US,
                    "%.0fms",
                    row.meanAnalysisToResultMs
                ) +
                " stale=" +
                String.format(
                    java.util.Locale.US,
                    "%.0f%%",
                    row.stalePercent
                )
        }
    }
}
