package com.example.data.parser

/**
 * Encapsulates parser output with status and non-fatal diagnostic warnings.
 *
 * @param data Parsed models
 * @param warnings Descriptive log messages for skipped or malformed rows
 * @param isSuccess True if parser completed without fatal abort
 */
data class ParserResult<T>(
    val data: T,
    val warnings: List<String> = emptyList(),
    val isSuccess: Boolean = true
)
