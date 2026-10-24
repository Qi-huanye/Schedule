@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.wakeuppure.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wakeuppure.domain.model.Todo
import dev.wakeuppure.ui.components.SheetTitle
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodoListSheet(title: String, todos: List<Todo>, onDismiss: () -> Unit, onTodo: (Todo) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetTitle(title)
            todos.forEach { todo ->
                Surface(onClick = { onDismiss(); onTodo(todo) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(todo.title, style = MaterialTheme.typography.titleMedium)
                        val due = Instant.ofEpochMilli(todo.dueAt).atZone(ZoneId.systemDefault())
                        Text(due.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) + if (todo.completed) " 已完成" else " 截止", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
