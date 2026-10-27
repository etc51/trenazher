package ru.trenazher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.trenazher.core.ExamResult
import ru.trenazher.core.Json
import ru.trenazher.core.Progress
import ru.trenazher.core.Question
import ru.trenazher.core.Section
import ru.trenazher.ui.ThemeMode
import java.io.File

/** Короткая запись о разделе для главного экрана: без загрузки всех вопросов. */
data class SectionInfo(val code: String, val title: String, val count: Int, val order: String, val file: String) {
    val group: String get() = code.take(1)
}

/**
 * База вопросов лежит в assets/baza: index.json со списком разделов и по файлу на раздел.
 * Прогресс и настройки хранятся только на телефоне.
 */
class Repo private constructor(private val context: Context) {

    val index: List<SectionInfo> by lazy { readIndex() }

    private val cache = HashMap<String, Section>()
    private val mutex = Mutex()

    private val progressFile = File(context.filesDir, "progress.json")
    val progress: Progress = Progress.fromJson(progressFile.takeIf { it.exists() }?.readText())

    /** Меняется при каждом ответе: экраны со статистикой перерисовываются. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val textScale = MutableStateFlow(prefs.getFloat("textScale", 1f))
    val theme = MutableStateFlow(runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM))

    fun setTextScale(v: Float) {
        textScale.value = v
        prefs.edit().putFloat("textScale", v).apply()
    }

    fun setTheme(v: ThemeMode) {
        theme.value = v
        prefs.edit().putString("theme", v.name).apply()
    }

    private fun readIndex(): List<SectionInfo> {
        val text = context.assets.open("baza/index.json").bufferedReader().use { it.readText() }
        return (Json.parse(text) as List<*>).map {
            val m = it as Map<*, *>
            SectionInfo(
                code = m["code"].toString(),
                title = m["title"].toString(),
                count = (m["count"] as Number).toInt(),
                order = (m["order"] as? String).orEmpty(),
                file = m["file"].toString(),
            )
        }
    }

    fun info(code: String) = index.firstOrNull { it.code == code }

    fun cached(code: String): Section? = synchronized(cache) { cache[code] }

    suspend fun section(code: String): Section = mutex.withLock {
        cached(code) ?: withContext(Dispatchers.IO) {
            val info = info(code) ?: error("Нет раздела $code")
            val text = context.assets.open("baza/${info.file}").bufferedReader().use { it.readText() }
            Section.parse(text).also { synchronized(cache) { cache[code] = it } }
        }
    }

    /** Поиск по тексту вопросов всех разделов. Первый раз загружает всю базу. */
    suspend fun search(query: String, limit: Int = 100): List<Pair<Section, Question>> {
        val words = query.lowercase().split(' ', ',', '.').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Section, Question>>()
        for (info in index) {
            val s = section(info.code)
            withContext(Dispatchers.Default) {
                for (q in s.questions) {
                    val t = q.text.lowercase()
                    if (words.all { it in t }) {
                        out += s to q
                        if (out.size >= limit) break
                    }
                }
            }
            if (out.size >= limit) break
        }
        return out
    }

    /** Изучено и ошибок по разделу без загрузки вопросов: id вопроса начинается с кода раздела. */
    fun learned(code: String) = progress.stats.count { (k, v) -> v.lastRight && k.startsWith("$code#") }

    fun mistakes(code: String) = progress.stats.count { (k, v) -> !v.lastRight && k.startsWith("$code#") }

    /** Раздел, который открывали последним: для кнопки «Продолжить». */
    var lastSection: String?
        get() = prefs.getString("last", null)?.takeIf { info(it) != null }
        set(v) { prefs.edit().putString("last", v).apply() }

    fun answer(q: Question, ok: Boolean) {
        progress.answer(q, ok)
        changed()
    }

    fun saveExam(r: ExamResult) {
        progress.exams += r
        changed()
    }

    fun setPosition(code: String, i: Int) {
        progress.position[code] = i
        changed()
    }

    fun resetSection(s: Section) {
        s.questions.forEach { progress.stats.remove(it.id) }
        progress.exams.removeAll { it.section == s.code }
        progress.position.remove(s.code)
        changed()
    }

    /** Перерисовать экраны и сохранить прогресс на диск. */
    fun changed() {
        _version.value++
        val json = progress.toJson()
        // Запись в фоне: файл небольшой, а ответ не должен ждать диска.
        Thread {
            synchronized(progressFile) {
                val tmp = File(progressFile.path + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(progressFile)
            }
        }.start()
    }

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo =
            instance ?: synchronized(this) { instance ?: Repo(context.applicationContext).also { instance = it } }
    }
}
