package app.roll.gallery.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.roll.gallery.ui.GalleryViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.roll.gallery.data.Media
import app.roll.gallery.data.commonMimeType

/**
 * Something that shows Android's confirmation for a change request (favorite, trash, delete)
 * and calls [onDone] with whether the user agreed. The library refreshes by itself afterwards,
 * because the media store announces the change.
 */
@Composable
fun rememberRequestLauncher(onDone: (Boolean) -> Unit = {}): (IntentSender) -> Unit {
    val done by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        done(it.resultCode == Activity.RESULT_OK)
    }
    return remember(launcher) { { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) } }
}

/**
 * Something that moves photos to the trash: Android's confirmation first, then, if agreed, a
 * message with Undo (from [viewModel]) and [onDone].
 */
@Composable
fun rememberTrash(viewModel: GalleryViewModel, onDone: () -> Unit = {}): (List<Media>) -> Unit {
    var pending by remember { mutableStateOf<List<Media>>(emptyList()) }
    val done by rememberUpdatedState(onDone)
    val request = rememberRequestLauncher { agreed ->
        if (agreed && pending.isNotEmpty()) {
            viewModel.trashed(pending)
            done()
        }
        pending = emptyList()
    }
    return remember(request) {
        { items ->
            pending = items
            request(viewModel.trashRequest(items, trash = true))
        }
    }
}

/**
 * Opens Android's share sheet for [items]. The receiving app gets read access to just these
 * files, through the grant flag and clip data (the share sheet's preview needs the clip data).
 */
fun Context.shareMedia(items: List<Media>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { it.uri })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.type = commonMimeType(items.map { it.mimeType })
    intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, null))
}
