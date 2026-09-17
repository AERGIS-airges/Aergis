---
name: kotlin-api-design
description: Use when designing or reviewing public Kotlin APIs, interfaces, models, constructors, visibility, nullability, or dependency boundaries in AERMOTUS/AERGIS.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Kotlin API design

- Make the smallest API surface that expresses the actual contract.
- Prefer explicit types, nullability, ownership, and lifecycle over implicit conventions.
- Keep implementation details private unless callers have a demonstrated need.
- Preserve source/binary compatibility only when the repository's actual release contract requires it.
- Avoid adding abstractions without a concrete consumer.
- Keep domain APIs independent of Android framework types under the project's Clean Architecture standard.
- For pointer/gesture policy components, make invariants and failure behavior explicit and testable.
