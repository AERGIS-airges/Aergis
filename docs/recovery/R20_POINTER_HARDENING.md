# R20 Pointer Hardening

## Continuation point

R20 continues directly from the completed R19 Gaze Lab implementation at commit `2060edf00a23a9f41cb902153de5195cded971a8`.

The R19 branch already contains the causal `PointerKinematicFilter`, One Euro shadow challenger, pointer-filter telemetry, and production gaze-engine wiring.

## R20 change

- Low-confidence micro-travel is damped in the production causal pointer estimator.
- Meaningful travel and confirmed large movement retain the existing response path.
- Timestamp ordering remains strict; stale/duplicate callbacks cannot manufacture motion.
- Regression coverage protects confidence, timing, and large-travel behavior.

## Deliberate non-change

R20 does not replace the production filter with One Euro, and it does not make gaze the primary pointer source. The R19 gaze integration remains subject to dedicated validation before it can be promoted into the pointer-control path.

## Validation gate

Required before merge:

1. Android unit tests pass.
2. Lint passes.
3. APK assembly passes.
4. Repository source-fidelity/identity gates pass.
5. Physical Galaxy A54 validation confirms pointer stability, responsiveness, and no new false motion.
