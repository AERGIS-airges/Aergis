---
name: compose-ui-testing-patterns
description: Use when adding or repairing Compose UI tests in AERMOTUS/AERGIS. Favor stable semantics, deterministic state, behavior assertions, and regression coverage over implementation-detail assertions.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Compose UI testing

- Test user-visible behavior and stable semantics rather than private layout implementation details.
- Keep tests deterministic: control state, time, navigation, and asynchronous work where possible.
- Give important controls stable semantic identifiers when text is not a durable selector.
- Add a regression test for every reproducible UI defect that can be expressed without a physical device.
- Separate screenshot/visual validation from behavioral assertions; do not treat one as proof of the other.
- For gesture/tracking UI, test state transitions and safety policy independently from physical pointer accuracy, which still requires device evidence.
