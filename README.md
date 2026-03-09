# TrueRoute

TrueRoute is an Android VPN client that combines `VpnService`, a SOCKS5 tunnel with UDP Associate support, Smart DNS routing, and per-app routing. The project is implemented in Kotlin with Jetpack Compose and integrates the upstream `HevSocks5Tunnel` native engine through Android `ndkBuild`.

## What is included

- Android Studio project scaffold with Gradle wrapper
- Compose configuration screen for SOCKS5 settings, DNS mode, routing mode, connect/disconnect, and session logs
- `VpnService` implementation for all-app and per-app routing
- Smart DNS behavior with provider-side DNS mapping or custom DNS through the proxy
- Encrypted local storage for proxy credentials via Android Keystore
- Vendored upstream sources in `third_party/hev-socks5-tunnel`

## Build notes

1. Open the project in Android Studio.
2. Ensure the Android SDK and NDK components required by AGP are installed.
3. Sync Gradle and run on an Android 9+ device or emulator.

## Caveats

- The repository vendors `HevSocks5Tunnel` and its dependencies directly under `third_party/` for reproducible native builds.
- Because this environment does not have a local Android SDK/JDK toolchain available, the project was assembled statically and not fully built inside the workspace.

