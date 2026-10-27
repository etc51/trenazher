package ru.trenazher.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    private fun sample(n: Int = 60): String {
        val qs = (1..n).joinToString(",") { i ->
            val sub = if (i <= n / 3) "Б.9.1" else "Б.9.2"
            """{"n":$i,"sub":"$sub","q":"Вопрос \"$i\"\nстрока","multi":${if (i % 7 == 0) 2 else 1},"options":["а","б","в","г"],"correct":${if (i % 7 == 0) "[1,3]" else "[2]"},"basis":"ст. 2 116-ФЗ"}"""
        }
        return """{"code":"Б.9","title":"Подъёмные сооружения","order":"57-рп от 10.07.2024","since":"2024-09-01","questions":[$qs,{"n":999,"q":"битый","options":["x"],"correct":[5]}]}"""
    }

    @Test fun parsesSectionAndSkipsBrokenQuestions() {
        val s = Section.parse(sample())
        assertEquals("Б.9", s.code)
        assertEquals("Б", s.group)
        assertEquals(60, s.questions.size)
        assertEquals("Вопрос \"1\"\nстрока", s.questions[0].text)
        assertEquals(listOf("Б.9.1", "Б.9.2"), s.subs)
        assertEquals(TicketRules(), s.rules)
        val q7 = s.questions[6]
        assertEquals(setOf(1, 3), q7.correct)
        assertTrue(q7.isRight(setOf(3, 1)))
        assertFalse(q7.isRight(setOf(1)))
    }

    @Test fun ticketIsUniqueAndSized() {
        val s = Section.parse(sample())
        repeat(50) { seed ->
            val t = Tickets.exam(s, Random(seed))
            assertEquals(20, t.size)
            assertEquals(20, t.map { it.id }.toSet().size)
            assertTrue(t.count { it.sub == "Б.9.1" } >= 6)
        }
        val small = s.copy(questions = s.questions.take(5))
        assertEquals(5, Tickets.exam(small).size)
    }

    @Test fun progressRoundTrip() {
        val s = Section.parse(sample())
        val p = Progress()
        p.answer(s.questions[0], true)
        p.answer(s.questions[1], false)
        p.answer(s.questions[1], false)
        p.exams += ExamResult("Б.9", 1_700_000_000_000, 18, 20, true, 600)
        p.position["Б.9"] = 12
        val r = Progress.fromJson(p.toJson())
        assertEquals(Stat(0, 2, false), r.stat(s.questions[1]))
        assertEquals(1, r.learned(s))
        assertEquals(listOf(s.questions[1]), r.mistakes(s))
        assertEquals(p.exams, r.exams)
        assertEquals(12, r.position["Б.9"])
        assertEquals(0, Progress.fromJson("{oops").stats.size)
    }

    @Test fun jsonEscapes() {
        val v = Json.parse("""{"a":"А\t\\","b":[1,-2.5,true,null]}""") as Map<*, *>
        assertEquals("А\t\\", v["a"])
        assertEquals(listOf(1, -2.5, true, null), v["b"])
        assertEquals(v, Json.parse(Json.write(v)))
    }
}
