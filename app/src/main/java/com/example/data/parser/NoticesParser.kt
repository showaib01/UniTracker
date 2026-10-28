package com.example.data.parser

import com.example.data.local.entity.NoticeEntity
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class NoticesParser : PageParser<List<NoticeEntity>> {

    override fun canParse(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        return lower.contains("notice") || lower.contains("announcement")
    }

    override fun parse(html: String): ParserResult<List<NoticeEntity>> {
        val list = mutableListOf<NoticeEntity>()
        val warnings = mutableListOf<String>()

        if (html.isBlank()) {
            return ParserResult(emptyList(), listOf("Provided HTML is empty"), isSuccess = false)
        }

        try {
            val doc: Document = Jsoup.parse(html)
            val items = doc.select(PortalSelectorConfig.NOTICE_ITEM_SELECTOR)

            for ((index, item) in items.withIndex()) {
                val title = item.select(PortalSelectorConfig.NOTICE_TITLE_SELECTOR).text().trim()
                val date = item.select(PortalSelectorConfig.NOTICE_DATE_SELECTOR).text().trim()
                val snippet = item.select(PortalSelectorConfig.NOTICE_CONTENT_SELECTOR).text().trim()
                val link = item.select(PortalSelectorConfig.NOTICE_LINK_SELECTOR).attr("href").trim()

                if (title.isNotBlank()) {
                    list.add(
                        NoticeEntity(
                            noticeId = "notice_${index}_" + System.currentTimeMillis(),
                            title = title,
                            publishDate = date.ifBlank { "Recent" },
                            contentSnippet = snippet,
                            fullContent = snippet,
                            sourceUrl = link,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            warnings.add("Notices parse error: ${e.message}")
            return ParserResult(list, warnings, isSuccess = false)
        }

        return ParserResult(list, warnings, isSuccess = list.isNotEmpty())
    }
}
