# Radio Garden TV

A small Android TV wrapper that opens [radio.garden](https://radio.garden) full screen and lets
you drive it with a TV remote. Built for a Formuler Z11 Pro Max (32-bit Android 11), where the
official phone app can't be installed.

## Remote controls

| Button | Action |
|---|---|
| Arrows | Move the pointer (eases up to speed when held); past the top/bottom edge scrolls or zooms the globe |
| OK | Click |
| Hold OK | Spin mode: arrows turn the globe (OK or Back to leave) |
| Back | Back in the site; press twice on the start page to exit |

Audio keeps playing after Home; exit with Back to stop it.

## Build and install

```
gradlew assembleRelease
adb connect <box-ip>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

Needs the Android SDK (`local.properties` with `sdk.dir`) and JDK 21. The release build is signed
with the debug key, for sideloading on your own devices.

Pointer feel is tuned by `GLIDE_START_DP_S`, `GLIDE_MAX_DP_S` and `GLIDE_RAMP_S` in
`MainActivity.kt`.
