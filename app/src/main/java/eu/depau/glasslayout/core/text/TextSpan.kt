package eu.depau.glasslayout.core.text

import eu.depau.glasslayout.core.model.FontToken

sealed interface TextSpan {
    data class Text(val text: String) : TextSpan
    data class Image(val key: Any, val payload: Any, val width: Int, val height: Int) : TextSpan
}

interface TextSpanParser {
    fun parse(text: String, font: FontToken): List<TextSpan>
}

object DefaultTextSpanParser : TextSpanParser {
    override fun parse(text: String, font: FontToken): List<TextSpan> = listOf(TextSpan.Text(text))
}

interface LineBreaker {
    fun wrap(spans: List<TextSpan>, font: FontToken, maxWidth: Int, measurer: TextMeasurer): List<List<TextSpan>>
}
