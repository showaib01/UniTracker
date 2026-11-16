package com.example

import com.example.data.auth.LoginErrorType
import com.example.data.auth.PortalAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortalAuthParsingTests {

    @Test
    fun testParseLoginPage() {
        val html = """
            <html>
                <body>
                    <form>
                        <input name="__RequestVerificationToken" value="TOKEN_12345" />
                        <input id="CaptchaDeText" value="CAPTCHA_TOKEN_6789" />
                    </form>
                </body>
            </html>
        """.trimIndent()

        val (verificationToken, captchaToken) = PortalAuth.parseLoginPage(html)
        assertEquals("TOKEN_12345", verificationToken)
        assertEquals("CAPTCHA_TOKEN_6789", captchaToken)
    }

    @Test
    fun testParseRefreshToken() {
        val jsScript = """$('#CaptchaDeText').attr("value", "NEW_REFRESH_TOKEN");"""
        val newToken = PortalAuth.parseRefreshToken(jsScript)
        assertEquals("NEW_REFRESH_TOKEN", newToken)

        assertNull(PortalAuth.parseRefreshToken(""))
        assertNull(PortalAuth.parseRefreshToken("invalid script"))
    }

    @Test
    fun testClassifyLoginError() {
        assertEquals(LoginErrorType.CAPTCHA, PortalAuth.classifyLoginError("Invalid captcha code entered."))
        assertEquals(LoginErrorType.LOCKED, PortalAuth.classifyLoginError("Account is locked. Contact administrator."))
        assertEquals(LoginErrorType.WRONG_CREDENTIALS, PortalAuth.classifyLoginError("Invalid user name or password."))
        assertEquals(LoginErrorType.WRONG_CREDENTIALS, PortalAuth.classifyLoginError("Password does not match."))
        assertEquals(LoginErrorType.UNKNOWN, PortalAuth.classifyLoginError(""))
        assertEquals(LoginErrorType.UNKNOWN, PortalAuth.classifyLoginError("Welcome user"))
    }
}
