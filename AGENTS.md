# AGENTS.md

Guidance for AI coding agents working on the **Network Marketing Planner** Android app.

## Project at a glance

- Android app: Kotlin + Jetpack Compose + Room, built with Gradle (wrapper pinned to Gradle 8.9).
- Requirements: JDK 17+ (21 is fine) and Android SDK 35 (`compileSdk`/`targetSdk` 35, `minSdk` 26).
- Application ID: `com.networkmarketing.planner`.
- All payout/compensation math lives under `app/src/main/java/com/networkmarketing/planner/domain/compensation` (`AmwayNaPy2027.kt`, `CompensationEngine.kt`, `LeadershipBonus.kt`). Change payout behavior only there.
- Persistence is Room (`data/local`) behind `PlannerRepository`.

## Build and test

Run from the repo root:

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (no emulator needed)
```

The unit tests cover the compensation engine and the LOS graph, so they are the fastest
high-signal check for changes to domain/engine logic. Prefer them over an emulator when a
change does not touch UI.

## Cursor Cloud specific instructions

### Environment setup

The Cloud Agent base image ships JDK 21 but no Android SDK. The SDK is installed by the
committed environment config, so a fresh agent can build out of the box:

- `.cursor/environment.json` runs `.cursor/install.sh` as the `install` step.
- `.cursor/install.sh` is idempotent and: installs the Android command-line tools,
  accepts SDK licenses, installs `platform-tools` + `platforms;android-35` +
  `build-tools;35.0.0` into `$HOME/android-sdk`, writes a gitignored `local.properties`
  (`sdk.dir=$HOME/android-sdk`), and warms the Gradle cache with `assembleDebug`.

If you need SDK tooling on `PATH` in an interactive shell:

```bash
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator
```

`local.properties` is gitignored — never commit it.

### Running the app on an emulator (caveat)

Hardware KVM acceleration faults in this nested-virt VM (`kvm_spurious_fault` on vCPU
creation), so the standard `-enable-kvm` emulator hangs at 0% CPU before the kernel boots.
Work around it by running the emulator in **software (TCG) mode**:

1. Grant KVM access is not required in TCG mode; instead disable acceleration.
2. Install emulator + a system image once:
   `sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"`
3. Create an AVD:
   `avdmanager create avd -n planner -k "system-images;android-35;google_apis;x86_64" -d pixel_6`
4. Launch headless in software mode (slow — cold boot ~10 min):
   `emulator -avd planner -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-accel -no-snapshot -qemu -machine accel=tcg`
5. Wait for `adb shell getprop sys.boot_completed` to return `1`, then
   `adb install -r app/build/outputs/apk/debug/app-debug.apk` and launch with
   `adb shell monkey -p com.networkmarketing.planner -c android.intent.category.LAUNCHER 1`.

Because it is headless and slow, drive the UI with `adb shell input tap/swipe` and capture
evidence with `adb exec-out screencap -p`. Expect occasional ANR dialogs from the slow
emulation; dismiss them with the "Wait" button rather than treating them as app bugs.

For most changes, the unit tests plus `assembleDebug` are sufficient verification; only spin
up the emulator when a change genuinely needs UI-level validation.
