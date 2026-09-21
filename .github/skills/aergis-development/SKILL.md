# AERGIS Development Skill

## Purpose
Use this skill for all AERMOTUS/AERGIS implementation, debugging, refactoring, and feature work.

## Mandatory workflow
1. Inspect the live repository and active branch before changing code.
2. Treat GitHub/GitHub Actions as the objective source of truth.
3. Establish root cause from evidence before editing production code.
4. Make the justified change/changes; avoid speculative fixes and feature creep.
5. Add or update regression tests for every reproducible defect.
6. Validate with repository tests, lint/build, and CI where available.
7. Report separately what is implemented, what CI proves, and what remains unverified on hardware.
8. Never claim a fix, build, APK, or test passed without evidence.

## Engineering priorities
- Pointer stability,gesture mapping and recognition and positional accuracy first.
- Reduce false positives and false negatives.
- Preserve causal/timestamp correctness and fail-safe behavior.
- Prefer deterministic, testable policy/filter components.
- Follow the project's Clean Architecture standard: dependencies flow inward/downward and domain logic remains free of Android framework dependencies.
- Preserve the protected/main baseline unless an explicit merge/release decision is made.

## Device constraint
Development is Android-phone-only. Use free, phone-accessible workflows and do not require a PC, paid API credits, or paid cloud development environments.

## Evidence discipline
When a CI run fails, inspect the actual failing job/log/test and identify the root cause before making another code change. Do not weaken assertions merely to make CI green.
