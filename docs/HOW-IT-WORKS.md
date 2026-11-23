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

## Two editions

Gallery comes as **Gallery** and **Gallery Plus**. They're the same app (same app id, so either
installs over the other and keeps its settings and Locked folder); Plus adds reading the text
in photos, finding people and searching by description, which needs recognition models and a small engine to run them, so it's bigger. Code
for Plus only lives in `app/src/plus/`, and the plain edition's stand-ins in `app/src/standard/`;
`Edition.kt` in each says what that edition includes.

The models are PaddleOCR's small text finder (about 2.5 MB) and Latin-script text reader (about
8 MB), the OpenCV Zoo's YuNet face finder (about 0.2 MB) and 8-bit SFace face matcher
(about 10 MB), and TinyCLIP ViT-39M/16 + Text-19M for searching by description, shrunk to
8-bit by the build where that keeps its fingerprints nearly unchanged. They and a cut-down copy of ONNX Runtime 1.20 (only the operators these models use, for
64-bit phones; about 1.4 MB) are built by `scripts/plus/build.sh`, which CI runs and caches. The
script shrinks the reader to 8-bit numbers with "calibration": the rounding is worked out
beforehand from sample lines of text, which lets phone processors use their fast 8-bit
instructions throughout. That makes it about twice as fast and a third of the size (about
2.2 MB), and it's kept only if it reads at least as many test lines correctly as the original.
The other models were tried the same way (the by-hand "Compare 8-bit shrinking" workflow) and
lost too much accuracy, so they stay as they are.

## Screens

| Screen | What it's for | File |
| --- | --- | --- |
| Access | Asking for permission to see photos, with a way into Android's settings | `ui/access/AccessScreen.kt` |
| Search | Finding photos by name, album, date or kind, with shortcuts before you type | `ui/search/SearchScreen.kt` |
| Settings | Photo access, Media management, On this day, Folders in Photos, Text and people (Plus), version and licenses | `ui/settings/SettingsScreen.kt` |
| Folders in Photos | Which folders show in the Photos tab | `ui/settings/FoldersScreen.kt` |
| Edit date & time | Picking a new date and time for a photo | `ui/viewer/EditDate.kt` |
| Photos tab | On this day, then every photo and video under date headers; pinch to zoom, select, fast-scroll | `ui/photos/PhotosTab.kt`, `MediaGrid.kt`, `FastScroller.kt` |
| Videos tab | Every video, newest first, in the same grid as Photos | `ui/GalleryApp.kt` |
| Collections tab | People (Plus), reorderable tiles (Favorites, Screenshots, Recently added, Duplicates, Locked folder, Trash), and every album | `ui/collections/CollectionsTab.kt` |
| Album, Favorites, Videos, Screenshots, Recently added | One set of photos as its own grid | `ui/collections/SetScreens.kt` |
| Create tab | Collage, Animation, Video maker and Moods | `ui/create/CreateTab.kt` |
| Choosing for Create | Picking the photos (and clips) to make something from | `ui/create/MakeScreens.kt` |
| Collage, Animation, Video maker | Setting up and saving each | `ui/create/MakeScreens.kt` |
| Trash | Trashed items, to restore, delete, or empty all at once | `ui/collections/SetScreens.kt` |
| Viewer | One photo or video full screen, swiping through the rest | `ui/viewer/ViewerScreen.kt`, `Pages.kt`, `Gestures.kt` |
| Single-file viewer | A photo or video another app opened in Gallery | `ui/viewer/ExternalViewer.kt` |
| Choose for another app | Picking photos or videos to hand back to the app that asked | `ui/pick/PickScreen.kt` |
| Add to album | Moving or copying photos into an album or a new one | `ui/collections/AddToAlbum.kt` |
| New album | Naming a new album and choosing its photos | `ui/collections/AddToAlbum.kt` |
| Duplicates | Copies and near-identical shots, to trash the extras | `ui/collections/DuplicatesScreen.kt` |
| Editor | Cropping, turning, Enhance and adjustments, filters and moods, Markup, then saving | `ui/editor/EditorScreen.kt`, `EditCanvas.kt`, `Markup.kt` |
| Video editor | Trimming, turning, cutting to a shape, speed, removing sound, saving a frame | `ui/editor/VideoEditor.kt` |
| Locked folder | Photos and videos hidden behind the screen lock | `ui/locked/LockedScreen.kt` |
| Person | One person's photos, and giving them a name (Plus) | `ui/people/People.kt` |
| Details sheet | When it was taken, file, folder, size and camera settings | `ui/viewer/InfoSheet.kt` |

