# PyReels Studio

A fully offline Android app that turns **one scenario file** (`*.pyreels.json`) into a calm, polished vertical (1080×1920, 30 fps) programming lesson video for TikTok / Instagram Reels / Shorts. Each episode is a WhatsApp-style group chat between **Ahmed (أحمد)**, a beginner, and programming languages as teachers, with an in-video code editor, a visual execution trace and a terminal.

- **No external media**: fonts are bundled, avatars/icons are drawn in code, every sound (12 SFX + 3 ambient moods) is synthesised procedurally.
- **No network, no WebView, no screen recording**: frames are rendered deterministically from a timeline onto the MediaCodec encoder surface (H.264 High + AAC 48 kHz).
- **Arabic-first, RTL-correct**: HarfBuzz/ICU shaping through `StaticLayout`; code always LTR; two calm themes.
- **AI-ready**: a wizard builds an engineered prompt (saved where *you* choose via SAF); the strict validator explains every problem in Arabic and can write a repair prompt.

## Build

Requirements: JDK 17, Android SDK (API 35), Python 3.11 on the PATH (used by Chaquopy at build time).

```bash
export PYREELS_BUILD_PYTHON=python3.11
./gradlew :app:assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease         # minified, signed with the debug key for side-loading
./gradlew :core:test                   # 50+ JVM unit tests (no Android SDK needed)
./gradlew :app:connectedDebugAndroidTest   # export integration + UI flow tests on a device/emulator
```

Without an Android SDK, `settings.gradle.kts` automatically builds only the pure-Kotlin `core` module (or force it with `PYREELS_CORE_ONLY=1`). CI (`.github/workflows/android.yml`) runs the core tests, builds the APKs (downloadable as the `pyreels-studio-apk` artifact) and runs the instrumented tests on an emulator.

## Architecture

```
core/  (pure Kotlin, JVM-tested)                app/  (Android)
├─ model/      Scenario, Scene (sealed), Tokens  ├─ engine/render/  FrameRenderer, TextKit, SyntaxHighlighter
├─ schema/     MiniJsonSchema (2020-12 subset)   ├─ engine/export/  VideoExporter (MediaCodec+MediaMuxer), ExportService, Storage
├─ validate/   ScenarioValidator, AutoRepair      ├─ engine/tts/     VoiceEngine (synthesizeToFile, measured durations)
├─ timeline/   Timeline → Frame (render model),   ├─ engine/python/  PythonRunner (Chaquopy) + src/main/python/pyreels_runner.py
│              DurationFitter, Easing             ├─ data/           Room: projects, episodes, presets, export jobs
├─ audio/      Synth (SFX, ambient), AudioMixer,  ├─ ui/             Compose screens (MVVM, Hilt)
│              LoudnessMeter (BS.1770), Wav       └─ di/
├─ speech/     SpeakCode (data-driven rules)
├─ prompt/     PromptBuilder (12 sections), repair prompt
├─ quality/    QualityChecker (score 0–100, one-tap fixes)
├─ publish/    SRT, caption text, teaser window
├─ templates/  draft-from-roadmap, paste-lesson-text
├─ content/    curriculum (36 lessons), errors library (59 errors)
└─ brief/      wizard Brief, StylePreset, SeriesMemory
```

**One timeline, two consumers.** `Timeline.build(scenario, voiceMs, runResults)` fits scene durations to the target and plans every event (typing snapshots, chat messages, trace steps, SFX cues, voice cues). `timeline.frameAt(t)` returns a pure data `Frame`; `FrameRenderer.draw(canvas, frame)` paints it. The 60 fps preview and the 30 fps encoder call the same two functions, so the preview is exactly the export. The `AudioMixer` streams the soundtrack from the same timeline (ducking, fades, −16 LUFS normalisation, soft limiter), and the preview uses its audio clock as master.

## How to…

### add a lesson
Edit `tools/gen_curriculum.py` (id, concept, title, summary, analogy, example code, expected output, key sentence) and run
`python3 tools/gen_curriculum.py core/src/main/resources/pyreels/curriculum.json`. The test `curriculumHasAtLeast30OrderedLinkedLessons` checks ordering and links; `everyRoadmapLessonDraftsIntoAValidVideo` checks that the offline draft of every lesson validates.

### add a beginner error
Add a tuple to `tools/gen_errors.py` (snippet, Arabic explanation, fix, lesson ids) and run it **with Python 3.11**:
`cd /tmp && python3.11 -I <repo>/tools/gen_errors.py <repo>/core/src/main/resources/pyreels/errors.json`. The interpreter message is captured from real execution; `errorMessagesMatchRealPython` re-verifies every entry.

### add a sound effect
1. Add the id to `SfxIds` (`core/.../model/Rules.kt`) and to the `sfxId` enum in `scenario.schema.json` (+ `docs/pyreels.schema.json`).
2. Add a generator branch in `Synth.generate` (oscillators/envelopes/filtered noise; keep the peak below 0.5).
3. `AudioTest.everySfxIsSoftAndShort` checks loudness and length.

### add a character (teacher language)
Add a `Persona` to `Personas.TEMPLATES` (`Rules.kt`): Arabic name, accent colours (dark/light), personality, style, do's/don'ts, sample lines, TTS pitch/rate and monogram. Unknown languages already get a default persona. Optionally add keywords to `SyntaxHighlighter` and a run command / file name in `Timeline.fileName/runCommand`.

### change the code-reading rules
Edit `core/src/main/resources/pyreels/speech/python.ar.json` (patterns, operators, keywords, letters). Covered by `SpeakCodeTest`.

## Documents

- [`docs/ANALYSIS.md`](docs/ANALYSIS.md) — what was kept/dropped from the old apps, risks
- [`docs/PLAN.md`](docs/PLAN.md) — milestones and acceptance criteria
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — ambiguous points and the calm/cheap choice made
- [`docs/SCENARIO_SPEC.md`](docs/SCENARIO_SPEC.md) + [`docs/pyreels.schema.json`](docs/pyreels.schema.json)

Fonts: Cairo and JetBrains Mono, SIL Open Font License (see `docs/licenses/`).
