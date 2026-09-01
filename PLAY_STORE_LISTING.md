# Play Console submission pack

Everything below is drafted for this exact build. Replace bracketed fields before submitting.

## Store listing

**App name (30 char max)**

```
All Video Downloader
```

**Short description (80 char max)**

```
Download videos from direct links and play them in your favourite video player.
```

**Full description (4000 char max)**

```
All Video Downloader saves videos from direct download links to your phone, with a simple
two-screen design and nothing you don't need.

HOW IT WORKS
1. Copy the address of a video file.
2. Paste it into the app and tap Search & Download.
3. Open the Library tab and play it in any video player installed on your phone.

SIMPLE BY DESIGN
• One text box, one button. No sign-up, no account, no clutter.
• Paste from clipboard with a single tap, or share a link into the app from your browser.
• Live progress for every download, with the system notification you already know.
• Videos are saved to Movies/AllVideoDownloader so your gallery and file manager can see them.

PLAYS IN YOUR PLAYER
The app does not lock you into a built-in player. Tap a video and Android asks which of your
installed players should open it, so you keep the controls, subtitles, and gestures you are used
to.

LIGHT AND PRIVATE
• No ads.
• No account, no tracking, no analytics.
• No data collected — the only server the app talks to is the one hosting the file you asked for.
• Requests no all-files access and no unnecessary permissions.

WHAT IT SUPPORTS
Direct links to video files: MP4, MKV, WEBM, MOV, AVI, M4V, 3GP, TS and similar formats.

WHAT IT DOES NOT SUPPORT
This app cannot download from YouTube, Facebook, Instagram, TikTok, X, Netflix, Prime Video,
Hulu, Disney+, Vimeo, Dailymotion, Twitch or similar streaming services. Those services do not
permit third-party downloading, and the app blocks them on purpose. Live streaming playlists
(.m3u8 and .mpd) are also not supported.

Only download content you own or have permission to save. You are responsible for the links you
paste.
```

**Category:** Tools
**Tags:** Downloader, Video, File manager
**Contains ads:** No
**In-app purchases:** No

**Graphics you still need to produce**

| Asset | Requirement |
| --- | --- |
| App icon | 512 × 512 PNG, 32-bit, no transparency. Reuse `ic_launcher_foreground.xml` on the `#1B1B3A` background. |
| Feature graphic | 1024 × 500 PNG or JPEG, no transparency |
| Phone screenshots | 2–8 images, 16:9 or 9:16, min 1080 px on the short edge. Shoot the Download screen with a download in progress, and the Library screen with three or four videos. |

## Data safety form

Answer this way — it matches the code, and mismatches here are a common rejection reason.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A (no data collected) |
| Do you provide a way for users to request that their data is deleted? | N/A (no data collected) |
| Data collected — Files and docs | **No.** The app writes files to the user's device but sends nothing off-device. |
| Data collected — Device or other IDs | **No** |
| Data collected — App activity / App info and performance | **No.** No analytics or crash SDK is bundled. |

If you later add a crash reporter or ads, this form must change.

## Permissions declaration

| Permission | Justification |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Fetch the file at the URL the user supplies. |
| `READ_MEDIA_VIDEO` (Android 13+) | Core functionality: the Library screen lists the user's downloaded videos, including files saved before a reinstall. Video only; no photo or document access. |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) | Same purpose on Android 12 and older. |

No restricted or sensitive permissions are declared. In particular the app does **not** use
`MANAGE_EXTERNAL_STORAGE` (All files access), `QUERY_ALL_PACKAGES`,
`REQUEST_INSTALL_PACKAGES`, or an accessibility service, so no declaration form is required.

## Note for the review team

Paste this into *App content → App access* notes, or the review comments field:

```
This app downloads a file from a direct HTTP/HTTPS URL that the user types or pastes. It contains
no site scraping, no video extraction from streaming platforms, and no embedded browser.

Links from services that prohibit third-party downloading are blocked in code, including YouTube,
Facebook, Instagram, TikTok, X, Netflix, Prime Video, Hulu, Disney+, Vimeo, Dailymotion and
Twitch. The blocklist is in LinkValidator.BLOCKED_HOSTS and is applied both to the pasted URL and
to the final URL after any redirects. Adaptive streaming manifests (.m3u8, .mpd) are also refused.

On first launch the user must acknowledge a notice stating that they may only download content
they own or have permission to save.

To test, paste any direct link to a video file, for example a public domain sample such as
https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
The file is saved to Movies/AllVideoDownloader and appears on the Library tab, where tapping it
opens the system chooser so the user can play it in their own video player.

No account is required. The app collects no user data and contains no ads or analytics.
```

## Pre-submission checklist

- [ ] Privacy policy published at a public URL and entered in the Console (`PRIVACY_POLICY.md`)
- [ ] Release build signed with an upload key, uploaded as an AAB
- [ ] `targetSdk` meets the Console's current requirement
- [ ] Tested on Android 10, 13 and 15: download, cancel, publish, play, share, delete
- [ ] Tested a blocked host (paste a YouTube link — expect a clear refusal)
- [ ] Tested a `.m3u8` link — expect a clear refusal
- [ ] Tested with the Library permission denied — app still lists this install's downloads
- [ ] `./gradlew :app:connectedAndroidTest` passes (`LinkValidatorTest`)
- [ ] Content rating questionnaire completed
- [ ] App name does not imply support for any platform it blocks
