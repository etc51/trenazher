package ru.trenazher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import ru.trenazher.core.ExamResult
import ru.trenazher.data.Repo
import kotlin.random.Random

/**
 * Только в отладочной сборке, для проверки и скриншотов:
 * adb shell am broadcast -n <пакет>/ru.trenazher.debug.DemoDataReceiver -a ru.trenazher.debug.SEED
 * SEED — правдоподобный прогресс в нескольких разделах, CLEAR — чистый старт.
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val repo = Repo.get(context)
                runBlocking {
                    repo.progress.stats.clear()
                    repo.progress.exams.clear()
                    repo.progress.position.clear()
                    if (intent.action == "ru.trenazher.debug.SEED") seed(repo)
                    repo.lastSection = if (intent.action == "ru.trenazher.debug.SEED") repo.index.firstOrNull()?.code else null
                    repo.changed()
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private suspend fun seed(repo: Repo) {
        val rnd = Random(7)
        // Первый раздел изучен на две трети, ещё пара — начата.
        val shares = listOf(0.68, 0.32, 0.12, 0.05)
        repo.index.take(shares.size).forEachIndexed { k, info ->
            val s = repo.section(info.code)
            val n = (s.questions.size * shares[k]).toInt()
            s.questions.take(n).forEach { q -> repo.progress.answer(q, rnd.nextFloat() > 0.12f) }
            repo.progress.position[s.code] = n
        }
        repo.index.firstOrNull()?.let {
            val now = System.currentTimeMillis()
            repo.progress.exams += ExamResult(it.code, now - 26 * 3_600_000L, 16, 20, false, 1010)
            repo.progress.exams += ExamResult(it.code, now - 2 * 3_600_000L, 19, 20, true, 845)
        }
    }
}
