# Gallery

A gallery app for Android 16 and up, built with Jetpack Compose and Material 3 Expressive,
laid out like the photo apps people already know: a date-grouped Photos grid, a Collections
tab for albums, favorites, videos and trash, and a full-screen viewer. It takes its colours
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
  for details. Videos play in place with a seek bar.
- **Details:** date and time, file name, folder, size, resolution, and the camera, aperture,
  shutter speed, ISO and focal length stored in the photo.
- **Collections:** Favorites, Videos, Trash, and every folder with photos as an album (camera
  and screenshots first). Make new albums, and move or copy photos into albums.
- **Slideshow:** from the viewer's menu; a new photo every four seconds, tap to stop.
- **Wallpaper:** set a photo as the home screen, lock screen or both, from the viewer's menu.
- **Other apps:** "Open with Gallery" and the camera's thumbnail open a photo or video in
  Gallery's viewer, even without photo access. Apps that ask to choose photos or videos can
  pick them from Gallery, one or several.
- **Settings:** photo access, Media management, On this day, version and licenses.
- **Trash:** Android's own trash, shared with other apps. Items are deleted for good after
  30 days; restore or delete them sooner, one at a time, by selection, or all at once.

## Permissions

| Permission | Why |
| --- | --- |
| Photos and videos (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`) | To show them. |
| Selected photos (`READ_MEDIA_VISUAL_USER_SELECTED`) | So Android can offer "Select photos" instead of all. Gallery then shows only those, with a card offering to allow all. |
| Set wallpaper (`SET_WALLPAPER`) | For "Set as wallpaper". Granted without asking. |
| Media management (`MANAGE_MEDIA`) | Optional, turned on by you in Android's Settings → Apps → Special app access → Media management. With it, favoriting, trashing, deleting and moving go through without Android asking you to confirm each time. |

Gallery doesn't ask for location access, so it doesn't read where photos were taken.

## Building

GitHub Actions builds two kinds of APK, each signed with its own key:

| | When | App | Artifact |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Gallery Dev" (`app.roll.gallery.dev`) | `gallery-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch | "Gallery" (`app.roll.gallery`) | attached to a GitHub Release |

A release is tagged with the version name on line 2 of `version.txt` (line 1 is the version
code, which must go up with every release), and its notes come from that version's section of
`CHANGELOG.md`. Each run's page shows the APK's signing certificate SHA-256.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :app:assembleDev       # test build
./gradlew :app:assembleRelease   # release
./gradlew :app:testDebugUnitTest   # unit tests
```

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

Without the dev key, test builds are signed with a throwaway key and the run says so; a
release refuses to build without its key.

## License

Apache License 2.0 (see `LICENSE`). Third-party parts are listed in `NOTICE`.
