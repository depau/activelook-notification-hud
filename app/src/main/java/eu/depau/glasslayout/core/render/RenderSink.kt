package eu.depau.glasslayout.core.render

/**
 * Consumes a frame of [RenderCommand]s. Implementations decide how to present them (e.g. diff
 * against the previous frame and partial-redraw to a device). The core never calls a device.
 */
interface RenderSink {
    /** Present this frame. Implementations may diff against the previously presented frame. */
    fun present(commands: List<RenderCommand>)

    /** Forget any cached previous frame so the next [present] does a full redraw. */
    fun invalidate()
}
