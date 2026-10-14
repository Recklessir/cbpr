package com.cbpr.ui

object PreviewText {
    const val ELLIPSIS = "\u2026"

    fun limit(lines: List<String>, maxLines: Int, widthOf: (String) -> Int, maxWidth: Int): List<String> {
        if (lines.size <= maxLines) return lines
        val kept = lines.take(maxLines - 1)
        var last = lines[maxLines - 1].trimEnd()
        while (last.isNotEmpty() && widthOf(last + ELLIPSIS) > maxWidth) last = last.dropLast(1).trimEnd()
        return kept + (last + ELLIPSIS)
    }
}
