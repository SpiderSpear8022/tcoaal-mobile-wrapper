# TCOAAL

An unofficial Android launcher for the RPG Maker MV browser version of *The
Coffin of Andy and Leyley*. This is a bring-your-own-files app: it does not
include the game, its `www` directory, or game scripts, images, audio, fonts, or
other assets. Provide game files from a copy you are authorized to use. This
project is not affiliated with the game’s developers or publisher.

## Upstream project credit

- [LlamaToolkit](https://github.com/Llamaware/LlamaToolkit) — its asset
  decryption workflow was used as the reference for this app's Android asset
  decryptor. This project contains an Android implementation; it does not
  bundle or run LlamaToolkit or include its Windows executable.
- [AndroidX DocumentFile](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/documentfile/)
  — runtime library dependency used for Android document-provider file access
  (`androidx.documentfile:documentfile`).
- [JUnit 4](https://github.com/junit-team/junit4) — test-only dependency
  (`junit:junit`).

No source files from AndroidX DocumentFile or JUnit are copied into this
project; they are included as declared dependencies. The app's other
functionality is implemented in this project, with LlamaToolkit credited above
as the reference for the asset-decryption workflow.

## Set up the app

1. Build and install the app as described in [Build](#build), or install a
   trusted release APK if one is available.
2. Copy the original game `www` folder to storage the Android folder picker can
   access. Keep the original contents and folder structure.
3. Open **TCOAAL**, tap **Select original www folder**, and
   choose the `www` folder itself—the one containing `index.html` and
   `js/rpg_core.js`. Approve Android's access prompt. The selected storage
   provider must allow the app to create files in the folder.
4. Wait for **Decrypted assets folder: ready**. If a usable `www/decrypted`
   folder exists, the app uses it. Otherwise, it creates `www/decrypted` from
   `www/audio`, `www/data`, and `www/img`. You do not need to decrypt the files
   yourself; originals are left in place.
5. Tap **Launch game**.

Folder selections are remembered on the device. Select the new `www` folder
again if you move the game files. **Select decrypted assets folder** is an
optional alternative for assets stored separately; it is not normally needed.

## Controls

- D-pad on the left: movement.
- Buttons on the right: **Q/page up**, **W/page down**, **Run/Shift**,
  **Back/Escape**, and **OK/confirm**.
- Press and hold for continuous movement or button input. Touches outside the
  controls continue to reach the game.
- Tap **Customize controls** on the folder screen to move, resize, or adjust
  opacity of the controls, then save the layout.

## Requirements and known limitations

- Android 6.0 (API 23) or later, in landscape orientation, with a working
  Android System WebView (or Chrome's WebView component).
- **Estimated minimum:** 2 GB RAM and enough free storage for the generated
  decrypted copy. This is a practical estimate, not a tested certification;
  performance varies with the phone, storage, Android version, and game.
- **Recommended:** Android 10 or later, 4 GB RAM or more, and fast internal
  storage. The app has been exercised on an Android 13 Samsung Galaxy A32.
- During first-time preparation, leave free space at least equal to the combined
  size of the original `audio`, `data`, and `img` folders. The app also uses a
  temporary asset cache of up to 512 MB; Android may clear it when storage is
  low. A usable existing `www/decrypted` folder avoids generating another copy.
- First visits to scenes can take longer while assets are read and cached;
  revisited scenes may be faster while cached files remain available.
- The app targets RPG Maker MV browser builds and uses Android WebView, not the
  desktop NW.js runtime. Games requiring desktop-only Node.js or operating
  system features may not work.
- Game saves are held in this app's WebView storage. Clearing app data or
  uninstalling the app may erase them.

## Asset preparation

The Android decryptor streams supported encrypted assets instead of loading
entire files into memory. It records completed files in
`www/decrypted/.tcoaal-decryption-manifest-v1.txt`, so interrupted preparation
can resume and partial outputs are not treated as a finished asset set.
Temporary `.tcoaal-part-*` files are replaced only after a file is finalized.

The Android decryptor does not run LlamaToolkit's DRM-removal or
script-deobfuscation commands. Decrypted files are created under the
player-selected `www/decrypted` folder, not packaged in the APK. See
[Upstream project credit](#upstream-project-credit).

## Build

Requirements:

- Android Studio with JDK 17 or later.
- Android SDK Platform 35.
- Internet access for the first Gradle sync/build to download Gradle, the
  Android Gradle Plugin, and AndroidX dependencies.

From this `mobile` project directory:

```shell
./gradlew testDebugUnitTest assembleDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device
with Android Platform Tools:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is for testing. Create and securely sign a release build before
distributing an APK. Never distribute game files; players must provide their
own original `www` folder.
