package ru.trenazher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.trenazher.core.ExamResult
import ru.trenazher.core.Question
import ru.trenazher.core.Section
import ru.trenazher.core.Tickets
import ru.trenazher.data.Repo

/** Итог экзамена для экрана результатов: вопросы и что выбрал человек. */
class ExamOutcome(val section: Section, val questions: List<Question>, val answers: Map<Int, Set<Int>>, val result: ExamResult)

/**
 * Состояние одного занятия. В режиме подготовки ответ проверяется сразу и показывается норма,
 * на экзамене ответы проверяются только в конце.
 */
class Session(val section: Section, val mode: Mode, val questions: List<Question>, startAt: Int) {
    val exam = mode == Mode.Exam
    var index by mutableIntStateOf(startAt.coerceIn(0, (questions.size - 1).coerceAtLeast(0)))
    /** Выбранные варианты по номеру вопроса, в исходной нумерации вариантов. */
    val chosen = mutableStateMapOf<Int, Set<Int>>()
    /** Вопросы, на которые уже ответили в режиме подготовки. */
    val revealed = mutableStateListOf<Int>()
    /** Порядок показа вариантов для каждого вопроса: перемешан один раз за занятие. */
    private val orders = HashMap<Int, List<Int>>()
    val startedAt = System.currentTimeMillis()
    var deadline by mutableLongStateOf(if (exam) startedAt + section.rules.minutes * 60_000L else 0L)

    /** Экзамен уже завершён: таймер и кнопка не должны записать результат дважды. */
    var finished = false

    fun order(i: Int) = orders.getOrPut(i) { Tickets.optionOrder(questions[i]) }
}

@Composable
fun QuizScreen(repo: Repo, code: String, mode: Mode, onClose: () -> Unit, onExamDone: (ExamOutcome) -> Unit) {
    val section by produceState<Section?>(repo.cached(code), code) { value = repo.section(code) }
    val s = section
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val session = remember(code, mode) {
        val qs = when (mode) {
            Mode.Order -> s.questions
            Mode.Random -> s.questions.shuffled()
            Mode.Mistakes -> repo.progress.mistakes(s).shuffled()
            Mode.Exam -> Tickets.exam(s)
            is Mode.Sub -> s.questions.filter { it.sub == mode.sub }
            is Mode.One -> s.questions.filter { it.id == mode.id }
        }
        val start = if (mode == Mode.Order) repo.progress.position[code] ?: 0 else 0
        Session(s, mode, qs, start)
    }
    if (session.questions.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = code, onBack = onClose)
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Здесь пока нет вопросов", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }
    QuizContent(repo, session, onClose, onExamDone)
}

