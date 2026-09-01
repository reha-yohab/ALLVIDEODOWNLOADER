# Running the app

Written for a Linux machine with nothing Android-related installed yet. Budget about an hour,
most of it downloads.

Steps 1 to 4 are needed either way. After that, jump to step 5 for an emulator or to
"Installing on a physical phone" near the end if you want it on real hardware.

> **If you only want the app on your phone and would rather not install any of this**, read
> `GET_AN_APK.md` instead. A free GitHub account builds the APK in the cloud and gives you a link
> to download it straight onto the phone. This file is the route for actually developing the app —
> editing code, using a debugger, watching Logcat.

## 1. Install Android Studio

Get the Linux tarball from <https://developer.android.com/studio>, then unpack and launch it:

```bash
cd ~/Downloads
tar -xzf android-studio-*-linux.tar.gz
sudo mv android-studio /opt/
/opt/android-studio/bin/studio.sh
```

The snap package (`sudo snap install android-studio --classic`) also works and puts Studio in your
app launcher, but the tarball is easier to update by hand.

You do **not** need to install a JDK separately. Android Studio bundles its own JetBrains Runtime
and uses it for Gradle, which is why this project only specifies a Java *target* of 17 rather than
requiring a particular JDK on your machine.

On first launch choose the **Standard** setup type and accept the licence agreements. The wizard
downloads the SDK, platform tools, and an emulator image — roughly 6–8 GB, so make sure you have
about 20 GB free before you start.

## 2. Turn on hardware acceleration (the big Linux gotcha)

Without KVM the emulator runs in pure software emulation and is slow enough to be unusable — boot
alone can take twenty minutes. Check whether your machine supports it and whether you have access:

```bash
sudo apt install cpu-checker qemu-kvm
kvm-ok
```

If `kvm-ok` reports that acceleration can be used but the emulator later complains about
`/dev/kvm` permissions, add yourself to the `kvm` group and log out and back in:

```bash
sudo adduser "$USER" kvm
```

If `kvm-ok` says virtualisation is disabled, it needs enabling in your BIOS/UEFI (usually called
Intel VT-x or AMD-V).

## 3. Confirm the right SDK pieces are installed

The project builds against API 35, which the Standard wizard may or may not have fetched. Open
**Settings → Languages & Frameworks → Android SDK**, then on the *SDK Platforms* tab tick
**Android 15 (API 35)**, and on the *SDK Tools* tab make sure **Android SDK Build-Tools 35**,
**Android SDK Platform-Tools**, and **Android Emulator** are all present.

## 4. Open the project

Choose **Open** from the welcome screen and select:

```
/home/nawshad/Desktop/APP/AllVideoDownloader
```

Pick that folder specifically, not the `APP` folder above it — Studio needs to see
`settings.gradle.kts` at the top level or it will treat the directory as plain files. Approve the
"trust this project" prompt.

Gradle sync then starts on its own and will take several minutes the first time while it downloads
Gradle 8.9 and the AndroidX libraries. It needs a working internet connection. The missing
`gradle/wrapper/gradle-wrapper.jar` is not a problem here: Studio reads the distribution URL out of
`gradle-wrapper.properties` and fetches Gradle itself rather than shelling out to `./gradlew`.

Expect one harmless deprecation warning about `resourceConfigurations`. Anything red is worth
reading; anything yellow you can ignore for now.

## 5. Create the emulator — pick a Google Play image

Open **Device Manager** (the phone icon in the right sidebar), choose **Create Virtual Device**,
and pick **Pixel 7**. For the system image, choose an **API 35 image with the Google Play Store**
rather than a plain AOSP one.

That choice matters more than it looks. This app deliberately ships no video player and hands
playback to whatever the user has installed. A bare AOSP emulator has no video player at all, so
tapping a downloaded video would open an empty chooser and you would see "No app on this device can
play this video" — which looks like a bug in the app but is really an empty emulator. A Google Play
image lets you install VLC or MX Player from the Play Store and test the handoff properly.

While you are in the configuration screen, click **Show Advanced Settings** and raise **Internal
Storage** to 8 GB. Video files fill the default allocation quickly.

## 6. Run it

Pick your emulator from the device dropdown in the toolbar, leave the run configuration on **app**,
and press the green Run arrow (or Shift+F10). Studio builds the debug variant, boots the emulator,
and installs the app.

The debug build installs as `com.allvideodownloader.app.debug`, so it can live side by side with a
release build later without conflict.

## 7. What to check once it launches

A disclaimer dialog appears on first launch and has to be acknowledged — that is the Play
compliance notice, and it only shows once. Tap Agree.

For a successful download, paste this public-domain sample into the text box and tap
**Search & Download**:

```
https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
```

You should see a progress card appear on the first screen and a system download notification. When
the transfer finishes, the file is copied into shared storage in the background, so it shows up on
the **Saved videos** tab a second or two later rather than instantly. Tapping it opens the system
chooser; install VLC from the Play Store first if the chooser is empty.

To confirm the file really landed in public storage rather than the app's private directory:

```bash
~/Android/Sdk/platform-tools/adb shell ls -l /sdcard/Movies/AllVideoDownloader
```

For the refusal paths, paste a YouTube link and then any `.m3u8` link. Both should produce a clear
explanatory dialog with no download attempt. These are the cases Play review will try, and
`LinkValidatorTest` covers the same ground.

## 8. Command-line builds, if you want them

