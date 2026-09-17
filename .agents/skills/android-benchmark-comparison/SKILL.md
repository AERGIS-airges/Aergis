---
name: android-benchmark-comparison
description: Use when comparing Android performance configurations or measured defaults for AERMOTUS/AERGIS. Require controlled conditions, comparable measurements, and bounded conclusions.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Android benchmark comparison

- Define the exact device, build, configuration, workload, and metric before comparing results.
- Keep thermal state, battery state, background load, orientation, permissions, and test duration as controlled as practical.
- Compare like-for-like configurations; do not infer causality from unrelated runs.
- Repeat unstable measurements and report spread, not just the best run.
- Use traces/logs when rankings or cadence appear unstable.
- Separate app-side scheduling cadence from CameraX capture cadence and MediaPipe result cadence.
- Treat Galaxy A54 measurements as physical-device evidence; CI cannot prove them.
- Conclude only what the collected measurements support.
