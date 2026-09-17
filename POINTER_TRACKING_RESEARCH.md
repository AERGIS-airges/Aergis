# Aergis Pointer Tracking Research Architecture

## Objective

Make the Air Pointer feel like a high-rate physical pointing device rather than a sequence of sparse ML detections. The pointer position must remain anatomically owned by MediaPipe index fingertip landmark #8, while temporal estimation, camera cadence and pixel-level motion evidence improve latency, stability and precision.

## Evidence reviewed

1. **MediaPipe Hand Landmarker / Gesture Recognizer**
   - Hand Landmarker exposes 21 image landmarks, 21 world landmarks and handedness.
   - In LIVE_STREAM mode, MediaPipe uses temporal tracking to avoid palm detection on every frame and may drop inputs when busy.
   - Gesture Recognizer adds gesture-classification work on top of the hand landmark pipeline. When pointer mode does not need gesture classification, a dedicated Hand Landmarker path is the preferred long-term architecture.

2. **CameraX ImageAnalysis**
   - `YUV_420_888` is the default analysis format.
   - Requesting RGBA requires an internal YUV→RGBA conversion and therefore adds overhead.
   - `KEEP_ONLY_LATEST` is appropriate for low-latency camera analysis.
   - The target resolution is a hint; actual output depends on the camera device.

3. **Sparse optical flow / KLT**
   - Lucas–Kanade estimates local point displacement from consecutive frames using image gradients and least-squares fitting.
   - Pyramidal LK extends the convergence region for larger motion.
   - For Aergis, the strongest architecture is ML landmark correction + sparse pixel motion around the #8 ROI, with forward/backward/error validation before accepting optical-flow motion.

4. **Speed-adaptive filtering**
   - The One Euro filter is a useful low-latency baseline because cutoff can rise with motion speed.
   - A causal alpha-beta filter is a Kalman-family constant-velocity estimator and gives an explicit position/velocity state while remaining computationally tiny.
   - A robust innovation gate is required so one-frame landmark teleports cannot move the cursor across the screen.

## Mathematical pointer model

Let the measured index-tip position be:

`z_k = [x_k, y_k]^T`

with timestamp interval `dt`.

The causal state is:

`p_k = [x, y]^T`

`v_k = [vx, vy]^T`

Prediction:

`p^-_k = p_{k-1} + v_{k-1} dt`

Measurement innovation:

`e_k = z_k - p^-_k`

A confidence/process-noise-dependent alpha-beta update is used to obtain the corrected state. A robust innovation gate reduces the gain for implausibly large one-frame innovations.

Aergis additionally clamps the emitted pointer coordinate to the interval between the previous emitted position and the newest measured landmark. Therefore the estimator cannot emit a future coordinate or overshoot the newest measured fingertip.

## Target production architecture

```text
CameraX high-rate frames
        |
        +--> ML hand landmark anchor (#8)
        |       |
        |       +--> handedness / world geometry / hand continuity
        |
        +--> lightweight Y-plane ROI tracker around previous #8
                |
                +--> sparse LK / gradient displacement
                |
                +--> forward-backward/error/confidence gate
                         |
ML #8 --------------------+----> robust measurement fusion
                                   |
                                   v
                         causal alpha-beta/Kalman state
                                   |
                                   v
                         calibrated screen mapping
                                   |
                                   v
                         Accessibility pointer
```

## Important ownership rule

No palm, wrist, hand centroid, thumb, middle finger or generic gesture classification may contribute to pointer X/Y. Those signals may validate identity, confidence, click intent or recovery, but landmark #8 owns the pointer measurement channel.

## R18 implemented slice

- Replaced the previous fixed-cutoff pointer smoother with `PointerKinematicFilter`.
- The new estimator is causal, cadence-aware, confidence-aware and robust to isolated innovations.
- The emitted position cannot exceed the newest landmark-8 measurement.
- Pointer submission pacing ceiling increased from 16 ms (~62.5 Hz) to 8 ms (~125 Hz), while adaptive overload backoff remains.
- Existing pointer ownership, click semantics and no-forward-extrapolation behavior remain intact.

## Next highest-value implementation

Replace pointer-mode `GestureRecognizer` inference with a dedicated `HandLandmarker` pipeline. Pointer mode currently disables generic gesture execution, so paying for gesture classification is unnecessary. The dedicated landmarker should use one hand, GPU when stable, and the official Hand Landmarker model bundle.

After that, add a lightweight ROI optical-flow tracker between ML landmark results. It should operate on the camera's Y plane, use a small pyramidal LK/KLT implementation, validate forward/backward consistency, reject low-gradient patches and fuse only accepted displacement into the #8 measurement stream. This is the key architectural step for turning a 8–12 Hz ML result stream into a visually continuous pointer without inventing motion.

## Device metrics required before final tuning

Record separately:

- camera frame arrival FPS;
- frames submitted to MediaPipe FPS;
- MediaPipe result FPS;
- inference latency p50/p90/p99;
- result age p50/p90/p99;
- dropped/busy submissions;
- raw landmark-8 jitter at rest;
- filtered landmark-8 jitter at rest;
- endpoint error at screen targets;
- maximum stable pointer speed;
- false-click count during relaxed-hand torture test.

A higher submission ceiling alone must never be reported as higher tracking FPS. The real result cadence must be measured on the Galaxy A54.
