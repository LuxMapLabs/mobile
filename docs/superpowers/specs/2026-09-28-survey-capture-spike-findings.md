# Survey capture spike findings (2026-09-28)

Devices tested: Samsung SM-A075F, Android SDK 36 (device 1 of the 2–3 recommended by spec §4 — see note at the end)

Spike harness: throwaway `SpikeCaptureActivity` (Camera2 TEMPLATE_RECORD → MediaCodec surface input →
MediaMuxer, segment rotation every 60s for the spike run, `CONTROL_AE_MODE_OFF` + fixed
ISO 800/shutter 20ms/frame duration 33.33ms, `CONTROL_AF_MODE_OFF`, `CONTROL_VIDEO_STABILIZATION_MODE_OFF`,
AWB locked via `CONTROL_AWB_LOCK` once `CONTROL_AWB_STATE_CONVERGED`). Deleted after this spike, not part
of the app's real feature set.

## 1. Config held for 10+ minutes?

**Yes on Samsung SM-A075F.** Ran ~12 minutes 24 seconds continuously (13 segments: 12 full ~61.9s
segments + 1 final partial segment at stop). All 13 `segment_*.mp4` files have real, substantial size
(46–62 MB each) — confirms the encoder kept producing valid frames throughout, not just the first
segment. `awb locked at frame=0` — AWB converged effectively instantly on this device and stayed
locked (`CONTROL_AWB_LOCK`) for the whole session; no re-drift observed at any of the 12 rotation
boundaries.

**Two real bugs found and fixed during this spike (both in the spike harness itself, not the device):**
- A plain foreground `Activity` (no wake lock, no foreground service) let the screen auto-lock, and
  Android killed the background process mid-recording once. Fixed for the spike with
  `FLAG_KEEP_SCREEN_ON`. **Real implication for Task 17d:** `SurveyCaptureService` must be a true
  foreground service with a persistent notification (already planned), or the same kill will happen
  during a real multi-minute unattended recording.
- `MediaCodec` only fires `INFO_OUTPUT_FORMAT_CHANGED` once per encoder lifetime, not once per
  segment — the first attempt only initialized `MediaMuxer.start()`/`addTrack()` for segment 0, so
  every segment after that had an uninitialized muxer, crashing with
  `IllegalStateException: Can't stop due to wrong state(INITIALIZED)` on the first rotation. Fixed by
  caching the `MediaFormat` from the one-time event and immediately calling `addTrack`/`start` on
  each new segment's `MediaMuxer` in `openNextSegment()`. **Real implication for Task 17a:**
  `VideoCaptureSession`/`RealMuxerPort` must do the same — this is exactly the kind of bug the spike
  was meant to catch before it was buried inside the full pipeline.

## 2. Segment rotation: frame loss or visible glitch at the boundary?

**Not checked frame-by-frame yet** — the log confirms rotation happens cleanly at a keyframe
(`isKeyFrame=true` gates the rotation in the harness) with no gap in `pts_us` across any of the 12
boundaries (e.g. segment 0→1: last logged frame pts_us=41021646118, first logged frame of segment 1
pts_us=41026675321 — consistent with the ~5s log interval, not a jump). **Still needed:** eyeball
`segment_0.mp4`'s last second against `segment_1.mp4`'s first second for a visible stutter/dropped
frame — the timing math looks right but has not been confirmed visually yet.

## 3. MediaCodec PTS vs Camera2 SENSOR_TIMESTAMP: correlation

**Extremely stable — the strongest result of this spike.** `offset_us(sensor_ts_ns/1000 - pts_us)`
stayed at a **constant 586,322,905,045 µs** across the entire ~12.4 minute recording and all 12
segment rotations, with zero measurable drift between log points ~5 seconds apart. This confirms:
- The two clocks (encoder PTS and Camera2 `SENSOR_TIMESTAMP`) advance at exactly the same rate on
  this device — a fixed, one-time offset is enough to convert between them for the whole session.
- The FIFO pairing strategy (queue `SENSOR_TIMESTAMP` values in `onCaptureCompleted`, pop one per
  encoded output frame in arrival order) holds correctly **as long as the repeating request is never
  swapped**. One earlier test run showed the offset jump by ~80 seconds in a single 5-second window
  immediately after `CONTROL_AWB_LOCK` was applied via `session.setRepeatingRequest(newBuilder, ...)`
  — re-submitting a repeating request desynced the FIFO pairing by a fixed amount for the rest of that
  session (it restabilized afterward, just at a different constant). **Real implication for Task
  17a:** avoid resubmitting `setRepeatingRequest` after AWB locks if at all possible in the real
  implementation, or explicitly account for a possible one-time correlation step at that exact
  moment.

## 4. SENSOR_INFO_TIMESTAMP_SOURCE per device

- Samsung SM-A075F, Android SDK 36: **REALTIME**. ✅ (device supported per spec §10's hard block)

## Decision

**Camera2 → MediaCodec → MediaMuxer confirmed viable on this device**, once the two harness bugs above
were fixed. PTS-to-SENSOR_TIMESTAMP correlation is trustworthy under normal operation; the one
identified risk (repeating-request resubmission desyncing the FIFO pairing) is a concrete, actionable
finding for Task 17a rather than a blocker.

**Not yet done — spec §4 asked for 2–3 devices, only 1 tested so far.** Proceeding to Task 3 on this
one device's data is a judgment call the project owner should confirm; if a second device becomes
available later, re-run the same `SpikeCaptureActivity` and append its results here before Task 17a
starts, since device-specific `SENSOR_INFO_TIMESTAMP_SOURCE`/AWB-convergence-speed/exposure-range
differences are exactly what a second device would catch.

## Chosen defaults for capture_config.json (spec §8)

- resolution: 1920x1080
- fps: 30
- bitrate_bps: 8,000,000 (used in the spike; not evaluated against WP4's quality needs — carry as a
  starting point, not a final decision)
- keyframe_interval_s: 2
- segment_duration_s: 180 (2–5 min range per spec — the spike itself used 60s only to observe more
  rotations inside one run; a real session should use the full 2–5 minute range)
- iso / shutter_ns: 800 / 20,000,000 ns (20ms) — worked on this device in typical indoor lighting;
  **not validated in actual night/outdoor low-light survey conditions** — spec §16's real-device
  checklist still calls for a genuine low-light test before treating these as final.
