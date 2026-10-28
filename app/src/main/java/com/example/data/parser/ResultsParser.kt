package com.example.data.parser

import com.example.data.local.entity.ResultEntity
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ResultsParser : PageParser<List<ResultEntity>> {

    override fun canParse(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        return lower.contains("result") || lower.contains("grade") || lower.contains("cgpa")
    }

    override fun parse(html: String): ParserResult<List<ResultEntity>> {
        val list = mutableListOf<ResultEntity>()
        val warnings = mutableListOf<String>()

        if (html.isBlank()) {
            return ParserResult(emptyList(), listOf("Provided HTML is empty"), isSuccess = false)
        }

        try {
            val doc: Document = Jsoup.parse(html)
            val rows = doc.select(PortalSelectorConfig.RESULTS_GRADE_ROW)

            for (row in rows) {
                val code = row.select(PortalSelectorConfig.RESULTS_COL_CODE).text().trim()
                val title = row.select(PortalSelectorConfig.RESULTS_COL_TITLE).text().trim()
                val creditStr = row.select(PortalSelectorConfig.RESULTS_COL_CREDIT).text().trim()
                val grade = row.select(PortalSelectorConfig.RESULTS_COL_GRADE).text().trim()
                val gpStr = row.select(PortalSelectorConfig.RESULTS_COL_GRADE_POINT).text().trim()

                if (title.isNotBlank() && !title.equals("Title", ignoreCase = true) && !title.equals("Course Title", ignoreCase = true)) {
                    val credits = creditStr.toDoubleOrNull() ?: 3.0
                    val gradePoint = gpStr.toDoubleOrNull() ?: 3.75

                    list.add(
                        ResultEntity(
                            semester = "Current",
                            courseCode = code.ifBlank { "CSC" },
                            courseTitle = title,
                            credits = credits,
                            grade = grade.ifBlank { "A" },
                            gradePoint = gradePoint,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            warnings.add("Results parse error: ${e.message}")
            return ParserResult(list, warnings, isSuccess = false)
        }

        return ParserResult(list, warnings, isSuccess = list.isNotEmpty())
    }
}
