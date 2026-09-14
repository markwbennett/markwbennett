package com.ivi3.locationfacts.ai

/**
 * One run of assistant text plus the sources the API attached to it. A response that
 * uses web search comes back split into several text blocks, each carrying the
 * citations for the claims inside it, so keeping text and sources paired lets us
 * attribute sources to individual facts by character range.
 */
data class TextSegment(val text: String, val sources: List<Source> = emptyList())

data class ParsedFacts(val facts: List<Fact>, val note: String?)

/**
 * Parses the TITLE:/BODY:/NOTE: shape requested in [ClaudeFactsService.SYSTEM_PROMPT]
 * and attaches each fact the sources cited within its own span of the response.
 *
 * Tolerant by design: unexpected lines before the first TITLE are ignored, a fact
 * without a BODY line keeps whatever prose followed its title, and text that
 * contains no TITLE at all comes back as a single untitled fact rather than nothing.
 */
object FactsParser {

    private const val TITLE_PREFIX = "TITLE:"
    private const val BODY_PREFIX = "BODY:"
    private const val NOTE_PREFIX = "NOTE:"

    fun parse(segments: List<TextSegment>): ParsedFacts {
        val combined = StringBuilder()
        val spans = ArrayList<SourceSpan>(segments.size)
        for (segment in segments) {
            val start = combined.length
            combined.append(segment.text)
            if (segment.sources.isNotEmpty()) {
                spans += SourceSpan(start, combined.length, segment.sources)
            }
        }
        val text = combined.toString()

        val drafts = mutableListOf<Draft>()
        var note: String? = null
        var current: Draft? = null
        var offset = 0

        for (line in text.lineSequence()) {
            val lineStart = offset
            offset += line.length + 1 // +1 for the newline the sequence consumed
            val trimmed = line.trim()

            when {
                trimmed.startsWith(TITLE_PREFIX, ignoreCase = true) -> {
                    current?.let { drafts += it }
                    current = Draft(
                        title = trimmed.substring(TITLE_PREFIX.length).trim(),
                        start = lineStart,
                        end = offset,
                    )
                }

                trimmed.startsWith(NOTE_PREFIX, ignoreCase = true) -> {
                    current?.let { drafts += it }
                    current = null
                    val value = trimmed.substring(NOTE_PREFIX.length).trim()
                    note = if (note.isNullOrEmpty()) value else "$note $value"
                }

                else -> {
                    val open = current
                    if (open == null) {
                        // Prose before any TITLE line: keep it as an untitled fact so a
                        // model that ignored the format still produces something readable.
                        if (trimmed.isNotEmpty() && drafts.isEmpty()) {
                            current = Draft(title = "", start = lineStart, end = offset).also {
                                it.body += trimmed
                            }
                        }
                    } else {
                        val value = if (trimmed.startsWith(BODY_PREFIX, ignoreCase = true)) {
                            trimmed.substring(BODY_PREFIX.length).trim()
                        } else {
                            trimmed
                        }
                        if (value.isNotEmpty()) {
                            if (open.body.isNotEmpty()) open.body += " "
                            open.body += value
                            open.end = offset
                        }
                    }
                }
            }
        }
        current?.let { drafts += it }

        val facts = drafts
            .filter { it.title.isNotEmpty() || it.body.isNotEmpty() }
            .map { draft ->
                Fact(
                    title = draft.title,
                    body = draft.body,
                    sources = sourcesIn(spans, draft.start, draft.end),
                )
            }
        return ParsedFacts(facts, note?.takeIf { it.isNotEmpty() })
    }

    /** Sources cited anywhere inside [start, end), de-duplicated by URL, order preserved. */
    private fun sourcesIn(spans: List<SourceSpan>, start: Int, end: Int): List<Source> {
        val seen = LinkedHashMap<String, Source>()
        for (span in spans) {
            if (span.start < end && span.end > start) {
                for (source in span.sources) {
                    val existing = seen[source.url]
                    // Prefer an entry that has a title over one that doesn't.
                    if (existing == null || (existing.title.isNullOrBlank() && !source.title.isNullOrBlank())) {
                        seen[source.url] = source
                    }
                }
            }
        }
        return seen.values.toList()
    }

    private class Draft(val title: String, val start: Int, var end: Int) {
        var body: String = ""
    }

    private class SourceSpan(val start: Int, val end: Int, val sources: List<Source>)
}
