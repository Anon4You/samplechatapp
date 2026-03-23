# SampleChatApp

Android AI chat app using on‑device LLM (RunAnywhere SDK).  
Offline after model download.

## Build in Termux (TermuxVoid required)

```bash
# Add TermuxVoid repository
curl -sL https://termuxvoid.github.io/repo/install.sh | bash

# Install dependencies
apt install android-sdk gradle openjdk-21

# Clone and build
git clone https://github.com/yourusername/SampleChatApp.git
cd SampleChatApp
gradle assembleDebug
```

APK: app/build/outputs/apk/debug/app-debug.apk

> Note: This project is a simple example of building an Android app natively in Termux using the TermuxVoid repository. It demonstrates a complete workflow from installing the SDK to compiling an APK – all from the command line.


