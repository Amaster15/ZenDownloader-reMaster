# Maintenance notes

This copy retains the upstream source and history. Downloaded videos, user databases,
browser profiles, signed media links, local JDKs, and packaged app output are not included.

## Changes

- Match Chromium and ChromeDriver versions; report driver setup failures clearly.
- Correct launcher paths and FFmpeg/FFplay executable detection and extraction.
- Follow the embedded player's countdown and normal Close button, with progress messages.
- Wait two additional seconds before Close, two after player load, and require a visible,
  enabled Play button before extracting links.
- Retry missing links, full-capacity responses, and failed video connections with fresh links.
  Settings default to 30 retries after the initial attempt, 30 seconds apart.
- Check media sizes with HTTP status validation and range responses, and report TLS errors.

## Build

Use JDK 21 and the Gradle wrapper:

```sh
bash gradlew :app:packageFatJar
```

The app jar is written to `app/build/custom-jars/`. Browser-based tests need Chromium
and a matching ChromeDriver. Java regression tests and manual diagnostic probes live
in `tests/`; compile them against the fat jar. Browser probes should use an isolated
`-Duser.home` directory so they do not modify an active app's database.

## Optional local certificate exception

Normal certificate validation remains the default. Setting
`ZEN_ALLOW_EXPIRED_VIDEO_CERTIFICATE=1` enables an exception only for the exact
SHA-256 certificate pinned in `VideoCertificateException.kt`, on `neptun` and
`u<number>.cizgifilmlerizle.com`. Hostname and issuer validation remain in place.
This deliberately permits that expired leaf certificate; it does not include `e08`
or `e09`, accept arbitrary certificates, or change JVM-wide TLS defaults.
Set the variable to `0` or omit it to disable the exception. The local launcher used
during development enables it explicitly; that machine-specific launcher is not included.

## Verification

Regression checks cover launcher behavior, HTTP/range sizes, TLS errors and exception
scope, retry counts/delays/cancellation, and popup navigation with delayed Play-button
visibility. Live checks resolved video qualities and completed sample downloads;
site capacity and certificate problems can still prevent later attempts.

## 9anime fallback investigation (2026-10-28)

Branch: `feature/9anime-fallback`. No provider has been enabled.

The requested host `https://9anime-online.com/` returned HTTP 403 to a plain
HTTP request but rendered its catalog in the browser. The observed series page
`/watch/cyberpunk-edgerunners/` and season page
`/watch/cyberpunk-edgerunners/season/1/` displayed catalog/episode metadata.
Selecting a season or episode showed a “Browser Issue Detected” / missing-component
prompt with a “Fix It Now” link. The season DOM contained episode spans but no
video, source, or iframe elements. No component was installed and no playable
media source or downloadable stream was verified. This does not establish that
every title behaves identically, but is insufficient evidence for a working provider.

Next requirement: an episode URL that plays normally without installing a component.
Before enabling fallback, verify media extraction and match series, season, episode,
and dub/sub language explicitly to avoid downloading a different version.

Follow-up: the user identified the component prompt as advertising. Inspection of
the page's public `modal-register-ECxVzVQb.js` confirms a dismissible overlay with
an automatic countdown redirect and ad-block detection. This is not evidence that
a browser component is required for playback. An ad-blocked playback test remains
outstanding; no claim of site-wide download incompatibility is established.

## HiAnime fallback investigation (2026-10-28)

The user replaced the 9anime candidate with
`https://hianime.ms/watch-overgeared-episode-1-ba308`. The investigation branch is
now `feature/hianime-fallback`.

The episode page exposes separate SUB and DUB player choices (Ryu, Volt, Warp,
Ayame). Selecting DUB Ryu loaded a MegaPlay iframe. The actual video element
reported readyState 4, playing, and a duration of approximately 1420 seconds.
Its source is a temporary blob URL, not a downloadable file URL. Opening the
observed MegaPlay URL independently returned error 410, so the embedded playback
context matters. No direct media URL or successful download has been verified.

A provider implementation must resolve the underlying media playlist within the
normal episode flow, retain required request headers, and explicitly match the
chosen episode and dub/sub language before automatic fallback can be enabled.
The current WCO app has not been modified or rebuilt for this investigation.

