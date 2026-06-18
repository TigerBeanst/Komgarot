package fail.tiger.komgarot.ui.aitranslation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTranslationTaskScreen(
    vm: AiTranslationTaskViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_translation_tasks)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = { if (vm.state.paused) vm.resumeAll() else vm.pauseAll() }) {
                    Text(stringResource(if (vm.state.paused) R.string.ai_translation_resume_all else R.string.ai_translation_pause_all))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.refresh))
                }
            }
            if (vm.state.tasks.isEmpty()) {
                Text(stringResource(R.string.ai_translation_no_tasks), modifier = Modifier.padding(16.dp))
            } else {
                vm.state.tasks.forEach { task ->
                    ListItem(
                        headlineContent = { Text(task.title.ifBlank { task.bookId }) },
                        supportingContent = {
                            Text(stringResource(R.string.ai_translate_progress, task.completedPages, task.pageCount, task.failedPages))
                        }
                    )
                }
            }
        }
    }
}
