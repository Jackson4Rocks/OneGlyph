<div align="center">
  <img src="assets/oneglyph.svg" width="128" alt="OneGlyph icon" />

  # OneGlyph

  **Make the one dot do more. :)**

  Your Nothing Phone (3a) Lite has a tiny little Glyph dot sitting there doing nothing most of the day.
  OneGlyph puts it to work — music, messages, timers, toys and all sorts of fun stuff.

  👉 Built for the **Nothing Phone (3a) Lite (Galaxian)** running stock Nothing OS · **v2.0**
</div>

---

## Hello! What is this? ;)

OneGlyph is a small, friendly app that controls the single Glyph dot on your phone. No complicated dashboards,
no 40-toggle settings maze — just open it, tap something, and watch the dot dance.

Here's a taste of what the dot can do for you:

- 🎵 bounce along to your music
- 🔔 blink when a notification lands
- ▪️ flash secret messages in Morse code
- 🥁 keep time like a metronome
- 🕰️ literally tell you the time in flashes
- 🎲 roll dice, 🌬️ guide your breathing, 📷 count down your camera timer…

…and that's just the fun stuff. It also handles genuinely useful jobs like charging feedback and low-battery alerts.

## Getting started

Getting going takes about a minute, promise :)

1. **Install** the OneGlyph APK on your phone.
2. **Open** OneGlyph.
3. The app turns itself on — check the dot at the top says **READY**. If it says **LINKING**, give it a few seconds ;)
4. Tap **Blink** on the Home tab. Dot blinked? You're all set!

### Setting up Beat Sync 🎵

Beat Sync needs two permissions before it can hear your music:

1. Tap **TURN ON BEAT SYNC**.
2. Allow **Audio access** when asked (so it can analyse the sound).
3. Allow **Media Access** on the next screen (so it knows what's playing).
4. Play something! The music card will say **"The dot is dancing."** :)

> Tip: while Beat Sync is on you'll see a live engine line like
> `ENGINE • LISTENING • last beat 0.4s ago`. That's the app proving it's really hearing beats.
> If the dot stays dark, hit **TEST BEAT** — one blink means the Glyph side is fine and the hiccup is on the audio side.

## Take the tour

OneGlyph has four tabs, reachable by tapping the pill at the bottom — or just swiping sideways ;)

### 🏠 Home — everyday stuff

| Card | What it does |
| --- | --- |
| Music | Beat Sync on/off, current track, engine status, TEST BEAT |
| Blink | Quick patterns: Blink, Double, Heart, plus STOP GLYPH |
| Camera | Camera countdown with accelerating flashes (3s / 5s / 10s) |
| Manual Control | Brightness slider with a TEST BLINK button |

### 🎮 Play — the fun zone :)

This is where the dot shows off:

- **Morse Coder** — type up to 40 characters, the dot flashes them in real Morse code. Great for secret messages ;)
- **Metronome** — 30 to 240 BPM with a bright accent every 4th beat. Keeps running in the background while you practise!
- **Talking Clock** — one tap and the dot flashes the time: hours… pause… minute tens… pause… minute ones. A zero reads as one long glow.
- **Battery Meter** — flashes your exact battery level (tens, then ones).
- **Dot Dice** — rolls a d6 with a slowing suspense flicker, then flashes your number.
- **Breathe 4-7-8** — a guided breathing cycle: the dot swells as you inhale (4s), holds dim (7s) and sinks as you exhale (8s). Relaxing, we promise :)

### 🧩 Composer — your own patterns

Build blinking sequences up to **8 steps**, each with its own brightness and duration. Preview it, tweak it, save it. Go wild ;)

### ⚙️ Settings — the control room

| Setting | What it does |
| --- | --- |
| OneGlyph | Master on/off switch for everything |
| Appearance | Dark or light mode |
| Charging effects | 4 blinks when charging starts, 9 blinks at your target |
| Charge target | 80%, 90% or 100% — match it to your phone's charging limit :) |
| Low battery alert | Flashes SOS when the battery runs low |
| Camera timer | 3s, 5s or 10s countdown |
| Glyph service | Live status of the connection to your phone's Glyph service |
| Media Access | Shortcut to Android's media-access settings |
| Notification flash | Double-blink for new notifications (pauses while Toy, Music Sync or Metronome runs) |
| Project Maintainer | That's Leon! Say hi ;) |
| Special Thanks | Lovely people who helped shape OneGlyph |

## Persistent modes — they keep going without you :)

**Dot Toy**, **Music Sync** and **Metronome** keep running in the background even if you close the app, thanks to a little foreground service (that's the calm "Keep Blinking!" notification).

- Only **one** of them runs at a time — starting one politely stops the others ;)
- If Android naps the service, it wakes itself back up automatically.
- Turning the master **OneGlyph** switch off stops everything.

## Troubleshooting — don't panic :)

**The top dot says LINKING forever**
- Make sure you're on stock Nothing OS on a supported phone. OneGlyph talks to Nothing's Glyph service, which only exists there.
- Check Settings → **Glyph service** — it breaks the connection down piece by piece so you can see what's missing.

**Beat Sync won't dance**
- Both **Audio access** and **Media Access** must be allowed — the music card tells you which one is missing.
- Watch the **engine line**: `LISTENING • last beat Xs ago` means beats are landing. Stale numbers mean the detector isn't firing on this track — try something with a stronger beat ;)
- **TEST BEAT** isolates the problem: blink = Glyph side fine, look at audio; no blink = Glyph side issue.

**Notifications don't flash**
- Needs **Media Access** (same permission as Beat Sync).
- Flashes pause while Toy, Music Sync or Metronome is running — they own the dot then.
- Very chatty apps are debounced: max one flash every few seconds, so your dot doesn't have a seizure :)

**Battery worries?**
- One-shots (blinks, clock, dice…) cost essentially nothing.
- Persistent modes hold a lightweight foreground service. Music Sync additionally samples audio, so it sips a little more — turn it off when the party's over ;)

## Compatibility

OneGlyph is built for the **Nothing Phone (3a) Lite / Galaxian** and depends on the Glyph functionality in its stock Nothing software.

It is **not** a universal Glyph app and won't do much on other phones — sorry! :)

## For the curious (dev notes)

Device-specific Glyph wiring lives in `GlyphController.kt`; user-facing effects live in the app layer:

```text
app/src/main/java/com/jackson4rocks/oneglyph/
├── GlyphController.kt         # stock Nothing Glyph service / Binder integration
├── GlyphBackgroundService.kt  # persistent Toy / Music Sync / Metronome host
├── GlyphFeatures.kt           # effects, media, charging, camera + DotFun toys
└── MainActivity.kt            # Compose UI (Home / Play / Composer / Settings)
```

## Credits — the lovely humans :)

**Project Maintainer — Leon Sony ([Jackson4Rocks](https://github.com/Jackson4Rocks))**
An independent community project, made with care :)

**Special Thanks**
- **saran_archiz** (Nothing Community) — for the general idea that sparked OneGlyph
- **creet_seneseca** (Reddit) — for general testing and bug reports

*I thank you sincerely for your contributions ;)*

---

<div align="center">

**One dot. A lot more possibilities.**

Made by **Leon Sony**
[GitHub](https://github.com/Jackson4Rocks)

</div>
