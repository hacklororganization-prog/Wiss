# ZINGO Fast

Electronic circuit design and simulation: the web app (`app/`) and the Windows desktop app (`desktop/`).

## Layout

| Path | What it is |
|---|---|
| `app/index.dev.html` | Source of truth (all code, styles and fonts in one file) |
| `app/build.py` | Builds `app/index.html` (release: developer tools removed, code wrapped) |
| `desktop/` | Electron shell: native file dialogs, projects folder, `.zingo` files, installer |

## Projects (.zingo files)

- **New project** (Ctrl+N): asks for a name. In the desktop app the file is created straight away as
  `Documents\ZINGO Fast\<name>.zingo`.
- **Save** (Ctrl+S), **Save as** (Ctrl+Shift+S), **Open** (Ctrl+O). Click the project name in the top bar to rename it.
- A dot next to the name means there are unsaved changes. Closing the window then asks: Save / Don't save / Cancel.
- Exports (PNG, SVG, parts list CSV) are named after the project.
- Double-clicking a `.zingo` file in Explorer opens it in ZINGO Fast. Recent projects show on the start screen and in the menu.
- Older circuit `.json` files still open.

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
