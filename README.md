# AMOLED Fix

A Flutter app with a native Android Foreground Service to display persistent overlay lines for fixing AMOLED screen defects.

## Prerequisites

- [Flutter SDK](https://flutter.dev/docs/get-started/install) installed and configured.
- [VS Code](https://code.visualstudio.com/) with the [Flutter extension](https://marketplace.visualstudio.com/items?itemName=Dart-Code.flutter) installed.
- An Android device or emulator (Android 8.0+ recommended).

## Setup & Running on VS Code

Since this repository contains the source code but might be missing some platform-specific build files, follow these steps to get it running:

1.  **Open the project in VS Code.**
    - Launch VS Code and open the folder containing this `README.md`.

2.  **Generate Build Files.**
    - Open a terminal in VS Code (`Terminal` -> `New Terminal`).
    - Run the following command to generate the necessary Android build files:
      ```bash
      flutter create .
      ```
    - **Important:** If asked to overwrite `lib/main.dart` or `android/app/src/main/AndroidManifest.xml`, choose **no** (or back up the provided files and restore them after). `flutter create .` usually respects existing source files.

3.  **Install Dependencies.**
    - Run:
      ```bash
      flutter pub get
      ```

4.  **Run the App.**
    - Connect your Android device or start an emulator.
    - Press `F5` or go to `Run` -> `Start Debugging`.
    - Select your device if prompted.

## Usage

1.  **Grant Permissions:** Upon first launch, tap "Grant Permission" to allow "Display over other apps". This is required for the overlay to appear outside the app.
2.  **Start Service:** Tap the power button icon to start the overlay service.
3.  **Add Lines:** Tap "Add Vertical Line" to add a black line.
4.  **Adjust:** Use the on-screen slider to adjust thickness. Use the floating control pad (which appears when service starts) to move the line left/right.

## Troubleshooting

-   **Permission Denied:** If the app says permission is denied even after granting it, try going to the App Info -> Advanced -> Display over other apps manually and toggle it off and on.
-   **Service Killed:** On some aggressive battery management systems (like Xiaomi/Samsung), you might need to set the app battery usage to "Unrestricted".
