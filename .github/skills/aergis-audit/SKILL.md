# AERGIS Audit Skill

## Purpose
Use this skill for comprehensive AERMOTUS/AERGIS audits, reliability reviews, architecture reviews, and pre-release inspections.

## Audit method
1. Establish the exact commit/branch under review.
2. Inventory source, tests, workflows, assets, configuration, and documentation.
3. Search systematically for defects, dead paths, contradictory logic, weak validation, stale assumptions, outdated APIs/attributes, performance risks, lifecycle/concurrency issues, and privacy/safety regressions.
4. Trace important execution paths end-to-end rather than reviewing isolated files only.
5. Cross-check implementation against tests, workflow invariants, documented requirements, and the Clean Architecture standard.
6. Classify findings by severity and confidence.
7. Fix only findings supported by evidence; add regression coverage.
8. Re-run validation and distinguish confirmed findings from items requiring physical-device verification.

## Priority order
1. Crashes, data corruption, security/privacy, or unsafe automation.
2. Pointer accuracy/stability and false activation behavior.
3. Timing, concurrency, lifecycle, camera/vision pipeline, and accessibility failures.
4. Performance, battery, thermal, and resource risks.
5. Architecture and maintainability.
6. Non-essential UX/features only after higher-priority risks are controlled.

## Evidence rule
Do not call an issue fixed because code looks plausible. A fix requires a reproducible test, CI evidence, or explicit device evidence appropriate to the defect.
