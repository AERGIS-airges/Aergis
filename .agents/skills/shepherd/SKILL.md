---
name: shepherd
description: Use for multi-step AERMOTUS/AERGIS work spanning source changes, tests, CI, artifacts, documentation, and device gates. Keep the workflow coherent and evidence-backed from start to finish.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Repository shepherd

- Start from the actual repository state and define the target outcome.
- Keep one coherent workstream; avoid unrelated feature creep.
- After each meaningful change, maintain a clear chain: source → tests → CI → artifact → device evidence where applicable.
- Do not treat documentation as proof of implementation.
- Do not treat CI as proof of physical-device behavior.
- If a downstream step is blocked, preserve the evidence and fix the blocker rather than silently skipping it.
- Before handoff, report exact commit/ref, implemented changes, validation evidence, unresolved risks, and next required physical-device action.
