# Aergis

Aergis is an Android accessibility app that lets you control your phone with mid-air hand
gestures, captured on-device via the front camera (CameraX + MediaPipe). No cloud processing,
no chained automations — one recognized gesture triggers one user-configured action.

- **Package / application ID:** `com.airgesture.control` (unchanged intentionally — do not rename;
  see "Signing" below for why)
- **Min SDK:** 26 · **Target/compile SDK:** 36
- **UI:** Jetpack Compose
- **Current version:** see `app/build.gradle.kts` (`versionName` / `versionCode`) — this is the
  single source of truth; don't trust version numbers written in older docs.

Read [`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) for exactly where the project stands and
what to do next. `MIGRATION_BASELINE.md` is archival history from before this repository existed
and is not actionable.

## Build

This project has no committed Gradle wrapper (there was none in the original source, and there's
no local machine in the loop to generate one — everything builds in GitHub Actions). CI installs
a pinned Gradle version explicitly. To build locally if you ever have a machine with Android
Studio / a JDK 17 + Gradle install available:

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`.github/workflows/ci.yml` runs the same three steps, plus packaging and a set of hard invariant
checks (see below), on every push.

## Hard invariants (CI-enforced)

These are deliberate product/regression guardrails from past regressions; CI fails the build if
any is violated:

- `AergisActivity` must be the **only** `MAIN`/`LAUNCHER` activity.
- The legacy `ProductionActivity` must stay non-exported/non-launchable.
- `FilterDiagnosticsActivity` must stay hidden/non-launchable.
- No floating camera/landmark debug overlay may appear outside the debug build's mirror bridge.
- The app must use its bundled font (`R.font.aergis_display`), never a bare `FontFamily.SansSerif`
  fallback.
- The pointer path must keep using landmark #8 as the sole position measurement, causally
  filtered, with no forward extrapolation.

If a change (yours or an AI assistant's) touches any file covered by these checks, expect
`ci.yml` to fail loudly rather than silently regress — that's the point.

## Signing

`keystore/airgesture-preview.jks` is a **preview/debug-equivalent** keystore committed to the
repo on purpose, so CI and any device you install on can produce byte-consistent, installable
test APKs without secrets management. It must **not** be used to sign a Play Store release —
generate a dedicated release keystore when release work starts (out of scope for now).

## Project layout

- `app/src/main/java/com/airgesture/control/` — application code (Compose UI, CameraX/MediaPipe
  pipeline, pointer filters, accessibility service, gesture mapping).
- `app/src/test/java/...` — unit tests (279+ at last count; keep this green).
- `app/src/debug/` / `app/src/release/` — build-type-specific sources (e.g. the debug tracking
  mirror bridge).
- `docs/` — engineering notes and recovery evidence.
- `.agents/skills/`, `.github/skills/` — reusable instructions for AI coding agents working in
  this repo (Compose, Kotlin, Gradle, CI-validation conventions). Worth reading before making
  non-trivial changes.
