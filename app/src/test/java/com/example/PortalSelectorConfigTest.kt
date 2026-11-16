package com.example

import com.example.data.parser.PageState
import com.example.data.parser.PortalSelectorConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PortalSelectorConfigTest {

    @Test
    fun testClassifyPage() {
        assertEquals(PageState.APP, PortalSelectorConfig.classifyPage("app", "https://portal.aiub.edu/Student/Home"))
        assertEquals(PageState.APP, PortalSelectorConfig.classifyPage("other", "https://portal.aiub.edu/Student/Schedule"))
        assertEquals(PageState.LOGIN, PortalSelectorConfig.classifyPage("login", "https://portal.aiub.edu/"))
        assertEquals(PageState.LOGIN, PortalSelectorConfig.classifyPage("login", "https://portal.aiub.edu/Student"))
        assertEquals(PageState.OTHER, PortalSelectorConfig.classifyPage("other", "https://portal.aiub.edu/Notice"))
    }
}
