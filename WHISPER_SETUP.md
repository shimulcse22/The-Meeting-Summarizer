# Whisper (staging flavor) — setup checklist

The `staging` build flavor will use **Whisper** (OpenAI's model) running on-device
via **whisper.cpp**. Unlike Google STT, Whisper needs a small native build. This
file lists what must be done before the real Whisper engine can be wired in
(it currently uses a placeholder).

## What you (or we) need to do

### 1. Install the NDK + CMake (one-time)
In Android Studio:
- **Settings → Languages & Frameworks → Android SDK → SDK Tools**
- Check **NDK (Side by side)** and **CMake** → **Apply** (≈1 GB download).

### 2. Add the whisper.cpp native sources
From a terminal in the project root (`MeetingSummarizer/`):
```bash
git clone https://github.com/ggerganov/whisper.cpp.git
```
(We'll point CMake at this folder; it stays out of the app module.)

### 3. Native module + JNI bridge (I will generate)
- A `:whisper` library module with `CMakeLists.txt` + a small JNI wrapper.
- A Kotlin `WhisperContext` wrapper exposing `transcribe(floatArray)`.

### 4. Model download (I will generate)
- `WhisperModelManager` downloads a GGML model on first use:
  - `ggml-tiny.en.bin` (~75 MB) — fast
  - `ggml-base.en.bin` (~142 MB) — **recommended default**
  - `ggml-small.en.bin` (~466 MB) — most accurate
- For Bangla later: the multilingual `ggml-base.bin` (not the `.en` variant).

### 5. WhisperEngine (I will generate)
- Implements `SpeechEngine`.
- Captures audio with the existing `AudioRecorder` (16 kHz mono PCM).
- Buffers ~3–5 s windows, converts PCM → float, runs whisper, appends text.
- Replaces `WhisperPlaceholderEngine` in `src/staging`.

## Notes / expectations
- Whisper is **chunked**, so live text appears every few seconds (not word-by-word).
- It adds **punctuation and capitalization** automatically.
- Runs fully **offline** after the one-time model download.
- More CPU/battery than Google STT; fine for meetings.

## Current status
- ✅ Flavor split in place: `qa` = Google STT, `staging` = Whisper.
- ✅ whisper.cpp cloned at project root.
- ✅ `:whisper` native module (CMake + JNI bridge + Kotlin `WhisperContext`).
- ✅ `WhisperModelManager` (downloads `ggml-base.en.bin` ~142 MB on first use).
- ✅ `WhisperEngine` wired into the `staging` flavor (chunked 5s live transcription).
- ▶️ Next: select the **stagingDebug** variant, build (first build compiles whisper.cpp —
  takes a few minutes), run, and record.
