# PyReels Studio

A fully offline Android app that turns **one scenario file** (`*.pyreels.json`) into a calm, polished vertical (1080×1920, 30 fps) programming lesson video for TikTok / Instagram Reels / Shorts. Each episode is a WhatsApp-style group chat between **Ahmed (أحمد)**, a beginner, and programming languages as teachers, with an in-video code editor, a visual execution trace and a terminal.

- **Any language, nothing built in**: you add any programming language (C++, Rust, anything) by name and give it a picture from your gallery; there are no ready-made lessons, samples, templates or language lists. Android 10+.
- **No external media**: fonts are bundled, icons are drawn in code, every sound effect is synthesised procedurally (never a background track).
- **No network, no WebView, no screen recording**: frames are rendered deterministically from a timeline onto the MediaCodec encoder surface (H.264 High + AAC 48 kHz).
- **Arabic-first, RTL-correct**: HarfBuzz/ICU shaping through `StaticLayout`; code always LTR; two calm themes.
- **AI-ready**: a wizard builds an engineered prompt (saved where *you* choose via SAF); the strict validator explains every problem in Arabic and can write a repair prompt.

## Build

Requirements: JDK 17, Android SDK (API 35; the app runs on Android 10 / API 29 and newer), Python 3.11 on the PATH (used by Chaquopy at build time).

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
├─ util/       ScenarioTools (simple trace, series record)
└─ brief/      wizard Brief, StylePreset (yours only), SeriesMemory
```

**One timeline, two consumers.** `Timeline.build(scenario, voiceMs, runResults)` fits scene durations to the target and plans every event (typing snapshots, chat messages, trace steps, SFX cues, voice cues). `timeline.frameAt(t)` returns a pure data `Frame`; `FrameRenderer.draw(canvas, frame)` paints it. The 60 fps preview and the 30 fps encoder call the same two functions, so the preview is exactly the export. The `AudioMixer` streams the soundtrack from the same timeline (ducking, fades, −16 LUFS normalisation, soft limiter), and the preview uses its audio clock as master.

## How to…

### add a sound effect
1. Add the id to `SfxIds` (`core/.../model/Rules.kt`) and to the `sfxId` enum in `scenario.schema.json` (+ `docs/pyreels.schema.json`).
2. Add a generator branch in `Synth.generate` (oscillators/envelopes/filtered noise; keep the peak below 0.5).
3. `AudioTest.everySfxIsSoftAndShort` checks loudness and length.

### add a language (teacher)
In the app: **Settings → Languages and pictures → Add a language** (name, optional personality note, picture from the gallery). Nothing is edited in code: colour, voice and monogram are derived from the name (`Personas.forLanguage`), the code font colouring uses the generic keyword set unless the language is one of the few `SyntaxHighlighter` knows, and the file name / run command in the editor come from `Languages` (unknown languages get `main.<id>`). Only Python scenes can run for real; every other language shows the output and trace the AI wrote.

### change the code-reading rules
Edit `core/src/main/resources/pyreels/speech/python.ar.json` (patterns, operators, keywords, letters). Covered by `SpeakCodeTest`.

## Documents

- [`docs/ANALYSIS.md`](docs/ANALYSIS.md) — what was kept/dropped from the old apps, risks
- [`docs/PLAN.md`](docs/PLAN.md) — milestones and acceptance criteria
- [`docs/DECISIONS.md`](docs/DECISIONS.md) — ambiguous points and the calm/cheap choice made
- [`docs/SCENARIO_SPEC.md`](docs/SCENARIO_SPEC.md) + [`docs/pyreels.schema.json`](docs/pyreels.schema.json)

Fonts: Cairo and JetBrains Mono, SIL Open Font License (see `docs/licenses/`).
