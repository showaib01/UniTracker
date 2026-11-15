package com.example

import com.example.data.auth.CaptchaSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
