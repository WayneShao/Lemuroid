package com.swordfish.lemuroid.app.rayneo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.SurfaceView
import com.swordfish.libretrodroid.GLRetroView
import timber.log.Timber

/**
 * A SurfaceView that mirrors the content of a GLRetroView (which is a GLSurfaceView)
 * using PixelCopy each display frame.
 *
 * This is the "right eye" of the dual-eye display. The "left eye" is the GLRetroView itself.
 *
 * PixelCopy copies SurfaceView content asynchronously; the source and destination have
 * separate presentation lifetimes.
 *
 * Copy cost and the phase difference between eyes require device measurements.
 */
class RayNeoMirrorView(context: Context) : SurfaceView(context), Choreographer.FrameCallback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sourceView: GLRetroView? = null
    private var mirrorBitmap: Bitmap? = null
    private var copyPending = false
    private var running = false

    init {
        // Keep the SurfaceView in the same Z-order layer as other SurfaceViews
        // (behind the View hierarchy). Compose's AndroidView handles the punch-through.
        holder.setFormat(PixelFormat.RGBX_8888)
    }

    /** Call this once the GLRetroView is attached and its surface is ready. */
    fun attachSource(glRetroView: GLRetroView) {
        sourceView = glRetroView
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Stop the mirror loop, e.g. when the Activity is stopping. */
    fun detachSource() {
        running = false
        sourceView = null
    }

    // --- Choreographer.FrameCallback ---

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            detachSource()
            return
        }

        val source = sourceView
        if (source == null || !source.isAttachedToWindow || copyPending) {
            Choreographer.getInstance().postFrameCallback(this)
            return
        }

        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) {
            Choreographer.getInstance().postFrameCallback(this)
            return
        }

        val bitmap = getOrCreateBitmap(w, h)
        copyPending = true

        PixelCopy.request(
            source,
            bitmap,
            { result ->
                copyPending = false
                if (result == PixelCopy.SUCCESS) {
                    drawBitmapToSurface(bitmap)
                } else {
                    Timber.w("RayNeoMirrorView: PixelCopy failed with code $result")
                }
                if (running) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            },
            mainHandler,
        )
    }

    // --- Private helpers ---

    private fun getOrCreateBitmap(
        w: Int,
        h: Int,
    ): Bitmap {
        val existing = mirrorBitmap
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { mirrorBitmap = it }
    }

    private fun drawBitmapToSurface(bitmap: Bitmap) {
        if (!holder.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas != null) {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        } catch (e: Exception) {
            Timber.w(e, "RayNeoMirrorView: failed to draw to surface")
        } finally {
            if (canvas != null) holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachSource()
        mirrorBitmap?.recycle()
        mirrorBitmap = null
    }
}
