package com.example

import com.example.data.auth.CaptchaSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaSolverUnitTests {

    @Test
    fun testValidEquations() {
        assertEquals(42, CaptchaSolver.parseEquation("19+23=?"))
        assertEquals(2, CaptchaSolver.parseEquation("7-5="))
        assertEquals(21, CaptchaSolver.parseEquation("9 + 12 = ?"))
        assertEquals(10, CaptchaSolver.parseEquation("5+5"))
    }

    @Test
    fun testInvalidEquations() {
        assertNull(CaptchaSolver.parseEquation("7x5"))
        assertNull(CaptchaSolver.parseEquation("123+4")) // 3 digits
        assertNull(CaptchaSolver.parseEquation("5-9"))     // negative result (-4)
        assertNull(CaptchaSolver.parseEquation("abc"))
        assertNull(CaptchaSolver.parseEquation(""))
    }

    @Test
    fun testDecideAnswer() {
        // (a1=7, c1=90, a2=null) -> 7
        assertEquals(7, CaptchaSolver.decideAnswer(7, 90, null))
        // (7, 60, 7) -> 7
        assertEquals(7, CaptchaSolver.decideAnswer(7, 60, 7))
        // (7, 60, 8) -> null
        assertNull(CaptchaSolver.decideAnswer(7, 60, 8))
        // (7, 60, null) -> null
        assertNull(CaptchaSolver.decideAnswer(7, 60, null))
        // (null, 90, 7) -> null
        assertNull(CaptchaSolver.decideAnswer(null, 90, 7))
    }

    @Test
    fun testPreprocessingAndDespeckle() {
        val w = 100
        val h = 40
        val ink = BooleanArray(w * h)
        // Synthetic dotted blue glyph: larger connected area
        for (y in 10..20) {
            for (x in 10..40) {
                ink[y * w + x] = true
            }
        }
        // Small noise dots (1-4 pixels)
        ink[2 * w + 2] = true
        ink[35 * w + 95] = true

        val despeckled = CaptchaSolver.despeckle(ink, w, h, minBlobSize = 5)
        // Noise dots should be removed
        assertFalse(despeckled[2 * w + 2])
        assertFalse(despeckled[35 * w + 95])
        // Glyph should survive
        assertTrue(despeckled[15 * w + 25])

        val dilated = CaptchaSolver.directionalDilate(despeckled, w, h, radius = 2)
        val count = dilated.count { it }
        assertTrue(count >= 100)
    }
}
