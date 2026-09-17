---
name: gradle-run
description: Use for Gradle commands, build/test/lint execution, and repeated AERMOTUS/AERGIS build failures. Stop blind reruns and diagnose the actual failure first.
metadata:
  owner: aermotus
  upstream: chrisbanes/skills
  upstream-commit: 9d982e1d9f62211af2efb7c3ff34a898d693e0a7
  integration: curated-overlay
---

# Gradle execution discipline

1. Identify the exact commit, branch, Gradle wrapper/version, task, and environment.
2. Run the narrowest relevant command first when diagnosing a failure.
3. Preserve the full failure fingerprint: task, exception, source location, and first causal error.
4. Group repeated failures by fingerprint instead of spending runs on identical failures.
5. Inspect the cited source/test/configuration before editing.
6. Make one minimal repair at a time and verify it with the same command.
7. Do not weaken tests, disable lint, skip tasks, or alter assertions just to obtain a green build.
8. A green compile/test run proves only the checks that actually ran; APK/device behavior needs its own evidence.

For CI failures, use the project's `aergis-ci-validation` skill in addition to this skill.
