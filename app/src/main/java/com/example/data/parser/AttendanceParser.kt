package com.example.data.parser

import com.example.data.local.entity.AttendanceEntity
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AttendanceParser : PageParser<List<AttendanceEntity>> {

    override fun canParse(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        return lower.contains("attendance") ||
                (lower.contains("attended") && lower.contains("percentage")) ||
                (lower.contains("present") && lower.contains("absent"))
    }

    override fun parse(html: String): ParserResult<List<AttendanceEntity>> {
        val list = mutableListOf<AttendanceEntity>()
        val warnings = mutableListOf<String>()

        if (html.isBlank()) {
            return ParserResult(emptyList(), listOf("Provided HTML is empty"), isSuccess = false)
        }

        try {
            val doc: Document = Jsoup.parse(html)
            val table = doc.select(PortalSelectorConfig.ATTENDANCE_CONTAINER_SELECTOR).first()

            if (table == null) {
                warnings.add("No attendance table matching selector '${PortalSelectorConfig.ATTENDANCE_CONTAINER_SELECTOR}' found.")
                return ParserResult(emptyList(), warnings, isSuccess = false)
            }

            val rows = table.select(PortalSelectorConfig.ATTENDANCE_ROW_SELECTOR)
            if (rows.isEmpty()) {
                warnings.add("Attendance table exists but contains no rows.")
                return ParserResult(emptyList(), warnings, isSuccess = true)
            }

            var rowIndex = 0
            for (row in rows) {
                rowIndex++

                // Skip tfoot rows
                if (row.parents().any { it.tagName().equals("tfoot", ignoreCase = true) }) {
                    continue
                }

                val cells = row.select("td")
                if (cells.isEmpty()) continue

                if (cells.size < 4) {
                    warnings.add("Row $rowIndex skipped: expected at least 4 columns, found ${cells.size} ('${row.text().take(40)}')")
                    continue
                }

                val courseRaw = cleanText(cells.getOrNull(0)?.text())
                val section = cleanText(cells.getOrNull(1)?.text()).ifBlank { "A" }
                val attendedStr = cleanText(cells.getOrNull(2)?.text())
                val totalStr = cleanText(cells.getOrNull(3)?.text())
                val percentStr = cleanText(cells.getOrNull(4)?.text())

                // Skip header repetitions
                if (courseRaw.equals("Course", ignoreCase = true) || courseRaw.equals("Code", ignoreCase = true)) {
                    continue
                }

                if (courseRaw.isBlank()) {
                    warnings.add("Row $rowIndex skipped: Course title is empty.")
                    continue
                }

                val (code, name) = splitCourseCodeAndName(courseRaw)

                val attended = attendedStr.filter { it.isDigit() }.toIntOrNull()
                val total = totalStr.filter { it.isDigit() }.toIntOrNull()

                if (attended == null || total == null || total == 0) {
                    warnings.add("Row $rowIndex ('$name'): missing or invalid attendance figures (attended: '$attendedStr', total: '$totalStr').")
                    continue
                }

                list.add(
                    AttendanceEntity(
                        courseCode = code,
                        courseName = name,
                        section = section,
                        attendedCount = attended,
                        totalClasses = total,
                        manualCorrection = 0, // Fresh sync keeps portal values; DAO preserves previous corrections!
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }

            if (list.isEmpty() && warnings.isEmpty()) {
                warnings.add("No valid attendance entries extracted from the table.")
            }

        } catch (e: Exception) {
            warnings.add("Attendance parsing error: ${e.message ?: e.javaClass.simpleName}")
            return ParserResult(list, warnings, isSuccess = false)
        }

        return ParserResult(list, warnings, isSuccess = list.isNotEmpty())
    }

    private fun cleanText(text: String?): String {
        if (text == null) return ""
        return text
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Extracts Course Code and Title from combined representations.
     * E.g. "CSC 3211 - Operating Systems" -> ("CSC 3211", "Operating Systems")
     * E.g. "Algorithms [CSC 3222]" -> ("CSC 3222", "Algorithms")
     */
    internal fun splitCourseCodeAndName(raw: String): Pair<String, String> {
        val trimmed = cleanText(raw)
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-", limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].matches(Regex("(?i)[A-Z]{2,4}\\s*\\d{3,4}"))) {
                return Pair(parts[0], parts[1])
            }
        }

        // Regex check for code at start e.g. "CSC3211 Operating Systems"
        val codeMatch = Regex("(?i)^([A-Z]{2,4}\\s*\\d{3,4})\\s*(.*)").find(trimmed)
        if (codeMatch != null) {
            val code = codeMatch.groupValues[1].trim()
            val name = codeMatch.groupValues[2].trim().ifBlank { code }
            return Pair(code, name)
        }

        return Pair(trimmed.take(10).trim(), trimmed)
    }
}