The main screen (the search bar with its Settings button, the three tabs — Photos, Videos and
Collections — and the bottom bar) and moving between screens are in
`ui/GalleryApp.kt` and `ui/Navigation.kt`. Screens open on top of each other, and each one
keeps its state while covered, so Back returns to the screen before exactly as it was: the same
tab, scrolled to the same place. Shared pieces such as the search bar, grid squares, selection bars,
grouped-list rows, empty-screen messages and date wording are in `ui/components/`, and the colours and fonts in
`ui/theme/Theme.kt`, which matches Contacts and Dialtone.

## Features

**The Photos grid.** Everything in the media store except the trash, newest first, by when it
was taken: the date the camera saved inside the photo, or if there's none, a date in its file
name (like IMG_20240512_143000 or Screenshot_2024-05-12), or failing both, when the file last
changed. Photos Gallery writes back out (copied to an album, or moved out of the Locked
folder) keep the date they had. Photos are grouped by day:
"Today", "Yesterday", the weekday for the past week, then dates, with the year only for other
years. Videos show their length and favorites a heart.

**Stacks.** Photos taken in quick succession in the same folder (each within two seconds of the
one before), like a camera burst or several tries at a shot, show as one tile in Photos with a
count. The cover is the shot the camera marked as the burst's cover, or the newest. Opening it
shows every shot, and selecting it selects the whole stack. Settings → Stack similar shots turns
this off. Albums and search always show every shot.

**Search.** The bar at the top of the main screen opens search; the gear on its right opens
Settings. Every word you type must match. A word can be a kind ("videos", "photos",
"favorites", "screenshots"), a year ("2024"), a month ("June" or "Jun", in your phone's
language), or part of a file name, album name, folder or file type ("png"). Before you type,
shortcuts list the kinds, every year you have photos from, and your biggest albums. Results can
be selected and opened like the Photos tab, and the viewer pages through just the results.
In Gallery, search doesn't look inside photos. In **Gallery Plus** a word also matches text read
in the photo (see Text in photos).

**Search by description (Gallery Plus).** In the same background pass, each photo is shown to
TinyCLIP's image half (a small model from Microsoft, shrunk to 8-bit numbers), which turns it
into a "fingerprint" of 512 numbers; similar-looking photos, and words describing them, get
similar fingerprints. These are kept in a file in Gallery's private storage, about half a
kilobyte per photo, never pictures. When you search, a moment after you stop typing your words
go through TinyCLIP's text half (which splits them into word pieces exactly as CLIP does) and
come out as a fingerprint too; the photos whose fingerprints are closest are added to the
results, as long as they're close enough and near the best match. Searches made only of kinds,
years and months ("videos 2024") skip this. Results are still shown by date. It understands
English best. Settings → Text and people → Search by description shows how far it's got and
can turn it off, which forgets the fingerprints.

**Things (Gallery Plus).** Using the same fingerprints as search by description, Gallery Plus
sorts photos into Things on the Collections tab: Documents, Receipts, Selfies, Pets, Food,
Landscapes, Flowers and Cars. Each Thing is described by a few phrases; a photo goes into the
Thing whose phrase it's most like, as long as it's close enough and not closer to a phrase for
something else (people, rooms, buildings, streets, screenshots), which keeps near misses out.
They're worked out again a moment after more photos are fingerprinted, and appear once there are
photos for them. Turning off Search by description turns Things off too.

**Copy text (Gallery Plus).** When text was read in a photo, the viewer's menu has Text in
photo, which shows it to select part of or copy all of. It's the text read in the background,
so it can have small mistakes.

