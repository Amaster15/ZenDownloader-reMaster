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

## App updates

The app quietly checks for updates at startup and shows a red indicator on Settings and the update control when a newer release is available. Open **Settings → App updates → Check for updates** to check releases from this repository. Download the update inside the app, then choose **Install and restart** after stopping active downloads. Downloads are staged and validated before installation; your settings and library remain in your user profile.

**Upgrading from 1.3.3 or earlier:** download and extract the full 1.4.0 ZIP once to get the new launcher. Keep the new `launcher.jar`, `app`, and `runtime` together, and launch using `run.bat` or `run.sh`. Updating only the JAR does not upgrade the old launcher.

## AnimeFreak search, watching, and downloads

Use **Watch → AnimeFreak** to search by title or paste a series URL, then choose **Play** or **Download** beside an episode. Search remembers display titles and URL names for the same series, so previously found English and romanized Japanese names remain searchable. Alternate URL names appear below results. This is a learned title index, not automatic translation. Watching uses the source quality and your selected player. Downloads from Watch stop if you leave the Watch tab; use the batch panel for longer queues.

Under **Downloads → Direct link**, paste an `animefreak.it.com` URL. Episode links start that episode; series links load an episode picker. You can also search **Browse → AnimeFreak** and choose Download. This currently supports the EchoVideo player. Chrome/Chromium loads the provider pages; FFmpeg downloads the HLS stream into an MP4 in your configured download folder.

Files use the series title and season/episode numbering. Existing files are skipped. Cancel stops the current download and removes its partial file; completed files remain. Direct downloads appear in the shared Queue and Completed lists, with activity in In progress. They remain separate from automatic WCO-to-HiAnime fallback. Browser verification or provider changes may interrupt loading; errors are reported in the activity log.

## Watching episodes

Use the **Watch** tab beside Browse to play episodes. In Watch, choose WCO, HiAnime, AnimeFreak, or KimCartoon, search for a series (or paste its series URL), then choose an episode and press **Play**. Playback uses the FFplay, mpv, or VLC player selected in Watch or Settings, without opening the site's advertising pages. The **Get VLC** button opens the official VideoLAN download page; VLC must be installed separately. HiAnime currently supports the Ayame player and lists available dub/sub episodes separately.

Watch reuses the WCO database for title search, stores episode lists for six hours, and shows when each list was saved. **Refresh episodes** checks the provider again; a failed refresh keeps the saved list visible. Media links are resolved afresh for each playback. WCO MP4 links are checked before the player starts and streamed through a private local connection with seeking support, using the downloader’s existing connection and certificate policy. WCO link retries follow the page retry settings and can be cancelled. HiAnime chunks are cleaned as they stream through a private local connection; closing the player releases that connection and its small memory cache.

New episode downloads use the series title followed by a season/episode code, for example `Overgeared - S01E01 English Dubbed (720p).mp4`. Season labels from the source page and explicit title/URL season information takes priority over the series season; numbered episodes without a season label use season 1. Movies, unnumbered specials, fractional episodes, and combined episode ranges retain their titles. Existing files are not renamed.

## Display scaling

**Settings → Interface size** applies immediately and is saved automatically. **Auto** respects system scaling and raises the effective interface scale to 125% on 1440p displays or 200% on 4K displays when the desktop is reporting a smaller scale. **System** uses the operating system's scale; the other percentages apply an additional size adjustment. Fonts, buttons, spacing, and thumbnails scale together. Newly opened dialogs are sized to fit the display, and automatic scaling follows the window when it moves between monitors.

## Navigation

The four main tabs are **Browse**, **Watch**, **Downloads**, and **My Library**. Browse opens on startup, with search and filters above compact series rows. Watch, Download, Favorite (star), and Follow (bookmark) actions are visible on each row; descriptions and genres expand under Details. Right-click actions remain available.

Downloads groups **In progress** (activity log), **Direct link**, **Queue**, and **Completed**. Direct link detects WCO, HiAnime, AnimeFreak, or KimCartoon from the URL. Episode links resolve immediately; series links offer episode selection. Browse offers provider choices for search. Queue includes WCO episodes waiting for player/link resolution and displays their current status. Use F12 to open Direct link. My Library groups **New episodes**, **Followed**, **Favorites**, and **History**. Its badge and the new-episode banner show unread episode discoveries. Following checks series on startup; favoriting only saves a series. **Settings** opens the existing full settings page; **Error Console** is a labeled top-level action with a new-error indicator. Player selection and Get VLC remain in Watch.

