package com.example

import com.example.data.auth.evalMathEquation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptchaSolverUnitTests {

    @Test
    fun testAdditionParsing() {
        assertEquals(12, evalMathEquation("7 + 5 = ?"))
        assertEquals(12, evalMathEquation("7+5=?"))
        assertEquals(15, evalMathEquation("10 + 5"))
        assertEquals(100, evalMathEquation("99 + 1 = ?"))
    }

    @Test
    fun testSubtractionParsing() {
        assertEquals(4, evalMathEquation("9 - 5 = ?"))
        assertEquals(0, evalMathEquation("7-7=?"))
        assertEquals(10, evalMathEquation("25 - 15 = ?"))
    }

    @Test
    fun testMultiplicationParsing() {
        assertEquals(20, evalMathEquation("4 x 5 = ?"))
        assertEquals(14, evalMathEquation("7 × 2 = ?"))
        assertEquals(30, evalMathEquation("6 * 5 = ?"))
    }

    @Test
    fun testDivisionParsing() {
        assertEquals(5, evalMathEquation("20 / 4 = ?"))
        assertEquals(3, evalMathEquation("15 ÷ 5 = ?"))
        assertNull(evalMathEquation("10 / 0 = ?"))
    }

    @Test
    fun testOcrNoiseHandling() {
        assertEquals(12, evalMathEquation(" 7 + 5 = ? "))
        assertEquals(8, evalMathEquation("10 - 2 = ?\n"))
        assertEquals(15, evalMathEquation("3 x 5 = ? "))
        assertNull(evalMathEquation("invalid text"))
        assertNull(evalMathEquation(""))
    }
}