### Other dubbed players tested

On the same Overgeared episode page, DUB Volt initially loaded VidNest
(`/anime/212888/1/dub`) but its frame subsequently navigated to an extension
advertisement; playback was not verified. DUB Warp loaded TryEmbed and showed
a 23:40 player with advancing playback position. DUB Ayame loaded VidNest
(`/animepahe/212888/1/dub`) and showed active playback (Pause control, 01:40 /
23:40). Both working alternatives also contained extension advertising.
No extension was installed. These are playback observations, not verified
download URLs or completed downloads. No provider has been enabled yet.

### Verified Ayame download

A standalone test resolved Ayame's normal VidNest source response for Overgeared
episode 1, English dub. It returned an HLS master playlist with 1080p/720p/360p
variants. The 1080p playlist contained 356 segments. Those segments carry an image
prefix followed by MPEG-TS data; stock FFmpeg misidentified them as PNG even with
nonstandard segment extensions enabled. The test located and validated TS packet
synchronization, removed the prefix, downloaded all segments, and remuxed to MP4.

Verified output: 485,508,928 bytes; duration 1420.139 seconds; H.264 1920x1080 video
and AAC audio. Beginning and ending five seconds decoded without errors. Media,
signed URLs, API response payloads, and temporary segments are excluded from Git.
This is a successful standalone download test, not yet an integrated app provider.
Warp's embed endpoint returned HTTP 403 to the standalone HTTP client, including
with the episode referrer; Warp downloading remains unverified.

### Integrated HiAnime recovery pass (2026-10-28)

`HIANIME_FALLBACK` is an enabled-by-default settings checkbox. Failed WCO episodes
are deduplicated by slug and held until the normal queue is empty and every active
WCO worker has finished. Recovery then runs sequentially through HiAnime Ayame.
Pending recovery prevents automatic session completion; Stop clears both queues.
Page-link/server-capacity retry settings remain unchanged (30 retries by default).

The provider requires a unique exact normalized series title, episode number,
and explicitly identified English dub/sub language. Missing or ambiguous matches
are skipped. Movies and manually chosen HLS downloads are excluded. It discovers
the public player's response configuration, resolves supported 576p/720p/1080p
VOD playlists, validates and strips segment prefixes, and remuxes with FFmpeg.
Unsupported encrypted/live/byte-range streams are rejected. HTTPS validation is
normal and independent of the existing WCO certificate exception.

Recovery logs queued/searching/matched/progress/failure messages. Downloads use a
separate `[HiAnime]` filename and temporary directory; the final MP4 is published
only after all segments and remuxing succeed. Existing output is never replaced.
Each segment gets up to three attempts; a failed recovery is left failed rather
than endlessly cycling between providers.

Validation: Gradle fat-JAR build; `HiAnimeFallbackTest` for identity matching,
playlist restrictions and segment validation; `HiAnimeQueueTest` for the queued
and active-WCO barrier, pending completion, deduplication and Stop cleanup;
`HiAnimeProbe` for live Overgeared episode 2 English-dub resolution and download.

The integrated provider downloaded episode 2 as a 410,197,183-byte MP4, duration
1420.065 seconds, H.264 1920x1080 with AAC. Beginning/end five-second decode checks
passed. This verifies the provider download; a live exhausted-WCO batch has not
been run end-to-end because its configured retries would take substantial time.

### Repeated player investigation (2026-10-29)

An isolated live Overgeared episode-4 quality probe completed one popup cycle
and resolved three primary qualities with no second player. The normal app 720p
size request then failed on e20.cizgifilmlerizle.com with its expired Apr 17, 2026
certificate. Thus valid link extraction can still trigger the existing new-link
connection retry and another popup. This reproduces a cause of repeated loading;
it does not establish the cause of every historical repeat.

Normal-console diagnostics now identify primary/second-player checks, inner
frame reloads, empty link responses, resolved quality counts, and post-resolution
connection failures. Empty quality responses now consume the retry budget in
both player loops; previously those responses could loop indefinitely.
Certificate validation and retry settings are unchanged.
