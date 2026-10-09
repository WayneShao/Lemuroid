package com.swordfish.lemuroid.app.rayneo

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.lang.ref.WeakReference

/** One instance per overlay/window. It never looks through an Activity into another window. */
@OptIn(ExperimentalComposeUiApi::class)
internal class RayNeoWindowNavigation(
    private val overlay: View,
) : RayNeoNavigationSession.Host<RayNeoWindowNavigation.Target> {
    data class Target(
        val view: View,
        val ancestry: List<View>,
        val semanticsId: Int? = null,
        val semantics: SemanticsConfiguration? = null,
        val bounds: androidx.compose.ui.geometry.Rect? = null,
        val adapter: Any? = null,
        val itemPosition: Int? = null,
        val itemId: Long? = null,
        val item: Any? = null,
    )

    private val session = RayNeoNavigationSession(this)
    private var keyDispatcher: ((KeyEvent) -> Boolean)? = null
    private var lastRegion = WeakReference<View>(null)
    private var lastView = WeakReference<View>(null)
    private var pendingCheck: Runnable? = null
    private var generation = 0
    private var keyDownTime = 0L
    private val root: View get() = overlay.rootView

    fun setKeyDispatcher(dispatcher: (KeyEvent) -> Boolean) {
        keyDispatcher = dispatcher
    }

    override fun isActive(): Boolean = overlay.isAttachedToWindow && overlay.hasWindowFocus() && overlay.isShown

    fun captureTarget(): Target? {
        if (!isActive()) return null
        recoverFocus()
        return currentTarget()
    }

    override fun currentTarget(): Target? {
        if (!isActive()) return null
        val focused = root.findFocus()?.takeIf(::validView) ?: return null
        val path = ancestry(focused)
        val compose = focused as? ViewRootForTest
        if (compose != null) {
            val node =
                semanticsNodes(compose).lastOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
                    ?: return null
            if (!validNode(node)) return null
            rememberRegion(focused)
            return Target(focused, path, node.id, node.config.copy(), node.boundsInRoot)
        }
        val list = path.filterIsInstance<AdapterView<*>>().firstOrNull()
        if (list != null && list.selectedItemPosition == AdapterView.INVALID_POSITION) return null
        rememberRegion(focused)
        return Target(
            focused,
            path,
            adapter = list?.adapter,
            itemPosition = list?.selectedItemPosition,
            itemId = list?.selectedItemId,
            item = list?.selectedItem,
        )
    }

    override fun recoverFocus() {
        if (!isActive()) return
        root.findFocus()?.takeIf(::validView)?.let { focused ->
            if (focused.isInTouchMode) focused.requestFocusFromTouch()
        }
        if (currentTarget() != null) return
        val candidates = listOfNotNull(lastView.get(), lastRegion.get(), root).distinct()
        for (candidate in candidates) {
            if (!validView(candidate) || !candidate.hasFocusable()) continue
            candidate.requestFocusFromTouch()
            if (currentTarget() != null) return
            val focused = root.findFocus()
            val compose = focused as? ViewRootForTest
            if (compose != null) {
                // Request focus only: never invoke a click while establishing a navigation target.
                val node =
                    semanticsNodes(compose).firstOrNull {
                        validNode(it) && it.config.getOrNull(SemanticsActions.RequestFocus)?.action != null
                    }
                node?.config?.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
            } else if (focused is AdapterView<*> && focused.count > 0 && focused.selectedItemPosition < 0) {
                focused.setSelection(focused.firstVisiblePosition.coerceIn(0, focused.count - 1))
            }
            if (currentTarget() != null) return
        }
    }

    fun execute(command: RayNeoGestureRecognizer.Command) {
        if (!isActive()) return
        when (command.gesture) {
            RayNeoGestureRecognizer.Gesture.CONFIRM -> (command.target as? Target)?.let { confirmWhenStable(it, 0) }
            RayNeoGestureRecognizer.Gesture.MENU -> session.menu()
            RayNeoGestureRecognizer.Gesture.UP -> session.move(RayNeoNavigationSession.Key.UP)
            RayNeoGestureRecognizer.Gesture.DOWN -> session.move(RayNeoNavigationSession.Key.DOWN)
            RayNeoGestureRecognizer.Gesture.LEFT -> session.move(RayNeoNavigationSession.Key.LEFT)
            RayNeoGestureRecognizer.Gesture.RIGHT -> session.move(RayNeoNavigationSession.Key.RIGHT)
            RayNeoGestureRecognizer.Gesture.BACK -> Unit
        }
        root.invalidate()
        if (command.gesture != RayNeoGestureRecognizer.Gesture.CONFIRM) observeAfterNavigation(0)
    }

    override fun dispatchKey(
        key: RayNeoNavigationSession.Key,
        down: Boolean,
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        if (down) keyDownTime = now
        val keyCode =
            when (key) {
                RayNeoNavigationSession.Key.UP -> KeyEvent.KEYCODE_DPAD_UP
                RayNeoNavigationSession.Key.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                RayNeoNavigationSession.Key.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                RayNeoNavigationSession.Key.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                RayNeoNavigationSession.Key.CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
                RayNeoNavigationSession.Key.MENU -> KeyEvent.KEYCODE_MENU
            }
        val event =
            KeyEvent(
                keyDownTime,
                now,
                if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
                keyCode,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_DPAD,
            )
        return keyDispatcher?.invoke(event) ?: root.dispatchKeyEvent(event)
    }

    override fun moveViewFocus(key: RayNeoNavigationSession.Key) {
        val focused = root.findFocus()?.takeIf(::validView) ?: return
        val direction =
            when (key) {
                RayNeoNavigationSession.Key.UP -> View.FOCUS_UP
                RayNeoNavigationSession.Key.DOWN -> View.FOCUS_DOWN
                RayNeoNavigationSession.Key.LEFT -> View.FOCUS_LEFT
                RayNeoNavigationSession.Key.RIGHT -> View.FOCUS_RIGHT
                else -> return
            }
        val next = focused.focusSearch(direction) ?: return
        // Containers can have focusable descendants without being focusable themselves.
        if (next !== focused && validView(next) && next.hasFocusable()) next.requestFocus(direction)
    }

    fun cancel() {
        generation++
        pendingCheck?.let(overlay::removeCallbacks)
        pendingCheck = null
    }

    fun onWindowReady() {
        observeAfterNavigation(0)
    }

    private fun confirmWhenStable(
        target: Target,
        attempt: Int,
    ) {
        if (!isActive() || target != currentTarget()) return
        if (layoutPending()) {
            if (attempt < MAX_LAYOUT_CHECKS) postCheck { confirmWhenStable(target, attempt + 1) }
            return
        }
        session.confirm(target)
    }

    private fun observeAfterNavigation(attempt: Int) {
        if (!isActive()) return
        postCheck {
            if (layoutPending() && attempt < MAX_LAYOUT_CHECKS) {
                observeAfterNavigation(attempt + 1)
            } else {
                // A valid, unmoved target is a normal navigation boundary; do not reset it.
                if (currentTarget() == null) recoverFocus()
                root.invalidate()
            }
        }
    }

    private fun postCheck(action: () -> Unit) {
        pendingCheck?.let(overlay::removeCallbacks)
        val expectedGeneration = generation
        pendingCheck =
            Runnable {
                pendingCheck = null
                if (generation == expectedGeneration && isActive()) action()
            }.also(overlay::postOnAnimation)
    }

    private fun layoutPending(): Boolean =
        root.isLayoutRequested || (root.findFocus() as? ViewRootForTest)?.hasPendingMeasureOrLayout == true

    private fun validView(view: View): Boolean =
        view !== overlay && view.isAttachedToWindow && view.rootView === root && view.isShown && view.isEnabled

    private fun ancestry(view: View): List<View> = generateSequence(view) { it.parent as? View }.toList()

    private fun rememberRegion(view: View) {
        lastView = WeakReference(view)
        lastRegion = WeakReference(view.parent as? View)
    }

    private fun validNode(node: SemanticsNode): Boolean =
        node.layoutInfo.isAttached && node.layoutInfo.isPlaced && !node.boundsInRoot.isEmpty &&
            !node.config.contains(SemanticsProperties.Disabled)

    private fun semanticsNodes(owner: ViewRootForTest): List<SemanticsNode> {
        val nodes = ArrayList<SemanticsNode>()

        fun visit(node: SemanticsNode) {
            nodes += node
            node.children.forEach(::visit)
        }
        visit(owner.semanticsOwner.unmergedRootSemanticsNode)
        return nodes
    }

    private companion object {
        const val MAX_LAYOUT_CHECKS = 3
    }
}
