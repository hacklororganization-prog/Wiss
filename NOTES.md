# 7 سنين — demo slice: notes

## Build pipeline
- Local Godot install was blocked in this sandbox (github.com / dl.google.com unreachable; running the downloaded engine binary was refused by the sandbox safety policy).
- **Plan B in use:** `.github/workflows/build.yml` builds on every push in the `barichello/godot-ci:4.7.2` image: import → headless smoke test (autoplay bot) → `--export-debug "Android"` → `unzip -t` + `aapt dump badging` → APK uploaded as artifact `7senin_demo_apk`.
- Verified by CI export: signed debug APK, ~29 MB, `native-code: arm64-v8a` only, `sdkVersion: 24`, Compatibility renderer.

## IMPORTANT: missing story text
`7_SENIN_Story_Final.md` was **not provided** with the task. No dialogue/paper text was invented. Every approved line that must come from it is a visible placeholder `[MISSING_FROM_STORY …]` in `data/dialogue.json`:
flashback lines (§4B, 5 entries; speaker order assumed), Omar's station-1 lines (§4C.1), papers 2016 / 2018 / final.
Paste the approved text there verbatim (add/remove entries freely, arrays are read in order). The intro beats follow the task summary of §4A, not the story file itself.
Only these lines came from the task prompt: «اللي قاله عمّي... نتأكد منو.» and «شكون إنتِ؟».

## What was cut / simplified
- No pause menu, no saves, no papers screen (as specified). Android back button = system default.
- Settings: volume, quality (Low/Med), look sensitivity only. Auto-drop to Low if avg FPS < 24 for 10 s.
- Watcher: static figure (no walk animation); visibility check is a view-cone test (no raycast).
- Gate-house navigation uses the house's outdoor lamp (windows dark).
- Min SDK is whatever the Godot 4.7 non-Gradle template ships (CI reports 24).
- 3D resolution scale default 75% (Low = 60%); depends on the Compatibility renderer honouring it.

## Audio
`tools/make_audio.py` (numpy only) generates 44 mono ogg files (~0.7 MB) with variants; QA (non-silent, no clipping, no DC, loop seams) passed — see `audio/qa_report.txt`. Override any file via `audio_custom/<file>.ogg`.

## Verified vs. unverified
- Verified: APK exports/signs/validates; headless smoke test results — see the latest CI run (the `SMOKE PASS` line).
- **Not verified (no device / no screen):** visuals, lighting levels, Arabic shaping on device, touch controls feel, FPS on a real phone, audio mix levels.

## Test first on the phone
1. Arabic text renders connected and right-to-left (menu, hint, subtitles, paper).
2. Joystick + swipe look at the same time; the action / ثبّت / ضوء buttons respond while moving.
3. Brightness: is the night readable? (flashlight, lamps, fog). Tune `fog_density` / `ambient_light_energy` in `scripts/world.gd`.
4. Gate house: hold ثبّت for 8 s without moving → gate opens, paper appears at the hinge.
5. Debug menu (5 fast taps top-left): jump to Confrontation → cat jump, fall, wake, final paper, ending.
