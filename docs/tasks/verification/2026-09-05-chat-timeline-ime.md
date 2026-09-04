# Chat timeline and Android IME verification — 2026-09-05

## Scope

- Merge text and file-transfer state into one scrollable conversation timeline.
- Place outgoing delivery/read state below, not inside, the text bubble.
- Keep the Android composer above the IME with one correctly consumed bottom inset.
- Do not install or launch the Android application on a device.

## Automated verification

```shell
./gradlew :app:shared:jvmTest :network:jvmTest \
  :app:androidApp:assembleDebug :app:desktopApp:compileKotlin
```

Result: `BUILD SUCCESSFUL`. This covers the deterministic timeline merge test, all 18 network JVM tests, Android
manifest/resource/Kotlin compilation and packaging, and desktop Kotlin compilation.

The first combined verification exposed a duplicated `@Composable` annotation left at the removed panel boundary and was
fixed before the successful run. Its reachability test also failed once immediately after server startup; the unchanged
test passed in both following complete runs, so no retry or production behavior was added to conceal that timing failure.

```shell
git diff --check
rg -n "windowSoftInputMode" app/androidApp/build/intermediates -g AndroidManifest.xml
```

Result: no whitespace errors. Merged and packaged debug manifests contain `android:windowSoftInputMode="adjustResize"`.
The generated APK is `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk` (approximately 76 MiB).

## Evidence from code structure

- `ChatTimeline` owns the only `LazyColumn` and renders both `TextMessage` and `FileMessage` items.
- File-transfer creation records `createdAt`; accepting an incoming offer updates the existing item instead of replacing
  its timestamp.
- `DeliveryState` is a sibling immediately after the text `Surface`, outside the bubble content.
- Root Scaffold padding is consumed before `ChatScreen` applies outer `imePadding`; `Composer` no longer applies its own
  IME padding.

## Unverified boundary

No Android APK was installed, per the current user constraint. Compilation proves inset APIs and manifest integration are
valid, but actual OEM keyboard animation, focus behavior and pixel-level attachment to the IME remain unverified until a
physical-device interaction run is explicitly authorized.

## Follow-up: adaptive message measurement

The text bubble now uses an explicit `CenterStart` content container inside its 48 dp minimum height. The file card no
longer has a fixed 260 dp minimum, an inner `fillMaxWidth`, or a fill-weighted text column; its intrinsic text/status width
determines the card width and 440 dp remains only an upper bound.

```shell
./gradlew :app:shared:jvmTest :app:shared:compileAndroidMain :app:desktopApp:compileKotlin
```

Result: `BUILD SUCCESSFUL in 5s`; 45 actionable tasks, 19 executed and 26 up-to-date. `git diff --check` also passed.
This proves both target source sets accept the measurement code and the shared logic regression remains green. Pixel-level
measurement still requires running the UI on a target platform.
