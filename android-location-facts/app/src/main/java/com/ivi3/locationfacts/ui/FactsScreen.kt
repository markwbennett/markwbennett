package com.ivi3.locationfacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivi3.locationfacts.Stage
import com.ivi3.locationfacts.UiState
import com.ivi3.locationfacts.ai.Fact
import com.ivi3.locationfacts.ai.FactsResult
import com.ivi3.locationfacts.ai.Source

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactsScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onGrantPermission: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onForgetApiKey: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? UiState.Ready)?.place?.label ?: "Where am I?",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state is UiState.Ready || state is UiState.Failed) {
                        TextButton(onClick = onRefresh) { Text("Refresh") }
                    }
                },
            )
        }
    ) { padding ->
        when (state) {
            is UiState.NeedsApiKey -> ApiKeyPane(
                modifier = Modifier.padding(padding),
                onSave = onSaveApiKey,
            )

            is UiState.NeedsPermission -> MessagePane(
                modifier = Modifier.padding(padding),
                title = "This app needs your location",
                body = if (state.permanentlyDenied) {
                    "Location permission is denied. Grant it in Settings → Apps → Location Facts, " +
                        "then come back and tap Try again."
                } else {
                    "It looks up what is interesting about the exact spot you are standing on, " +
                        "so it needs a location fix. Nothing is stored or sent anywhere except " +
                        "the place description in the request to the Claude API."
                },
                actionLabel = if (state.permanentlyDenied) "Try again" else "Continue",
                onAction = onGrantPermission,
            )

            is UiState.Loading -> LoadingPane(
                modifier = Modifier.padding(padding),
                stage = state.stage,
            )

            is UiState.Failed -> MessagePane(
                modifier = Modifier.padding(padding),
                title = "That didn't work",
                body = state.message,
                actionLabel = if (state.retryable) "Try again" else null,
                onAction = onRefresh,
                secondaryLabel = "Change API key",
                onSecondary = onForgetApiKey,
            )

            is UiState.Ready -> FactsList(
                contentPadding = padding,
                result = state.result,
                onOpenSource = onOpenSource,
            )
        }
    }
}

@Composable
private fun FactsList(
    contentPadding: PaddingValues,
    result: FactsResult,
    onOpenSource: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result.searchCount == 0) {
            item {
                Card {
                    Text(
                        text = "No web searches ran for this answer, so none of it is sourced. " +
                            "Treat it as a starting point, not a fact.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        items(result.facts) { fact ->
            FactCard(fact = fact, onOpenSource = onOpenSource)
        }

        result.note?.let { note ->
            item {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            Text(
                text = "${result.facts.size} facts · ${result.searchCount} web searches · ${result.model}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FactCard(fact: Fact, onOpenSource: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (fact.title.isNotEmpty()) {
                Text(
                    text = fact.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(text = fact.body, style = MaterialTheme.typography.bodyMedium)

            if (fact.sources.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No source cited for this one",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                fact.sources.forEach { source ->
                    SourceChip(source = source, onClick = { onOpenSource(source.url) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: Source, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = source.title?.takeIf { it.isNotBlank() } ?: source.url.removePrefix("https://"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun LoadingPane(modifier: Modifier = Modifier, stage: Stage) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(text = stage.label, style = MaterialTheme.typography.bodyLarge)
        if (stage == Stage.ASKING_CLAUDE) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This part takes a while — it is reading the web, not guessing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessagePane(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.padding(start = 8.dp)) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun ApiKeyPane(modifier: Modifier = Modifier, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Anthropic API key", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "The app calls the Claude API directly, so it needs your own key from " +
                "console.anthropic.com. It is encrypted with a key held in this device's " +
                "keystore — but a key on a phone is still a key you are handing out, so use " +
                "one you can rotate, and read the note in the README before shipping this to " +
                "anyone else.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("sk-ant-…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(key) },
            enabled = key.trim().length > 10,
        ) {
            Text("Save and find facts")
        }
    }
}