**People (Gallery Plus).** In the same background pass as the text, Gallery Plus looks for faces
in every photo (not videos, never the Locked folder), on the phone. YuNet finds each face and
where its eyes, nose and mouth are; the face is turned and scaled so those land in the same
places every time, and SFace turns it into a "fingerprint" of 128 numbers that comes out
similar for the same person. Only the face's box and fingerprint are kept, in a file in
Gallery's private storage, never pictures of faces. Faces are then grouped: each joins the
group whose average it's most like, if alike enough, or starts a new one. A group seen in at
least three photos (or given a name) is a person, shown as a round picture at the top of
Collections. Tapping one shows their photos; the pencil gives them a name, and searching that
name finds their photos. Grouping is redone as more photos are looked at, and a name stays with
the face it was given on. The same person may sometimes be split into two people, or two
lookalikes joined; there's no merging or splitting yet. Settings → Text and people → Find
people shows how far it's got and can turn it off, which forgets the faces found but keeps the
names.

**Text in photos (Gallery Plus).** In the background, Gallery Plus reads the text in every
photo (not videos, and never the Locked folder) with its built-in models, on the phone: the
finder marks where there's text, each spot is cut out and read, and the words go into a small
text file in Gallery's private storage, so search can match signs, receipts, screenshots and
documents. Each photo is read once, and again if it's edited; photos with no text are noted so
they aren't read twice, and deleted photos are dropped from the file. Android runs the reading
when the battery isn't low and, unless that setting is turned off, only while charging, and
pauses it otherwise; it picks up where it stopped, and checks for new photos every 12 hours.
To go faster, each photo is first given a quick look at a small size (480 pixels), and only
looked at properly if that finds something like text, since most photos have none (very small
text can be missed this way). Newest photos are looked at first. Each photo is loaded once
and shared by the text reader, face finder and description model, each of which works through
the photos at its own pace with a few loaded ahead, so a quick one isn't held up by a slow
one. When only a description is still needed, the photo is loaded small, which is much quicker.
Only the models with photos left to do are loaded. While the phone is charging, the
performance cores (the fast ones, worked out from each core's top speed; the small
power-saving ones are left alone) are shared out between those models, with the text reader
getting any left over; otherwise each uses 2 cores. Android normally stops background work
after 10 minutes, so as soon as Gallery is open (Android allows it no sooner) the work turns
into a foreground job, with a quiet "Getting photos ready for search" notification showing
progress, and keeps going after Gallery is closed (Android allows this kind of job up to 6
hours a day). Gallery Plus asks once to show notifications; without that, the work still runs
but the notification is hidden.
Settings → Text and people shows how many photos have been read, updating as it goes, can turn reading off (which
deletes the text file) and can let it run without charging. The reader knows the Latin
alphabet: English and most European languages.

**Opening from other apps.** A file manager's "Open with", a messaging app's attachment or
the camera's thumbnail can open one photo or video in Gallery. It shows in a viewer of its own
that zooms, plays and shares, and works even if Gallery hasn't been given photo access, because
the other app lends access to that one file. It has no favorite or trash, since the file might
not be in the phone's library.

**Choosing for other apps.** When an app asks to "choose a photo" and you pick Gallery, you get
the library in the usual grid, showing only the kinds the app asked for (photos, videos or
both). If it takes one, tapping a photo hands it back; if it takes several, tap or drag to
select and press Done. The app gets access to just the photos you chose.

**Settings.** On this day turns the memories row on or off, Folders in Photos picks which
folders show in the Photos tab, Stack similar shots turns stacks on or off, and in Gallery Plus the Text in
photos switches; these are stored in Gallery's own private settings file. Photo access shows whether Gallery sees everything or only photos you picked,
and opens the dialog to pick more (or Android's settings, to take access back). Media
management opens Android's switch for it and shows whether it's on. About shows the version
and the licenses.

**Memories.** At the top of Photos, a row of cards:

- **Recent highlights:** the last two weeks, when there are at least nine photos; up to 40,
  favorites first, the rest picked evenly through the fortnight.
- **On this day:** a card for each earlier year with at least three photos taken within three
  days of today's date, labelled "1 year ago", "3 years ago" and so on.
- **Busy days:** up to two of the past year's days with 20 or more photos (older than the last
  two weeks), labelled with the date, up to 40 photos each.

