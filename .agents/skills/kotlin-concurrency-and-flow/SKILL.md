---
name: kotlin-concurrency-and-flow
description: Use for Kotlin coroutines, Flow, StateFlow, SharedFlow, Channel, cancellation, scopes, lifecycle, threading, and asynchronous ownership in AERMOTUS/AERGIS.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Kotlin concurrency and Flow

- Every coroutine must have an explicit owner and intended lifetime.
- Prefer structured concurrency; avoid raw threads/executors unless their lifecycle is explicit and justified.
- Inspect cancellation propagation before changing scopes or moving work between layers.
- Use StateFlow for durable observable state and SharedFlow/Channel only when the event semantics require it.
- Choose `stateIn`/`shareIn` policies deliberately; do not assume sharing is free.
- Keep camera, MediaPipe, pointer, accessibility, and UI lifecycles distinct where their ownership differs.
- Do not introduce background work merely to mask a timing symptom; identify the producer, consumer, and cancellation boundary first.
- Add deterministic tests around race-prone policy/filter logic whenever practical.
