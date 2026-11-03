# TCOAAL Mobile Wrapper

An unofficial Android launcher for the RPG Maker MV browser version of *The
Coffin of Andy and Leyley*. This app is a bring-your-own-files wrapper: it does
not include the game, its `www` directory, or any of the game's scripts, images,
audio, fonts, or other assets. You must provide game files from a copy you are
authorized to use. This project is not affiliated with the game’s developers or
publisher.

## Get started

1. Install the Android app. If you have an APK from a trusted release, open it
   on your device and allow installation from that source if Android asks.
2. Copy the original game `www` folder to storage on your device. Keep its
   original contents and folder structure.
3. Open **RPG Maker Mobile Shell**, tap **Select original www folder**, and
   choose the `www` folder itself—the one containing `index.html` and
   `js/rpg_core.js`. Approve Android's access prompt. The selected storage
   provider must allow the app to create files in the folder.
4. Wait for the launcher to report **Decrypted assets folder: ready**. If a
   usable `www/decrypted` folder already exists, the app uses it. Otherwise,
   the app creates `www/decrypted` from the original `www/audio`, `www/data`,
   and `www/img` assets. You do not need to decrypt them yourself. The original
   files are left in place.
5. Tap **Launch game**.

The app remembers the folder selection on the device. If the game folder
changes or you move the files, select the new `www` folder again. **Select
decrypted assets folder** is an optional alternative for assets stored
separately; it is not normally needed when automatic preparation succeeds.

### Touch controls

- Use the on-screen D-pad on the left to move.
- Buttons on the right provide **Q/page up**, **W/page down**, **Run/Shift**,
  **Back/Escape**, and **OK/confirm**.
- Press and hold for continuous movement or button input. Touches outside the
  controls go to the game.
- Tap **Customize controls** on the folder screen to move, resize, or adjust the
  opacity of the controls, then save the layout.

## Storage, performance, and compatibility

- The app targets Android 6.0 (API 23) and later. A current Android System
  WebView (or Chrome's WebView component) is required.
- **Estimated minimum:** an Android phone with 2 GB RAM and enough
  storage for the original game files plus the generated decrypted copy. This
  is a practical estimate, not a tested device certification; performance will
  vary substantially by phone, storage speed, Android version, and game.
- **Recommended:** Android 10 or later, 4 GB RAM or more, and fast internal
  storage. The app has been exercised on an Android 13 Samsung Galaxy A32, but
  that does not guarantee every game's compatibility.
- During first-time preparation, leave free space at least equal to the combined
  size of the original `audio`, `data`, and `img` folders. The app also uses a
  temporary asset cache of up to 512 MB; Android may clear it when storage is
  low. A pre-existing usable `www/decrypted` folder avoids generating another
  copy.
- First visits to scenes can take longer while assets are read and cached.
  Revisited scenes may load faster while cached files remain available.
- The app is designed for RPG Maker MV browser builds. It uses Android WebView,
  not the desktop NW.js runtime; games that require desktop-only Node.js or
  operating-system features may not work.
- Game saves are held in this app's WebView storage on the device. Clearing the
  app's data or uninstalling it may erase those saves.

## Decryption and game files

The app includes an Android implementation of the asset-decryption portion of
the open-source [LlamaToolkit](https://github.com/Llamaware/LlamaToolkit)
workflow. It performs local asset preparation only: the Windows toolkit
executable is not included, and the app does not run the toolkit's DRM-removal
or script-deobfuscation commands. Decrypted files are created in the
player-selected `www/decrypted` folder, not packaged in the APK.

Never download or share game files on behalf of someone else. Obtain the
original game and its files through legitimate means, and follow the game's
license and applicable law.

## Build from source

The Android Studio project uses Android Gradle Plugin 9.1.1, Gradle 9.3.1,
Android SDK Platform 35, and Java 11 source compatibility. Use Android Studio
with JDK 17 or later and install Android SDK Platform 35. The first Gradle sync
needs internet access to download the Gradle distribution, Android Gradle
Plugin, and AndroidX dependency.

The GitHub repository currently contains documentation and a license, but not
the Android project source or a release APK. A fresh clone therefore cannot
build or install the app yet. The commands below apply when the Android project
files are available.

From the Android project directory:

```shell
./gradlew testDebugUnitTest assembleDebug
```

On Windows, run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected
device with Android Platform Tools:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is intended for testing; create and securely sign a release build
before distributing an APK.
