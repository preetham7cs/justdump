package io.github.preetham7cs.justdump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.preetham7cs.justdump.data.Note
import io.github.preetham7cs.justdump.ui.NotesUiState
import io.github.preetham7cs.justdump.ui.NotesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {
    private val notesViewModel: NotesViewModel by viewModels {
        NotesViewModel.factory((application as JustDumpApplication).notesRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JustDumpApp(notesViewModel)
        }
    }
}

@Composable
private fun JustDumpApp(viewModel: NotesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveNotice?.id) {
        val notice = uiState.saveNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.message)
        viewModel.onSaveNoticeShown(notice.id)
    }

    MaterialTheme {
        if (uiState.selectedNoteId == null) {
            CaptureAndRecentsScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onDraftChanged = viewModel::onDraftChanged,
                onSave = viewModel::saveDraftAsNote,
                onOpenNote = viewModel::openNote,
            )
        } else {
            NoteDetailScreen(
                note = uiState.selectedNote,
                onBack = viewModel::returnToCapture,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CaptureAndRecentsScreen(
    uiState: NotesUiState,
    snackbarHostState: SnackbarHostState,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("JustDump") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Capture a note",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.draftText,
                    onValueChange = onDraftChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    enabled = uiState.draftReady,
                    label = { Text("Write or paste a note") },
                    placeholder = { Text("Drop a thought, quote, or reminder…") },
                    supportingText = {
                        Text("Drafts are saved locally after a short pause.")
                    },
                    isError = uiState.inputError != null,
                )
                uiState.inputError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                uiState.saveError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSave,
                    enabled = uiState.draftReady && !uiState.isSaving,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.isSaving) "Saving" else "Save")
                }
            }

            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recent notes",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (uiState.recentNotes.isEmpty()) {
                item {
                    Text(
                        text = "Saved notes will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(uiState.recentNotes, key = Note::id) { note ->
                    NoteRow(note = note, onClick = { onOpenNote(note.id) })
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.content.lineSequence().firstOrNull { it.isNotBlank() } ?: "Untitled note",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content.replace('\n', ' '),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatTimestamp(note.createdAtEpochMillis),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NoteDetailScreen(
    note: Note?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Note") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        if (note == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("This note is unavailable.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text("Saved on this device", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatTimestamp(note.createdAtEpochMillis), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(note.content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

@Preview(showBackground = true)
@Composable
private fun CaptureAndRecentsPreview() {
    MaterialTheme {
        CaptureAndRecentsScreen(
            uiState = NotesUiState(draftReady = true),
            snackbarHostState = remember { SnackbarHostState() },
            onDraftChanged = {},
            onSave = {},
            onOpenNote = {},
        )
    }
}
