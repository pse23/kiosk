package com.shapeshed.kiosk.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shapeshed.kiosk.KioskApp
import com.shapeshed.kiosk.R
import com.shapeshed.kiosk.data.SwipeAction
import kotlinx.coroutines.launch

/** Pick what swiping a story row right and left does. */
@Composable
internal fun SwipeSettingsPane(modifier: Modifier = Modifier) {
    val settings = (LocalContext.current.applicationContext as KioskApp).settings
    val scope = rememberCoroutineScope()
    val right by settings.swipeRightAction.collectAsStateWithLifecycle(SwipeAction.SAVE)
    val left by settings.swipeLeftAction.collectAsStateWithLifecycle(SwipeAction.HIDE)

    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.swipe_actions_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwipeActionPicker(R.string.swipe_right, right) { scope.launch { settings.setSwipeRightAction(it) } }
        SwipeActionPicker(R.string.swipe_left, left) { scope.launch { settings.setSwipeLeftAction(it) } }
    }
}

@Composable
private fun SwipeActionPicker(
    @StringRes title: Int,
    selected: SwipeAction,
    onSelect: (SwipeAction) -> Unit,
) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    val options = SwipeAction.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, action ->
            SegmentedButton(
                selected = action == selected,
                onClick = { onSelect(action) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        stringResource(
                            when (action) {
                                SwipeAction.NONE -> R.string.swipe_none
                                SwipeAction.SAVE -> R.string.save
                                SwipeAction.HIDE -> R.string.hide
                            },
                        ),
                    )
                },
            )
        }
    }
}
