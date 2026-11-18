# Gallery

A gallery app for Android 16 and up, built with Jetpack Compose and Material 3 Expressive,
laid out like the photo apps people already know: a date-grouped Photos grid, a Collections
tab for albums, favorites, videos and trash, a Create tab for collages, animations and videos,
and a full-screen viewer. It takes its colours
from the wallpaper, as Contacts and Dialtone do.

Everything stays on the phone. Gallery has no account, no cloud and no internet permission (the build strips it even if a library asks for it), and
uses no Google services: it reads and changes the photos and videos in Android's shared media
store, so other apps see the same favorites and trash.

## Features

- **Photos:** every photo and video, newest first, under Today, Yesterday, weekday and date
  headers. Pinch to switch between large, normal and month views. A fast-scroll handle appears
  while scrolling and shows the month as you drag it.
- **Search:** a search bar across the top, as in Contacts, with Settings on its right. Search
  by file or album name, year, month, or kind ("videos", "favorites", "screenshots"), with
  shortcuts for each before you type.
- **On this day:** cards at the top of Photos with photos from around today's date in past
  years; tap one to play it as a slideshow. Can be turned off in Settings.
- **Selecting:** long-press a photo, then keep your finger down and drag across others to
  select a run of them (the grid scrolls when you reach its edge). A circle by each date
  selects that whole day. Share, favorite, add to an album or trash the selection.
- **Viewer:** swipe between photos, pinch or double-tap to zoom, drag down to close, drag up
  for details. Videos play in place with a seek bar. Motion photos play their moment of video
  when they come up, and again from the Motion chip.
- **Editor:** crop (free or a fixed shape), straighten, rotate, flip, brightness, contrast, saturation,
  warmth, filters and moods, with a live preview; hold the photo to compare. Save over the original
  or as a copy beside it.
- **Details:** date and time, file name, folder, size, resolution, and the camera, aperture,
  shutter speed, ISO and focal length stored in the photo.
- **Videos:** a tab of its own with every video, newest first, selectable like Photos.
- **Collections:** tiles for Favorites, Screenshots, Recently added, Duplicates, the
  Locked folder and Trash, in an order you can change, then every folder with photos as an
  album (camera and screenshots first). Make new albums, and move or copy photos into albums.
- **Create:** make a collage of 2 to 9 photos, an animation from a burst of photos, or a
  slideshow video of photos and clips with music from your own audio files, all on the phone
  and saved as new files. Moods give a photo a named look, from Airy to Night lights.
- **Undo:** trashing, moving to an album and moving into the Locked folder can be undone from
  the message that follows.
- **Albums:** pin albums to the top, sort them by most recent or name, choose a cover, and
  choose which folders show in Photos (Settings → Folders in Photos).
- **Edit date & time:** fix when a photo was taken, from the viewer's menu.
- **Slideshow:** from the viewer's menu; a new photo every four seconds, tap to stop.
- **Duplicates:** finds copies of the same photo and near-identical shots taken moments apart,
  suggests which to keep, and trashes the rest in one go.
- **Locked folder:** move photos and videos into a private folder behind your screen lock
  (fingerprint, face, PIN, pattern or password). They're hidden from your photos and other
  apps, screenshots are blocked there, and it locks again when you leave the app. The folder
  stays on the phone: it isn't backed up and is deleted if Gallery is uninstalled.
- **Wallpaper:** set a photo as the home screen, lock screen or both, from the viewer's menu.
- **Other apps:** "Open with Gallery" and the camera's thumbnail open a photo or video in
  Gallery's viewer, even without photo access. Apps that ask to choose photos or videos can
  pick them from Gallery, one or several.
- **Settings:** photo access, Media management, On this day, version and licenses, and in
  Gallery Plus, Text and people.
- **Search by description (Gallery Plus):** search for what's in a photo, like "red car" or
  "old log cabin", worked out on the phone.
- **People (Gallery Plus):** finds faces in your photos on the phone and groups them into
  people at the top of Collections. Name someone and search their name to find their photos.
- **Text in photos (Gallery Plus):** reads the text in your photos in the background, on the
  phone (by default only while charging), so search finds signs, receipts, screenshots and
  documents. Latin alphabet languages.
