# Analysis of the reference material

Inputs examined: `index.html` ("Code Chat Studio", 526 KB, 24 inline scripts), `EditJson_1.0.apk`, `WatsREC_1.0.apk` (unzipped; resources, manifest, dex strings).

## What the old apps are

| App | What it really is | Evidence |
|---|---|---|
| **EditJson 1.0** | A WebView wrapper around a 233 KB `assets/index.html` ("ZINGO") JSON scenario editor. | `Landroid/webkit/WebView`, `addJavascriptInterface`, `https://zingo.local/app/index.html`, `HTML/index.html` in dex strings. |
| **WatsREC 1.0** | A WebView wrapper that loads HTML screens from storage and records them. | `Screens/ChatWats.html`, `Screens/EditorPydroid.html`, `Screens/HomeWats.html`, `scenario.json`, `last.html`, "Start Recording" strings; legacy `org.apache.http` stack. |
| **index.html (Code Chat Studio)** | A WhatsApp-style 9:16 recorder with a code editor, all in one page. | WhatsApp chat painting (`paintHead`, `msg`, `kbType`), editor (`paintEd`, `markLn`, `lint`), WebAudio oscillator SFX, lock-screen / call "hooks", end card. |

## Keep (ideas only, no code is ported)

- WhatsApp-like group chat as the narrative device; typing indicator before messages; read ticks; avatars and sender colours.
- A code editor inside the video with line numbers, a line marker and a terminal.
- A JSON scenario as the single source of truth ("scenario.json").
- Procedural sounds made with oscillators (the HTML already used `sine/square/triangle/sawtooth` WebAudio voices) — we keep the *procedural* idea but design soft sounds.
- A fixed hook at the start and an end card — formalised as the signature hook «توقّع قبل أن أشغّل» and the teaser.
- Arabic-first, RTL UI.

## Drop

| Dropped | Why |
|---|---|
| WebView rendering + screen recording | Main cause of lag, unstable recording and audio desync. Replaced by deterministic offscreen Canvas rendering + MediaCodec. |
| Network fonts (Google Fonts + IndexedDB "FontCache") and remote images (`user-images.githubusercontent.com`) | External dependence, breaks offline. Fonts are bundled in `res/font`. |
| User media (`pickMedia`, `loadVid`, `loadImg`, chat background images) | Rule 0: no external inputs except the scenario file. |
| Games (`flappy`, `snake`, `gameBox`), "scare"/"cineScare", `crackAnim`, `shake`, `flash`, `glitch`-style effects | Not educational; flashing/shaking violates the calm motion rules. |
| Lock-screen / phone-call hooks, battery/clock painting, fake notifications | Distracting chrome; replaced by one consistent signature hook. |
| Many themes (per-surface dark/light, inverted backgrounds) | Exactly two calm themes ship. |
| Hook music | Forbidden by the brief. |
| `org.apache.http`, `addJavascriptInterface` bridges | Network/security surface that is not needed. |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Arabic shaping/bidi in a custom renderer | Use Android `StaticLayout`/`TextPaint` (HarfBuzz shaping + ICU bidi) for every text run; code runs are forced LTR with `TextDirectionHeuristics.LTR`; Arabic chat with `FIRSTSTRONG_RTL`. |
| Encoder performance on mid-range devices | Render straight onto the encoder's input `Surface` (`lockHardwareCanvas`), 30 fps, no bitmaps per frame, cached `StaticLayout`s. |
| Audio/video sync | One timeline clock: video frame *n* is rendered at `n/30 s`; audio samples are computed from the same timeline (sample-accurate). PTS derive from sample counts. |
| TTS availability for Arabic | Detect `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED`, show a clear message, fall back to silent captions; durations then come from estimates. |
| Chaquopy size / build complexity | Restricted to `arm64-v8a` + `x86_64`; Python only used for `exec: real`; sandbox with timeout and blocked modules. |
| AI scripts that do not fit | Strict JSON-Schema validation with Arabic messages, safe auto-repair, and a repair prompt. |
| Build environment without Android SDK | Domain logic lives in a pure-Kotlin `core` module (fully unit-tested on any JVM); the APK is built by GitHub Actions. |