Screenshots and videos don't count, nor folders kept out of Photos. Tapping a card opens those
photos as a slideshow. The row can be turned off in Settings (On this day).

**Slideshow.** From the viewer's menu, or by opening a memory. The controls hide and a new
photo slides in every four seconds, from the one on screen onwards; the screen stays on. It
stops at the last photo, or when you tap.

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
Unfavorite, when everything selected already is), Add to album and Trash.

**The viewer.** Swipe sideways between photos of the set you opened it from: the whole library,
an album, Favorites, Videos or the trash. Tap to show or hide the controls and Android's bars.
Pinch or double-tap to zoom; while zoomed in, dragging moves the photo instead of changing
photo. Drag down to close, drag up to open the details. The thumbnail shows at once while the
full photo loads.

**Editing.** Edit (next to Share in the viewer, for photos) opens the editor on a screen-sized
copy of the photo, so every change shows at once. Crop has a straighten slider (up to 45° either way,
in half-degree steps; tap the number to put it back to 0), a frame you drag by its corners or
middle, a choice of shapes (free, the photo's own, square, 4:3, 3:4, 16:9), a button that turns
the photo a quarter clockwise, one that mirrors it, and a reset. Adjust starts with
Enhance, which looks at how light, spread out and colourful the photo is and sets brightness,
contrast, saturation, shadows and highlights to suit (gently, so a good photo changes little);
then brightness, contrast, saturation, warmth, highlights and shadows, each a slider from -100
to 100, and vignette (darkened corners) and sharpen, from 0 to 100 (tap the number to put it
back to 0). Highlights and shadows change only the light or dark parts, and with sharpening are
worked out pixel by pixel in the background, so the preview catches up a moment after the
slider. Markup draws on the photo with a pen or a see-through highlighter, or places text where
you tap, in six colours, with undo and clear; marks are kept relative to the finished photo, so
they land in the same place when it's saved full size.
Filters has Vivid, Warm, Cool, Fade, Mono, Noir and Sepia, then the moods, fuller looks that
change several things at once: Airy, Golden hour, Night lights, Film, Pastel, Crisp, Matte, and
Teal & orange. Each is shown on a small copy of the photo. Straightening zooms the photo in just enough that its tilted edges never leave empty
corners, so a small tilt loses a little of the edges. Holding the photo shows the original
colours. Nothing changes until you save. **Save**
writes over the original, after Android asks you to allow it (unless Media management is on);
it's offered only for JPEG, PNG and WebP, the formats Android can write back. **Save as copy**
leaves the original and adds "name-edited" next to it (or in Pictures/Edited if that folder
can't hold photos). Saving works on the full-size photo, up to 8192 pixels on its longer side,
and carries over the date and camera details. Leaving with unsaved edits asks first.

**Edit from other apps.** "Edit" on a new screenshot (and in other apps that offer it) can open
Gallery's editor, and the share sheet has "Edit in Gallery" for photos. When the other app
allows changing the original (Edit usually does), Save writes over it; otherwise the edit is
saved as a copy in Pictures/Edited. Closing the editor goes back to the other app.

**Editing videos.** Edit in the viewer opens the video editor for videos. Drag the two ends of
the bar under the video to trim it; the kept part plays on a loop. The turn button turns it a
quarter clockwise; the shape chips cut it to square, 16:9, 9:16 or 4:3 around the middle (after
turning), with the parts cut off dimmed; the speed chips go from a quarter speed to four times
(the sound changes speed too, keeping its pitch); and Remove sound takes the sound out. The photo
button saves the frame on screen as a photo in Pictures/Gallery. Save as copy makes a new MP4
beside the original, named like "VID_1234-edited.mp4" and dated as the original, with a
progress bar and Cancel; the original isn't changed. It's done on the phone with Media3's
Transformer.

**Videos.** The video on screen plays by itself, with a play/pause button and seek bar that
come and go with the other controls. It pauses when you leave the app and stops when you swipe
to another item.

**Motion photos.** Some cameras save a second or two of video inside the photo file. When such
a photo comes up in the viewer, its video plays once, silently, over the still, then the still
returns; a Motion chip under the top bar plays it again. Gallery finds the video by reading the
photo's XMP metadata near the start of the file, which says how long the video at the end of
the file is, in either of the two ways cameras write it. The video is played straight out of
the photo file; nothing is copied out. It doesn't play during a slideshow.

**Details.** The date and time it was taken, the file name, resolution and size, the folder it's
in, and, for photos, the camera and its settings (aperture, shutter speed, focal length, ISO).
In the trash, the date Android will delete it for good. Gallery doesn't read where a photo was
taken.

**Collections tiles.** The top of the Collections tab has a tile each for Favorites,
Screenshots, Recently added, Duplicates, the Locked folder and Trash. Reorder (under them) lets
you move each up or down, or reset to the usual order; the order is kept in Gallery's settings.
Screenshots holds everything in a folder called Screenshots (or Screen recordings), wherever it
is. Recently added holds whatever arrived on the phone in the last 30 days, whenever it was
taken (downloads and saved photos too), grouped by the day it arrived. Videos have their own
tab instead of a tile.

**Create.** The Create tab makes new things from your photos, on the phone; the photos used
aren't changed. Each starts by choosing photos in the usual grid, and what you choose is used
oldest first.

- **Collage:** 2 to 9 photos in one picture. Pick a layout (rows, columns, one big photo with
  the rest beside it, and more), a shape (square, portrait, story or landscape), the spacing,
  how round the corners are, and a white or black background. Tap one photo and then another
  to swap them. Each photo fills its cell, cut around its middle. Saved as a 3000-pixel JPEG in
  Pictures/Gallery.
- **Animation:** 2 to 60 photos shown one after another, quickly, previewed on a loop. Speed
  goes from half a second down to a tenth of a second per photo, and it can play there and back
  so the loop has no jump. Saved as a video (played through enough times to last at least
  three seconds, since not every player loops) in Movies/Gallery.
- **Video maker:** photos and clips one after another, each photo for 2, 3 or 5 seconds and
  clips in full with their sound, in a landscape, portrait or square frame, either fitted inside
  it with black bars or filling it (cropping the edges). Music can be added from any audio file
  on the phone, chosen with Android's own file picker; it repeats if it's shorter than the video
  and is cut off at the end if it's longer. There are no transitions between shots. Saved as
  an MP4 in Movies/Gallery.
- **Moods:** opens one photo in the editor on its looks, scrolled to the moods (see Editing).

Videos are made with Media3's Transformer, the open-source AndroidX media library, using the
phone's own video encoder; a progress bar shows while it works, and Cancel stops it without
saving anything. New files are named after when they were made, like
Collage_20261117_194512.jpg.

**Albums.** Every folder holding photos or videos is an album, with its newest photo as the
cover unless you choose one ("Use as album cover" in the viewer's menu, when opened from that
album). The sort button by Albums orders them by most recent (camera folder first, screenshots
second, then the rest by their newest photo) or by name. An album's menu (top right) pins it to
the top (pinned albums show a pin, in the order they were pinned) and has Show in Photos.

**Folders in Photos.** Settings → Folders in Photos (or Show in Photos in an album's menu)
chooses which folders are mixed into the Photos tab. A folder turned off, like downloads or a
messaging app's pictures, stays an album and in search. These choices, pins, covers and the
sort order are kept in Gallery's own settings, not in the files.

**Undo.** After moving things to the trash, into another album or into the Locked folder, the
message at the bottom has Undo for a few seconds. Undoing a trash asks Android to confirm (unless
Media management is on); a move puts each item back in its old folder; the Locked folder moves
them back out.

**Editing the date.** "Edit date & time" in the viewer's menu picks a new date, then a new time,
then asks Android for permission to change the file (unless Media management is on). For JPEG,
PNG and WebP photos the new date is written into the photo itself, so it travels with the file;
for other photos and videos only Android's media store is changed.

**Making albums, moving and copying.** "Add to album" (for a selection, or from the viewer's
menu) opens a list of albums with a Move or Copy switch. Moving shifts the files into that
album's folder; they keep their dates and favorites and leave the album they were in. Copying
makes new files there and leaves the originals. Android only lets photos live under the DCIM
and Pictures folders (and videos under Movies too), so albums elsewhere, such as Download or
a messaging app's folder, are greyed out. Before moving files Gallery didn't make, Android
asks you to allow it (not if Media management is on). "New album" on the Collections tab asks
for a name, then for its photos, then whether to move or copy them; the album is a new folder
in Pictures. An album is a folder, so it only exists once something is in it. A message at the
bottom says how many items were moved or copied, or how many couldn't be.

**Duplicates.** On the Collections tab. Gallery looks at every photo (not videos) in the
background, showing how far it's got. For each photo it shrinks Android's small thumbnail to
9 × 8 pixels and notes, for each pixel, whether it's brighter than the next: a 64-bit
"fingerprint" that stays the same when a photo is resized or recompressed. Photos with the same
file size and (almost) the same fingerprint are **copies**, wherever they are. Photos taken
within two minutes of each other with close fingerprints are **similar shots** (bursts and
retakes). In each group, the suggested keeper is a favorite if there is one, otherwise the one
with the most pixels, then the biggest file, then the oldest; everything else is ticked. Tap
photos to change what's ticked, then move the ticked ones to the trash. Fingerprints are kept
in memory while the app is open, so a second look only reads new or changed photos; nothing is
saved.

**Locked folder.** On the Collections tab. "Move to Locked folder" (in a photo's menu, or the
menu at the top while selecting) copies the items into Gallery's own private storage, then asks
Android to delete the originals (no question if Media management is on); if you say no, the
copies are removed, so nothing is ever in both places. Locked items are hidden from the Photos
tab, albums, search and every other app. Opening the folder asks for your screen lock
(fingerprint, face, or PIN, pattern or password), and it needs the phone to have one. It locks
again as soon as Gallery leaves the screen, and a locked-folder viewer closes when that happens.
Screenshots and screen recording are blocked while it's showing, and the recent apps list shows
it blank. Inside, items can be viewed, moved out (back to the folder they came from, or to
Pictures if Android won't allow that folder) or deleted for good; there's no trash here and no
sharing. The folder is excluded from Android's backups and from moving to a new phone, and it
is deleted if Gallery is uninstalled, which the folder itself warns about. Each item is its file
plus a small note of its name, type, dates, size and original folder.

**Wallpaper.** The viewer's menu (top right) has "Set as wallpaper" for photos: home screen,
lock screen, or both. Android fits the photo to the screen.

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
| Building albums from folders, ordering, pinning, covers and folders kept out of Photos | `data/Albums.kt` |
| Grouping quick runs of shots into stacks | `data/Stacks.kt` |
| Messages with Undo, and undoing | `ui/GalleryViewModel.kt`, `ui/GalleryApp.kt` |
| Matching search words to photos | `data/Search.kt` |
| Reading what another app asked for, and describing a file it handed over | `intents/ExternalRequest.kt` |
| Which kinds a pick request wants | `data/Picking.kt` |
| Handing chosen photos back to the app that asked | `MainActivity.kt` |
| Drag-to-select range maths | `data/Selection.kt` |
| Reading camera settings from a photo | `data/Details.kt` |
| Formatting lengths, sizes, megapixels and camera settings | `data/Formats.kt` |
| Keeping the library loaded and refreshing it when the media store changes | `ui/GalleryViewModel.kt`, `util/ContentChanges.kt` |
| Keeping, listing, restoring and deleting Locked folder files | `data/LockedFolder.kt` |
| Asking for the screen lock, blocking screenshots, moving items in | `ui/locked/LockedAccess.kt` |
| Keeping the Locked folder out of backups | `res/xml/data_extraction_rules.xml` |
| Grid thumbnails from Android's own thumbnail cache (or the file, for locked items) | `image/Thumbnails.kt`, `GalleryApplication.kt` |
| Picking memories: On this day, Recent highlights, busy days | `data/Memories.kt` |
| Gallery's own settings | `data/Settings.kt` |
| Grouping duplicates and choosing what to keep | `data/Duplicates.kt` |
| Working out a photo's fingerprint from its thumbnail | `data/PhotoHash.kt` |
| Running the duplicate search in the background | `ui/GalleryViewModel.kt` |
| What an edit is, colour maths, straighten zoom, crop shapes and dragging the crop frame | `data/Edits.kt` |
| Drawing edits and saving the result | `data/EditRenderer.kt` |
| Highlights, shadows, sharpening, vignette, drawing markup, and Enhance's settings | `data/Tones.kt` |
| What Create makes, collage layouts and crops, animation timing, new file names | `data/Creations.kt` |
| Drawing and saving collages, and saving made videos | `data/CreationWriter.kt` |
| Making videos from photos and clips | `data/VideoMaking.kt` |
| Saving an edited video, and grabbing a frame from one | `data/VideoEditing.kt` |
| The Collections tiles and their order | `data/CollectionTiles.kt` |
| Album name rules and which folders can hold photos | `data/AlbumRules.kt` |
| Copying and moving files, and setting the wallpaper | `data/MediaWriter.kt` |
| Share sheet and change confirmations | `ui/components/MediaActions.kt`, `ui/Actions.kt` |
| Zooming, panning and swipe-to-close in the viewer | `ui/viewer/Gestures.kt` |
| What each edition includes | `Edition.kt` in `app/src/standard/` and `app/src/plus/` |
| Reading text in a photo (Plus) | `app/src/plus/.../recognition/PaddleTextReader.kt`, `recognition/TextReader.kt` |
| Sizing images for the models, finding text boxes, turning scores into characters | `recognition/OcrMath.kt` |
| The file of text read in photos | `recognition/TextIndex.kt` |
| Looking at photos in the background (text, faces and descriptions), and scheduling it | `recognition/RecognitionWorker.kt` |
| The notification shown while photos are being looked at | `recognition/ScanNotification.kt` |
| Asking once to show that notification (Plus) | `ui/components/AskForNotifications.kt` |
| Dates from file names, keeping dates on files Gallery writes, and changing a photo's date | `data/FileDates.kt` |
| Counting the phone's performance cores | `recognition/CpuCores.kt` |
| Loading a model with its number of processor cores (Plus) | `app/src/plus/.../recognition/Sessions.kt` |
| Finding faces and fingerprinting them (Plus) | `app/src/plus/.../recognition/YuNetFaceFinder.kt`, `recognition/FaceFinder.kt` |
| Reading the face finder's output, straightening faces, comparing and grouping them | `recognition/FaceMath.kt` |
| The file of faces found, and people's names | `recognition/FaceIndex.kt` |
| Turning faces into people, naming them, making names searchable | `recognition/People.kt` |
| Round face pictures | `image/Thumbnails.kt` |
| Description fingerprints: storing, comparing, picking best matches | `recognition/Descriptions.kt` |
| Sorting photos into Things | `recognition/Things.kt`, `ui/collections/Things.kt` |
| Showing and copying text read in a photo | `ui/viewer/PhotoText.kt` |
| Splitting search words into CLIP's word pieces | `recognition/ClipTokenizer.kt` |
| TinyCLIP's image and text halves (Plus) | `app/src/plus/.../recognition/TinyClip.kt` |
| Building the models and the cut-down runtime | `scripts/plus/build.sh`, `prepare_models.py`, `models.txt`, `ort_settings.json` |
| Video playback | `ui/viewer/Pages.kt` |
| Finding the video inside a motion photo | `data/MotionPhotos.kt` |
| Playing it out of the photo file | `ui/viewer/MotionPlayer.kt` |
| Hiding and showing Android's bars in the viewer | `ui/viewer/ViewerScreen.kt` |

## Tests

The rules that don't need a phone (day and month grouping, header wording, album order, search matching, pick requests, album names and folders, memories, motion photo metadata, the text finder's boxes and reader's characters, the text file's lines, face finding, straightening, grouping and naming, the faces file's lines, description fingerprints and matching, sorting into Things, the word-piece splitter (checked against CLIP's own when the Plus models are built), duplicate fingerprints and grouping, collage layouts, gaps and crops, video trim, speed and crop maths, stacks, animation timing, the Collections tiles' order, Screenshots and Recently added, editing maths and saved-file names, highlights, shadows and Enhance, where locked items go back to, the
drag-to-select range, number and size formatting, camera names) have tests in `app/src/test/`.
They run before every build.
