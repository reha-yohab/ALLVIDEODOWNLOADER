# Getting an APK without installing anything

This is the route to take if you don't want an IDE, a JDK, the Android SDK, or Gradle on your
machine. A free GitHub account builds the app for you on their machines and hands you back an
`.apk` file you can download straight onto your phone.

Everything below happens in a web browser plus a couple of terminal commands. Nothing Android-related
gets installed on your computer — no JDK, no SDK, no Gradle, no IDE. Expect about ten minutes of
setup and five minutes of waiting for the first build.

The only local tool involved is git, which most Linux installs already have. There's a
browser-only path in step 3 if you'd rather skip even that.

The project already contains the piece that makes this work: `.github/workflows/build-apk.yml`.
That file tells GitHub which Java version, which Android SDK, and which Gradle version to use, and
where to put the finished APK. You don't have to edit it.

## 1. Get a GitHub account

<https://github.com/signup>. Free tier is enough — builds are unlimited on public repositories.

## 2. Create an empty repository

Go to <https://github.com/new>. Give it a name like `all-video-downloader`. Leave "Add a README"
unchecked and don't add a `.gitignore` or licence — the upload works more cleanly into a genuinely
empty repository.

**Choose Public.** This matters for a practical reason rather than an ideological one: a release
file on a public repository has a plain download link that your phone's browser can open without
signing in. On a private repository, downloading requires being logged into GitHub on the phone,
which is fiddly. If you'd rather not publish the source, pick Private and plan to download the APK
on your computer and transfer it to the phone instead.

Click **Create repository**.

## 3. Push the project

The repository is already prepared: the folder is a git repo, on a branch called `main`, with all
45 files committed in one commit. Nothing is left to stage. Two commands send it to GitHub —
substitute your own username and repository name:

```bash
cd /home/nawshad/Desktop/APP/AllVideoDownloader
git remote add origin https://github.com/YOUR-NAME/all-video-downloader.git
git push -u origin main
```

If `git --version` says the command isn't found, install it with `sudo apt install git`. That's a
few megabytes rather than the several gigabytes an IDE would cost.

### Authenticating the push

GitHub stopped accepting account passwords over HTTPS, so `git push` needs a token in place of one.
Go to <https://github.com/settings/tokens>, choose **Tokens (classic) → Generate new token**, and
tick **two** scopes:

* `repo` — to push the code at all.
* `workflow` — **easy to miss, and the push fails without it.** GitHub specifically refuses any push
  that creates or edits a file under `.github/workflows/`, which is exactly what this project does.
  The error reads `refusing to allow a Personal Access Token to create or update workflow`.

Copy the token immediately; GitHub won't show it again. When `git push` prompts you, give your
GitHub username and paste the token as the password. To avoid pasting it on every push:

```bash
git config --global credential.helper store
```

That writes the token in plain text to `~/.git-credentials`, which is fine for a personal machine
and less so on a shared one.

SSH keys are the other option and have no equivalent scope trap — `ssh-keygen -t ed25519`, paste
`~/.ssh/id_ed25519.pub` into <https://github.com/settings/keys>, then use the `git@github.com:...`
remote URL instead of the `https://` one.

### About the commit author

The commit is authored as `nawshad <nawshad@users.noreply.github.com>`, set only inside this
repository so nothing on your machine's global git config was touched. GitHub will show the commit
but won't link it to your profile, because that email isn't registered to your account. If you care
about the attribution, fix it before pushing:

```bash
git config user.email "the-email-on-your-github-account"
git commit --amend --reset-author --no-edit
```

### Or upload in the browser instead

If you'd rather not deal with git at all, the empty repository page has an **uploading an existing
file** link. Select everything *inside* the `AllVideoDownloader` folder — not the folder itself, or
`settings.gradle.kts` ends up one level too deep and the build won't find the project — and drag it
onto the upload area.

The catch with this route is the hidden `.github` folder. Dot-folders are hidden on Linux, so your
file manager may not show it and some browsers skip it during a folder upload. Press `Ctrl+H` first
to reveal it, and afterwards check the repository listing for a `.github` entry. If it's missing,
click **Add file → Create new file**, type `.github/workflows/build-apk.yml` as the filename (the
slashes create the folders), and paste in the contents of your local copy. Without that file
nothing builds.

## 4. Watch it build

The push starts the build automatically. Click the **Actions** tab at the top of the repository and
you'll see a run called "Build APK" with a yellow dot. Click into it to watch the steps.

The first run takes roughly four to eight minutes, most of it downloading the Android libraries.
Later runs are faster because the dependencies are cached.

