# ZINGO Fast

Electronic circuit design and simulation: the web app (`app/`) and the Windows desktop app (`desktop/`).

## Layout

| Path | What it is |
|---|---|
| `app/index.dev.html` | Source of truth (all code, styles and fonts in one file) |
| `app/build.py` | Builds `app/index.html` (release: developer tools removed, code wrapped) |
| `desktop/` | Electron shell: native file dialogs, projects folder, `.zingo` files, AI calls, installer |
| `server/` | `app.js`: the shared AI brain server (Cloudflare tunnel + Firebase), see `server/README.md` |

## Projects (.zingo files)

- **New project** (Ctrl+N): asks for a name. In the desktop app the file is created straight away as
  `Documents\ZINGO Fast\<name>.zingo`.
- **Save** (Ctrl+S), **Save as** (Ctrl+Shift+S), **Open** (Ctrl+O). Click the project name in the top bar to rename it.
- A dot next to the name means there are unsaved changes. Closing the window then asks: Save / Don't save / Cancel.
- Exports (PNG, SVG, parts list CSV) are named after the project.
- Double-clicking a `.zingo` file in Explorer opens it in ZINGO Fast. Recent projects show on the start screen and in the menu.
- Older circuit `.json` files still open.

## ZINGO AI

The **AI** tab (or the ✦ button in the top bar) opens the assistant. It sees the open circuit (parts, nets,
live readings) and a local diagnosis (shorts, burnt or overloaded parts, reversed capacitors, floating pins),
so it can explain the circuit, find mistakes and propose fixes. When it builds or changes something it shows a
card with the proposed changes; nothing touches the workspace until you press **Apply** (one Ctrl+Z undoes it).
It can also design new custom parts in the Part Builder format.

Providers (⚙ in the AI tab): ZINGO free (no key), Claude (desktop app, official Anthropic SDK, default model
`claude-opus-5-5`), Groq, Gemini, OpenRouter, OpenAI, or any OpenAI-compatible address (including a local
Ollama / LM Studio on `http://127.0.0.1`). API keys are stored encrypted by Windows.

Learning: 👍 answers, designs that were applied and pass the check, and fixes that removed a problem are stored
in a local brain and recalled for similar questions; preferences ("short answers", "in Arabic"...) are learned
from what you write. With the user's consent, these items are shared with the community brain server
(`server/app.js`), which curates them and sends the shared brain back to every app.

## Build the Windows installer

Automatic: every push that touches `zingo-fast/` runs the **ZINGO Fast - Windows installer** workflow
(GitHub → Actions). Download `ZINGO-Fast-Setup` from the run's *Artifacts*.

By hand on Windows (Node.js 22 and Python 3 installed):

```
cd zingo-fast\desktop
npm ci
npm run dist
```

The installer is written to `zingo-fast\desktop\dist\ZINGO-Fast-Setup-<version>.exe`.
`npm start` runs the desktop app without installing it.

The installer is not code-signed, so Windows SmartScreen shows "Unknown publisher" the first time.
Signing needs a code-signing certificate (set `CSC_LINK` / `CSC_KEY_PASSWORD` before `npm run dist`).
