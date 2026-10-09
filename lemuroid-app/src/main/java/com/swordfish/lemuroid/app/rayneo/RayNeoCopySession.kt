package com.swordfish.lemuroid.app.rayneo

/**
 * Main-thread ownership of a single asynchronous copy and its reusable destination.
 * Invalidation cancels scheduled work, but only completion may release an in-flight buffer.
 */
internal class RayNeoCopySession<T>(
    private val createBuffer: (Int, Int) -> T,
    private val releaseBuffer: (T) -> Unit,
) {
    class Frame internal constructor(internal val generation: Long)

    class Request<T> internal constructor(
        val buffer: T,
        internal val width: Int,
        internal val height: Int,
        internal val generation: Long,
    )

    private var active = false
    private var generation = 0L
    private var scheduled: Frame? = null
    private var inFlight: Request<T>? = null
    private var reusable: Request<T>? = null

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (!active) invalidate()
    }

    fun invalidate() {
        generation++
        scheduled = null
        reusable?.let { releaseBuffer(it.buffer) }
        reusable = null
    }

    fun schedule(): Frame? {
        if (!active || scheduled != null || inFlight != null) return null
        return Frame(generation).also { scheduled = it }
    }

    fun begin(
        frame: Frame,
        width: Int,
        height: Int,
    ): Request<T>? {
        if (scheduled !== frame) return null
        scheduled = null
        if (!active || inFlight != null || width <= 0 || height <= 0) return null

        val previous = reusable
        reusable = null
        val buffer =
            if (previous != null && previous.width == width && previous.height == height) {
                previous.buffer
            } else {
                previous?.let { releaseBuffer(it.buffer) }
                createBuffer(width, height)
            }
        return Request(buffer, width, height, frame.generation).also { inFlight = it }
    }

    /** Returns whether this result still belongs to the current, active output. */
    fun complete(request: Request<T>): Boolean {
        if (inFlight !== request) return false
        inFlight = null
        val current = active && request.generation == generation
        if (current) {
            reusable = request
        } else {
            releaseBuffer(request.buffer)
        }
        return current
    }
}
