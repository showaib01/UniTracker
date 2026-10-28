package com.example.data.parser

/**
 * Single Configuration File for AIUB Portal CSS Selectors and Authentication Markers.
 *
 * All selectors and login detection logic are maintained here.
 */
object PortalSelectorConfig {

    const val DEFAULT_PORTAL_HOST = "portal.aiub.edu"

    // -------------------------------------------------------------
    // Page Route Paths & Keywords
    // -------------------------------------------------------------
    val ROUTINE_PATH_KEYWORDS = listOf("/routine", "/classroutine", "/student/routine", "/student/schedule", "/student/home")
    val ATTENDANCE_PATH_KEYWORDS = listOf("/attendance", "/courseattendance", "/student/attendance")
    val RESULTS_PATH_KEYWORDS = listOf("/result", "/grade", "/student/grade", "/student/result")
    val NOTICES_PATH_KEYWORDS = listOf("/notice", "/announcement", "/student/notice")

    // -------------------------------------------------------------
    // Authentication & Session Status Selectors
    // -------------------------------------------------------------
    const val LOGGED_IN_INDICATOR = "a[href*='/Login/Logout']"
    const val PASSWORD_FIELD_SELECTOR = "input[type='password']"
    const val LOGIN_URL_KEYWORD = "/login"

    /**
     * Determines whether the page has a valid logged-in session indicator.
     */
    fun isLoggedIn(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        return html.contains("/Login/Logout", ignoreCase = true)
    }

    /**
     * Determines whether the page is a login/authentication page.
     * Rule:
     * - A logged-in page contains a logout link: a[href*="/Login/Logout"]. Use this as the "logged in" indicator.
     * - A logged-out/login page has a password input and/or the URL contains "/Login" (and no logout link).
     * - Do NOT use form#aspnetForm or .alert-danger for login detection.
     */
    fun isLoginPage(url: String, html: String?): Boolean {
        // If logout link is present, user is definitely logged in
        if (isLoggedIn(html)) return false

        val lowerUrl = url.lowercase()
        val isLoginUrl = lowerUrl.contains("/login")

        var hasPasswordInput = false
        if (!html.isNullOrBlank()) {
            val lowerHtml = html.lowercase()
            hasPasswordInput = lowerHtml.contains("type=\"password\"") || lowerHtml.contains("type='password'")
        }

        return isLoginUrl || hasPasswordInput
    }

    // -------------------------------------------------------------
    // Class Schedule / Routine Selectors (Real AIUB Student Home / Schedule)
    // -------------------------------------------------------------
    const val SCHEDULE_CONTAINER_SELECTOR = "div.scheduleTable"
    const val SCHEDULE_DAY_ROWS_SELECTOR = "div.scheduleTable > div.row"
    const val SCHEDULE_DATE_LABEL_SELECTOR = ".col-md-2 label:first-of-type"
    const val SCHEDULE_CLASS_ITEMS_SELECTOR = ".col-md-10 .col-md-6"
    const val SCHEDULE_COURSE_TITLE_SELECTOR = "a"
    const val SCHEDULE_TIME_LABEL_SELECTOR = "div label:nth-of-type(1)"
    const val SCHEDULE_ROOM_LABEL_SELECTOR = "div label:nth-of-type(2)"
    const val SCHEDULE_NO_CLASS_SELECTOR = ".col-md-12"
    const val SCHEDULE_NO_CLASS_TEXT = "No Class On This Day"

    // -------------------------------------------------------------
    // Attendance Selectors
    // -------------------------------------------------------------
    const val ATTENDANCE_CONTAINER_SELECTOR = "table.attendance-table, table.attendance, table#attendanceTable, table:has(th:contains(Attended)), table:has(th:contains(Percentage))"
    const val ATTENDANCE_ROW_SELECTOR = "tbody tr, tr:has(td)"
    const val ATTENDANCE_COL_COURSE = "td:nth-child(1)"
    const val ATTENDANCE_COL_SECTION = "td:nth-child(2)"
    const val ATTENDANCE_COL_ATTENDED = "td:nth-child(3)"
    const val ATTENDANCE_COL_TOTAL = "td:nth-child(4)"
    const val ATTENDANCE_COL_PERCENTAGE = "td:nth-child(5)"

    // -------------------------------------------------------------
    // Results & CGPA Selectors
    // -------------------------------------------------------------
    const val RESULTS_SEMESTER_SECTION = "div.semester-result, div.panel-grade, div.grade-section"
    const val RESULTS_SEMESTER_TITLE = ".semester-title, h4, span.semester-name"
    const val RESULTS_GRADE_ROW = "table tbody tr, table.grade-table tr:has(td)"
    const val RESULTS_COL_CODE = "td:nth-child(1)"
    const val RESULTS_COL_TITLE = "td:nth-child(2)"
    const val RESULTS_COL_CREDIT = "td:nth-child(3)"
    const val RESULTS_COL_GRADE = "td:nth-child(4)"
    const val RESULTS_COL_GRADE_POINT = "td:nth-child(5)"
    const val RESULTS_SUMMARY_CGPA = ".cgpa-value, span#cgpa, #lblCGPA"

    // -------------------------------------------------------------
    // Notices Selectors
    // -------------------------------------------------------------
    const val NOTICE_ITEM_SELECTOR = "div.notice-item, tr.notice-row, div.announcement-card, .notice-list > li"
    const val NOTICE_TITLE_SELECTOR = "h5, .notice-title, a.title, .heading"
    const val NOTICE_DATE_SELECTOR = ".notice-date, span.date, .pub-date"
    const val NOTICE_CONTENT_SELECTOR = ".notice-body, p.content, .description"
    const val NOTICE_LINK_SELECTOR = "a[href]"
}
