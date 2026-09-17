# Chris Banes skills integration

## Purpose

AERMOTUS/AERGIS uses a curated Agent Skills-compatible overlay based on the engineering concerns covered by `chrisbanes/skills`.

Upstream source: https://github.com/chrisbanes/skills
Pinned upstream commit: `9d982e1d9f62211af2efb7c3ff34a898d693e0a7` (2026-09-11, verified GitHub commit).

The repository does **not** copy the upstream skill text verbatim. The `.agents/skills/` files are concise AERGIS-specific overlays that preserve the relevant engineering intent while adding the project's stronger evidence, safety, phone-only, pointer, CI, and device-validation constraints.

## Adopted overlays

- `using-chrisbanes-skills` — routing/skill selection.
- `compose-state-and-effects` — Compose state ownership and effects.
- `compose-performance` — measured Compose performance diagnosis.
- `compose-ui-testing-patterns` — deterministic Compose behavior tests.
- `kotlin-concurrency-and-flow` — coroutine/Flow ownership and cancellation.
- `kotlin-api-design` — explicit Kotlin API and boundary design.
- `kotlin-control-flow` — safe Kotlin control-flow refactoring.
- `android-benchmark-comparison` — controlled Android performance comparisons.
- `gradle-run` — evidence-first Gradle/build failure discipline.
- `to-plan` — repository-grounded planning before risky work.
- `shepherd` — multi-step repository workflow coordination.

## Deliberately not imported

- `release-kotlin-library`: AERG​IS is an Android application, not a Kotlin library release project.
- `implement-with-subagents`: not activated because the upstream workflow depends on external provider skills that have not been independently verified in this environment.
- `run-github-project`: not activated for the same provider/dependency reason; AERG​IS already has project-specific GitHub/CI procedures.
- `grounded-writing`: useful but not material to the current engineering workflow.
- `compose-component-design`, `compose-focus-navigation`, and `compose-animations`: available as future additions if AERG​IS work creates a concrete need.

## Authority and precedence

1. Live AERMOTUS repository state, Git history, tests, GitHub Actions evidence, and physical-device evidence are authoritative.
2. AERMOTUS project skills under `.github/skills/` impose project-specific constraints.
3. These curated upstream-derived overlays provide focused Kotlin/Compose/Gradle guidance.
4. Upstream skill guidance never overrides AERG​IS safety, evidence, branch, or device-validation rules.

## Discovery

The `.agents/skills/` location follows the current Agent Skills project-scope convention used by Codex/GitHub Copilot-compatible installations. A skill is discoverable only in a skills-compatible agent/runtime; merely committing a `SKILL.md` does not change ChatGPT behavior automatically.

## Validation requirement

Before treating the collection as release-ready skill infrastructure, validate each `.agents/skills/*` directory with the Agent Skills reference validator (`skills-ref validate <skill-directory>`). If the validator is unavailable in the execution environment, report that validation as unverified rather than claiming success.
