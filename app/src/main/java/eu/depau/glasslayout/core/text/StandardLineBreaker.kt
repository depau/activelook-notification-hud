package eu.depau.glasslayout.core.text

import eu.depau.glasslayout.core.model.FontToken

class StandardLineBreaker : BasicLineBreaker() {
    
    private class Tok(val span: TextSpan, val width: Int, val precededBySpace: Boolean)

    override fun wrap(
        spans: List<TextSpan>,
        font: FontToken,
        maxWidth: Int,
        measurer: TextMeasurer
    ): List<List<TextSpan>> {
        val paragraphs = super.wrap(spans, font, maxWidth, measurer)
        val allLines = ArrayList<List<TextSpan>>()
        val spaceW = measurer.measureWidth(" ", font)

        for (paragraph in paragraphs) {
            val toks = tokenize(paragraph, font, measurer)
            val lines = ArrayList<List<TextSpan>>()
            var cur = ArrayList<TextSpan>()
            var curW = 0

            for (t in toks) {
                var sep = if (cur.isEmpty()) 0 else if (t.precededBySpace) spaceW else 0
                if (cur.isNotEmpty() && curW + sep + t.width > maxWidth) {
                    lines += cur
                    cur = ArrayList()
                    curW = 0
                    sep = 0
                }
                if (cur.isEmpty() && t.width > maxWidth && t.span is TextSpan.Text) {
                    hardSplit(t.span.text, font, maxWidth, measurer, lines).let { leftover ->
                        cur = arrayListOf(TextSpan.Text(leftover))
                        curW = measurer.measureWidth(leftover, font)
                    }
                    continue
                }
                if (sep > 0) {
                    cur += TextSpan.Text(" ")
                    curW += spaceW
                }
                cur += t.span
                curW += t.width
            }
            if (cur.isNotEmpty()) lines += cur
            if (lines.isEmpty()) lines.add(emptyList())
            allLines.addAll(lines)
        }
        return allLines
    }

    private fun tokenize(spans: List<TextSpan>, font: FontToken, measurer: TextMeasurer): List<Tok> {
        val out = ArrayList<Tok>()
        var pendingSpace = false
        for (span in spans) {
            when (span) {
                is TextSpan.Image -> {
                    out += Tok(span, span.width, pendingSpace)
                    pendingSpace = false
                }
                is TextSpan.Text -> {
                    var word = StringBuilder()

                    fun flushWord() {
                        if (word.isNotEmpty()) {
                            val s = word.toString()
                            out += Tok(TextSpan.Text(s), measurer.measureWidth(s, font), pendingSpace)
                            pendingSpace = false
                            word.setLength(0)
                        }
                    }

                    for (ch in span.text) {
                        if (ch.isWhitespace()) {
                            flushWord()
                            pendingSpace = true
                        } else {
                            word.append(ch)
                        }
                    }
                    flushWord()
                }
            }
        }
        return out
    }

    private fun hardSplit(
        s: String,
        font: FontToken,
        maxWidth: Int,
        measurer: TextMeasurer,
        lines: MutableList<List<TextSpan>>
    ): String {
        val chunk = StringBuilder()
        for (ch in s) {
            if (chunk.isNotEmpty() && measurer.measureWidth("$chunk$ch", font) > maxWidth) {
                lines += listOf(TextSpan.Text(chunk.toString()))
                chunk.setLength(0)
            }
            chunk.append(ch)
        }
        return chunk.toString()
    }
}
