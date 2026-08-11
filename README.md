![ThunderID Android SDK](https://raw.githubusercontent.com/thunder-id/thunderid/refs/heads/main/docs/static/assets/images/readme/repo-banner-android-sdk.png)

Android SDK for ThunderID. Provides authentication and user management for native Android applications.

- [Quickstart](https://thunderid.dev/docs/next/getting-started/connect-your-application/android/)
- [API reference](https://thunderid.dev/docs/next/sdks/android/overview/)

## Installation

### Gradle

![GitHub release](https://img.shields.io/github/v/release/thunder-id/android-sdks)

Make sure your project's `settings.gradle.kts` includes JitPack:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

```kotlin
dependencies {
    implementation("com.github.thunder-id.android-sdks:android:<latest-release-tag>")
}
```

For Jetpack Compose UI components, also add:

```kotlin
dependencies {
    implementation("com.github.thunder-id.android-sdks:compose:<latest-release-tag>")
}
```

> [!NOTE]
> Replace `<latest-release-tag>` with the [latest release tag](https://github.com/thunder-id/android-sdks/releases) of the `android-sdks` repository.

## License

This project is licensed under the [Apache License 2.0](https://github.com/thunder-id/thunderid/blob/main/LICENSE)
