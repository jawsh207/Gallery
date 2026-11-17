package app.roll.gallery.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.roll.gallery.R

/**
 * The top bar while selecting: how many are selected, a button to stop selecting, and a menu
 * of less-used actions ([menu], left out when empty).
 */
@Composable
fun SelectionTopBar(count: Int, menu: List<BarAction> = emptyList(), onClear: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_selection)) }
        },
        actions = {
            if (menu.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        for (item in menu) {
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                leadingIcon = { Icon(item.icon, contentDescription = null) },
                                onClick = {
                                    open = false
                                    item.onClick()
                                },
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** One button of [SelectionBottomBar]: an icon over a short label. */
data class BarAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** The bar of actions for the selection, in place of the bottom tabs while selecting. */
@Composable
fun SelectionBottomBar(actions: List<BarAction>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (action in actions) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = action.onClick) { Icon(action.icon, contentDescription = action.label) }
                    // The button already reads the label aloud.
                    Text(action.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }
}
