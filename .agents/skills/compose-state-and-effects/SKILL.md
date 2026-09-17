---
name: compose-state-and-effects
description: Use for Jetpack Compose state ownership, hoisting, effects, lifecycle collection, navigation state, one-shot UI events, focus, and snackbar behavior in AERMOTUS/AERGIS.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Compose state and effects

## Rules
- Every state value must have one clear owner.
- Hoist state only to the lowest level that genuinely needs to coordinate it.
- Keep UI state separate from domain/data state; do not leak Android framework concerns into pure domain logic.
- Prefer lifecycle-aware Flow collection for UI observation.
- Justify every effect by its lifecycle trigger and cancellation behavior.
- Treat one-shot events as events, not persistent screen state, unless the UI contract explicitly requires persistence.
- Keep composables previewable and testable without hidden application-global dependencies.
- Inspect navigation and lifecycle boundaries before changing effect placement.

## AERGIS validation
For gesture/tracking screens, explicitly verify that calibration, tracking, mapping, and accessibility state cannot acquire competing owners or survive longer than their intended session.
