package com.swordfish.lemuroid.app.rayneo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.swordfish.libretrodroid.GLRetroView
import timber.log.Timber

/**
 * The right eye mirrors the existing GLRetroView; no second emulator is created.
 * Copy cost and the phase difference between eyes require device measurements.
 * All lifecycle, Surface and PixelCopy callbacks are handled on the main thread.
 */
class RayNeoMirrorView(context: Context) : SurfaceView(context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destinationBounds = Rect()
    private val copySession =
        RayNeoCopySession(
            { width, height -> Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) },
            Bitmap::recycle,
        )
    private var sourceView: GLRetroView? = null
    private var lifecycle: Lifecycle? = null
    private var frameCallback: CopyFrameCallback? = null
    private var released = false
    private var lastFailureLogMillis = -FAILURE_LOG_INTERVAL_MILLIS

    private val lifecycleObserver = LifecycleEventObserver { _, _ -> updateCopyState() }
    private val surfaceCallback =
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = resetCopies()

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = resetCopies()

            override fun surfaceDestroyed(holder: SurfaceHolder) = stopCopies()
        }
    private val sourceAttachListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = resetCopies()

            override fun onViewDetachedFromWindow(view: View) = stopCopies()
        }

    init {
        // Keep both SurfaceViews behind the View hierarchy, as in the existing renderer.
        holder.setFormat(PixelFormat.RGBX_8888)
        holder.addCallback(surfaceCallback)
    }

    fun bindLifecycle(lifecycle: Lifecycle) {
        if (released || this.lifecycle === lifecycle) return
        this.lifecycle?.removeObserver(lifecycleObserver)
        stopCopies()
        this.lifecycle = lifecycle
        lifecycle.addObserver(lifecycleObserver)
        updateCopyState()
    }

    fun attachSource(glRetroView: GLRetroView) {
        if (released || sourceView === glRetroView) return
        detachSource()
        sourceView = glRetroView
        glRetroView.holder.addCallback(surfaceCallback)
        glRetroView.addOnAttachStateChangeListener(sourceAttachListener)
        updateCopyState()
    }

    fun detachSource() {
        stopCopies()
        sourceView?.holder?.removeCallback(surfaceCallback)
        sourceView?.removeOnAttachStateChangeListener(sourceAttachListener)
        sourceView = null
    }

    /** Permanent cleanup when Compose releases this AndroidView. */
    fun release() {
        if (released) return
        released = true
        lifecycle?.removeObserver(lifecycleObserver)
        lifecycle = null
        detachSource()
        holder.removeCallback(surfaceCallback)
        // A pending PixelCopy owns its Bitmap until its callback, even after release.
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateCopyState()
    }

    override fun onDetachedFromWindow() {
        stopCopies()
        super.onDetachedFromWindow()
    }

    private fun canCopy(): Boolean {
        val source = sourceView ?: return false
        return !released &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true &&
            isAttachedToWindow && source.isAttachedToWindow &&
            holder.surface.isValid && source.holder.surface.isValid
    }

    private fun updateCopyState() {
        if (canCopy()) {
            copySession.setActive(true)
            scheduleNextFrame()
        } else {
            stopCopies()
        }
    }

    private fun resetCopies() {
        removeFrameCallback()
        copySession.invalidate()
        updateCopyState()
    }

    private fun stopCopies() {
        removeFrameCallback()
        copySession.setActive(false)
    }

    private fun removeFrameCallback() {
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
    }

    private fun scheduleNextFrame(delayMillis: Long = 0) {
        val frame = copySession.schedule() ?: return
        val callback = CopyFrameCallback(frame)
        frameCallback = callback
        choreographer.postFrameCallbackDelayed(callback, delayMillis)
    }

    private inner class CopyFrameCallback(private val frame: RayNeoCopySession.Frame) : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (frameCallback !== this) return
            frameCallback = null
            copyFrame(frame)
        }
    }

    private fun copyFrame(frame: RayNeoCopySession.Frame) {
        val source = sourceView
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || source == null || !canCopy()) {
            stopCopies()
            return
        }

        val sourceBounds = source.holder.surfaceFrame
        val request =
            try {
                copySession.begin(frame, sourceBounds.width(), sourceBounds.height())
            } catch (error: RuntimeException) {
                reportFailure("failed to allocate copy destination", error)
                scheduleNextFrame(RETRY_DELAY_MILLIS)
                return
            }
        if (request == null) {
            scheduleNextFrame(RETRY_DELAY_MILLIS)
            return
        }

        try {
            PixelCopy.request(
                source,
                request.buffer,
                { result ->
                    val current = copySession.complete(request)
                    if (current && sourceView === source && canCopy()) {
                        if (result == PixelCopy.SUCCESS) {
                            drawBitmapToSurface(request.buffer)
                        } else {
                            reportFailure("PixelCopy failed with code $result")
                        }
                    }
                    // Only the currently active session may schedule; stale results never reactivate it.
                    scheduleNextFrame(if (result == PixelCopy.SUCCESS) 0 else RETRY_DELAY_MILLIS)
                },
                mainHandler,
            )
        } catch (error: RuntimeException) {
            copySession.complete(request)
            reportFailure("PixelCopy request rejected", error)
            scheduleNextFrame(RETRY_DELAY_MILLIS)
        }
    }

    private fun drawBitmapToSurface(bitmap: Bitmap) {
        val destination = holder
        if (!destination.surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = destination.lockCanvas()
            if (canvas != null) {
                // The right-eye Surface can differ by a pixel or use a different buffer size.
                destinationBounds.set(0, 0, canvas.width, canvas.height)
                canvas.drawBitmap(bitmap, null, destinationBounds, bitmapPaint)
            }
        } catch (error: RuntimeException) {
            reportFailure("failed to draw to surface", error)
        } finally {
            if (canvas != null) {
                try {
                    destination.unlockCanvasAndPost(canvas)
                } catch (error: RuntimeException) {
                    reportFailure("surface disappeared before posting", error)
                }
            }
        }
    }

    private fun reportFailure(
        message: String,
        error: RuntimeException? = null,
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFailureLogMillis < FAILURE_LOG_INTERVAL_MILLIS) return
        lastFailureLogMillis = now
        Timber.w(error, "RayNeoMirrorView: %s", message)
    }

    companion object {
        private const val RETRY_DELAY_MILLIS = 100L
        private const val FAILURE_LOG_INTERVAL_MILLIS = 5_000L
    }
}
