package com.shapeshed.kiosk.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shapeshed.kiosk.KioskApp
import com.shapeshed.kiosk.R
import kotlinx.coroutines.launch

/** Manage muted sites and title words; matching lives in `isMuted`. */
@Composable
internal fun MutedPane(modifier: Modifier = Modifier) {
    val settings = (LocalContext.current.applicationContext as KioskApp).settings
    val scope = rememberCoroutineScope()
    val domains by settings.mutedDomains.collectAsStateWithLifecycle(emptySet())
    val keywords by settings.mutedKeywords.collectAsStateWithLifecycle(emptySet())

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.muted_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MuteSection(
            title = R.string.muted_sites,
            hint = R.string.mute_site_hint,
            entries = domains,
            onAdd = { scope.launch { settings.muteDomain(it) } },
            onRemove = { scope.launch { settings.unmuteDomain(it) } },
        )
        MuteSection(
            title = R.string.muted_words,
            hint = R.string.mute_word_hint,
            entries = keywords,
            onAdd = { scope.launch { settings.muteKeyword(it) } },
            onRemove = { scope.launch { settings.unmuteKeyword(it) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuteSection(
    @StringRes title: Int,
    @StringRes hint: Int,
    entries: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by rememberSaveable(title) { mutableStateOf("") }
    val submit = {
        if (input.isNotBlank()) onAdd(input)
        input = ""
    }
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(stringResource(hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = submit, enabled = input.isNotBlank()) {
            Text(stringResource(R.string.add))
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        entries.sorted().forEach { entry ->
            InputChip(
                selected = false,
                onClick = { onRemove(entry) },
                label = { Text(entry) },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.unmute, entry),
                        modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    )
                },
            )
        }
    }
}
