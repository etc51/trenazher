package ru.trenazher.core

import kotlin.random.Random

/** Как человек отвечал на вопрос: сколько раз верно, сколько с ошибкой и как в последний раз. */
data class Stat(val right: Int = 0, val wrong: Int = 0, val lastRight: Boolean = false) {
    fun answered(ok: Boolean) =
        if (ok) copy(right = right + 1, lastRight = true) else copy(wrong = wrong + 1, lastRight = false)
}

/** Результат одного пробного экзамена. */
data class ExamResult(val section: String, val at: Long, val right: Int, val total: Int, val passed: Boolean, val seconds: Int)

/** Прогресс по всем разделам. Хранится одним JSON-файлом на телефоне. */
class Progress(
    val stats: MutableMap<String, Stat> = HashMap(),
    val exams: MutableList<ExamResult> = ArrayList(),
    /** Где человек остановился в режиме «По порядку»: код раздела → индекс вопроса. */
    val position: MutableMap<String, Int> = HashMap(),
) {
    fun answer(q: Question, ok: Boolean) {
        stats[q.id] = (stats[q.id] ?: Stat()).answered(ok)
    }

    fun stat(q: Question) = stats[q.id]

    /** Вопросы, на которые в последний раз ответили неверно. */
    fun mistakes(s: Section) = s.questions.filter { stats[it.id]?.lastRight == false }

    /** Сколько вопросов раздела в последний раз отвечены верно. */
    fun learned(s: Section) = s.questions.count { stats[it.id]?.lastRight == true }

    fun lastExam(code: String) = exams.lastOrNull { it.section == code }

    fun toJson(): String = Json.write(
        mapOf(
            "v" to 1,
            "stats" to stats.mapValues { (_, s) -> listOf(s.right, s.wrong, if (s.lastRight) 1 else 0) },
            "exams" to exams.map { listOf(it.section, it.at, it.right, it.total, if (it.passed) 1 else 0, it.seconds) },
            "position" to position,
        )
    )

    companion object {
        fun fromJson(text: String?): Progress {
            if (text.isNullOrBlank()) return Progress()
            return runCatching {
                val m = Json.parse(text) as Map<*, *>
                val p = Progress()
                (m["stats"] as? Map<*, *>)?.forEach { (k, v) ->
                    val l = v as List<*>
                    p.stats[k.toString()] = Stat((l[0] as Number).toInt(), (l[1] as Number).toInt(), (l[2] as Number).toInt() == 1)
                }
                (m["exams"] as? List<*>)?.forEach { e ->
                    val l = e as List<*>
                    p.exams += ExamResult(
                        l[0].toString(), (l[1] as Number).toLong(), (l[2] as Number).toInt(),
                        (l[3] as Number).toInt(), (l[4] as Number).toInt() == 1, (l[5] as Number).toInt(),
                    )
                }
                (m["position"] as? Map<*, *>)?.forEach { (k, v) -> p.position[k.toString()] = (v as Number).toInt() }
                p
            }.getOrElse { Progress() } // Повреждённый файл не должен ронять приложение.
        }
    }
}

object Tickets {
    /**
     * Билет как на экзамене: [TicketRules.size] случайных вопросов раздела,
     * разложенных по подобластям пропорционально их размеру.
     */
    fun exam(s: Section, random: Random = Random.Default): List<Question> {
        val size = minOf(s.rules.size, s.questions.size)
        val bySub = s.questions.groupBy { it.sub }
        val total = s.questions.size.toDouble()
        val picked = ArrayList<Question>()
        // Сначала по доле подобласти (округление вниз), остаток добираем случайно из всех.
        for ((_, qs) in bySub) {
            val k = (qs.size / total * size).toInt()
            picked += qs.shuffled(random).take(k)
        }
        val rest = s.questions.filter { it !in picked }.shuffled(random)
        picked += rest.take(size - picked.size)
        return picked.shuffled(random)
    }

    /** Порядок показа вариантов: перемешан, чтобы запоминали смысл, а не место ответа. */
    fun optionOrder(q: Question, random: Random = Random.Default): List<Int> = q.options.indices.shuffled(random)
}
