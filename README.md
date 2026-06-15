# Meeting Summarizer (Android)

Offline meeting recorder, transcriber, and summarizer. This repo currently
contains the **project skeleton** — it builds and runs, with empty screens wired
together by navigation. Features are added milestone by milestone (see `../WBS.md`).

## What works right now
- App launches to a **Home** screen.
- Buttons navigate to **Record**, **Import**, and **Settings** (placeholder screens).
- A **Meeting detail** screen exists and accepts a meeting id.
- Local database (Room), repository, and engine interfaces are scaffolded but
  not yet wired into the UI.

## Requirements
- **Android Studio** (latest stable — Koala or newer recommended).
- **JDK 17** (bundled with recent Android Studio).
- An Android device or emulator running **Android 8.0 (API 26)** or higher.

## How to run (recommended: Android Studio)
1. Open Android Studio → **Open** → select this `MeetingSummarizer` folder.
2. Let Gradle **sync** (it downloads dependencies and sets up the Gradle wrapper
   automatically on first sync).
3. Pick a device/emulator and press **Run ▶**.

## How to run (command line)
The Gradle **wrapper jar** is not committed. Generate it once (needs a local
Gradle install), then build:
```bash
cd MeetingSummarizer
gradle wrapper          # one-time: creates ./gradlew
./gradlew assembleDebug # build the debug APK
./gradlew installDebug  # install to a connected device/emulator
```
> If you open the project in Android Studio first, the wrapper is created for
> you and you can skip the `gradle wrapper` step.

## Project structure
```
MeetingSummarizer/
├── settings.gradle.kts
├── build.gradle.kts            # root build
├── gradle.properties
├── gradle/libs.versions.toml   # dependency version catalog
└── app/
    ├── build.gradle.kts        # app module build
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/shimul/meetingsummarizer/
        │   ├── MainActivity.kt
        │   ├── MeetingApp.kt            # Application
        │   ├── ui/
        │   │   ├── theme/               # Compose theme
        │   │   ├── navigation/          # routes + NavHost
        │   │   └── screens/             # home, record, importfile, detail, settings
        │   ├── data/
        │   │   ├── local/               # Room database, dao, entity
        │   │   └── repository/          # MeetingRepository
        │   └── domain/
        │       ├── model/               # Meeting domain model
        │       ├── transcription/       # Transcriber interface (Vosk later)
        │       └── summarization/       # Summarizer interface (Gemma later)
        └── res/                         # strings, colors, theme, launcher icon
```

## Roadmap (milestones)
- **M1 — Skeleton** ✅ (this)
- **M2 — Recording + live transcript** (Vosk)
- **M3 — File import + transcription**
- **M4 — On-device summarization** (Gemma via MediaPipe)
- **M5 — Library / history**
- **M6 — Polish** (settings, models, privacy, theming)