- **Trash:** Android's own trash, shared with other apps. Items are deleted for good after
  30 days; restore or delete them sooner, one at a time, by selection, or all at once.

## Permissions

| Permission | Why |
| --- | --- |
| Photos and videos (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`) | To show them. |
| Selected photos (`READ_MEDIA_VISUAL_USER_SELECTED`) | So Android can offer "Select photos" instead of all. Gallery then shows only those, with a card offering to allow all. |
| Set wallpaper (`SET_WALLPAPER`) | For "Set as wallpaper". Granted without asking. |
| Biometrics (`USE_BIOMETRIC`) | Lets the Locked folder show Android's unlock prompt (fingerprint, face, or your PIN, pattern or password). Granted without asking; Gallery never sees your fingerprint or face. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`) | Gallery Plus only. Lets looking at photos for search keep going past Android's 10-minute limit for background work, with a notification showing progress. Granted without asking. |
| Notifications (`POST_NOTIFICATIONS`) | Gallery Plus only, asked once: shows the progress of getting photos ready for search. Saying no only hides the notification. |
| Media management (`MANAGE_MEDIA`) | Optional, turned on by you in Android's Settings → Apps → Special app access → Media management. With it, favoriting, trashing, deleting and moving go through without Android asking you to confirm each time. |

Gallery doesn't ask for location access, so it doesn't read where photos were taken.

## Editions

| | What's in it |
| --- | --- |
| **Gallery** | Everything above. |
| **Gallery Plus** | Everything above, plus reading text in photos, finding people, and searching by description ("a black dog"), on the phone. Bigger, because it carries recognition models and a small engine to run them. |

Both have the same app id and signing keys, so either installs over the other and keeps its
settings and Locked folder.

## Building

GitHub Actions builds both editions, in two kinds of APK, each signed with its own key:

| | When | App | Artifacts |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Gallery Dev" / "Gallery Plus Dev" (`app.roll.gallery.dev`) | `gallery-dev-apk`, `gallery-plus-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch | "Gallery" / "Gallery Plus" (`app.roll.gallery`) | both attached to a GitHub Release |

A release is tagged with the version name on line 2 of `version.txt` (line 1 is the version
code, which must go up with every release), and its notes come from that version's section of
`CHANGELOG.md`. Each run's page shows the APK's signing certificate SHA-256.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :app:assembleStandardDev        # Gallery test build
./gradlew :app:assembleStandardRelease    # Gallery release
./gradlew :app:testStandardDebugUnitTest  # unit tests
```

Gallery Plus also needs its models and runtime, built once into `build/plus/` by
`scripts/plus/build.sh` (Python 3, the Android NDK and Ninja; it compiles ONNX Runtime, which
takes a while). Then `./gradlew :app:assemblePlusDev`. CI does this itself and caches the
result until something in `scripts/plus/` changes.

Every CI build runs the unit tests (`app/src/test`) and `scripts/check-docs.py` first, and
stops if either fails.

For what each part of the app does and which file it's in, see
[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

### Signing keys

Set as repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `DEV_KEYSTORE_BASE64` | base64 of the dev keystore (alias `gallery-dev`) |
| `DEV_KEYSTORE_PASSWORD` | its password (store and key) |
| `RELEASE_KEYSTORE_BASE64` | base64 of the release keystore (alias `gallery`) |
| `RELEASE_KEYSTORE_PASSWORD` | its password (store and key) |

The certificates' SHA-256 fingerprints, to check an APK was signed with the right key:

| Key | SHA-256 |
| --- | --- |
| Dev (`app.roll.gallery.dev`) | `c21d38e8f8b32c887e38bc19f085e1d1b513f913c79902e2e7320c4898f34ccd` |
| Release (`app.roll.gallery`) | `b8a19f676dd4b5274599462f4fbadf27b417cda60083a0012941253f6683d19a` |

Without the dev key, test builds are signed with a throwaway key and the run says so; a
release refuses to build without its key.

## License

Apache License 2.0 (see `LICENSE`). Third-party parts are listed in `NOTICE`.
