package ru.trenazher.core

/** Один вопрос билета. [correct] — номера верных вариантов в [options]. */
data class Question(
    val id: String,
    val n: Int,
    val sub: String,
    val text: String,
    val multi: Int,
    val options: List<String>,
    val correct: Set<Int>,
    val basis: String,
    val note: String,
) {
    fun isRight(chosen: Set<Int>) = chosen == correct
}

/** Правила экзамена по разделу: сколько вопросов, минут и верных ответов нужно. */
data class TicketRules(val size: Int = 20, val minutes: Int = 20, val pass: Int = 18)

/** Раздел аттестации (А.1, Б.7 и т. д.) со всеми вопросами. */
data class Section(
    val code: String,
    val title: String,
    val order: String,
    val since: String,
    val rules: TicketRules,
    val questions: List<Question>,
) {
    /** Буква группы: А, Б, В или Г. */
    val group: String get() = code.take(1)

    /** Подобласти в порядке появления, например Б.9.1, Б.9.2. */
    val subs: List<String> get() = questions.map { it.sub }.distinct()

    companion object {
        fun parse(text: String): Section {
            @Suppress("UNCHECKED_CAST")
            val m = Json.parse(text) as Map<String, Any?>
            val code = m.str("code")
            val t = m["ticket"] as? Map<String, Any?>
            val rules = if (t == null) TicketRules() else TicketRules(
                size = (t["size"] as? Number)?.toInt() ?: 20,
                minutes = (t["minutes"] as? Number)?.toInt() ?: 20,
                pass = (t["pass"] as? Number)?.toInt() ?: 18,
            )
            val qs = (m["questions"] as List<*>).mapIndexedNotNull { idx, raw ->
                val q = raw as Map<*, *>
                val options = (q["options"] as? List<*>)?.map { it.toString() }.orEmpty()
                val correct = (q["correct"] as? List<*>)?.map { (it as Number).toInt() }?.toSet().orEmpty()
                // Вопрос без вариантов или с неверными номерами в тренажёр не попадает.
                if (options.size < 2 || correct.isEmpty() || correct.any { it !in options.indices }) return@mapIndexedNotNull null
                val n = (q["n"] as? Number)?.toInt() ?: (idx + 1)
                Question(
                    id = "$code#$n",
                    n = n,
                    sub = (q["sub"] as? String)?.ifBlank { null } ?: code,
                    text = q["q"].toString().trim(),
                    multi = (q["multi"] as? Number)?.toInt() ?: correct.size,
                    options = options,
                    correct = correct,
                    basis = (q["basis"] as? String).orEmpty(),
                    note = (q["note"] as? String).orEmpty(),
                )
            }
            return Section(
                code = code,
                title = m.str("title"),
                order = (m["order"] as? String).orEmpty(),
                since = (m["since"] as? String).orEmpty(),
                rules = rules,
                questions = qs,
            )
        }

        private fun Map<String, Any?>.str(k: String) =
            (this[k] as? String) ?: throw IllegalArgumentException("нет поля $k")
    }
}
