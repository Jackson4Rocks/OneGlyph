# OneGlyph

**Make the one dot do more.**

OneGlyph is an open-source Android app for the Nothing Phone 3a Lite (Galaxian) that turns the single Glyph dot into something programmable.

## First milestone

- Discover the lights exposed by the ROM
- Select the Glyph light
- Turn it on and off
- Control intensity with Android's `LightState`
- Run blink, pulse, and heartbeat patterns
- Stay independent from Nothing's proprietary Glyph UI/framework

## Architecture

`OneGlyph -> LightsManager -> Android lights service -> ROM AIDL Light HAL -> noth_leds -> Glyph dot`

OneGlyph uses Android's standard `android.hardware.lights.LightsManager` path. Android gates that API with `android.permission.CONTROL_DEVICE_LIGHTS`, so this project is intended to be installed as a privileged ROM app rather than trying to bypass the permission.

The custom ROM also needs a Glyph-aware light HAL. The existing Galaxian open-source HAL is backlight-only, so the app cannot make the dot work until the HAL exposes the Glyph IDs.

## ROM integration

The `rom/` directory contains:

- `OneGlyph.mk` — adds the APK to the product
- `Android.bp` — imports the APK as a privileged, platform-signed app
- `privapp-permissions-com.jackson4rocks.oneglyph.xml` — grants `CONTROL_DEVICE_LIGHTS`

For a source ROM build, put the built APK at `rom/OneGlyph.apk`, add the module to the product, and include the permission XML in the ROM's privileged-permissions set.

## Important

This repository does **not** include Nothing's proprietary framework or extracted Nothing APK code.

The first animations are app-driven by repeatedly submitting ordinary `LightState` requests. That keeps the first version simple. Later releases can add a proper composer and move timing/effects to a ROM-side effect layer where that makes sense.

## Roadmap

1. Basic dot control — current
2. Saved Glyph Composer sequences
3. Notification reminder patterns
4. Charging effects
5. Camera countdown
6. Music visualization
7. Ringtone/notification pattern playback
8. Background effect service with battery-safe limits
