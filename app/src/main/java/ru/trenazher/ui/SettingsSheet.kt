package ru.trenazher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.trenazher.data.Repo

const val CONTACT_EMAIL = "k@etc51.ru"

/** Настройки: размер текста, тема и сведения о приложении. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(repo: Repo, onWrite: () -> Unit, onDismiss: () -> Unit) {
    val scale by repo.textScale.collectAsState()
    val theme by repo.theme.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            Text("Размер текста", style = MaterialTheme.typography.titleMedium)
            Text(
                "Так будут выглядеть вопросы. Сейчас ${(scale * 100).toInt()}%.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = scale,
                onValueChange = { repo.setTextScale((it * 20).toInt() / 20f) },
                valueRange = 0.85f..1.4f,
                steps = 10,
            )
            Spacer(Modifier.height(12.dp))

            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(
                        selected = theme == m,
                        onClick = { repo.setTheme(m) },
                        label = { Text(m.title, maxLines = 1, softWrap = false) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Text("О приложении", style = MaterialTheme.typography.titleMedium)
            Text(
                "Вопросы взяты из распоряжений Ростехнадзора об утверждении вопросов тестирования. " +
                    "Варианты ответов составлены по нормативным документам, к каждому указано основание. " +
                    "Приложение не является официальным приложением Ростехнадзора. " +
                    "Работает без интернета, ничего не собирает и не показывает рекламу.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Нашли ошибку в ответе? Напишите нам:", style = MaterialTheme.typography.bodyMedium)
            BigButton(CONTACT_EMAIL, null, onClick = onWrite, filled = false, modifier = Modifier.fillMaxWidth())
        }
    }
}
