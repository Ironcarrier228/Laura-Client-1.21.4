# Sakura Launcher

Anime-styled Minecraft launcher built with Electron.

## Features

- **Tabs:** Play, Settings, Mods, Profile, About us, About project
- **Launch options:** Vanilla / Forge / Fabric, offline or Microsoft auth
- **Customizable:** RAM slider, Java path, instance path, resolution, fullscreen
- **Anime UI:** glassmorphism, parallax background, falling sakura petals, neon pink/violet/cyan accents
- **Config:** local `config.json` in userData
- **Modular:** main / preload / config / launcher / renderer separated

## Project structure

```
launcher/
├── package.json
├── README.md
├── assets/                  (images, icons — add your own)
└── src/
    ├── main/                (Node — main process)
    │   ├── main.js          Electron entry, window + IPC
    │   ├── preload.js       contextBridge API
    │   ├── config.js        config.json load/save
    │   └── launcher.js      Minecraft launch logic
    └── renderer/            (Browser — UI)
        ├── index.html       Layout
        ├── styles/
        │   ├── main.css     Variables, layout, sidebar, parallax
        │   ├── components.css  Cards, sliders, toggles, buttons
        │   └── animations.css  Keyframes
        └── js/
            └── app.js       UI logic, state, IPC calls
```

## Run

```bash
cd launcher
npm install
npm start
```

Dev mode (with DevTools):

```bash
npm run dev
```

Build installer:

```bash
npm run build
```

## Config

`config.json` is stored in:
- Windows: `%APPDATA%\Sakura Launcher\config.json`
- Linux: `~/.config/Sakura Launcher/config.json`
- macOS: `~/Library/Application Support/Sakura Launcher/config.json`

Example:

```json
{
  "profile": { "nickname": "Player", "authMode": "offline" },
  "game": {
    "version": "1.21.4", "loader": "vanilla",
    "ramMin": 2048, "ramMax": 4096,
    "javaPath": "", "instancePath": "",
    "fullscreen": false, "width": 854, "height": 480,
    "autoUpdate": true
  },
  "ui": { "accent": "#ff2d8a", "theme": "sakura", "animations": true },
  "lastPlayed": null
}
```

## Notes

- `launcher.js` is a stub — it returns the launch command instead of actually spawning Java. To make it real, you need to download Minecraft client/libraries/assets and build the classpath, similar to the existing `windows/LauraLauncher.ps1` in this repo.
- Background uses pure CSS gradients and animated radial overlays (no external image needed).
- All animations can be disabled from Settings → Animations.
