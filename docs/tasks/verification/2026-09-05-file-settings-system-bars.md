# File settings, opening and system bars verification — 2026-09-05

## Scope

- Default incoming files to automatic acceptance, with a persistent opt-in confirmation setting.
- Persist a user-selected receive directory and use it for the next incoming transfer.
- Open eligible file-message cards with the operating system's default application.
- Make Android status and navigation bars transparent without installing the APK.
- Record, but do not misrepresent as implemented, the separate progressive-media preview design.

## Automated verification

```shell
./gradlew :data:jvmTest :network:jvmTest :app:shared:jvmTest \
  :app:androidApp:assembleDebug :app:desktopApp:compileKotlin
```

Result: `BUILD SUCCESSFUL in 1m 6s`; 115 actionable tasks, 74 executed, one restored from cache and 40 up-to-date.
The Android packaging warning says `libandroidx.graphics.path.so` could not be stripped and was packaged unchanged; it
does not fail the Debug build.

After preserving the original extension and MIME type for Android provider-backed outgoing files, the same complete
command was run again and returned `BUILD SUCCESSFUL in 2s` with the configuration cache reused.
The final settings-row presentation change was followed by `:app:shared:compileKotlinJvm` and
`:app:shared:compileAndroidMain`; both compiled successfully in 1 second.

The regression set proves:

- a fresh settings repository defaults to automatic reception;
- directory and confirmation changes survive construction of a new repository over the same settings storage;
- automatic mode transfers a multi-chunk file without publishing an incoming offer dialog;
- confirmation mode exposes `WAITING_FOR_ACCEPTANCE`, supports explicit acceptance, and supports rejection without bytes;
- the selected destination directory is used and the completed bytes equal the source;
- the shared chat timeline still orders text and file messages deterministically;
- Android sources, resources and manifest package into a Debug APK, and desktop Kotlin compiles.

```shell
git diff --check
```

Result: no whitespace errors after the final documentation update.

## Code evidence

- `MultiplatformFileTransferSettingsRepository` exposes a `StateFlow` and persists both settings using
  Multiplatform Settings.
- `SubnetDropTransport.handleFileOffer` reads the setting for each offer and retains the signed `FILE_DECISION` protocol
  in both automatic and confirmation modes.
- incoming data uses FileKit `RawSink`, a hidden partial directory and a collision-free final destination; only the
  completed destination path is eligible for receiver-side system opening.
- Android requests FileKit bookmark data after directory selection, which retains provider access for a content URI.
- `MainActivity` supplies transparent status/navigation bar styles and disables the Android 10+ navigation contrast
  scrim; the root Compose surface draws behind the bars.

## Unverified boundary

No APK was installed, per the current user constraint. Android SAF persistence, OEM system-bar appearance and default-app
opening therefore remain physical-device checks. Windows native dialogs and file association also require a Windows run.
Progressive media playback is intentionally not shipped in this change; only streaming receive-to-disk is implemented.
