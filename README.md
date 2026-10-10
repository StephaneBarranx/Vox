# 🎙️ Vox - Application de Transcription Vocale Hors-Ligne pour Android

**Vox** est une application Android moderne, gratuite, hautement performante et 100% privée permettant de transcrire des enregistrements vocaux et des fichiers audio directement sur l'appareil (on-device) grâce au moteur **whisper.cpp**. Aucune API payante, aucun service cloud et aucune télémétrie ne sont utilisés.

---

## ✨ Fonctionnalités Principales

- 🎙️ **Enregistrement vocal direct** : Capture audio en PCM 16 kHz mono avec visualisation de la forme d'onde en temps réel (60 fps).
- 📁 **Import multi-format** : Prise en charge des fichiers MP3, M4A, AAC, WAV, OGG, FLAC, OPUS, 3GP via le sélecteur système et le menu de partage Android (`ACTION_SEND` / `ACTION_VIEW`).
- 🤖 **Moteur Whisper (via whisper.cpp)** : Modèles GGML exécutés en natif via NDK C++ avec optimisations ARM NEON.
- ⏬ **Gestionnaire de modèles Hugging Face** : Téléchargement à la demande de modèles Whisper (`Tiny ~31 Mo`, `Base ~57 Mo`, `Small ~182 Mo`) avec reprise et suivi de progression.
- ⚙️ **Arrière-plan fiable** : Traitement via `WorkManager` (Foreground Task) et `AudioRecordService` pour continuer la transcription ou l'enregistrement écran éteint.
- 📝 **Résultat & Édition** : Mode texte éditable, vue des segments horodatés cliquables (`[00:01:23]`), lecteur ExoPlayer synchronisé.
- 📤 **Partage & Exportation** : Exportation sous formats `.txt` et `.srt` via `FileProvider` et feuille de partage native.
- 🎨 **Material 3 & Edge-to-Edge** : Support des couleurs dynamiques (Material You), thèmes clair/sombre et typographie Google Fonts (`Plus Jakarta Sans`).

---

## 🛠️ Stack Technique

- **Langage** : Kotlin + Coroutines + Flow (UDF / Unidirectional Data Flow)
- **UI** : Jetpack Compose + Material 3
- **Navigation** : Jetpack Navigation Compose type-safe (`@Serializable`)
- **Architecture** : MVVM / Clean Architecture
- **Injection de dépendances** : Hilt
- **Moteur STT Natif** : whisper.cpp (JNI / C++17 / CMake NDK avec NEON ARM)
- **Décodage Audio Natif** : `MediaExtractor` + `MediaCodec`
- **Enregistrement Audio** : `AudioRecord` (16kHz Mono WAV)
- **Persistance** : Room Database + DataStore Preferences
- **Arrière-plan** : WorkManager + Foreground Services
- **Lecteur Audio** : Media3 ExoPlayer

---

## 🚀 Guide de Compilation & Installation

### Prérequis
- **Android Studio** (Griffin / Ladybug / Meerkat ou plus récent)
- **Android SDK** API 35 (minSdk 26, targetSdk 35)
- **NDK** (Side by side) `30.0.16248370` ou supérieur
- **CMake** `3.22.1`

### Cloner et sous-module Git
```bash
git clone https://github.com/votre-user/Vox.git
cd Vox
git submodule update --init --recursive
```

### Compiler le projet
Dans Android Studio, synchronisez le projet Gradle et lancez la tâche de compilation :
```bash
./gradlew app:assembleDebug
```

---

## 📄 Licence
- Code de l'application : MIT
- Engine : **whisper.cpp** par ggerganov (Licence MIT)
- Modèles : OpenAI Whisper (Licence MIT)
