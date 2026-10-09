package com.swordfish.lemuroid.app.rayneo

/** Framework-independent ordering and target ownership for a single window's native navigation. */
internal class RayNeoNavigationSession<T : Any>(private val host: Host<T>) {
    enum class Key { UP, DOWN, LEFT, RIGHT, CENTER, MENU }

    interface Host<T> {
        fun isActive(): Boolean

        fun currentTarget(): T?

        fun recoverFocus()

        fun dispatchKey(
            key: Key,
            down: Boolean,
        ): Boolean

        fun moveViewFocus(key: Key)
    }

    fun move(key: Key) {
        if (!host.isActive()) return
        if (host.currentTarget() == null) host.recoverFocus()
        val before = host.currentTarget()
        val handled = sendPair(key)
        if (!host.isActive()) return
        if (!handled && before != null && before == host.currentTarget()) host.moveViewFocus(key)
        if (host.currentTarget() == null) host.recoverFocus()
    }

    fun confirm(target: T) {
        if (host.isActive() && target == host.currentTarget()) sendPair(Key.CENTER)
    }

    fun menu() {
        if (host.isActive()) sendPair(Key.MENU)
    }

    private fun sendPair(key: Key): Boolean {
        val downHandled = host.dispatchKey(key, true)
        val upHandled = host.dispatchKey(key, false)
        return downHandled || upHandled
    }
}
