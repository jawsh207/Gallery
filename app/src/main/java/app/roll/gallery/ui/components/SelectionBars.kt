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

/** The top bar while selecting: how many are selected, and a button to stop selecting. */
@Composable
fun SelectionTopBar(count: Int, onClear: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_selection)) }
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
