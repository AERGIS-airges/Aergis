# Historical Recovery Baseline (archival — read-only)

> **Status:** The GitHub organization referenced below (`AERMOTUS-9404025043089`) has been closed and no longer exists.
> This file is kept only as a historical record of how the current source tree was originally recovered.
> It has no bearing on the new repository and does not need to be acted on. See `DEVELOPMENT_STATUS.md`
> for the current, actionable state of the project.

# AERMOTUS Recovery Baseline

## Active repository
- Owner: `AERMOTUS-9404025043089`
- Repository: `AERMOTUS`
- Visibility: private
- Purpose: clean active development repository with fresh GitHub Actions/storage ownership.

## Known-good recovery source
- Historical source repository: `mrpsrabe-coder/AIR-GESTURE-CONTROL`
- Exact source commit: `744d8372a6127fe0f065a5cbb4b4d946ca19844b`
- Historical CI run: `#709`
- CI result: successful
- App version: `0.18.15-preview`
- Version code: `37`
- Application ID: `com.airgesture.control`
- Minimum SDK: API 26
- Build configuration at this checkpoint: minification disabled

## Known-good APK evidence
- Original filename: `AirGestureControl_Proto_bySTEPHRABE9404025043089-audit-744d837-ci709.apk`
- Recovery filename: `AirGestureControl-v0.18.15-CI709-KNOWN-GOOD-unminified.apk`
- Size: `68,095,185 bytes` (~64.94 MB)
- Original/recovery MD5: `eb30741822dfe2011d24bf9303a4c688`
- Recovery SHA-256: `0c5855154dfa8a1e6ed7fde5d491086a1910e3b4fab53c0d6c382ca9ea67df01`
- Signing certificate SHA-256: `31dae6c69ef983030be3a1df1f0bf7eb51e91c17ddd75b62a955bb5f171004d0`
- Recovery build verification: exact CI709 app tree gate, unit tests, lint, unminified debug assembly and signing-certificate verification all passed.
- Byte identity: VERIFIED. Recovery APK MD5 and byte size exactly match the user-preserved known-good APK evidence.
- Device status: VERIFIED 2026-09-10 on the user's Galaxy A54. The byte-identical recovered APK reproduced the remembered reliable behavior and is accepted as the authoritative recovery control.

## Recovery contract
1. Reconstruct the new AERMOTUS source baseline from the exact `744d8372...` source tree, not from later v0.18.19/minified branches.
2. Do not import old Git history, Actions artifacts, caches, stale build outputs, or the old F01-F34 audit as a controlling backlog.
3. Preserve useful source, resources, models/assets, Gradle configuration, tests, signing setup required for device-test APKs, and relevant regression evidence.
4. Keep routine device-test builds unminified unless a separately controlled minified build is being tested for release-equivalence.
5. Every meaningful successful development change must end with an installable APK published for physical-device testing.
6. Prefer GitHub Releases for APK delivery; Actions artifacts are secondary and should use conservative retention.
7. Physical Galaxy A54 behavior is authoritative for interaction quality; compilation/CI alone is not device verification.
8. Continue with one targeted evidence-driven improvement at a time from this baseline.

## Repository roles
- `AERMOTUS-9404025043089/AERMOTUS`: active development source of truth.
- `mrpsrabe-coder/AIR-GESTURE-CONTROL`: historical source/reference only; used for one-time CI709 recovery/export only.
- `mrpsrabe-coder/AERMOTUS`: superseded personal-account migration attempt; historical/reference only.

## Source promotion evidence

The organization promotion bundle from run [34513495268](https://github.com/AERMOTUS-9404025043089/AERMOTUS/actions/runs/34513495268), artifact `10166972091`, was independently downloaded and checked on 2026-09-10.

- Bundle ZIP SHA-256: `c1fc5c1f58a43c644645ecb695f2b0ba7de926cebbba57cefee13b7c73afeaf8`.
- Source archive SHA-256: `d6d0d2af906d6fb3061e2c3cc24615f90f8ca78df0e8fd41b3a3e2b9427b42d0`.
- All 88 retained files match the recursive Git tree at historical commit `744d8372a6127fe0f065a5cbb4b4d946ca19844b`, including modes. No extra archive files were present.
- The entire application tree is `6b2c519db230c29a2e6a214fd0435a22ea4609ff`.
- Four historical files are intentionally not promoted: old README, development status, audit resolution and Android workflow. Organization documentation and lean release CI replace them. They contain no application implementation.
- No Gradle wrapper or model binary existed in the historical Git tree. Explicit Gradle 8.11.1 setup and the unchanged pinned model downloader reproduce the accepted build.

The exhaustive retained-file record is [ci709-source-manifest.json](docs/recovery/ci709-source-manifest.json). Source fidelity proves source preservation. The new root-source workflow must also pass its exact APK identity check before UI implementation.

## Rollback

Protect release tag `ci709-baseline` and its APK; never overwrite it. The APK hash above remains authoritative. Later UI candidates must be separate commits, versions and releases. Physical interaction quality is accepted only through Galaxy A54 evidence.

## EXACT NEXT ACTION

Follow `DEVELOPMENT_STATUS.md`. Complete root-source build/Release verification, then begin the supplied UI design contract without engine changes.
