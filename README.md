# ZenDownloader-reMaster

A desktop downloader built with Kotlin and Jetpack Compose, with updated WCO player handling, configurable retries, and optional HiAnime recovery.

**[Download the latest release](https://github.com/Amaster15/ZenDownloader-reMaster/releases/latest)**

## Downloads and requirements

Release packages are available for **Windows x64** and **Linux x64**. Both include Java 21; no separate Java installation is needed when using the ZIP packages.

- Install Chrome or Chromium separately.
- FFmpeg is required for HLS video processing and HiAnime downloads. The app can download FFmpeg and FFplay when needed.
- macOS and ARM packages are not currently provided by this fork.
- Linux startup has been tested. The Windows package is cross-built and checked for required files; Windows GUI testing is still pending.

Download the ZIP for your operating system from this repository's Releases page. The standalone JAR is intended for updates or users who already have Java 21. `SHA256SUMS.txt` contains checksums for the release downloads.

### Windows

1. Extract the **entire** Windows ZIP.
2. Open the extracted folder and double-click `run.bat`.
3. Keep `app`, `runtime`, and `launcher.jar` together.

### Linux

Extract the entire Linux ZIP, open a terminal in the extracted folder, and run:

```sh
chmod +x run.sh
./run.sh
```

Keep `app`, `runtime`, and `launcher.jar` together. Use the launcher scripts so the app can apply updates from this fork.

## Downloading episodes

1. Open the app and let the initial database assets finish downloading.
2. Choose your save folder and preferred quality in Settings.
3. Paste a WCO series or episode URL, then select the episodes to download.
4. Follow progress in Downloads and the console.

The downloader waits for the player popup's countdown, allows two extra seconds before closing it, waits for the player to load, and checks for a visible Play button before extracting links. Each episode's countdown updates on one console line.

**“Player popup closed” refers to the timed overlay, not the video player.** A visible Play button does not guarantee that the video server will accept a download.

## Retries and HiAnime recovery

The default page-link retry settings are **30 retries after the initial attempt**, with **30 seconds between retries**. These settings can be adjusted in Settings. Failed video connections can also trigger a fresh page request to obtain another link.

The console distinguishes player-loading failures, missing links, and video-server connection errors. A refresh may show the popup again. Servers with expired HTTPS certificates can fail even when the app successfully finds a video link.

**HiAnime Fallback** is enabled by default and can be turned off in Settings:

- WCO completes its queue and configured retries first.
- Episodes that exhaust the supported WCO retry paths are collected for a separate HiAnime recovery pass.
- Recovery requires a unique matching show, episode number, and English dub/sub language. Ambiguous or unavailable matches are skipped.
- The current provider uses HiAnime's Ayame player. Movies and manually selected HLS streams are excluded.
- Recovered files use a separate `[HiAnime]` filename. Existing files are not overwritten.
- Stop clears pending recovery work.

Live tests completed all five Overgeared episodes from WCO at 720p and a separate HiAnime episode-2 download at 1080p with audio. Queue recovery tests passed; a complete live WCO-exhaustion-to-HiAnime run has not yet been verified.

## FFmpeg and optional players

If the automatic FFmpeg download fails, place the appropriate executables in your user database folder:

- Windows: `%USERPROFILE%\.zen_database\ffmpeg.exe` and `ffplay.exe`
- Linux: `~/.zen_database/ffmpeg` and `ffplay` (with executable permissions)

FFmpeg merges video/audio and converts supported streams to MP4. FFplay is used for playback. The optional mpv player can be selected under **Watch Online Video Player**; set its executable path if it is not available on your system PATH.

## Browser setup and troubleshooting

The app attempts to match ChromeDriver to the installed browser. If browser setup fails, set **Chrome Browser Path** and **Chrome Driver Path** in Settings to compatible browser and driver executables.

- **Player timeout:** check the console for the failed stage and adjust Timeout in Settings if needed.
- **Server full or link unavailable:** let the configured retries run; availability depends on the source site.
- **Expired certificate:** the app normally validates HTTPS certificates. A new-link retry may reach a different server. See [maintenance notes](MAINTENANCE.md) for the narrowly scoped, opt-in certificate exception.
- **First-run assets:** allow the asset download to complete before starting a series. See the [database guide](database/README.md) for the stored asset layout.

Settings, history, and cached data are stored in `.zen_database` under your user profile. Video files are stored in the save folder you choose in Settings.

For problems with this fork, include your operating system, app version, episode URL, and relevant console/error messages when reporting the issue on [this repository](https://github.com/Amaster15/ZenDownloader-reMaster).

## Building from source

Use JDK 21 and the included Gradle wrapper:

```sh
# Linux
bash gradlew :app:packageFatJar :bootstrap:packageBootstrapJar
```

```bat
rem Windows
gradlew.bat :app:packageFatJar :bootstrap:packageBootstrapJar
```

The app JAR is written to `app/build/custom-jars/`; the launcher is written to `bootstrap/build/libs/launcher.jar`.

[The release packaging script](scripts/package_release.py) builds Windows and Linux ZIPs from those JARs and extracted platform Java runtimes. Run it with `--help` for the required paths. See [maintenance notes](MAINTENANCE.md) for implementation details and test instructions.

## Support development

If you find ZenDownloader-reMaster useful, you can support its continued development:

**[Buy me a coffee](https://buymeacoffee.com/amaster15)**

Donations are optional. Thank you for supporting the project!

## Original project credit

ZenDownloader-reMaster is a fork of **[NobilityDeviant/ZenDownloader](https://github.com/NobilityDeviant/ZenDownloader)**. Credit for the original application goes to NobilityDeviant and its contributors. This fork maintains the changes described above independently. Original copyright notices and the [Apache 2.0 license](LICENSE) are retained.