## Video database and favorites

**Browse** browses the cached WCO library with title, URL, genre, description, and category filters. It refreshes when series details are cached or favorites change. **Update catalog** checks the live category indexes for missing titles without replacing your saved series, favorites, or history. Progress, cancellation, and provider errors appear in the tab. Open a series to load its episodes, or right-click **Update series details and thumbnail** to refresh an existing entry. WCO may block live updates with Cloudflare; failed updates retain saved data.

Thumbnails load on demand, validate downloaded images before caching them, and use the existing upstream artwork archive when the original image is unavailable. Missing artwork uses a placeholder. Cached thumbnails work offline. Newly discovered titles resolve missing artwork from their series page as they become visible, with at most two page lookups at a time. Unavailable artwork is retried after a cooldown.

Right-click a series and select **Add To Favorite**. The **My Library → Favorites** section uses the same saved favorites as Database, History, and the download episode window. Removing a favorite updates both tabs immediately. A bookmark icon marks followed shows next to the favorite star in Database, Favorites, and History. Right-click a series in Database, Favorites, or History to **Follow Series** or **Unfollow Series**. Database and Favorites also offer **Watch** and **Download**. Keyboard shortcuts: **F8** for Browse and **F9** for Favorites.

## Following series

Use **Follow** in Watch or **Follow Series** in a series' download episode list. The **My Library → Followed** section replaces Recent Series and keeps your followed shows across restarts. On each launch, the app checks their episode lists in the background. The first successful check establishes a baseline; later additions appear in a banner and the follow list until you choose **Mark seen**. **Check now** runs another check. Failed checks preserve the previous episode list and display the error. Following a show does not start downloads automatically.

## Downloading episodes

1. Open the app and let the initial database assets finish downloading.
2. Choose your save folder and preferred quality in Settings.
3. Open Downloads → Direct link and paste a supported episode or series URL, or search a provider in Browse and choose Download.
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

<a href="https://buymeacoffee.com/amaster15">
  <img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&amp;emoji=&amp;slug=Amaster15&amp;button_colour=00ff00&amp;font_colour=000000&amp;font_family=Cookie&amp;outline_colour=000000&amp;coffee_colour=FFDD00" alt="Buy me a coffee" height="60">
</a>

Scan the QR code or click the button to support development:

<a href="https://buymeacoffee.com/amaster15">
  <img src="images/buy-me-a-coffee-qr.png" alt="Buy Me a Coffee QR code for Amaster15" width="240">
</a>

Donations are optional. Thank you for supporting the project!

## Original project credit

ZenDownloader-reMaster is a fork of **[NobilityDeviant/ZenDownloader](https://github.com/NobilityDeviant/ZenDownloader)**. Credit for the original application goes to NobilityDeviant and its contributors. This fork maintains the changes described above independently. Original copyright notices and the [Apache 2.0 license](LICENSE) are retained.

## KimCartoon

Choose **KimCartoon** in Watch to search by title or paste a series/episode URL. Watch uses your selected VLC, MPV, or FFplay player. Quality options are 360p, 720p, and 1080p (the closest available lower quality is used). English captions are loaded for watching when supplied by the host.

For batch downloads, use **Downloads → Direct link** or choose Download from **Browse → KimCartoon**. Choose SUB or DUB, select episodes and quality, and use the **Include subtitles when available** checkbox. Checked embeds supplied English captions as a selectable MP4 subtitle track; unchecked saves video/audio only. Filenames contain the series, SxxExx numbering, and SUB/DUB to avoid collisions. Existing files are kept; cancellation removes partial downloads. Direct-link batch work continues when switching main tabs; downloads appear in Queue/Completed and progress messages appear in In progress. It does not use automatic WCO fallback. Single downloads in Watch stop when leaving Watch.

You can favorite or follow KimCartoon series from Watch. Favorites appear in My Library under All/Unsorted; followed series use startup episode checks and new-episode notices. Followed/favorited series offer Watch and Download actions. The site advertises both language buttons even where a host file is missing; removed files are reported without switching languages. Temporary resolution errors use the configured page-link retry count and delay.
