# All Video Downloader

An Android app that downloads a video from a **direct link the user pastes** and lists the saved
files so they can be played in whatever video player the user prefers.

Two screens, nothing else: paste a link and download, or browse what has been downloaded.

## How it works

```
paste link
   │
   ├─ LinkValidator      scheme + host + extension checks, blocks hosts whose ToS forbid downloading
   ├─ LinkProbe          HEAD request (falls back to a 1-byte ranged GET) for real name, size, type
   ├─ DownloadManager    system downloader → staged in Android/data/<pkg>/files/Movies/incoming
   ├─ DownloadCompleteReceiver → PublishDownloadWorker
   └─ MediaStorePublisher     copies into Movies/AllVideoDownloader, deletes the staged file
                              │
                              └─ Library tab reads it back from MediaStore and hands the
                                 content:// URI to an external player via ACTION_VIEW
```

Two design decisions worth knowing before you change anything:

**Downloads are staged, then published.** On Android 10 and newer, `DownloadManager` throws
`Unsupported path` if you point `setDestinationInExternalPublicDir` at `Movies/`. The only
permission-free destinations are the app's own external directory and public `Downloads`. So the
transfer lands in the app's directory and a `WorkManager` job copies it into
`Movies/AllVideoDownloader` through MediaStore. That copy is why the app needs no write
permission at all, and why finished files stay on the device after an uninstall.

`ACTION_DOWNLOAD_COMPLETE` is best-effort — it never arrives if the app is force-stopped while a
transfer is running. `DownloadViewModel` therefore asks `DownloadManagerSource.finishedIds()` on
every start and re-enqueues the publish job for anything still staged. Both paths go through
`PublishDownloadWorker.enqueue`, which is unique work keyed on the download ID, so a download is
never published twice.

**No bundled player.** `ACTION_VIEW` inside a chooser means the user picks their own player every
time, the temporary URI grant means the player needs no storage permission, and the app avoids
shipping media codecs.

## Build

Requires Android Studio Ladybug or newer (AGP 8.7, Kotlin 2.0.21, JDK 17).

```bash
# Open the folder in Android Studio and let it sync, or from the command line:
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:bundleRelease          # AAB for Play (needs signing config, see below)
./gradlew :app:connectedAndroidTest   # LinkValidator compliance tests, needs a device
```

The Gradle wrapper JAR is not checked in. Android Studio regenerates it on first sync; if you
build from the CLI first, run `gradle wrapper` once with a local Gradle 8.9 install.

`.github/workflows/build-apk.yml` builds a debug APK on GitHub Actions and attaches it to a
release, which is how to get an installable build without a local toolchain at all. It invokes
`gradle` rather than `./gradlew` for the same wrapper reason. See `GET_AN_APK.md`.

### Signing for release

Create `keystore.properties` in the project root (already git-ignored):

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

Then add to `app/build.gradle.kts` inside `android { }`:

```kotlin
val keystoreProps = java.util.Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

signingConfigs {
    create("release") {
        if (keystoreProps.isNotEmpty()) {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
}
// then in buildTypes.release:
//   signingConfig = signingConfigs.getByName("release")
```

## Play policy notes

The compliance story is deliberately narrow, because "download any video from anywhere" is the
single most common reason downloader apps get removed.

* `LinkValidator.BLOCKED_HOSTS` refuses YouTube, Facebook, Instagram, TikTok, X, Netflix, Prime
  Video, Hulu, Disney+, Vimeo, Dailymotion, Twitch and others whose terms of service prohibit
  third-party downloading. YouTube in particular is called out in Play's Misrepresentation
  policy and the YouTube API Terms. **Add hosts to that list, never remove them** — removing an
  entry is the fastest route to a suspension.
* Redirects are re-checked: if a link forwards to a blocked host, the download stops.
* Adaptive-streaming manifests (`.m3u8`, `.mpd`) are refused. Supporting them would mean
  reassembling a protected stream, which is both a policy risk and a technical rabbit hole.
* A first-run disclaimer requires the user to acknowledge that they only download content they
  own or have permission to save. It is stored in `Prefs.disclaimerAccepted`.
* No `MANAGE_EXTERNAL_STORAGE`, no `QUERY_ALL_PACKAGES`, no accessibility service, no
  `REQUEST_INSTALL_PACKAGES` — all of these draw extra scrutiny or a declaration form.
* No ads SDK, no analytics, no network calls other than the file the user asked for. That makes
  the Data Safety form almost empty; see `PLAY_STORE_LISTING.md`.

Read `PLAY_STORE_LISTING.md` before you submit — it has the store description, the Data Safety
answers, and the note to leave for the review team.

## Known limitations

These are deliberate, not oversights:

* **No streaming-site extraction.** By design. See above.
* **No HLS/DASH.** A `.m3u8` link is refused instead of saving an unplayable text file.
* **`minSdk` is 29.** Android 10 removed the need for a storage permission. Supporting Android 8
  and 9 would mean adding `WRITE_EXTERNAL_STORAGE`, a runtime permission request, and a
  non-MediaStore write path in `MediaStorePublisher` — roughly 100 extra lines for a few percent
  of devices. Bump `minSdk` down only if you are willing to add that branch.
* **The publish step copies rather than moves**, so a download briefly needs twice its size in
  free space. MediaStore does not allow moving a file into place without a permission.
* **Files from a previous install** need `READ_MEDIA_VIDEO` to appear in the Library, because
  MediaStore ownership is reset on uninstall. The Library asks once and works either way.
* **Play's target API level requirement rises every year.** If the Console rejects the bundle,
  raise `compileSdk`/`targetSdk` in `app/build.gradle.kts` and re-test the Library screen, since
  storage behaviour is what usually changes.

## Layout

```
app/src/main/java/com/allvideodownloader/app/
├── MainActivity.kt                 edge-to-edge host, handles shared links
├── data/
│   ├── LinkValidator.kt            scheme/host/extension policy gate
│   ├── LinkProbe.kt                HEAD + ranged-GET metadata probe
│   ├── DownloadManagerSource.kt    enqueue, poll progress, cancel
│   ├── MediaStorePublisher.kt      staged file → Movies/AllVideoDownloader
│   ├── VideoLibraryRepository.kt   query, delete, observe MediaStore
│   ├── ActiveDownloadStore.kt      persists our DownloadManager IDs
│   └── model/Models.kt
├── receiver/DownloadCompleteReceiver.kt
├── work/PublishDownloadWorker.kt
├── ui/
│   ├── AppRoot.kt                  scaffold, bottom nav, disclaimer
│   ├── DownloadViewModel.kt        validate → probe → enqueue
│   ├── LibraryViewModel.kt         list, delete, consent flow
│   ├── ThumbnailLoader.kt          MediaStore thumbnails, LRU cached
│   ├── screens/DownloadScreen.kt
│   ├── screens/LibraryScreen.kt
│   └── theme/Theme.kt
└── util/                           FileNames, Formatters, ExternalPlayer, Prefs, AppEvents
```
