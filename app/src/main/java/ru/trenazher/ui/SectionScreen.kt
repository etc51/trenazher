package ru.trenazher.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.trenazher.core.Section
import ru.trenazher.data.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Режим тренировки. [sub] — для занятий по одной подобласти. */
sealed class Mode(val key: String) {
    data object Order : Mode("order")
    data object Random : Mode("random")
    data object Mistakes : Mode("mistakes")
    data object Exam : Mode("exam")
    data class Sub(val sub: String) : Mode("sub=$sub")
    /** Один вопрос, открытый из поиска. */
    data class One(val id: String) : Mode("one=$id")

    companion object {
        fun parse(s: String): Mode = when {
            s == "order" -> Order
            s == "random" -> Random
            s == "mistakes" -> Mistakes
            s == "exam" -> Exam
            s.startsWith("sub=") -> Sub(s.removePrefix("sub="))
            s.startsWith("one=") -> One(s.removePrefix("one="))
            else -> Order
        }
    }
}

@Composable
fun SectionScreen(repo: Repo, code: String, onBack: () -> Unit, onStart: (Mode) -> Unit) {
    val section by produceState<Section?>(repo.cached(code), code) { value = repo.section(code) }
    LaunchedEffect(code) { repo.lastSection = code }
    repo.version.collectAsState().value // перерисовка после ответов
    val info = repo.info(code)

    Column(Modifier.fillMaxSize()) {
        TopBar(title = code, subtitle = info?.title, onBack = onBack)
        val s = section
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        SectionBody(repo, s, onStart)
    }
}

@Composable
private fun SectionBody(repo: Repo, s: Section, onStart: (Mode) -> Unit) {
    val p = repo.progress
    val learned = p.learned(s)
    val mistakes = p.mistakes(s).size
    val last = p.lastExam(s.code)
    val pos = (p.position[s.code] ?: 0).coerceIn(0, (s.questions.size - 1).coerceAtLeast(0))
    var askReset by remember { mutableStateOf(false) }
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val subs = s.subs

    Centered(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = nav.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(s.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
                if (s.order.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Вопросы по распоряжению Ростехнадзора № ${s.order}" + (if (s.since.isNotBlank()) ", действуют с ${formatDate(s.since)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f), padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(learned / s.questions.size.coerceAtLeast(1).toFloat(), label = "${learned * 100 / s.questions.size.coerceAtLeast(1)}%")
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("$learned из ${s.questions.size}", style = MaterialTheme.typography.titleMedium)
                                Text("изучено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    StatTile(
                        value = mistakes.toString(),
                        label = plural(mistakes, "ошибка", "ошибки", "ошибок"),
                        color = if (mistakes > 0) LocalAnswerColors.current.wrong else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.7f),
                        onClick = if (mistakes > 0) ({ onStart(Mode.Mistakes) }) else null,
                    )
                }
            }
            item {
                Card(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text("Пробный экзамен", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${pluralQuestions(minOf(s.rules.size, s.questions.size))} · ${s.rules.minutes} мин · нужно ${s.rules.pass} верных",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (last != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Chip(
                                if (last.passed) "Сдан" else "Не сдан",
                                color = if (last.passed) LocalAnswerColors.current.rightContainer else LocalAnswerColors.current.wrongContainer,
                                textColor = if (last.passed) LocalAnswerColors.current.right else LocalAnswerColors.current.wrong,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "последний: ${last.right} из ${last.total}, ${formatWhen(last.at)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    BigButton("Начать экзамен", Icons.Rounded.Timer, onClick = { onStart(Mode.Exam) }, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Text("Подготовка", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
            item {
                ModeRow(
                    Icons.Rounded.FormatListNumbered,
                    "Все вопросы по порядку",
                    if (pos > 0) "продолжить с вопроса ${pos + 1}" else "с ответом и ссылкой на норму сразу после выбора",
                ) { onStart(Mode.Order) }
            }
            item {
                ModeRow(Icons.Rounded.Shuffle, "Случайные вопросы", "вперемешку, без конца") { onStart(Mode.Random) }
            }
            item {
                ModeRow(
                    Icons.Rounded.ErrorOutline,
                    "Работа над ошибками",
                    if (mistakes > 0) "${pluralQuestions(mistakes)}, где вы ошиблись" else "ошибок пока нет",
                    enabled = mistakes > 0,
                ) { onStart(Mode.Mistakes) }
            }
            if (subs.size > 1) {
                item {
                    Text("Подобласти", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
                items(subs, key = { it }) { sub ->
                    val qs = s.questions.filter { it.sub == sub }
                    val l = qs.count { p.stat(it)?.lastRight == true }
                    Card(onClick = { onStart(Mode.Sub(sub)) }, padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sub, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("$l / ${qs.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Bar(l / qs.size.coerceAtLeast(1).toFloat(), height = 5.dp)
                    }
                }
            }
            item {
                TextButton(onClick = { askReset = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Сбросить прогресс раздела", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Ответы, ошибки и результаты экзаменов по разделу ${s.code} удалятся. Вернуть их будет нельзя.") },
            confirmButton = {
                TextButton(onClick = { repo.resetSection(s); askReset = false }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ModeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Card(onClick = if (enabled) onClick else null, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (enabled) Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 2024-09-01 → 01.09.2024 */
fun formatDate(iso: String): String {
    val p = iso.split('-')
    return if (p.size == 3) "${p[2]}.${p[1]}.${p[0]}" else iso
}

fun formatWhen(at: Long): String = SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(at))