Studio's Run button is enough for day-to-day work, but `./gradlew` will fail until the wrapper JAR
exists, because that file is not checked into the project. Install Gradle 8.9 once and let it
generate the wrapper for you:

```bash
sudo apt install gradle          # or use SDKMAN for an exact version
cd /home/nawshad/Desktop/APP/AllVideoDownloader
gradle wrapper --gradle-version 8.9
```

After that the usual commands work:

```bash
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:bundleRelease     # AAB for Play, needs the signing config from README.md
./gradlew :app:connectedAndroidTest   # LinkValidator compliance tests, emulator must be running
```

## Installing on a physical phone

Your phone needs to be on **Android 10 or newer**. The project sets `minSdk 29` deliberately —
Android 10 is the release that let the app write to shared storage without asking for a storage
permission, which is what keeps the Play submission clean. Anything older will refuse to install.

### Enable developer options

On the phone, open **Settings → About phone** and tap **Build number** seven times until it says
you are now a developer. Then go to **Settings → System → Developer options** and switch on
**USB debugging**.

### Plug it in and press Run

Connect the phone by USB. It will show an "Allow USB debugging?" prompt with your computer's key
fingerprint — tick **Always allow from this computer** and accept. The phone then appears in the
device dropdown in Android Studio's toolbar, and Run works exactly as it does for an emulator.

If the phone does not appear, check what `adb` sees:

```bash
~/Android/Sdk/platform-tools/adb devices
```

A device listed as `unauthorized` means the on-phone prompt was dismissed — unplug, replug, and
accept it. No device listed at all is usually missing udev rules on Linux, which this fixes:

```bash
sudo apt install android-sdk-platform-tools-common
```

Then unplug and replug the phone.

### Or debug over Wi-Fi, no cable

Android 11 and newer support wireless debugging, which is much nicer for testing this particular
app since you will want to walk around with it and watch downloads behave on mobile data. In
**Developer options → Wireless debugging**, choose **Pair device with pairing code**, then in
Android Studio open Device Manager and use **Pair using Wi-Fi**. The command-line equivalent is:

```bash
adb pair 192.168.1.50:37105     # address and port shown on the phone
adb connect 192.168.1.50:5555   # address and port from the Wireless debugging screen
```

### Sideloading an APK instead

If you would rather not connect the phone to your computer at all, build the APK and move it over
by whatever means you like:

```bash
./gradlew :app:assembleDebug
# result: app/build/outputs/apk/debug/app-debug.apk
```

Copy that file to the phone over USB file transfer, Bluetooth, or cloud storage, then open it with
a file manager. Android will ask you to allow that particular file manager to install unknown apps;
that permission is per-app and you can revoke it afterwards. With a cable attached, `adb` does the
same thing in one step:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Moving to a signed release build

The debug APK is fine for personal use, but there are two real reasons to build a release APK
before you rely on it or submit anything to Play. It is unminified, so it is noticeably larger and
slower to start. More importantly, the release build runs R8, and R8 is the single most common
source of bugs that appear in release but never in debug. This project's `proguard-rules.pro`
keeps the `work` package because WorkManager instantiates workers reflectively, so the thing to
verify in a release build is that a finished download still gets published into the library.

Create an upload keystore once:

```bash
keytool -genkey -v -keystore ~/upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Back that file up somewhere safe along with its passwords. If you publish to Play and then lose the
key, you cannot ship an update under the same listing. Then add `keystore.properties` and the
`signingConfigs` block exactly as described in `README.md`, and build:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

One thing that catches people out: `bundleRelease` produces an `.aab`, and an AAB **cannot be
installed on a phone**. It exists only for uploading to Play, which converts it into per-device
APKs on the way down. For sideloading you always want `assembleRelease`.

Debug and release builds coexist happily here, because the debug variant carries a `.debug`
applicationId suffix. What will not work is installing two builds with the same applicationId
signed by different keys — Android rejects that with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the
fix is to uninstall the old copy first.

### What a phone tells you that an emulator cannot

Check that your real Gallery or Photos app shows the downloads under `Movies/AllVideoDownloader`.
That is the entire reason the app publishes through MediaStore rather than keeping files private,
and it is the part most likely to look wrong on an emulator with no gallery app worth the name.
Check too that the player you actually use appears in the chooser when you tap a video.

Try a download over mobile data rather than Wi-Fi. The app allows metered connections but refuses
roaming, so that path is worth exercising once.

Then try the awkward case: start a large download, put the app in the background, and lock the
screen. The system download notification should keep counting up, because transfers are handed to
the platform `DownloadManager` rather than run in-process. Now force-stop the app from Settings
while a download is still running, let it finish, and reopen the app. The video should appear in
the library anyway. That last part exercises the start-up reconciliation pass, which exists
precisely because the completion broadcast is lost when an app is force-stopped — and some OEM
builds, Xiaomi and Oppo especially, force-stop background apps aggressively enough that this is
not a hypothetical.

## Troubleshooting

If sync fails with a network or repository error, you are behind something blocking
`dl.google.com` or `repo.maven.apache.org`; Gradle needs both.

If the emulator boots to a black screen or crawls, acceleration is not working — revisit step 2.

If the build complains about a missing platform, the API 35 SDK did not install; revisit step 3.

If a download reports a storage error, the emulator's internal storage is full. Wipe the device
data from Device Manager, or recreate it with a larger allocation.

To watch what the app is actually doing, open the **Logcat** pane and filter on
`com.allvideodownloader.app`. The publish step logs through WorkManager, so filter on `WM-` if a
finished download is not appearing in the library.
