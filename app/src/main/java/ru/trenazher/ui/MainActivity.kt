package ru.trenazher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.trenazher.data.Repo

/**
 * Навигация — стопка строк-маршрутов, чтобы пережить поворот экрана:
 * home, search, section:КОД, quiz:КОД:РЕЖИМ, result.
 */
class MainActivity : ComponentActivity() {

    private val stack = mutableStateListOf("home")

    /** Итог последнего экзамена для экрана result. После выгрузки процесса не восстанавливается. */
    private var outcome by mutableStateOf<ExamOutcome?>(null)

    private var settings by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList("stack")?.let {
            stack.clear()
            stack += it.filter { r -> r != "result" }.ifEmpty { listOf("home") }
        }
        intent.getStringExtra(EXTRA_SCREEN)?.let { openFromIntent(it) }
        val repo = Repo.get(this)
        setContent {
            val scale by repo.textScale.collectAsState()
            val theme by repo.theme.collectAsState()
            TrenazherTheme(theme, scale) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    App(repo)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SCREEN)?.let { openFromIntent(it) }
    }

    /** Для проверки и скриншотов: открыть экран сразу, например section:А.1 или quiz:А.1:order. */
    private fun openFromIntent(route: String) {
        stack.clear()
        stack += "home"
        if (route != "home") {
            val code = route.split(':').getOrNull(1)
            if (route.startsWith("quiz:") && code != null) stack += "section:$code"
            stack += route
        }
        if (intent.getBooleanExtra(EXTRA_SETTINGS, false)) settings = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("stack", ArrayList(stack))
    }

    private fun push(r: String) {
        stack += r
    }

    private fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
    }

    private fun replaceTop(r: String) {
        stack[stack.lastIndex] = r
    }

    @Composable
    private fun App(repo: Repo) {
        val route = stack.last()
        repo.version.collectAsState().value
        BackHandler(enabled = stack.size > 1 && !route.startsWith("quiz:")) { pop() }

        AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { r ->
            val parts = r.split(':')
            when (parts[0]) {
                "search" -> SearchScreen(
                    repo,
                    initial = parts.getOrNull(1).orEmpty(),
                    onBack = ::pop,
                    onOpen = { s, q -> push("quiz:${s.code}:one=${q.id}") },
                )
                "section" -> SectionScreen(repo, parts[1], onBack = ::pop, onStart = { m -> push("quiz:${parts[1]}:${m.key}") })
                "quiz" -> QuizScreen(
                    repo,
                    parts[1],
                    Mode.parse(parts.drop(2).joinToString(":")),
                    onClose = ::pop,
                    onExamDone = {
                        outcome = it
                        replaceTop("result")
                    },
                )
                "result" -> {
                    val o = outcome
                    if (o == null) {
                        LaunchedEffect(Unit) { pop() }
                    } else {
                        ResultScreen(
                            o,
                            onAgain = { replaceTop("quiz:${o.section.code}:exam") },
                            onClose = ::pop,
                        )
                    }
                }
                else -> HomeScreen(
                    repo,
                    onOpen = { push("section:$it") },
                    onSearch = { push("search") },
                    onSettings = { settings = true },
                )
            }
        }
        if (settings) {
            SettingsSheet(repo, onWrite = { writeUs(this, "Тренажёр аттестации") }, onDismiss = { settings = false })
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_SETTINGS = "settings"
    }
}

/** Письмо разработчику: почтовое приложение с заполненной темой. */
fun writeUs(context: Context, subject: String, body: String = "") {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Нет почтового приложения. Напишите на $CONTACT_EMAIL", Toast.LENGTH_LONG).show()
    }
}
