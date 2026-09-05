# Rokid Glasses ↔ Android Phone — AI Vision & Voice Demo

A working end-to-end prototype: the **Rokid Glasses** capture camera + mic, an
**Android phone** does the AI (object detection + speech), and results render back
on the glasses as an AR panel.

```
Rokid Glasses (YodaOS / Android 12, AR1)
 ┌─ Camera
 └─ Microphone
        │
        │  Wi-Fi  WebSocket  (ws://<phone-ip>:8080)
        ▼
Android Phone (edge compute)
 ├── Object Detection   (MediaPipe / TFLite)
 ├── Speech Recognition
 └── Command Processor
        │
        │  JSON result
        ▼
Rokid UI
 ├── Detection panel  (Person 96%, Laptop 88%, …)
 └── Speech response  ("I can see a laptop and a bottle.")
```

## Platform decision (why it's built this way)

Rokid Glasses (2025, Qualcomm AR1) run **YodaOS, an Android 12 fork**, so they can
run a normal sideloaded Android APK. Rokid's *official* path is the **CXR SDK**
(`CXR-S` on-glasses + `CXR-M` on-phone) with a managed BLE/Wi-Fi data channel — but
that is gated behind developer registration / enterprise sales approval.

For a fast prototype we take **Path B**: a plain Android app on the glasses talking
to the phone over a **raw Wi-Fi WebSocket**. The phone is the **server** (easy to
show its IP, can be a hotspot so we don't depend on venue Wi-Fi); the glasses are
the **client** with auto-reconnect.

> The camera/mic/network code lives in isolated modules, so if a retail unit blocks
> raw camera access we can swap just the capture layer for the Rokid SDK later
> without touching networking or UI.

## Repository layout

```
rokid-project/
├── android-phone-app/     # Phone = WebSocket SERVER + AI (Kotlin)
│   └── app/src/main/java/com/rokiddemo/phone/
│       ├── App.kt                     # owns the server for the app lifetime
│       ├── MainActivity.kt            # status UI (IP / port / clients / log)
│       ├── ServerBus.kt               # server-thread → UI event bus
│       ├── net/MessageProtocol.kt     # shared JSON envelope
│       ├── net/WebSocketServerManager.kt
│       └── util/NetworkUtils.kt       # lists the phone's IP addresses
│
├── rokid-app/             # Glasses = WebSocket CLIENT + capture + UI (Kotlin)
│   └── app/src/main/java/com/rokiddemo/glasses/
│       ├── MainActivity.kt            # connect UI + status panel
│       ├── ClientBus.kt               # client-thread → UI event bus
│       ├── net/MessageProtocol.kt     # mirror of phone's protocol
│       └── net/WebSocketClientManager.kt  # OkHttp client + auto-reconnect
│
└── tools/
    └── test-client.html   # Browser-based fake "glasses" to test the phone alone
```

## Message protocol

Every message is a JSON object with a `type` and a `timestamp`.

| type                | direction        | phase |
|---------------------|------------------|-------|
| `HELLO`             | glasses → phone  | 1     |
| `HELLO_ACK`         | phone → glasses  | 1     |
| `IMAGE_FRAME`       | glasses → phone  | 2     |
| `DETECTION_RESULT`  | phone → glasses  | 3     |
| `SPEECH_RESULT`     | glasses → phone  | 4     |
| `ASSISTANT_RESPONSE`| phone → glasses  | 4/5   |

Phase 1 example:

```json
// glasses → phone
{ "type": "HELLO", "device": "ROKID", "timestamp": 1720000000000 }
// phone → glasses
{ "type": "HELLO_ACK", "device": "PHONE", "timestamp": 1720000000001 }
```

## Development phases

- **Phase 1 — Connectivity (this build):** HELLO / HELLO_ACK handshake + auto-reconnect. ✅
- **Phase 2 — Camera streaming:** glasses send throttled JPEG frames; phone shows them.
- **Phase 3 — Object detection:** phone runs detection, returns `DETECTION_RESULT`; glasses render the panel.
- **Phase 4 — Speech:** push-to-talk → on-glasses `SpeechRecognizer` → text → phone command processor → response.
- **Phase 5 — Vision + voice combined:** "What am I looking at?" answered from the current detections.

---

# Phase 1 — Build, deploy, test

**Goal:** glasses connect to the phone and exchange a HELLO handshake, with a
visible `PHONE CONNECTED` status and auto-reconnect.

### 0. Prerequisites
- Android Studio (Hedgehog or newer). It will download the matching Gradle/AGP on first open.
- USB debugging enabled on both the phone and the Rokid Glasses (Developer options → USB debugging).
- `adb` on your PATH (bundled with Android Studio: `platform-tools`).

> **Gradle wrapper note:** these projects ship the wrapper *config*
> (`gradle/wrapper/gradle-wrapper.properties`) but **not** the binary
> `gradle-wrapper.jar` / `gradlew` scripts. **Open each project in Android Studio
> once** — the first sync generates them automatically. Only after that will the
> `./gradlew` / `gradlew.bat` commands below work from a terminal. (Alternatively,
> if you have a system Gradle: `gradle wrapper` inside each project folder.)

### Building WITHOUT Android Studio (portable toolchain — already set up here)
A self-contained toolchain lives in `toolchain/` (portable **JDK 17 + Android SDK +
Gradle 8.7**, no admin, nothing added to the system PATH). Use the helper script —
it sets all env vars, builds, copies both APKs to `apk/`, and can install over adb:

```powershell
.\tools\build.ps1                    # build both -> apk\rokid-phone-debug.apk, apk\rokid-glasses-debug.apk
.\tools\build.ps1 -Devices           # list connected devices (adb)
.\tools\build.ps1 -InstallPhone      # build + install the phone app
.\tools\build.ps1 -InstallGlasses    # build + install the glasses app
.\tools\build.ps1 -InstallGlasses -Serial <serial>   # when both are plugged in
```
The prebuilt APKs are already in `apk/`. adb lives at
`toolchain\android-sdk\platform-tools\adb.exe` (or run `.\tools\build.ps1 -Adb`).

> To recreate this toolchain on another machine, see `toolchain/` — or just install
> Android Studio, which bundles the same JDK/SDK/Gradle.

### 1. Build & install the PHONE app (the server)
Portable toolchain (this machine):
```powershell
.\tools\build.ps1 -InstallPhone
```
Or open `android-phone-app/` in Android Studio → sync → Run. Or with a wrapper:
```bash
cd android-phone-app && ./gradlew installDebug
```
Launch **Rokid Phone Server**. You'll see status `LISTENING`, `Port: 8080`, and a
list of IP addresses. Note the right one:
- **Phone hotspot on** → use the `ap0` / `swlan0` address (often `192.168.43.1`).
- **Both on the same Wi-Fi** → use the `wlan0` address (e.g. `192.168.1.x`).

### 2. Smoke-test the server WITHOUT the glasses (recommended first)
On a laptop connected to the same network (or the phone's hotspot), open
`tools/test-client.html` in a browser. Set the URL to `ws://<phone-ip>:8080` and
click **Connect**.
- Test client shows: `← {"type":"HELLO_ACK",...}`
- Phone log shows: `Client connected…`, `← {"type":"HELLO"...}`, `→ {"type":"HELLO_ACK"...}`

If that works, the server is good and any connection problem later is network/IP.

### 3. Build & install the GLASSES app
Plug in the Rokid Glasses. Confirm ADB sees them:
```bash
adb devices
```
Then:
```bash
cd rokid-app
./gradlew installDebug        # Windows: gradlew.bat installDebug
```
> If both the phone and glasses are plugged in, target the glasses explicitly:
> `adb -s <glasses-serial> install -r app/build/outputs/apk/debug/app-debug.apk`

Launch **Rokid Glasses Demo** on the glasses.

### 4. Connect
1. Make sure the glasses are on the **same Wi-Fi as the phone**, or connected to the
   **phone's hotspot**.
2. In the glasses app, enter the phone's IP + port `8080`, tap **Connect**.
3. Expected:
   - Glasses status turns green: **PHONE CONNECTED**
   - Glasses log: `→ HELLO` then `← HELLO_ACK`
   - Phone: `Rokid clients: 1`, log shows the HELLO / HELLO_ACK exchange.

### 5. Verify auto-reconnect
Toggle the phone's Wi-Fi/hotspot off and on, or force-stop & relaunch the phone app.
The glasses should drop to `CONNECTING…` and return to `PHONE CONNECTED` within a
few seconds on their own.

### Troubleshooting
- **`WebSocket error` / never connects:** wrong IP, or phone & glasses on different
  networks. Re-check with the HTML test client first. Use the phone-hotspot path to
  remove venue-Wi-Fi client isolation (many public APs block device-to-device traffic).
- **Server didn't start (`SERVER FAILED`):** port 8080 busy — change `PORT` in
  `App.kt` (and the port field on the glasses).
- **ADB can't see the glasses:** enable Developer options + USB debugging on YodaOS;
  accept the RSA prompt (it may appear inside the glasses display).

When Phase 1 is confirmed working, we move to **Phase 2 (camera streaming).**
