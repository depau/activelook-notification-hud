package eu.depau.glasslayout.core.text

import eu.depau.glasslayout.core.model.FontToken

/**
 * Measures text for the layout engine, abstracted over the concrete device font. Token-based so the
 * core never sees device font ids; the backend/app maps [FontToken] to a real font.
 */
interface TextMeasurer {
    /** Rendered pixel width of [text] at [token]. */
    fun measureWidth(text: String, token: FontToken): Int

    /** Height of a single line cell at [token]. */
    fun lineHeight(token: FontToken): Int

    /** Vertical distance between successive line tops at [token]. */
    fun linePitch(token: FontToken): Int

    /** Word-wrap [text] to fit [maxWidth] px at [token]. */
    fun wrap(text: String, token: FontToken, maxWidth: Int): List<String>

    /** Truncate [text] with an ellipsis-equivalent so it fits [maxWidth] px at [token]. */
    fun ellipsize(text: String, token: FontToken, maxWidth: Int): String
}
