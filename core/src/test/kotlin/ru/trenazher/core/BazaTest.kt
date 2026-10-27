package ru.trenazher.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Проверяет настоящую базу из приложения: битый вопрос должен ловиться до сборки, а не у пользователя. */
class BazaTest {
    private val dir = System.getProperty("baza")?.let(::File)

    private fun files() = dir?.listFiles { f -> f.extension == "json" && f.name != "index.json" }?.sortedBy { it.name }.orEmpty()

    @Test fun everySectionIsValid() {
        val problems = ArrayList<String>()
        for (f in files()) {
            val raw = Json.parse(f.readText()) as Map<*, *>
            val rawCount = (raw["questions"] as List<*>).size
            val s = Section.parse(f.readText())
            if (s.questions.size != rawCount) problems += "${f.name}: пропущено ${rawCount - s.questions.size} вопросов с неверными вариантами"
            val ids = HashSet<String>()
            for (q in s.questions) {
                val where = "${f.name} ${q.sub} № ${q.n}"
                if (!ids.add(q.id)) problems += "$where: повторяется номер"
                if (q.text.isBlank()) problems += "$where: пустой вопрос"
                if (q.options.size < 3) problems += "$where: меньше трёх вариантов"
                if (q.options.map { it.trim().lowercase() }.toSet().size != q.options.size) problems += "$where: одинаковые варианты"
                if (q.options.any { it.isBlank() }) problems += "$where: пустой вариант"
                if (q.multi != q.correct.size) problems += "$where: нужно выбрать ${q.multi}, а верных ${q.correct.size}"
                if (q.correct.size >= q.options.size) problems += "$where: все варианты верные"
                if (q.basis.isBlank()) problems += "$where: нет основания"
            }
        }
        if (problems.isNotEmpty()) fail("Ошибки в базе (${problems.size}):\n" + problems.take(50).joinToString("\n"))
    }

    @Test fun indexMatchesFiles() {
        val d = dir ?: return
        val indexFile = File(d, "index.json")
        if (files().isEmpty()) return
        assertTrue(indexFile.exists(), "нет index.json — запустите tools/make_index.py")
        val index = (Json.parse(indexFile.readText()) as List<*>).associate { m ->
            m as Map<*, *>
            m["file"].toString() to (m["count"] as Number).toInt()
        }
        assertEquals(files().map { it.name }.toSet(), index.keys, "index.json не совпадает со списком файлов")
        for (f in files()) {
            assertEquals(Section.parse(f.readText()).questions.size, index[f.name], "${f.name}: число вопросов в index.json устарело")
        }
    }
}
