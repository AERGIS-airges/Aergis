# AERGIS CI Validation Skill

## Purpose
Use this skill for GitHub Actions validation, failed-run diagnosis, APK verification, and release gating.

## Required sequence
1. Identify the exact commit SHA and workflow/ref being validated.
2. Check commit status and workflow result before claiming success.
3. If failed, inspect the failing job and actual error output.
4. Trace the failure to source/test/workflow/configuration root cause.
5. Make one minimal justified correction at a time.
6. Re-run CI and compare evidence against the prior failure.
7. Verify APK existence, package/application ID, version name/code, signing, and expected artifact path when the workflow produces an APK.
8. Treat physical Samsung Galaxy A54 verification as a separate gate; CI cannot prove camera FPS, MediaPipe result FPS, pointer latency/jitter, or real-world false-click behavior.

## Hard rules
- Empty or missing commit status is NOT a pass.
- A successful compile is NOT proof of behavioral correctness.
- A passing unit test is NOT proof of physical-device behavior.
- Never hide or weaken a failing assertion without proving that the assertion itself is wrong.
- Never report an APK as available unless its artifact is actually present and retrievable.
