# How Gallery works

A plain-language guide to the app: what each part does and where it lives in the code. No
programming knowledge needed. File paths are inside `app/src/main/java/app/roll/gallery/`.

## The big picture

Gallery doesn't keep its own copy or list of your photos. Android has a shared catalogue of
every photo and video on the phone, the **media store**, which the camera, downloads, messaging
apps and file managers all add to. Gallery is a window onto it: it reads what's there and, when
you favorite, trash, restore or delete something, asks the media store to make the change.
That's why your favorites and trash are the same in every app that uses them.

Android doesn't let an app change other apps' files on its own say-so. Each change is a
**request** that Android shows you to confirm ("Allow Gallery to move this photo to trash?").
If you give Gallery **Media management** access in Android's settings, Android approves these
requests without asking.

The app watches the media store, so when anything changes, even from another app, every
screen refreshes by itself.

Nothing goes over the network: the app has no internet access and uses no Google services.

## Screens

| Screen | What it's for | File |
| --- | --- | --- |
| Access | Asking for permission to see photos, with a way into Android's settings | `ui/access/AccessScreen.kt` |
| Search | Finding photos by name, album, date or kind, with shortcuts before you type | `ui/search/SearchScreen.kt` |
| Settings | Photo access, Media management, version and licenses | `ui/settings/SettingsScreen.kt` |
| Photos tab | Every photo and video under date headers; pinch to zoom, select, fast-scroll | `ui/photos/PhotosTab.kt`, `MediaGrid.kt`, `FastScroller.kt` |
| Collections tab | Favorites, Videos and Trash shortcuts, and every album | `ui/collections/CollectionsTab.kt` |
| Album, Favorites, Videos | One set of photos as its own grid | `ui/collections/SetScreens.kt` |
| Trash | Trashed items, to restore, delete, or empty all at once | `ui/collections/SetScreens.kt` |
| Viewer | One photo or video full screen, swiping through the rest | `ui/viewer/ViewerScreen.kt`, `Pages.kt`, `Gestures.kt` |
| Details sheet | When it was taken, file, folder, size and camera settings | `ui/viewer/InfoSheet.kt` |

The main screen (the search bar with its Settings button, the two tabs and the bottom bar) and moving between screens are in
`ui/GalleryApp.kt` and `ui/Navigation.kt`. Shared pieces such as the search bar, grid squares, selection bars,
grouped-list rows, empty-screen messages and date wording are in `ui/components/`, and the colours and fonts in
`ui/theme/Theme.kt`, which matches Contacts and Dialtone.

## Features

**The Photos grid.** Everything in the media store except the trash, newest first, by when it
was taken (or when the file last changed, if the photo doesn't say). Photos are grouped by day:
"Today", "Yesterday", the weekday for the past week, then dates, with the year only for other
years. Videos show their length and favorites a heart.

**Search.** The bar at the top of the main screen opens search; the gear on its right opens
Settings. Every word you type must match. A word can be a kind ("videos", "photos",
"favorites", "screenshots"), a year ("2024"), a month ("June" or "Jun", in your phone's
language), or part of a file name, album name, folder or file type ("png"). Before you type,
shortcuts list the kinds, every year you have photos from, and your biggest albums. Results can
be selected and opened like the Photos tab, and the viewer pages through just the results.
Search doesn't look inside photos (no text or face recognition).

**Settings.** Photo access shows whether Gallery sees everything or only photos you picked,
and opens the dialog to pick more (or Android's settings, to take access back). Media
management opens Android's switch for it and shows whether it's on. About shows the version
and the licenses.

**Zoom levels.** Pinching changes the grid between three levels: large (2 across), normal (4
across, grouped by day) and months (7 across, grouped by month). The grid keeps the same photos
in view when it changes.

**Fast scroll.** In a long grid, a handle slides in on the right edge while scrolling. Dragging
it jumps through the library and shows the month you're at.

**Selecting.** Long-press a photo to select it. Without lifting your finger, drag across others
to select everything from the first to the one under your finger; drag back to unselect. Near
the top or bottom edge, the grid scrolls by itself. Starting on an already selected photo
unselects instead. Once selecting, a tap adds or removes a single photo, and a circle next to
each date selects or unselects that whole day. The bottom bar then offers Share, Favorite (or
Unfavorite, when everything selected already is) and Trash.

**The viewer.** Swipe sideways between photos of the set you opened it from: the whole library,
an album, Favorites, Videos or the trash. Tap to show or hide the controls and Android's bars.
Pinch or double-tap to zoom; while zoomed in, dragging moves the photo instead of changing
photo. Drag down to close, drag up to open the details. The thumbnail shows at once while the
full photo loads.

**Videos.** The video on screen plays by itself, with a play/pause button and seek bar that
come and go with the other controls. It pauses when you leave the app and stops when you swipe
to another item.

**Details.** The date and time it was taken, the file name, resolution and size, the folder it's
in, and, for photos, the camera and its settings (aperture, shutter speed, focal length, ISO).
In the trash, the date Android will delete it for good. Gallery doesn't read where a photo was
taken.

**Albums.** Every folder holding photos or videos is an album, with its newest photo as the
cover. The camera folder comes first, screenshots second, then the rest by their newest photo.

**Trash.** This is Android's own trash, shared with other apps. Android deletes items for good
30 days after they're trashed. From the trash you can restore or delete items one at a time,
by selection, or empty it all at once after confirming.

**Access.** Gallery asks for photos and videos when it first opens. If you choose "Select
photos", it shows only those, with a card offering to allow all. If you've said no, the access
screen can open the app's Android settings. Access is checked again whenever you come back to
the app.

**Theme.** Colours come from your wallpaper and follow light or dark mode; the viewer is always
dark. Text is set in Google Sans Flex, bundled with the app.

## Where the work happens

| Job | File |
| --- | --- |
| Reading photos and videos from the media store, and building change requests | `data/MediaStoreSource.kt` |
| What a photo or video is, and the sets the viewer pages through | `data/Media.kt` |
| Grouping into days or months, header wording rules, zoom levels | `data/Timeline.kt` |
| Building albums from folders and ordering them | `data/Albums.kt` |
| Matching search words to photos | `data/Search.kt` |
| Drag-to-select range maths | `data/Selection.kt` |
| Reading camera settings from a photo | `data/Details.kt` |
| Formatting lengths, sizes, megapixels and camera settings | `data/Formats.kt` |
| Keeping the library loaded and refreshing it when the media store changes | `ui/GalleryViewModel.kt`, `util/ContentChanges.kt` |
| Grid thumbnails from Android's own thumbnail cache | `image/Thumbnails.kt`, `GalleryApplication.kt` |
| Share sheet and change confirmations | `ui/components/MediaActions.kt`, `ui/Actions.kt` |
| Zooming, panning and swipe-to-close in the viewer | `ui/viewer/Gestures.kt` |
| Video playback | `ui/viewer/Pages.kt` |
| Hiding and showing Android's bars in the viewer | `ui/viewer/ViewerScreen.kt` |

## Tests

The rules that don't need a phone (day and month grouping, header wording, album order, search matching, the
drag-to-select range, number and size formatting, camera names) have tests in `app/src/test/`.
They run before every build.
