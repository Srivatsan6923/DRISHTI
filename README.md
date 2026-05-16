# 🦯 DRISHTI
### Digital Reliable Intelligent Support for The visually Impaired

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Android](https://img.shields.io/badge/Android-31%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)
![Python](https://img.shields.io/badge/Python-3.7%2B-yellow.svg)
![License](https://img.shields.io/badge/license-MIT-orange.svg)

**🏆 Winner – Best Healthcare Hack (Hard Hack 2026) | 🏆 Winner – MLH Best Use of ElevenLabs**

*A comprehensive AI-powered assistive navigation device providing independence to the visually impaired — combining real-time vision understanding, face recognition, fall detection, and IoT hardware integration.*

[Features](#-features) • [Screenshots](#-screenshots) • [Architecture](#-architecture) • [Installation](#-installation) • [Usage](#-usage) • [Technologies](#-technologies-used) • [Contributing](#-contributing)

</div>

---

## 👥 Team
- [Srivatsan Sarvesan](https://github.com/Srivatsan6923)
- [Gokul Adethya](https://github.com/FrozenWolf-Cyber)
- [Indresh P](https://github.com/indreshp135)
- [Gayathri Rajesh](https://github.com/rajeshgayathri2003)


---

## 🌟 Inspiration

DRISHTI was created to address critical gaps in assistive technology for visually impaired individuals. Existing solutions are fragmented, expensive, and lack real-time visual understanding or reliable safety monitoring. Our goal was to build a **unified platform** combining AI-powered vision assistance with proactive fall detection — empowering users to navigate the world independently and safely.

---

## 📸 Screenshots & Media

### 📱 Android App Screenshots

<div align="center">

| Fall Detection | Fall Alarm | Image Question |
|:-----------:|:-------------:|:--------------:|
| ![Main Screen](https://github.com/user-attachments/assets/0aab30e2-707f-4f0e-b416-33dd9e21cb9b) | ![Service Active](https://github.com/user-attachments/assets/dff50e13-3651-4965-9306-c4bf9401677a) | ![Emergency Alert](https://github.com/user-attachments/assets/2d8571a5-b287-4795-b44c-b7efc42c8020) |

</div>

---

## ✨ Features

### 👁️ Vision Assistant
- Real-time image understanding using **Google Gemma-3n E2B** (on-device LLM) with a conversational voice interface
- Users can ask questions about their surroundings and receive natural language descriptions
- Streaming responses with real-time Text-to-Speech (TTS) integration
- Function-calling mechanism (`<FUNC>` tag) for switching into face recognition mode

### 🧑‍🤝‍🧑 Face Recognition
- Advanced face identification using **ArcFace embeddings** (512-dimensional vectors)
- Pre-loaded face database recognizes familiar people and announces them via audio (e.g., *"Indresh and Gayatri"*)
- ML Kit face detection pipeline with cosine similarity matching
- Fully personalized — register any familiar face for identification

### 🚨 Fall Detection
- Continuous monitoring via accelerometer and gyroscope sensors
- Multi-phase pattern recognition: **free-fall → impact → rotation**
  - Free-fall threshold: `< 8.0 m/s²`
  - Impact threshold: `> 12.0 m/s²`
  - Rotation threshold: `> 2.0 rad/s`
  - Temporal validation: `200–1000 ms`
- **12-second countdown alert** with dismissible notification (for false alarms)
- Auto-dials emergency contact and sends GPS location via SMS if not dismissed

### 🍓 Raspberry Pi Integration (Wearable Belt)
- Button-triggered camera capture and HTTP upload to Android backend
- **Ultrasonic distance sensor** (HC-SR04) for obstacle detection with buzzer alerts at 20 cm proximity
- Python-based GPIO control scripts with OpenCV camera support
- Seamless failover: if wearable hardware fails, the system automatically falls back to Android's built-in sensors

### ♿ Accessibility
- Full **Text-to-Speech** (TTS) and **voice input** throughout the app
- **ElevenLabs** integration for high-quality, natural-sounding audio feedback
- Screen reader optimization for all UI elements
- Designed for single-handed and eyes-free operation

---

## 🏗️ Architecture

### 📐 System Overview

<img width="2048" height="621" alt="System Architecture" src="https://github.com/user-attachments/assets/3332eed0-58ea-49da-9fe3-77624d085267" />

### 📱 Android App Architecture

```
┌──────────────────────────────────────────────────────────┐
│                      Presentation Layer                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ MainActivity │  │ EmergencyCall│  │ Notification │    │
│  │  (Compose)   │  │   Activity   │  │   Manager    │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
└──────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼─────────────────────────────────┐
│                      Service Layer                        │
│  ┌────────────────────────────────────────────────────┐   │
│  │         FallDetectionService (Foreground)          │   │
│  │  ┌──────────────┐  ┌──────────────┐                │   │
│  │  │   Observer   │  │ AlarmManager │                │   │
│  │  └──────────────┘  └──────────────┘                │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │         NanoHTTPD Local HTTP Server                │   │
│  │  (Receives image uploads from Raspberry Pi)        │   │
│  └────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────── ┘
                          │
┌─────────────────────────▼─────────────────────────────────┐
│                      ML / Inference Layer                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Gemma-3n E2B │  │  ArcFace     │  │  ML Kit      │     │
│  │ (Gemma LLM)  │  │  TFLite      │  │ Face Detect  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└──────────────────────────────────────────────────────────  ┘
                          │
┌─────────────────────────▼─────────────────────────────────┐
│                      Data Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ DataStore    │  │  Location    │  │ ElevenLabs   │     │
│  │ Repository   │  │  Manager     │  │ TTS Service  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└──────────────────────────────────────────────────────────  ┘
```

### 🍓 Raspberry Pi Flow

```
[Button Press]
      │
      ▼
[Camera Capture] ──OpenCV──▶ [Image Frame]
      │
      ▼
[HTTP POST] ──requests──▶ [NanoHTTPD Server on Android]
      │
      ▼
[Gemma-3n Vision Q&A / ArcFace Face ID]
      │
      ▼
[TTS Audio Response via ElevenLabs]

[Ultrasonic Sensor] ──GPIO──▶ [Distance < 20cm] ──▶ [Buzzer Alert]
```

---

## 🚀 Installation

### 📋 Prerequisites

- **Android Studio** – Hedgehog (2023.1.1) or later
- **JDK** – Version 11 or higher
- **Android SDK** – API Level 31+ (Android 12+)
- **Raspberry Pi** – Model 3B+ or later
- **Python** – 3.7+
- **Camera Module** – USB webcam (e.g., Creative Live! Cam Sync HD VF0770) or Raspberry Pi Camera v2
- **Ultrasonic Sensor** – HC-SR04 or compatible

---

### 📲 Android App Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/FrozenWolf-Cyber/ANVI.git
   cd ANVI
   ```

2. **Open in Android Studio**
   - Select *Open an Existing Project*
   - Navigate to the `FallAlert/src` directory

3. **Sync Gradle**
   - Android Studio will automatically sync all Gradle dependencies

4. **Configure Permissions**
   - Grant all requested permissions on first launch

5. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio's Run button
   ```

---

### 🍓 Raspberry Pi Setup

1. **Install Dependencies**
   ```bash
   sudo apt-get update
   sudo apt-get install python3-pip python3-opencv python3-rpi.gpio
   pip3 install requests
   ```

2. **Enable Camera and GPIO**
   ```bash
   sudo raspi-config
   # Interface Options → Enable Camera
   # Interface Options → Enable GPIO
   ```

3. **Configure Hardware Connections**

   **Ultrasonic Sensor (HC-SR04):**
   ```
   VCC  → 5V      (Pin 2)
   GND  → GND     (Pin 6)
   TRIG → GPIO 23 (Pin 16)
   ECHO → GPIO 24 (Pin 18)
   ```

   **Buzzer:**
   ```
   Positive → GPIO 21 (Pin 40)
   Negative → GND     (Pin 6)
   ```

   **Button:**
   ```
   One terminal   → GPIO 17 (Pin 11)
   Other terminal → GND     (Pin 6)
   ```

4. **Configure Server URL**
   ```bash
   cd raspi
   echo "YOUR_ANDROID_DEVICE_IP" > url.txt
   ```

5. **Run Scripts**
   ```bash
   # Camera + button capture
   python3 post.py

   # Ultrasonic sensor + buzzer
   python3 ultrsound_buzzer.py
   ```

---

## 💻 Usage

### 📱 Android App

1. **Initial Setup**
   - Launch the app and grant all permissions
   - Enter emergency contact phone number → *Save Emergency Contact*

2. **Start Monitoring**
   - Tap *Start Service* — a persistent foreground notification confirms the service is active

3. **Vision Assistant**
   - Point the camera and ask a question using voice input
   - Gemma-3n processes the image on-device and speaks the answer via ElevenLabs TTS

4. **Face Recognition**
   - Say the trigger phrase to enter face recognition mode
   - DRISHTI will announce recognized individuals using ArcFace embeddings

5. **Fall Detection**
   - Runs automatically in the background
   - On detected fall: a 12-second countdown starts with an alarm sound
   - Dismiss if it's a false alarm; otherwise the app auto-calls emergency contact and sends an SMS with GPS location

6. **Stop Monitoring**
   - Tap *Stop Service*

### 🍓 Raspberry Pi

```bash
# Camera + button
python3 post.py

# Ultrasonic proximity alert
python3 ultrsound_buzzer.py

# Stop alarm (after fall detection)
python3 stop_alarm.py
```

---

## ⚙️ Configuration

| Setting | Default | Location |
|:--------|:--------|:---------|
| Alarm countdown duration | 12 seconds | `AlarmManager.kt` |
| Obstacle detection threshold | 20 cm | `THRESHOLD_CM` in `ultrsound_buzzer.py` |
| Free-fall threshold | `< 8.0 m/s²` | `FallDetectionService.kt` |
| Impact threshold | `> 12.0 m/s²` | `FallDetectionService.kt` |
| Rotation threshold | `> 2.0 rad/s` | `FallDetectionService.kt` |
| Raspberry Pi server URL | *(set manually)* | `raspi/url.txt` |

---

## 🛠️ Technologies Used

### 📱 Android

| Library / Framework | Purpose |
|:--------------------|:--------|
| **Kotlin** | Primary language |
| **Jetpack Compose** | Modern declarative UI |
| **Material Design 3** | UI component system |
| **Hilt** | Dependency injection |
| **DataStore** | Secure preferences storage |
| **EncryptedSharedPreferences** | Encrypted key-value storage |
| **Kotlin Coroutines** | Asynchronous operations |
| **Google Gemma-3n E2B** | On-device vision-language model (LLM) |
| **TensorFlow Lite (TFLite)** | ArcFace face recognition inference |
| **ML Kit (Face Detection)** | Face bounding box detection |
| **ArcFace** | 512-dimensional face embedding model |
| **NanoHTTPD** | Embedded HTTP server for Raspberry Pi image ingestion |
| **ElevenLabs** | High-quality neural text-to-speech |
| **Activity Recognition API** | Fall detection (activity monitoring) |
| **Location Services** | GPS tracking for emergency SMS |
| **SMS Manager** | Emergency location SMS dispatch |
| **GPU Delegate / NNAPI** | Hardware-accelerated ML inference |

### 🍓 Raspberry Pi / Python

| Library | Purpose |
|:--------|:--------|
| **Python 3** | Scripting language |
| **OpenCV** | Camera capture and image processing |
| **RPi.GPIO** | GPIO pin control (button, buzzer, sensor) |
| **Requests** | HTTP POST to Android NanoHTTPD server |

---

## 🔐 Android Permissions

| Permission | Purpose |
|:-----------|:--------|
| `ACTIVITY_RECOGNITION` | Detect falls via device motion sensors |
| `ACCESS_FINE_LOCATION` | Precise GPS for emergency SMS |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `ACCESS_BACKGROUND_LOCATION` | Continuous background location |
| `CALL_PHONE` | Auto-dial emergency contact |
| `SEND_SMS` | Send GPS location via SMS |
| `POST_NOTIFICATIONS` | Alert notifications |
| `USE_FULL_SCREEN_INTENT` | Full-screen emergency UI |
| `SCHEDULE_EXACT_ALARM` | Precise countdown alarm |

---

## 🧪 Testing

### Android

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Coverage report
./gradlew jacocoTestReport
```

### Raspberry Pi

```bash
# Test camera
python3 -c "import cv2; print('Camera OK')"

# Test GPIO
python3 -c "import RPi.GPIO as GPIO; print('GPIO OK')"

# Test Android server connectivity
python3 request_android.py
```

---

## 🐛 Troubleshooting

| Issue | Possible Fix |
|:------|:-------------|
| Service not starting | Check all permissions; disable battery optimization for the app |
| Location unavailable | Ensure GPS is on; move outdoors or near a window |
| SMS not sending | Verify SMS permission and cellular connection; check contact number format |
| Raspberry Pi camera not working | Confirm camera is enabled in `raspi-config`; check USB or ribbon cable connection |
| Ultrasonic sensor not responding | Verify GPIO wiring and pin numbers; test with a multimeter |
| TFLite model failing | Ensure the `.tflite` model file is present in `assets/`; check GPU delegate compatibility |
| NanoHTTPD not receiving | Check that Android and Pi are on the same network; confirm IP in `url.txt` |

---

## 🔭 What's Next for DRISHTI

- Runtime face database management (add/remove people on the fly)
- Live camera face recognition (real-time, not frame-by-frame)
- ML-based fall detection model (replacing rule-based thresholds)
- Real-time object detection and OCR
- Smartwatch sensor integration for richer motion data

---

## 🏆 Awards

| Award | Hackathon |
|:------|:----------|
| 🥇 Best Healthcare Hack — Amazon Gift Cards | Hard Hack 2026 |
| 🥇 MLH Best Use of ElevenLabs | Hard Hack 2026 |


---

## 📞 Support

- 🐛 Issues: [GitHub Issues](https://github.com/FrozenWolf-Cyber/ANVI/issues)
- 📖 Documentation: [Wiki](https://github.com/FrozenWolf-Cyber/ANVI/wiki)

---

<div align="center">

**Made with ❤️ to create meaningful impact in people's lives**

*DRISHTI — Digital Reliable Intelligent Support for The visually Impaired*

⭐ Star this repo if you find it helpful!

</div>
