# Decisions

Each ambiguous point was decided for the calmer, clearer, cheaper-to-render option.

1. **Two modules (`core` + `app`).** All logic that does not need Android is in `core`, so it is unit-tested on any JVM; the `app` module is included only when an Android SDK is present. *Why:* testability and a build that never breaks on machines without the SDK.
2. **Render model instead of drawing inside the timeline.** `Timeline.frameAt(t)` returns plain data (`Frame`, `Layer`, `ChatView`, `CodeView`…); the Android `FrameRenderer` only draws. Preview and export call the same function → what you see is what you export.
3. **Hand-written mini JSON-Schema validator** (draft 2020-12 subset with discriminated `oneOf`) instead of a library: zero dependencies, and we control the Arabic messages and exact scene/field locations.
4. **Unknown fields are warnings** (forward-compatible); auto-repair removes them.
5. **Ahmed's messages are "outgoing"** (left side in the RTL chat, like Arabic WhatsApp) — the viewer learns *as* Ahmed.
6. **Stage model:** HOOK, CARD (recap / key sentence / teaser), CHAT (chat, analogy, engage, reveal) and CODE (code_type, run, common_mistake). Stage changes crossfade in 450 ms; within a stage nothing jumps.
7. **Durations**: each scene has min/ideal/max bounds; the fitter scales flexible scenes proportionally to the target. Chat scenes never go below the 2.5 words/s reading limit; the typing indicator shrinks instead.
8. **Audio is mono internally, stereo in the file.** The mixer streams (never holds 180 s of PCM in memory). Loudness is measured with ITU-R BS.1770 gating and normalised to −16 LUFS (stereo-equivalent), then a soft limiter at −1 dBFS.
9. **Cursor blink 530 ms half-period** (< 1 Hz full cycle) and hook ticks 1 s apart — nothing blinks faster than 1 Hz.
10. **Resumable export = automatic restart.** MediaMuxer cannot append to a finished MP4, so after process death the foreground service re-queues the job from its persisted description and renders again from frame 0 (rendering is deterministic). A partial file is never shown as complete.
11. **Teaser clip without spoilers:** hook + analogy + start of code typing + "next episode" card; it stops before any `run`/`reveal`.
12. **Real Python execution** only in `run` scenes with `exec: real`; the real output/trace replaces the declared one only when execution succeeds; otherwise the declared trace is used and a warning is shown.
13. **Emoji** are allowed in chat text (system emoji font), never in code; TTS strips them.
14. **Offline draft generator** (`Templates.draft`) creates a complete valid video from any roadmap lesson without an AI, so the app is useful on day one; the AI prompt is the premium path.
15. **Extra libraries:** none beyond the brief's stack (Compose, Hilt, Room, Kotlin Serialization, Coroutines, Chaquopy). Navigation is a small state machine (no navigation library) to keep the APK small.
16. **Python 3.11** is bundled with Chaquopy; the errors library records the version it was verified with and the test compares messages exactly with the same version.

## Calm pace, no background bed, centred frame (revision)

- **No continuous background audio.** The mixer plays only context sound effects and the optional voice; `meta.ambient` is kept in the schema for compatibility and ignored.
- **Calm pace first.** Typing, message arrival, trace steps and transitions are slower, and reading time is budgeted at 2 words/s. When the comfortable durations exceed the target, the video may run up to 35 % longer (`Limits.CALM_STRETCH`) before any scene is compressed.
- **Centred composition.** The phone and the cards have equal left and right margins; the safe-zone overlay still shows the platform UI areas.
- **Profile pictures.** Gallery pictures (group, Ahmed, each language) are copied to private storage by `AvatarStore` and drawn by the same renderer in preview and export.
- **Crash report.** `CrashLog` stores the last failure locally and shows it on the next launch with a copy button; an export that dies twice is not retried again.
