---
name: compose-performance
description: Use when diagnosing Compose recomposition, stability, frame pacing, UI jank, or measurable performance regressions in AERMOTUS/AERGIS. Prefer measured evidence and the smallest repair.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Compose performance

This is an AERGIS-specific integration of the performance principles from `chrisbanes/skills`, pinned to upstream commit `9d982e1d9f62211af2efb7c3ff34a898d693e0a7`. It is intentionally an operational overlay, not a verbatim copy.

## Workflow
1. Define one user-visible transition or measurable performance symptom.
2. Establish a baseline using the same scenario before changing code.
3. Classify the likely axis: recomposition/stability, state reads, layout/draw work, cross-phase state writes, allocation, or upstream camera/vision cadence.
4. Inspect the actual source and measurements before proposing a fix; do not optimize a suspected cause without evidence.
5. Make the smallest change that addresses the measured cause.
6. Re-run the same measurement and compare against the baseline.
7. Keep changes separate when multiple causes are plausible so each result is attributable.

## AERGIS rules
- Do not trade pointer accuracy or false-activation safety for cosmetic smoothness without explicit evidence.
- Do not call higher app-side submission cadence proof of higher CameraX or MediaPipe result cadence.
- Treat Galaxy A54 device measurements as distinct from JVM/unit-test evidence.
- Never claim a performance improvement without before/after evidence.
