package app.roll.gallery.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.History
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.roll.gallery.R
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.access.PHOTO_PERMISSIONS
import app.roll.gallery.ui.access.PhotoAccess
import app.roll.gallery.ui.access.photoAccess
import app.roll.gallery.ui.components.ListRow
import app.roll.gallery.ui.components.SEGMENT_GAP
import app.roll.gallery.ui.components.SectionHeader
import app.roll.gallery.ui.components.segmentShape

/**
 * Settings, in grouped cards as in Contacts: what the app may see, Android's "Media
 * management" access (which skips the confirmation on every change), whether memories show,
 * and About.
 * Access can change in Android's settings, so it's read again whenever this screen returns.
 */
@Composable
fun SettingsScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    // Bumped to re-read access after coming back from Android's settings or the permission dialog.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val access = remember(refresh) { context.photoAccess() }
    val manageMedia = remember(refresh) { MediaStore.canManageMedia(context) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        viewModel.onAccessChanged()
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var showLicenses by remember { mutableStateOf(false) }
    val showMemories by viewModel.showMemories.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            // Opaque in the background colour (status bar included), so the list scrolls behind it.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item(key = "access-header") { SectionHeader(stringResource(R.string.settings_access)) }
            item(key = "photo-access") {
                ListRow(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.settings_photo_access),
                    summary = stringResource(
                        if (access == PhotoAccess.Full) R.string.settings_photo_access_all else R.string.settings_photo_access_some,
                    ),
                    shape = segmentShape(0, 2),
                    onClick = {
                        // With some photos shared, the dialog lets the user pick more or allow all;
                        // with everything shared, only Android's settings can take it back.
                        if (access == PhotoAccess.Full) context.openAppSettings() else permissions.launch(PHOTO_PERMISSIONS)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "manage-media") {
                ListRow(
                    icon = Icons.Outlined.PermMedia,
                    title = stringResource(R.string.settings_manage_media),
                    summary = stringResource(if (manageMedia) R.string.settings_manage_media_on else R.string.settings_manage_media_off),
                    shape = segmentShape(1, 2),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${context.packageName}")),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "display-header") { SectionHeader(stringResource(R.string.settings_display)) }
            item(key = "memories") {
                ListRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.settings_memories),
                    summary = stringResource(R.string.settings_memories_summary),
                    shape = segmentShape(0, 1),
                    onClick = { viewModel.setShowMemories(!showMemories) },
                    trailing = { Switch(checked = showMemories, onCheckedChange = viewModel::setShowMemories) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "about-header") { SectionHeader(stringResource(R.string.settings_about)) }
            item(key = "version") {
                ListRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version),
                    summary = version,
                    shape = segmentShape(0, 2),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "licenses") {
                ListRow(
                    icon = Icons.Outlined.Balance,
                    title = stringResource(R.string.settings_licenses),
                    summary = stringResource(R.string.settings_licenses_summary),
                    shape = segmentShape(1, 2),
                    onClick = { showLicenses = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = { Text(stringResource(R.string.licenses_body)) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** Opens this app's page in Android's settings, where access can be changed or taken back. */
fun android.content.Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
