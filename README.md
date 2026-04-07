# GameSpace

GameSpace is a simple yet powerful app for AOSP that provides an enhanced gaming experience by leveraging existing Android framework APIs. Focused on minimalism it provides a non-intrusive overlay for monitoring and managing system state during gameplay.

## Features

- **Real-time Performance Monitoring**:
    - **FPS Counter**: Uses `WindowManager` task-specific FPS callbacks for accurate, real-time frame rate tracking.
    - **System Stats**: Monitors CPU usage, CPU temperature, and GPU headroom using `HardwarePropertiesManager` and `SystemHealthManager`.
- **Intelligent Game Management**:
    - **Auto-detection**: Automatically identifies games using the standard `CATEGORY_GAME` application metadata.
    - **Custom Library**: Allows manual addition of applications to the GameSpace library.
- ** Overlay UI**:
    - **Quick Toggles**: Easy access to DND, performance meters, and notification settings.
    - **App Sidebar**: Quickly launch apps from games.
- **Notification & Call Handling**:
    - **Compact Notifications**: Minimalist notification overlays that don't block gameplay.
    - **Call Management**: Mode-based handling (Answer/Reject/Off) using `TelecomManager` to ensure uninterrupted gaming.

## Building and Integration

GameSpace is designed to be built with aosp and work as is.

If you want a settings in apps as well pick: https://github.com/Oneplus-Nord5-development/android_packages_apps_Settings/commit/01665664b484b1b10e1b2a34a8fb8e60871e7278
Or if you want gamespace just in launcher build `GameSpace` as product package in your makefiles.

## License

This project is licensed under the Apache License 2.0.
