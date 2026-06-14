package eu.depau.glasslayout.core.text

import eu.depau.glasslayout.core.model.FontToken

open class BasicLineBreaker : LineBreaker {
    override fun wrap(
        spans: List<TextSpan>,
        font: FontToken,
        maxWidth: Int,
        measurer: TextMeasurer
    ): List<List<TextSpan>> {
        val out = ArrayList<List<TextSpan>>()
        var cur = ArrayList<TextSpan>()
        for (span in spans) {
            when (span) {
                is TextSpan.Image -> cur.add(span)
                is TextSpan.Text -> {
                    val lines = span.text.split('\n')
                    for (i in lines.indices) {
                        if (i > 0) {
                            out.add(cur)
                            cur = ArrayList()
                        }
                        if (lines[i].isNotEmpty()) {
                            cur.add(TextSpan.Text(lines[i]))
                        }
                    }
                }
            }
        }
        out.add(cur)
        return out
    }
}
