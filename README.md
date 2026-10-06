<div align="center">
  <img src="assets/oneglyph.svg" width="128" alt="OneGlyph icon" />

  # OneGlyph

  **Make the one dot do more.**

  A simple, user-friendly way to control and play with the single Glyph dot on the **Nothing Phone (3a) Lite (Galaxian)**.
</div>

---

## What is OneGlyph?

OneGlyph turns the tiny Glyph dot on your phone into a useful little tool instead of leaving it sitting there.

You can make the dot:

- blink to simple patterns
- react to music beats
- show charging feedback
- run camera countdown flashes
- play your own custom sequences
- run a silly **Dot Toy** that flashes continuously until you turn it off

The app is designed to be **easy to use first**, while still giving developers enough control to experiment with the Glyph.

## Features

### 🎵 Beat Sync

Connect OneGlyph to your currently playing media and let the dot react to the music.

Beat Sync uses the Android media session and audio output to detect rhythmic changes, then sends short flashes to the Glyph rather than leaving it permanently lit.

> Beat Sync needs **Media Access** and audio-related permission on the device.

### ✨ Blink patterns

Quick built-in patterns are ready from the Home screen:

- Blink
- Double Blink
- Heartbeat
- Stop

### 🧩 Glyph Composer

Build your own sequence with up to **8 steps**.

Each step can have its own:

- brightness
- duration

You can preview the sequence, then save it for later.

### 🔋 Charging effects

OneGlyph can react when you plug your phone in and when your chosen battery target is reached.

The current effects are:

- **4 blinks** when charging starts
- **9 blinks** when the selected target is reached

Targets: **80%, 90%, or 100%**

### 📷 Camera Countdown

Start a camera countdown with the Glyph acting as a visual timer.

The flashes become faster as the countdown gets closer to the end.

Available timers:

- 3 seconds
- 5 seconds
- 10 seconds

The countdown launches the phone's normal camera. OneGlyph does **not** remotely press the shutter.

### ● Dot Toy

A tiny built-in experiment for no particular reason.

Turn it on and the Glyph flashes quickly and continuously. Press the button again to stop it.

## Getting started

1. Install the OneGlyph APK on a supported Nothing Phone.
2. Open OneGlyph.
3. Turn **OneGlyph** on in Settings.
4. Start with **Blink** on the Home screen.
5. For Beat Sync, open **Media Access** and allow OneGlyph to access active media sessions.
6. Adjust the settings to your liking.

The Home screen is intentionally focused on the things you are most likely to use. Advanced controls live in **Composer** and **Settings**.

## Settings

OneGlyph includes settings for:

| Setting | What it does |
| --- | --- |
| OneGlyph | Master on/off switch for the app |
| Appearance | Switch between dark and light mode |
| Charging Effects | Enable or disable charging feedback |
| Charge Target | Choose 80%, 90%, or 100% |
| Camera Timer | Choose 3s, 5s, or 10s |
| Media Access | Open Android's media-access settings |
| Project Maintainer | Information about the project and its maintainer |

## Compatibility

OneGlyph is currently built for the **Nothing Phone (3a) Lite / Galaxian** and depends on the Glyph functionality provided by the stock Nothing software on that device.

It is **not** a universal Glyph framework replacement and is not intended to be a generic lighting app for every Android phone.

## Quick Note:

OneGlyph talks to the stock Nothing Glyph service rather than relying on the normal Android `LightsManager` API, which does not expose the Glyph dot normally on this device.

The project keeps the device-specific Glyph integration inside `GlyphController.kt`, while the user-facing effects live in the app layer.

Useful places to start:

```text
app/src/main/java/com/jackson4rocks/oneglyph/
├── GlyphController.kt   # Glyph service / Binder integration
├── GlyphFeatures.kt     # effects, media, charging and camera logic
└── MainActivity.kt      # Compose UI
```


## Project status

OneGlyph is an **active experiment** built specifically around the Glyph dot on Galaxian.

The app is intentionally focused on small, useful effects rather than trying to recreate Nothing's entire Glyph experience.
It can be archived or abandoned at any time.
## License

See the repository for the current license and project files.

---

<div align="center">

**One dot. A lot more possibilities.**

Made by **Leon Sony**  
[GitHub](https://github.com/Jackson4Rocks)

</div>


## Galaxian custom-ROM backend

OneGlyph includes a fallback backend for the Nothing Phone (3a) Lite / Galaxian custom-ROM kernel interface:

```text
/sys/class/leds/noth_leds/state
```

The Galaxian kernel driver accepts:

- `0` — off
- `1` — solid on
- values greater than `1` — kernel-managed blinking, with the value interpreted as a period in milliseconds

The app first tries the normal Android/Nothing Glyph service. When that service is unavailable, it detects the Galaxian sysfs node and uses it instead. It attempts a direct sysfs write first and falls back to `su -c` on rooted devices such as KernelSU/Magisk setups.

On the tested custom ROM, `/sys/class/leds/vibrator/brightness` can also illuminate the Glyph because the vibrator and Glyph paths share the PMIC regulator. OneGlyph deliberately uses `noth_leds/state` as the primary custom-ROM interface so it does not intentionally drive the vibrator API.

The Galaxian sysfs backend is binary: it controls the Glyph as off/on rather than exposing the stock service's full brightness scale. Continuous fast-flash effects use the kernel's built-in blinking support to avoid spawning a root shell for every transition.
