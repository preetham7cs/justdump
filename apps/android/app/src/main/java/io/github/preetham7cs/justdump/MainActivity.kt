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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.preetham7cs.justdump.data.Note
import io.github.preetham7cs.justdump.data.NoteSyncStatus
import io.github.preetham7cs.justdump.ui.AccountUiState
import io.github.preetham7cs.justdump.ui.AccountViewModel
import io.github.preetham7cs.justdump.ui.NotesUiState
import io.github.preetham7cs.justdump.ui.NotesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {
    private val notesViewModel: NotesViewModel by viewModels {
        val app = application as JustDumpApplication
        NotesViewModel.factory(app.notesRepository, app.noteSyncCoordinator)
    }
    private val accountViewModel: AccountViewModel by viewModels {
        AccountViewModel.factory((application as JustDumpApplication).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JustDumpApp(notesViewModel, accountViewModel)
        }
    }
}

@Composable
private fun JustDumpApp(
    viewModel: NotesViewModel,
    accountViewModel: AccountViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accountUiState by accountViewModel.uiState.collectAsStateWithLifecycle()
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
                onUploadNote = viewModel::uploadNote,
                accountUiState = accountUiState,
                onEmailChanged = accountViewModel::onEmailChanged,
                onPasswordChanged = accountViewModel::onPasswordChanged,
                onSignIn = accountViewModel::signIn,
                onSignOut = accountViewModel::signOut,
            )
        } else {
            NoteDetailScreen(
                note = uiState.selectedNote,
                onBack = viewModel::returnToCapture,
                onUploadNote = viewModel::uploadNote,
                isSyncing = uiState.syncingNoteId == uiState.selectedNoteId,
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
    onUploadNote: (String) -> Unit,
    accountUiState: AccountUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
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
                AccountCard(
                    state = accountUiState,
                    onEmailChanged = onEmailChanged,
                    onPasswordChanged = onPasswordChanged,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                )
            }

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
                    NoteRow(
                        note = note,
                        onClick = { onOpenNote(note.id) },
                        onUpload = { onUploadNote(note.id) },
                        isSyncing = uiState.syncingNoteId == note.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onUpload: () -> Unit,
    isSyncing: Boolean,
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(syncLabel(note), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onUpload, enabled = !isSyncing) {
                Text(
                    when {
                        isSyncing -> "Uploading"
                        note.syncStatus == NoteSyncStatus.SYNCED -> "Verify cloud copy again"
                        else -> "Upload and verify"
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NoteDetailScreen(
    note: Note?,
    onBack: () -> Unit,
    onUploadNote: (String) -> Unit,
    isSyncing: Boolean,
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(syncLabel(note), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { onUploadNote(note.id) }, enabled = !isSyncing) {
                    Text(
                        when {
                            isSyncing -> "Uploading"
                            note.syncStatus == NoteSyncStatus.SYNCED -> "Verify cloud copy again"
                            else -> "Upload and verify"
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatTimestamp(note.createdAtEpochMillis), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(note.content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun AccountCard(
    state: AccountUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cloud account", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            val signedInEmail = state.signedInEmail
            if (signedInEmail != null) {
                Text("Signed in as $signedInEmail", style = MaterialTheme.typography.bodyMedium)
                Text("Uploads are explicit. Saved notes stay on this device.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onSignOut, enabled = !state.isWorking) { Text("Sign out") }
            } else if (!state.configured) {
                Text("Supabase is not configured on this development build.", color = MaterialTheme.colorScheme.error)
            } else {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = onSignIn, enabled = !state.isWorking) {
                    Text(if (state.isWorking) "Signing in" else "Sign in")
                }
            }
        }
    }
}

private fun syncLabel(note: Note): String = when (note.syncStatus) {
    NoteSyncStatus.PENDING_UPLOAD -> "Saved on this device — upload pending"
    NoteSyncStatus.UPLOADING -> "Uploading and verifying cloud copy…"
    NoteSyncStatus.SYNCED -> "Cloud copy verified — still saved on this device"
    NoteSyncStatus.FAILED -> note.syncError ?: "Upload failed — local note is still available"
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
            onUploadNote = {},
            accountUiState = AccountUiState(configured = true),
            onEmailChanged = {},
            onPasswordChanged = {},
            onSignIn = {},
            onSignOut = {},
        )
    }
}
