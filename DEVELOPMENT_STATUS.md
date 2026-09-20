# Aergis — Development Status

This file replaces a long, episode-by-episode narrative (R17/R18/vc60/vc62/vc63...) that had
piled up across many AI-assisted sessions in the old repository. What matters from it has been
folded in below; the play-by-play is gone. Treat this as the single current-truth checkpoint —
edit it in place as you make real progress, don't append a new "R19 — vc64" section per session.

## Where things stand

- **App name:** Aergis. **Package:** `com.airgesture.control`. **Min SDK 26 / target-compile 36.**
- **Launcher:** `AergisActivity` is the sole `MAIN`/`LAUNCHER` activity. `ProductionActivity`
  (legacy UI) and `MainActivity` are compiled but non-exported/non-launchable — see "Cleanup
  backlog" below on whether to keep them.
- **Typography:** bundled Rajdhani SemiBold (`res/font/aergis_display.ttf`, SIL OFL 1.1,
  license under `app/src/main/assets/licenses/`). No `FontFamily.SansSerif` fallback anywhere in
  the theme — this is a CI-enforced invariant.
- **Pointer pipeline:** CameraX analysis in `YUV_420_888`. MediaPipe GestureRecognizer constrained
  to one hand during pointer sessions. Landmark #8 (index fingertip) is the sole position
  measurement, run through `PointerKinematicFilter` — a causal alpha-beta/Kalman-family estimator
  with confidence-aware innovation gating, a hard no-overshoot/no-forward-extrapolation
  constraint, and a raised position-gain floor (0.25) so deliberate motion tracks faster at low
  camera cadence. Filter state resets on session start, orientation/calibration change, control-
  hand discontinuity, and tracking loss. Raw landmark-8 telemetry stays available separately from
  the filtered cursor for debugging.
- **Automated verification:** unit tests (279+ last count), lint, debug APK assembly, and a set of
  hard-invariant checks all run in `ci.yml` on every push (see README for the invariant list).
- **Device verification:** not current. Every APK produced so far has been marked "device test
  required" — there is no recent confirmed pass on real hardware for camera FPS, pointer latency,
  jitter, or false-click rate at the current filter tuning.

## Known open engineering issue

Real MediaPipe result cadence has been observed around 8–12 FPS on a Galaxy A54, well below what
the pointer filter and pacing constants assume. This is upstream of the pointer-filter tuning
work (it's a CameraX/MediaPipe throughput problem, not a smoothing problem) and hasn't been
root-caused yet. Next investigation step: trace actual camera FPS → submitted-frame FPS →
MediaPipe result FPS, and check whether a higher-throughput CameraX stream configuration is
available and worth the trade-off.

## Cleanup backlog (safe to pick up any time)

- `ProductionActivity.kt` (~1180 lines) and `MainActivity.kt` (~560 lines) are legacy/unused UI
  kept compiled-but-unreachable. Decide whether anything in them is still needed as reference,
  then either delete them or fold anything useful into the current `AergisActivity` surface. This
  is pure cleanup — don't do it in the same change as a behavior fix, so a regression is easy to
  attribute.
- A handful of user-facing strings inside `ProductionActivity.kt` and `MainActivity.kt` still say
  "AERMOTUS" — irrelevant if those files get deleted per the point above; otherwise rename them
  for consistency.
- Move the keystore password (currently the literal `"android"`) into a gitignored
  `keystore.properties` file or a CI secret before this repo is ever made public, even though it's
  only a preview-signing cert with no production trust behind it.
- `AermotusVisuals.kt`, `AirModels.kt`, and other filenames still carry the old `AERMOTUS`/`Air`
  naming even though behavior and app identity have moved to Aergis. Renaming files is safe but
  touch-heavy (imports everywhere) — worth doing once, deliberately, not as a side effect of an
  unrelated change.

## Immediate next action

1. CI recovery (in progress): AGP was pinned to 9.3.1 (compatible with Kotlin 2.4.20).
   The remaining failure is the AGP 9.x built-in Kotlin conflict — remove the explicit
   `org.jetbrains.kotlin.android` plugin from both the root and `app` build files while
   keeping `org.jetbrains.kotlin.plugin.compose`. After that change, re-run `ci.yml`.
2. Once CI is green, install the resulting debug APK on a real device and measure
   camera → MediaPipe result FPS, pointer latency, jitter, and false-click rate.
   Nothing in this file should be trusted as “working” until that happens.
3. Only after device data exists: pick one item from the cleanup backlog or the
   camera-FPS investigation and do it as its own isolated change with its own CI run.