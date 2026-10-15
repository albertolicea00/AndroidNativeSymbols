<p align="center">
  <img src="icon.svg" alt="Native Symbols app icon" width="180" height="176">
</p>

<p align="center">
  <img src=".github/green-dot.svg" width="8" height="8" alt=""> Android 6.0+
  &nbsp;&nbsp;
  <img src=".github/green-dot.svg" width="8" height="8" alt=""> XML
  &nbsp;&nbsp;
  <img src=".github/green-dot.svg" width="8" height="8" alt=""> Kotlin
  &nbsp;&nbsp;
  <img src=".github/green-dot.svg" width="8" height="8" alt=""> Java snippets
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.albertolicea00.nativesymbols">
    <img src="https://img.shields.io/badge/Download_on-Google_Play-34A853?style=for-the-badge&amp;logo=googleplay&amp;logoColor=white" alt="Download on Google Play">
  </a>
</p>

<p align="center">
A small, fully native Android app for exploring icons and copying the code needed to use them. It runs offline and uses Android platform UI components, with no icon library or AndroidX dependencies.
</p>


## ✨ What you can do

- 🧭 Browse **System** and **Material** icons in a grid or list.
- 🔎 Search both catalogs by icon name and preview each drawable.
- 📋 Copy ready-to-use XML layout, Kotlin, and Java snippets.
- 🧩 Copy the complete vector XML for a bundled Material icon.
- 🌓 Follow the device's light or dark theme. The primary color is `#34A853`.

## 🎨 Icon sources

| Catalog | Contents | How it works |
| --- | --- | --- |
| 🧩 Material | 2,122 bundled classic Material Icons (Filled style) | Android vector XML files in `app/src/main/res/drawable/`, with matching copies in `app/src/main/assets/vectors/` for copying |
| 🤖 System | Public `android.R.drawable` resources available on the device | Discovered at runtime; no files or icon dependency are added to the app |

The Material catalog contains every Filled icon in the `@material-design-icons/svg` 0.14.15 package. The other four classic styles and the newer Material Symbols collection are separate catalogs. System icons can look different across Android versions and device themes. The app tints previews for a consistent catalog view; copied resource references use the drawable in your own app.

### 📦 App size

The current debug APK is about **3.1 MB**. The packaged Material vector files and their copyable XML assets account for about **1.6 MB** of it. Expanding the catalog from 38 to 2,122 icons added roughly **1.5 MB**. These are measurements of the debug APK; download and installed sizes can differ by build and device.

## 🛠️ Requirements

- Android Studio with Android SDK 36 installed
- JDK 17
- A device or emulator running Android 6.0 (API 23) or later

The project uses Gradle 8.13 through the included wrapper and Android Gradle Plugin 8.11.0. Its application ID is `com.albertolicea00.nativesymbols`.

## 🚀 Build and run

Open the repository in Android Studio, let Gradle sync, and run the **app** configuration.

From a terminal:

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On Windows, use `gradlew.bat` instead of `./gradlew`.

## 📋 Use an icon in another app

For a **Material vector**, open its detail view and copy **Vector drawable XML**. Save it in your project's `res/drawable/` directory with the resource name shown in the app. Then use one of the copied snippets, for example:

```xml
android:src="@drawable/ic_md_search"
```

```java
imageView.setImageResource(R.drawable.ic_md_search);
```

For a **System drawable**, no local file is needed:

```xml
android:src="@android:drawable/ic_menu_search"
```

```java
imageView.setImageResource(android.R.drawable.ic_menu_search);
```

The detail view also provides Kotlin equivalents and complete `ImageView` XML examples.

## ➕ Add a Material vector to this catalog

1. Add a valid Android vector drawable to `app/src/main/res/drawable/`, using a name such as `ic_md_example.xml`.
2. Put a copy with the **same filename** in `app/src/main/assets/vectors/`. The app reads this copy when you tap **Copy** for the vector XML.
3. Rebuild the app. The new icon appears automatically in the Material catalog.

Android Studio can create a vector drawable with **New → Vector Asset**. No catalog list needs to be edited.

### 🔄 Update the bundled Material icons

The app bundles its icons, so updates require rebuilding and releasing a new APK. Check the [SVG package versions](https://www.npmjs.com/package/@material-design-icons/svg?activeTab=versions) for a release newer than the currently bundled **0.14.15**. If one exists, replace the version in the commands below:

```bash
version=0.14.15
curl -L --fail "https://registry.npmjs.org/@material-design-icons/svg/-/svg-${version}.tgz" -o /tmp/material-icons-svg.tgz
python3 tools/import_material_icons.py /tmp/material-icons-svg.tgz
./gradlew :app:assembleDebug
```

The import script adds new Filled icons and replaces existing icons with the same names in both `res/drawable/` and `assets/vectors/`. Review the changed files and update the version and icon count in this README. If an upstream icon was removed, remove its XML from both directories manually. The Android app itself does not require npm or an internet connection.

Google says the classic [Material Icons set is no longer updated](https://github.com/google/material-design-icons#material-symbols--material-icons); new Google designs now go into **Material Symbols**, which would need a separate import rather than this Filled icon update process.

## 🖼️ Launcher icon

`icon.svg` is the source artwork. Generated PNGs under `app/src/main/res/mipmap-*/` support older Android versions, while `mipmap-anydpi-v26/ic_launcher.xml` defines the adaptive icon for Android 8.0 and later.

After changing the SVG, regenerate the launcher assets with:

```bash
python3 tools/generate_launcher_icons.py
```

This optional asset-generation step requires `rsvg-convert` and Python's Pillow package. Neither is needed to build or run the Android app.

## 📄 Credits and license

The bundled Material vectors were converted from [`@material-design-icons/svg` 0.14.15](https://www.npmjs.com/package/@material-design-icons/svg), which packages Google's [classic Material Icons](https://github.com/google/material-design-icons). They are licensed under [Apache License 2.0](MATERIAL_ICONS_LICENSE). Android system drawables are supplied by the operating system at runtime.
