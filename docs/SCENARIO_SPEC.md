# Scenario specification — `pyreels/1`

A scenario is a single UTF-8 JSON file (`*.pyreels.json`). It is the **only** external input of the app.
The machine-readable JSON Schema (draft 2020-12) is [`docs/pyreels.schema.json`](pyreels.schema.json) — the same file is bundled in the app (`core/src/main/resources/pyreels/scenario.schema.json`) and embedded in every generated prompt.

## Top level

```json
{ "schema": "pyreels/1", "meta": { … }, "cast": [ … ], "scenes": [ … ] }
```

A weekly batch wraps up to 7 scenarios: `{ "schema": "pyreels/1", "batch": [ scenario, … ] }`.

Unknown fields are ignored with a **warning** (forward compatibility); the importer's auto-repair removes them.

### `meta`

| field | type | default | notes |
|---|---|---|---|
| `title` | string ≤ 60 | — | shown in the title chip and on the cover |
| `language` | `ar` \| `en` | `ar` | language of the chat text and TTS |
| `lesson.id` | `[a-z0-9-]` | — | roadmap id (e.g. `py-variables`) or a custom id |
| `lesson.concept` | string ≤ 40 | — | **exactly one** concept |
| `lesson.level` | `beginner` \| `basics` | `beginner` | |
| `targetSeconds` | 30–180 | 90 | the engine fits durations to it (± 10 %) |
| `theme` | `calm-dark` \| `calm-light` | `calm-dark` | |
| `series` | `{name, episode, previousRecap, nextTeaser}` | | series memory |
| `ambient` | `none` | `none` | kept for compatibility; the renderer plays no background bed, only sound effects and voice |
| `watermark` | boolean | `true` | small series watermark |
| `groupName` | string ≤ 30 | `مجموعة <title>` | chat group header |

### `cast`

`ahmed` (role `learner`) is mandatory and unique, plus 1–4 teachers (role `teacher`, `language` = `python`, `java`, `cpp`, `javascript`, `kotlin`, `csharp`, `go`, `rust`, `php`, … ). Optional `voice: {pitch, rate, locale}` overrides the persona's TTS mapping.

## Scenes

Common optional fields: `say` (TTS + karaoke), `sfx` (override the default sound), `spoken` (explicit speech text), `speakCode` (convert code in the text to speech), `transition` (`fade` | `slide` | `zoom`), `durationMs` (rarely needed).

| type | required | optional | renders as |
|---|---|---|---|
| `hook_predict` | `code` | `language`, `question`, `holdMs` (1500–2500) | signature hook, always first, exactly 3 s |
| `recap` | `text` | | "تذكير من الحلقة السابقة" card |
| `chat` | `from`, `text` | `code`, `reactions`, `challenge` | WhatsApp bubble with typing indicator, ticks, reactions |
| `analogy` | `title` | `items` (≤ 4 `{label, value}`), `from`, `text` | labeled boxes card in the chat |
| `code_type` | `code` | `language`, `typing` (`natural`/`steady`/`instant`), `focusLines`, `typo`, `caption` | editor typing with soft cursor, then focus frame + zoom |
| `run` | — | `language`, `exec` (`real`/`simulated`), `code`, `trace`, `output`, `error`, `caption` | line highlight + dimming, variable boxes, iteration counter, call stack, terminal |
| `engage_moment` | `text` | `from`, `actions` (`like`/`follow`/`share`), `bridge` | warm in-chat message + heart + chip for ~4 s, then the bridge line |
| `reveal` | `answer`, `text` | `from` | answer card with the hook's code |
| `common_mistake` | `code`, `error`, `explain`, `fix` | `language`, `fixOutput`, `libraryId`, `from` | error in a warm tone, calm note, live fix, success |
| `key_sentence` | `text` (≤ 10 words) | | "جملة اليوم" big card |
| `teaser` | `text` | `challenge` | "في الحلقة القادمة" card, always last |

### `trace` steps

`{ "line": 2, "vars": {"x": 7}, "out": "7", "iteration": 2, "stack": ["greet()"] }` — `vars` are the values **after** the line ran. With `"exec": "real"` (Python only) the app executes the code in a sandbox (Chaquopy, `sys.settrace`, 2.5 s limit, no files/network, safe modules only) and uses the real trace and output; other languages are always simulated.

## Fixed structure (enforced)

1. `hook_predict` first (exactly one) · 2. `recap` second · … `engage_moment` exactly once (best at 45–55 s, right after the first correct output) · `reveal` exactly once (after the first `run`) · `key_sentence` exactly once near the end · `teaser` last.

## Limits

| | value |
|---|---|
| bubble | ≤ 34 chars/line, ≤ 4 lines (136 chars) |
| reading speed | ≤ 2.5 words/s |
| code | ≤ 12 lines, ≤ 38 chars/line |
| output | ≤ 6 lines shown |
| total | target 90 s, warning > 100 s, **blocking error > 180 s** |

## Validation messages

Every problem points at the exact place in Arabic, e.g. `المشهد 7 (chat) ← النص (text): النص طويل جدًا (150 حرفًا، الحد 136).`
Errors block import; warnings and tips do not. The importer offers **safe auto-repair** (trim, aliases such as `"message"` → `chat`, unknown SFX → default, unknown fields removed, structural reordering) and a **repair prompt** file for the AI.

## Samples

`core/src/main/resources/pyreels/samples/`: `01-print` (1 teacher), `02-variables` (2), `03-if` (2, light theme), `04-for-loop` (3), `05-functions` (4) and `weekly-batch.pyreels.json` (7 linked episodes).
