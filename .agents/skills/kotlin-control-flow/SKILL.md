---
name: kotlin-control-flow
description: Use when reviewing Kotlin branching, loops, early returns, nullability, sealed-state handling, or control-flow refactors in AERMOTUS/AERGIS.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Kotlin control flow

- Prefer control flow that makes invalid states difficult to represent.
- Use early returns when they reduce nesting without hiding important policy.
- Handle exhaustive sealed states deliberately; do not add an `else` that silently absorbs new states when exhaustiveness is part of the safety contract.
- Keep nullability explicit and avoid assertion operators for normal control flow.
- Do not refactor merely for style when the current code is clearer and covered.
- Preserve temporal and safety invariants in gesture/camera/accessibility state machines when simplifying branches.