@Composable
private fun QuizContent(repo: Repo, ss: Session, onClose: () -> Unit, onExamDone: (ExamOutcome) -> Unit) {
    var askQuit by remember { mutableStateOf(false) }
    var askFinish by remember { mutableStateOf(false) }
    val i = ss.index
    val q = ss.questions[i]
    val chosen = ss.chosen[i].orEmpty()
    val revealed = !ss.exam && i in ss.revealed

    fun finishExam() {
        if (ss.finished) return
        ss.finished = true
        val answers = ss.chosen.toMap()
        var right = 0
        ss.questions.forEachIndexed { idx, qq ->
            val ok = qq.isRight(answers[idx].orEmpty())
            if (ok) right++
            repo.answer(qq, ok)
        }
        val secs = ((System.currentTimeMillis() - ss.startedAt) / 1000).toInt()
        val r = ExamResult(ss.section.code, System.currentTimeMillis(), right, ss.questions.size, right >= minOf(ss.section.rules.pass, ss.questions.size), secs)
        repo.saveExam(r)
        onExamDone(ExamOutcome(ss.section, ss.questions, answers, r))
    }

    fun go(to: Int) {
        ss.index = to.coerceIn(0, ss.questions.size - 1)
        if (ss.mode == Mode.Order) repo.setPosition(ss.section.code, ss.index)
    }

    BackHandler { if (ss.exam) askQuit = true else onClose() }

    // Таймер экзамена: по истечении времени экзамен завершается сам.
    var left by remember { mutableLongStateOf(ss.deadline - System.currentTimeMillis()) }
    if (ss.exam) {
        LaunchedEffect(ss) {
            while (true) {
                left = ss.deadline - System.currentTimeMillis()
                if (left <= 0) {
                    finishExam()
                    break
                }
                delay(500)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (ss.exam) "Экзамен" else "Вопрос ${i + 1} из ${ss.questions.size}",
            subtitle = if (ss.exam) "Вопрос ${i + 1} из ${ss.questions.size} · ${ss.section.code}" else "${ss.section.code} · ${modeTitle(ss.mode)}",
            onBack = { if (ss.exam) askQuit = true else onClose() },
            backIcon = Icons.Rounded.Close,
            backLabel = "Закрыть",
        ) {
            if (ss.exam) TimerChip(left)
        }
        Centered {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (ss.exam) {
                    Navigator(ss)
                } else {
                    Bar((i + 1) / ss.questions.size.toFloat(), height = 5.dp)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            LaunchedEffect(i) { scroll.scrollTo(0) }
            // После ответа подтягиваем вердикт и основание в поле зрения: на маленьких экранах они ниже края.
            LaunchedEffect(i, revealed) {
                if (revealed) {
                    delay(250)
                    scroll.animateScrollTo(scroll.maxValue)
                }
            }
            Centered(Modifier.verticalScroll(scroll)) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
                    QuestionBlock(ss, i, q, chosen, revealed) { opt ->
                        if (revealed) return@QuestionBlock
                        val now = chosen.toMutableSet()
                        if (q.multi <= 1) {
                            now.clear()
                            now += opt
                        } else if (!now.remove(opt)) {
                            now += opt
                        }
                        ss.chosen[i] = now
                    }
                    AnimatedVisibility(revealed, enter = fadeIn() + expandVertically()) {
                        Explanation(q, q.isRight(chosen))
                    }
                }
            }
        }
        BottomBar(
            canBack = i > 0,
            onBack = { go(i - 1) },
        ) {
            val last = i == ss.questions.size - 1
            when {
                ss.exam && last -> BigButton("Завершить", Icons.Rounded.Done, onClick = { askFinish = true }, modifier = Modifier.fillMaxWidth())
                ss.exam -> BigButton(
                    if (chosen.isEmpty()) "Пропустить" else "Дальше",
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = { go(i + 1) },
                    filled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                !revealed -> BigButton(
                    if (q.multi > 1 && chosen.size < q.multi) "Выберите ещё ${q.multi - chosen.size}" else "Ответить",
                    Icons.Rounded.Check,
                    onClick = {
                        ss.revealed += i
                        repo.answer(q, q.isRight(chosen))
                    },
                    enabled = chosen.size == q.multi || (q.multi <= 1 && chosen.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth(),
                )
                last -> BigButton("Готово", Icons.Rounded.Done, onClick = onClose, modifier = Modifier.fillMaxWidth())
                else -> BigButton("Следующий вопрос", Icons.AutoMirrored.Rounded.ArrowForward, onClick = { go(i + 1) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (askQuit) {
        AlertDialog(
            onDismissRequest = { askQuit = false },
            title = { Text("Прервать экзамен?") },
            text = { Text("Ответы этого билета не сохранятся.") },
            confirmButton = { TextButton(onClick = { askQuit = false; onClose() }) { Text("Прервать", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { askQuit = false }) { Text("Продолжить") } },
        )
    }
    if (askFinish) {
        val missing = ss.questions.indices.count { ss.chosen[it].isNullOrEmpty() }
        AlertDialog(
            onDismissRequest = { askFinish = false },
            title = { Text("Завершить экзамен?") },
            text = {
                Text(
                    if (missing > 0) "Без ответа: ${pluralQuestions(missing)}. Они будут засчитаны как ошибки."
                    else "Ответы на все вопросы даны. Посмотрим результат?",
                )
            },
            confirmButton = { TextButton(onClick = { askFinish = false; finishExam() }) { Text("Завершить") } },
            dismissButton = { TextButton(onClick = { askFinish = false }) { Text("Вернуться") } },
        )
    }
}

private fun modeTitle(m: Mode) = when (m) {
    Mode.Order -> "по порядку"
    Mode.Random -> "случайные"
    Mode.Mistakes -> "ошибки"
    Mode.Exam -> "экзамен"
    is Mode.Sub -> m.sub
    is Mode.One -> "поиск"
}

@Composable
private fun TimerChip(leftMs: Long) {
    val secs = (leftMs / 1000).coerceAtLeast(0)
    val low = secs < 120
    val color = if (low) LocalAnswerColors.current.wrong else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        Modifier
            .padding(end = 12.dp)
            .clip(CircleShape)
            .background(if (low) LocalAnswerColors.current.wrongContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Осталось ${secs / 60} минут ${secs % 60} секунд" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Timer, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("%d:%02d".format(secs / 60, secs % 60), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Номера вопросов билета: видно, на какие уже ответил, и можно перейти к любому. */
@Composable
private fun Navigator(ss: Session) {
    val state = rememberLazyListState()
    LaunchedEffect(ss.index) { state.animateScrollToItem((ss.index - 3).coerceAtLeast(0)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(ss.questions) { idx, _ ->
            val current = idx == ss.index
            val answered = !ss.chosen[idx].isNullOrEmpty()
            val bg = when {
                current -> MaterialTheme.colorScheme.primary
                answered -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val fg = when {
                current -> MaterialTheme.colorScheme.onPrimary
                answered -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(role = Role.Button) { ss.index = idx }
                    .semantics { contentDescription = "Вопрос ${idx + 1}" + if (answered) ", отвечен" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text("${idx + 1}", style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

@Composable
private fun QuestionBlock(ss: Session, i: Int, q: Question, chosen: Set<Int>, revealed: Boolean, onToggle: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Chip(q.sub)
        Spacer(Modifier.width(8.dp))
        Text("№ ${q.n}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(14.dp))
    Text(cleanQuestion(q.text), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(10.dp))
    Text(
        if (q.multi > 1) "Выберите ${q.multi} варианта ответа" else "Выберите один вариант ответа",
        style = MaterialTheme.typography.bodyMedium,
        color = if (q.multi > 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (q.multi > 1) FontWeight.SemiBold else FontWeight.Normal,
    )
    Spacer(Modifier.height(16.dp))
    val letters = "АБВГДЕЖЗ"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ss.order(i).forEachIndexed { pos, opt ->
            OptionCard(
                letter = letters.getOrElse(pos) { '•' },
                text = q.options[opt],
                selected = opt in chosen,
                state = when {
                    !revealed -> OptionState.Plain
                    opt in q.correct -> OptionState.Right
                    opt in chosen -> OptionState.Wrong
                    else -> OptionState.Dim
                },
                multi = q.multi > 1,
                onClick = { onToggle(opt) },
            )
        }
    }
}

/** Официальный текст часто заканчивается подсказкой «Выберите правильный вариант ответа» — её показываем отдельно. */
fun cleanQuestion(t: String): String =
    t.replace(Regex("\\s*Выберите (правильный вариант ответа|\\d+ вариант[а-я]* ответа)\\.?\\s*$"), "").trim()

private enum class OptionState { Plain, Right, Wrong, Dim }

@Composable
private fun OptionCard(letter: Char, text: String, selected: Boolean, state: OptionState, multi: Boolean, onClick: () -> Unit) {
    val ac = LocalAnswerColors.current
    val cs = MaterialTheme.colorScheme
    val (bg, border, badgeBg, badgeFg) = when (state) {
        OptionState.Right -> listOf(ac.rightContainer, ac.right, ac.right, Color.White)
        OptionState.Wrong -> listOf(ac.wrongContainer, ac.wrong, ac.wrong, Color.White)
        OptionState.Dim -> listOf(cs.surfaceContainerLow, Color.Transparent, cs.surfaceContainerHigh, cs.onSurfaceVariant)
        OptionState.Plain -> if (selected) listOf(cs.primaryContainer, cs.primary, cs.primary, cs.onPrimary)
        else listOf(cs.surfaceContainerLow, cs.outlineVariant, cs.surfaceContainerHigh, cs.onSurfaceVariant)
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(bg)
            .border(if (border == Color.Transparent) 0.dp else 1.5.dp, border, shape)
            .clickable(enabled = state == OptionState.Plain, role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics {
                contentDescription = buildString {
                    append("Вариант $letter: $text")
                    if (selected) append(", выбран")
                    if (state == OptionState.Right) append(", правильный ответ")
                    if (state == OptionState.Wrong) append(", неверно")
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(32.dp).clip(if (multi) RoundedCornerShape(9.dp) else CircleShape).background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                OptionState.Right -> Icon(Icons.Rounded.Check, contentDescription = null, tint = badgeFg, modifier = Modifier.size(20.dp))
                OptionState.Wrong -> Icon(Icons.Rounded.Close, contentDescription = null, tint = badgeFg, modifier = Modifier.size(20.dp))
                else -> Text(letter.toString(), style = MaterialTheme.typography.labelLarge, color = badgeFg)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state == OptionState.Dim) cs.onSurfaceVariant else cs.onSurface,
            modifier = Modifier.weight(1f).padding(top = 3.dp),
        )
    }
}

@Composable
private fun Explanation(q: Question, ok: Boolean) {
    val ac = LocalAnswerColors.current
    Column(Modifier.padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(if (ok) ac.right else ac.wrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(if (ok) "Верно" else "Неверно", style = MaterialTheme.typography.titleMedium, color = if (ok) ac.right else ac.wrong)
        }
        if (q.basis.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Основание", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        Text(q.basis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        val context = LocalContext.current
        TextButton(
            onClick = { writeUs(context, "Ошибка в вопросе ${q.sub} № ${q.n}", "Вопрос: ${cleanQuestion(q.text)}\n\nЧто не так: ") },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Сообщить об ошибке в ответе", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Нижняя панель: «назад» и главная кнопка. Всегда на одном месте, под большим пальцем. */
@Composable
private fun BottomBar(canBack: Boolean, onBack: () -> Unit, main: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Centered {
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = canBack, role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = "Предыдущий вопрос" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = if (canBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) { main() }
            }
        }
    }
}
