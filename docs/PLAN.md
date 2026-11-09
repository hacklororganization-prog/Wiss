# Plan, milestones and acceptance criteria

Architecture: `core` (pure Kotlin/JVM: model, schema, validator, timeline, audio synthesis + mixer, speech rules, prompt builder, quality checker, publish texts, templates, curriculum and errors library) and `app` (Android: Compose UI, Canvas renderer, MediaCodec export, TTS, Chaquopy, Room, SAF). Both preview and export sample the **same** `Timeline.frameAt(t)` render model.

| # | Milestone | Acceptance criteria | Status |
|---|---|---|---|
| a | Skeleton, design tokens, fonts, theme | App launches; two calm themes; Cairo + JetBrains Mono bundled | ✅ |
| b | Timeline engine + renderer + preview | Deterministic frames (unit-tested); 60 fps Compose preview drawing the same renderer | ✅ |
| c | MediaCodec export + audio mux | 1080×1920, 30 fps, H.264 High, AAC 48 kHz; foreground service with progress + cancel; output verified | ✅ |
| d | Chat, code editor, trace, terminal scenes | Typing indicator, ticks, reactions; natural typing incl. typo-fix; line highlight + dimming; variable boxes, iteration counter, call stack | ✅ |
| e | Procedural audio + TTS + captions | 12 SFX ids + 3 ambient moods synthesised; ducking, limiter, −16 LUFS normalisation (tested); TTS synthesizeToFile with measured durations; karaoke words | ✅ |
| f | Schema, validator, SAF import | JSON Schema in assets + docs; Arabic error locations; auto-repair; repair prompt | ✅ |
| g | Wizard + prompt builder + SAF save | 12 mandated sections in order; `ACTION_CREATE_DOCUMENT` with suggested name; Open/Share/Copy | ✅ |
| h | Script editor | Card list, add/reorder/delete, undo/redo, autosave, mini live preview, paste-lesson-text mode, duration bar | ✅ |
| i | Python (Chaquopy) | `exec: real` runs with timeout, captured stdout/stderr, blocked modules, `sys.settrace` trace | ✅ |
| j | Curriculum, templates, errors library | ≥ 30 lessons (36), ≥ 40 errors (59) verified against real Python 3.11 in tests | ✅ |
| k | Series memory, presets, weekly plan | Room history, Ahmed level, 3 default presets + custom, 7-episode batch prompt + import | ✅ |
| l | Code-to-speech, publish pack, quality score | Data-driven reading rules (tested); SRT, caption file, teaser clip, cover; score 0–100 with one-tap fixes | ✅ |
| m | Polish, accessibility, tests | Unit tests (core), instrumented export test, TalkBack labels, font scale | ✅ (see README for what runs where) |
