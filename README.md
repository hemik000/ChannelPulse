# Channel Pulse — live YouTube channel stats for Android TV

A YouTube-Studio-style wall display for Android TV / Google TV / Fire TV, built with Kotlin and Compose for TV.

- **Dashboard** – every channel you track as a card: rolling subscriber count, live total views, views on the newest uploads in the last hour, a 60-minute mini chart, and a red dot when the channel is live.
- **Channel page** – big counters, a "last 60 minutes" and "last 48 hours" views chart, and the latest uploads with live view counts, likes, comments and concurrent viewers for live streams. Press OK on a video to open it in the YouTube app.
- **Head to head** – compare two channels side by side, with the subscriber gap and how it moved over the last 24 hours.
- **Live wall** – a full-screen giant counter that cycles through your channels. ◀ ▶ switches, OK pauses. It can also be the TV's screensaver.
- **Screen care** – burn-in protection (a slow few-pixel drift) and optional night dimming for TVs left on all day.
- **Multiple channels** – add as many as you like by `@handle`, channel link or `UC…` ID.
- **Multiple API keys** – the app rotates between keys and picks the fastest refresh rate your combined quota allows.
- **Phone setup** – scan the QR code on the TV to add channels and paste API keys from your phone's keyboard. Start/stop it in Settings; it also switches itself off after 15 idle minutes once you're set up.

## What "real time" means here

| Number | Source | Accuracy |
| --- | --- | --- |
| Total channel views | `channels.list` | Exact |
| Video views, likes, comments | `videos.list` | Exact |
| Live stream viewers | `videos.list` → `liveStreamingDetails.concurrentViewers` | Exact, while live |
| Subscribers | `channels.list` | Exact under 1,000; rounded down to 3 significant figures above that (YouTube does this for every app; the faint digits on screen show the rounded part) |
| "Last hour" and the 60-minute chart | View changes on each channel's 5 newest uploads, recorded by the TV | Fill in while the app is open. Video view counts update often; a channel's total views only update now and then, so the total isn't used here |
| 48-hour chart | Total views recorded by the TV | Fills in while the app is open |

YouTube Studio's own real-time panel isn't exposed by any public API. Google's sign-in flow for TVs only allows the YouTube Data API scopes, and YouTube Analytics data lags by up to 48 hours, so this app uses the Data API and polls it as often as your quota allows.

## 1. Get a YouTube API key (free)

1. Open <https://console.cloud.google.com/apis/library/youtube.googleapis.com>, create or pick a project, and click **Enable**.
2. **APIs & Services → Credentials → Create credentials → API key**.
3. Optional: under *API restrictions* pick **YouTube Data API v3**. Don't set an *Application restriction* (Android-app restricted keys need signing headers this app doesn't send).

Each project gets 10,000 units/day. Want faster refreshes? Create keys in 2–3 separate projects and add them all.

**Quota math:** one refresh costs 1 unit per 50 channels + 1 unit per 50 videos (5 newest per channel), plus 1 unit per channel every 10 minutes to refresh upload lists. Example: 6 channels with one key → about every 22 s on Auto. With three keys → about every 10 s.

## 2. Build the APK

**Android Studio:** File → Open → this folder. Let Gradle sync, then *Build → Build App Bundle(s) / APK(s) → Build APK(s)*. Plug in or connect to your TV and press Run.

**Without Android Studio:** push this folder to a GitHub repo. The included workflow (`.github/workflows/build-apk.yml`) builds the APK; download it from the run's *Artifacts*.

**Command line** (needs JDK 17+ and the Android SDK with `ANDROID_HOME` set):

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

The release build is signed with your own key once you've set one up (see **Signing** below); until then it uses the debug key so it still installs.

## 3. Install on the TV

1. On the TV: Settings → Device Preferences → About → click **Build** 7 times to enable Developer options, then turn on **USB/Network debugging**. (On Fire TV: Settings → My Fire TV → Developer options → ADB debugging.)
2. From your computer on the same network:

```bash
adb connect <tv-ip>:5555
adb install -r app-release.apk
```

Or copy the APK with a USB stick / "Send files to TV" and open it with a file manager.

## 4. First run

The app shows a QR code. Scan it with your phone (same Wi-Fi), paste your API key, then add channels — one per line to add several at once. Everything appears on the TV within seconds. Settings on the TV lets you do the same with the remote, reorder channels, set the refresh speed and the live-wall timing.

The phone page only works on your local network and needs the random code in the QR link.

## Signing

Android only installs an update over an app signed with the same key. The debug key is different on every computer (and on every GitHub build), so set up your own release key once and every build, local or on GitHub, can update the app on your TV.

**1. Create the key** (keep the password; you'll need it below):

```bash
keytool -genkeypair -v -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias channelpulse -keystore C:/Users/you/keys/channelpulse-release.jks
```

`keytool` comes with Android Studio (`C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`). **Back up the .jks file and its password.** If you lose them you can never update the app again without uninstalling it.

**2. Use it on your computer:** copy `keystore.properties.example` to `keystore.properties` (ignored by git), set `storeFile` to the .jks path and `storePassword` to its password.

**3. Use it on GitHub:** add two repository secrets (Settings → Secrets and variables → Actions):

```bash
base64 -w0 C:/Users/you/keys/channelpulse-release.jks | gh secret set SIGNING_KEYSTORE_BASE64
gh secret set SIGNING_PASSWORD
```

Without a key, release builds still work but are signed with the debug key, and Gradle prints a warning.

## Screensaver

Settings → Screensaver → **On** makes the live wall available as a screensaver; **Off** hides it again.

Google TV's settings only list Google's own Ambient mode, so other apps' screensavers don't show up there. To let the On/Off switch select the live wall itself, grant one permission once from a computer on the same network, then press **On** again:

```bash
adb connect <tv-ip>:5555
adb shell pm grant com.channelpulse.tv android.permission.WRITE_SECURE_SETTINGS
```

**Off** then gives the TV back the screensaver it had before.

## Project layout

```
app/src/main/java/com/channelpulse/tv/
├── MainActivity.kt          starts/stops polling and the phone server with the app
├── PulseApp.kt              creates the single instances
├── PulseDream.kt            the live wall as the TV screensaver, and its on/off switch
├── data/
│   ├── YouTubeApi.kt        Data API v3 client (channels, playlistItems, videos)
│   ├── PulseRepository.kt   polling loop, batching, auto refresh rate, key rotation
│   ├── KeyPool.kt           per-key usage today, out-of-quota and rejected keys
│   ├── HistoryStore.kt      48 h of readings per channel (saved), 2 h per video
│   ├── SettingsStore.kt     API keys, channels, refresh options
│   ├── ChannelRef.kt        parses @handle / URL / UC… input
│   └── Format.kt            number/time formatting, delta and chart maths
├── server/                  phone setup web page (LAN, token-protected)
└── ui/                      Compose for TV screens and components
```

Stack: Kotlin 2.0, AGP 8.7, Compose BOM 2024.12, `androidx.tv:tv-material` 1.0, OkHttp, Coil, ZXing. Android Studio may offer to upgrade these; that's safe to accept.
