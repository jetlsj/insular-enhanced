# Insular Enhanced

[简体中文](README.md) | [English](README_EN.md)

An Android app isolation, cloning, and management tool built around Android Work Profile. Based on Insular, this fork focuses on clone, installation, and cross-profile launch issues found on newer Android versions and Xiaomi HyperOS. It also adds direct installation of external APK files into the managed profile.

> [!IMPORTANT]
> This is an unofficial, community-maintained fork. It is not affiliated with or endorsed by Insular, Island, F-Droid, or any device manufacturer. Only install external APK files from sources you trust.

## Upstream lineage

This repository has the following lineage:

1. This fork is based on the `dev-ci` branch of [secure-system/Insular](https://gitlab.com/secure-system/Insular).
2. Insular is a fully free-software fork of [oasisfeng/Island](https://github.com/oasisfeng/island).
3. Insular also credits [PeterCxy/Shelter](https://github.com/PeterCxy/Shelter) as an inspiration.

The upstream Insular documentation is available at [secure-system.gitlab.io/Insular](https://secure-system.gitlab.io/Insular/). The `com.oasisfeng.island.fdroid` package on F-Droid is the upstream build, not a build from this enhanced fork.

## Main upstream capabilities

- Isolate apps and their data with Android Work Profile
- Clone an app installed in the personal profile into the managed profile
- Freeze, unfreeze, hide, and launch apps on demand
- Apply VPN, USB, and other policies to selected apps
- Expose controlled Android DPC capabilities through the Open API

Android normally allows only one DPC-managed work profile for each parent user. This project is not a virtual machine and cannot install multiple APKs with the same package name but different versions inside one managed profile.

## Changes in this fork

### 1. Necessary permissions settings

The Settings screen now contains a Necessary permissions section with status and shortcuts for permissions used by the main workflows:

- Install unknown apps: checked before cloning or installing an external APK
- Notifications: prevents installation failures from becoming invisible when notifications are disabled
- Start other apps (HyperOS): opens the corresponding Xiaomi/HyperOS permission editor

Permission status is refreshed whenever the Settings screen is opened or resumed.

### 2. Clone installation reliability and feedback

- Checks Install unknown apps in the target profile before cloning, shows a clear message, and opens the permission page when needed
- Launches the installer, app store, and permission pages through a target-profile `PendingIntent` sent by the visible foreground UI, compatible with Android 14+ background activity launch restrictions
- Adds trace logs for installation session commits, confirmation screens, status callbacks, and failure reasons
- Shows a notification or Toast when the confirmation screen cannot be opened, installation fails, or notifications are unavailable
- Improves installation session and result handling for consecutive clone operations, reducing cases where Repair Insular is required before the next installation
- Refreshes the app list and launchable state after installation

### 3. First launch after installation

- Checks the new app's `FLAG_STOPPED` state to detect first-launch requests silently dropped by Android or vendor firmware
- Falls back from the regular `LauncherApps` path to a cross-profile action created in the target profile and sent by the foreground UI
- Adds a HyperOS Start other apps settings shortcut and clearer guidance
- Refreshes the app list in stages after a successful first launch, avoiding the need to manually launch the app once before Insular can open it

### 4. Install an external APK directly into the managed profile

The main overflow menu now contains Install APK to Island:

1. Check Install unknown apps inside the managed profile.
2. Open a system document picker available inside that profile.
3. Pass the selected APK to the bundled installer and create the installation session inside the managed profile.
4. Show the system confirmation UI and record the final result.

The APK does not need to be installed in the personal profile first. On HyperOS, the implementation pins a real DocumentsUI activity available in the managed profile instead of relying on an implicit file-picker Intent that the firmware may rewrite. It also fixes an Android 16 issue where an empty `splitNames` array for a standalone APK could be mistaken for a split APK.

Only a single, complete `.apk` file is currently supported. `.apks`, `.xapk`, and other split APK collections are not supported. If the same package is already installed in the managed profile, Android still enforces normal signature and version checks. This feature does not bypass platform security rules.

## Compatibility and verification

The current changes have been verified with:

- A successful `completeFdroidDebug` build
- A real Xiaomi HyperOS device running Android 16 (API 36)
- Consecutive cloning, visible installation results, and cross-profile first launch
- Selecting a standalone external APK and receiving `INSTALL_SUCCEEDED` for the managed-profile user
- Removing the test app from the managed profile without affecting its personal-profile installation

Vendors may customize Work Profile, background launch, and permission behavior. When reporting a problem, include the Android version, ROM version, device vendor, and sanitized logs described below.

## Build

JDK 17 and Android SDK 35 are recommended. Use the Gradle Wrapper included in this repository and clone the submodules:

```bash
git clone --recurse-submodules https://github.com/KaiLiDev/insular-enhanced.git
cd insular-enhanced
```

Windows:

```powershell
.\gradlew.bat :assembly:assembleCompleteFdroidDebug --console=plain
```

Linux/macOS:

```bash
./gradlew :assembly:assembleCompleteFdroidDebug --console=plain
```

Debug APK output:

```text
assembly/build/outputs/apk/completeFdroid/debug/assembly-complete-fdroid-debug.apk
```

Configure your own signing and run a full regression test before distributing a release build. Changing the signing certificate affects upgrade compatibility and may affect an existing managed profile; do not replace a production installation without understanding the consequences.

## Diagnostic logs

Clone, installation, and cross-profile launch operations include trace IDs. Collect the relevant logs with:

```bash
adb logcat -v time -s Island.AC Island.AIA Island.AISR Island.AIN Island.AppControl Island.Manager Island.XPAL Island.ExternalApk
```

Before posting a public issue, remove package names, file URIs, or any other information you do not want to disclose.

## License and attribution

The repository already contains the upstream [Apache License 2.0](LICENSE), and this fork continues under the same license rather than introducing a different one. Source or binary redistributions should retain `LICENSE`, upstream copyright and attribution information, and a description of the modifications. See [NOTICE](NOTICE) for lineage and modification notices.

Unless required by applicable law, the software is provided on an “AS IS” basis, without warranties or conditions of any kind.

## Contributing

Reproducible reports are welcome through GitHub Issues. Include the necessary system details and sanitized logs. Pull requests should describe the target Android/ROM versions and verification results.
