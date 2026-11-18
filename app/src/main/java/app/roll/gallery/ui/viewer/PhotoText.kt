package app.roll.gallery.ui.viewer

import android.content.ClipData
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import app.roll.gallery.R
import kotlinx.coroutines.launch

/**
 * The text Gallery Plus read in a photo ([text]), to select part of or copy all of. It's what
 * was read in the background, so it may have small mistakes, and very small text may be missing.
 */
@Composable
fun PhotoTextDialog(text: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_text)) },
        text = {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) { Text(text) }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
                onDismiss()
            }) { Text(stringResource(R.string.photo_text_copy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