A green tick means it worked. A red X means it didn't — skip to "If the build fails" below.

## 5. Download the APK onto your phone

When the run goes green, go to the **Releases** section on the repository's front page (right-hand
sidebar) — or straight to `https://github.com/YOUR-NAME/all-video-downloader/releases`. The newest
release has a file attached called something like `AllVideoDownloader-1.apk`.

On your phone, open the browser, go to that releases page, and tap the `.apk` file. Chrome will
warn you that this type of file can harm your device; tap **Download anyway**. It's your own build,
compiled from the source in your own repository.

The APK is also on the Actions run page under "Artifacts", but that arrives as a zip and needs you
to be signed in, so the release link is the easier one.

## 6. Install it

Your phone needs **Android 10 or newer**. The app sets `minSdk 29` on purpose — Android 10 is the
version that let it write videos to shared storage without asking for a storage permission, which
is what keeps it clean for Play review. An older phone will refuse to install it.

Tap the downloaded file, from the browser's downloads list or a file manager. Android will say
something like "For your security, your phone currently isn't allowed to install unknown apps from
this source." Tap **Settings**, turn the permission on for that one app, and come back. The
permission is per-app and you can switch it off again afterwards.

Play Protect may then offer to scan the app or warn that it doesn't recognise the developer. That's
expected for anything not installed from the Play Store; choose **Install anyway**.

It installs as "All Video Downloader" with a `.debug` suffix on its package name, so it won't clash
if you ever install a release build alongside it.

## 7. First run

A disclaimer appears once on first launch — that's the Play compliance notice. Tap Agree.

To check it works end to end, paste this public-domain sample and tap **Search & Download**:

```
https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
```

A progress card appears on the first screen alongside a system download notification. When the
transfer finishes, the file is copied into shared storage in the background, so it turns up on the
**Saved videos** tab a second or two later rather than instantly. Tapping it opens your phone's app
chooser — pick VLC, MX Player, or whatever you use. The app ships no player of its own by design,
so if the chooser is empty it means the phone has no video player installed.

Your Gallery or Photos app should also show the file under `Movies/AllVideoDownloader`.

Then try the two refusals: paste a YouTube link, and paste any `.m3u8` link. Both should give a
clear explanation and no download. Those are the paths Play review will test.

## If the build fails

This is worth knowing up front: **the project has never been compiled anywhere**, because this
machine had no Android toolchain to compile it with. Everything was verified by reading it, not by
building it. So the first CI run is genuinely the first compile, and a mistake showing up there
would be unsurprising rather than alarming.

The good news is that the failure is easy to read. In the Actions tab, open the failed run, click
the red step, and expand it. Kotlin errors look like
`e: file:///.../LibraryScreen.kt:42:17 unresolved reference: foo`.

Copy the red lines and paste them to me and I'll fix the source. Then commit and push again and the
build reruns by itself:

```bash
git add -A && git commit -m "fix build error" && git push
```

If you took the browser route instead, edit the file through GitHub's web editor and committing
there triggers the same rerun.

Two failures that are about the setup rather than the code:

If the "Set up the Android SDK" step fails, GitHub's runner image has changed. Deleting that whole
step usually works, because the runners come with an Android SDK preinstalled and Gradle will use it.

If "Build the debug APK" fails with `gradle: command not found`, the fallback installer in the
"Confirm Gradle is available" step didn't take effect. That step downloads Gradle 8.9 directly, so
check its log for a network error.

## What you get, and what you don't

This is a **debug** build. It installs and runs exactly like a normal app, and for your own use
it's fine. Two differences from a proper release build are worth knowing.

It's larger and slower to start, because none of the code shrinking runs. More importantly, it
doesn't run R8, and R8 is the most common source of bugs that appear only in release builds. This
project's `proguard-rules.pro` keeps the `work` package because WorkManager creates its workers
reflectively — so if you ever move to a release build, the specific thing to re-test is that a
finished download still shows up in the library.

A debug APK also can't go on the Play Store. That needs a signed release build, which needs a
keystore, which needs a local toolchain or extra CI setup. `RUNNING.md` covers the signing part if
you get that far.

## Other ways to the same place

If GitHub feels like too much, the alternatives are:

Ask someone who already has Android Studio to open the folder and press Run with your phone
plugged in. That's a two-minute job for them.

Use another free CI service — Codemagic and Bitrise both build Android projects and both have free
tiers. Same idea as above, different dashboard.

Or install Android Studio after all. It's a big download but it removes every intermediate step,
and it's the only route that gives you a fast edit-and-rerun loop. `RUNNING.md` walks through it.
