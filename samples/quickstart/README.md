# ThunderID Android Quickstart

ThunderID Android Quickstart demonstrates the full authentication lifecycle using the `dev.thunderid:android` SDK on Android.

**Flow demonstrated:**
1. App opens → unauthenticated state (sign-in screen)
2. User initiates sign-in / sign-up → SDK starts app-native Flow Execution
3. User completes the flow
4. Sign-in → authenticated state with profile information, token debugging, and sign-out button.
   Sign-up creates the account but does not start a session, so it returns to the landing screen
   and the new credentials have to be used to sign in.
5. User taps Sign Out → session terminated, returns to sign-in screen

## Setup & Run

See [Try the Android Sample App](https://thunderid.dev/docs/v1.0.x/sdks/android/guides/try-the-sample-app)
in the Android SDK docs for prerequisites, configuration, attestation, passkeys, and run instructions.
