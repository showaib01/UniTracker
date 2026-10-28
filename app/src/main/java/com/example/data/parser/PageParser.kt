package com.example.data.parser

interface PageParser<T> {
    /**
     * Parses rendered HTML string from the portal into structured data model
     * wrapped in a [ParserResult] containing parsed data and non-fatal warnings.
     */
    fun parse(html: String): ParserResult<T>

    /**
     * Returns true if the provided HTML appears to match this parser's page type
     */
    fun canParse(html: String): Boolean
}
