package studio.pyreels.core

import studio.pyreels.core.speech.ArabicNumbers
import studio.pyreels.core.speech.SpeakCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeakCodeTest {
    @Test fun assignment() = assertEquals("إكس يساوي خمسة", SpeakCode.readLine("x = 5"))
    @Test fun equality() = assertEquals("إكس يطابق خمسة", SpeakCode.readLine("x == 5"))
    @Test fun forRange() = assertEquals("لكل آي في المدى من صفر إلى ثلاثة", SpeakCode.readLine("for i in range(3):"))
    @Test fun printCall() = assertTrue(SpeakCode.readLine("print(x)").startsWith("دالة الطباعة"))
    @Test fun indentation() = assertTrue(SpeakCode.readLine("    print(i)").startsWith("مسافة بادئة"))
    @Test fun colon() = assertTrue(SpeakCode.readLine("a:").contains("نقطتان"))
    @Test fun strings() = assertEquals("دالة الطباعة لـ النص «مرحبا»", SpeakCode.readLine("print(\"مرحبا\")"))
    @Test fun mixedSentence() {
        val s = SpeakCode.toSpeech("بالضبط! ونكتبها هكذا: `x = 5` 😊")
        assertEquals("بالضبط! ونكتبها هكذا: إكس يساوي خمسة", s)
    }
    @Test fun numbers() {
        assertEquals("واحد وعشرون", ArabicNumbers.words(21))
        assertEquals("مائة وخمسة", ArabicNumbers.words(105))
        assertEquals("ألفان", ArabicNumbers.words(2000))
        assertEquals("ثلاثة فاصلة خمسة", ArabicNumbers.read("3.5"))
    }
    @Test fun snakeCase() = assertEquals("user age", SpeakCode.identifier("user_age"))
    @Test fun rulesAreData() {
        val custom = SpeakCode.load("""{"language":"python","locale":"ar","operators":{"=":"تساوي"},"letters":{"x":"س"}}""")
        assertEquals("س تساوي خمسة", SpeakCode.readLine("x = 5", custom))
    }
}
