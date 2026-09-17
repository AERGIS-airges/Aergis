---
name: to-plan
description: Use before substantial AERMOTUS/AERGIS implementation, refactoring, or debugging work when the change has multiple files, architectural impact, or meaningful risk. Produce a repository-grounded plan before editing.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Repository-aware planning

- Establish the exact branch/commit under review.
- Inspect relevant source, tests, workflows, configuration, and current status before planning.
- State the observed problem and evidence; distinguish facts from hypotheses.
- Identify the smallest coherent change set and expected regression coverage.
- Identify validation commands and physical-device gates before implementation.
- Record blockers or unknowns explicitly rather than filling gaps with assumptions.
- Do not edit production source during a planning-only task.
- Once implementation is authorized, follow the plan but update it when new evidence invalidates an assumption.
