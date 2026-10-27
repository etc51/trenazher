package ru.trenazher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(outcome: ExamOutcome, onAgain: () -> Unit, onClose: () -> Unit) {
    val r = outcome.result
    val ac = LocalAnswerColors.current
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val wrong = outcome.questions.withIndex().filter { (i, q) -> !q.isRight(outcome.answers[i].orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Результат", subtitle = "${outcome.section.code} ${outcome.section.title}", onBack = onClose, backIcon = Icons.Rounded.Close, backLabel = "Закрыть")
        Centered(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = nav.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(color = if (r.passed) ac.rightContainer else ac.wrongContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(
                                r.right / r.total.coerceAtLeast(1).toFloat(),
                                size = 84.dp,
                                stroke = 9.dp,
                                color = if (r.passed) ac.right else ac.wrong,
                                label = "${r.right}/${r.total}",
                            )
                            Spacer(Modifier.width(18.dp))
                            Column {
                                Text(
                                    if (r.passed) "Экзамен сдан" else "Не сдан",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (r.passed) ac.right else ac.wrong,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Нужно ${outcome.section.rules.pass} верных · время ${r.seconds / 60}:%02d".format(r.seconds % 60),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton("Ещё билет", Icons.Rounded.Refresh, onClick = onAgain, modifier = Modifier.weight(1f))
                        BigButton("К разделу", null, onClick = onClose, filled = false, modifier = Modifier.weight(1f))
                    }
                }
                if (wrong.isNotEmpty()) {
                    item {
                        Text(
                            "Разбор ошибок",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    itemsIndexed(wrong, key = { _, v -> v.value.id }) { _, (i, q) ->
                        val mine = outcome.answers[i].orEmpty()
                        Card {
                            Text("Вопрос ${i + 1} · ${q.sub}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Text(cleanQuestion(q.text), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(12.dp))
                            if (mine.isEmpty()) {
                                Text("Без ответа", style = MaterialTheme.typography.bodyMedium, color = ac.wrong)
                            } else {
                                mine.forEach { AnswerLine(q.options[it], right = it in q.correct) }
                            }
                            q.correct.filter { it !in mine }.forEach { AnswerLine(q.options[it], right = true) }
                            if (q.basis.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Основание: ${q.basis}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerLine(text: String, right: Boolean) {
    val ac = LocalAnswerColors.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (right) ac.right else ac.wrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (right) Icons.Rounded.Check else Icons.Rounded.Close, contentDescription = if (right) "Правильно" else "Ваш ответ, неверно", tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
