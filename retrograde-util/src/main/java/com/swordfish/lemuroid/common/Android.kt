package com.swordfish.lemuroid.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

fun Bundle?.dump(): String {
    if (this == null) return "null"

    val builder = StringBuilder("Extras:\n")
    keySet()
        .toSet()
        .forEach { key ->
            builder.append(key).append(": ").append(get(key)).append("\n")
        }
    return builder.toString()
}

fun Activity.displayToast(
    string: String,
    length: Int = Toast.LENGTH_SHORT,
) {
    ActivityMessages.show(this, string, length)
}

fun Activity.displayToast(
    stringId: Int,
    length: Int = Toast.LENGTH_SHORT,
) {
    displayToast(getString(stringId), length)
}

/** Optional presentation at the common message boundary; the utility module stays device independent. */
object ActivityMessages {
    var presenter: ((Activity, String, Int) -> Boolean)? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(
        activity: Activity,
        message: String,
        duration: Int,
    ) {
        val present =
            Runnable {
                if (!activity.isDestroyed && !activity.isFinishing) {
                    val handled =
                        runCatching { presenter?.invoke(activity, message, duration) == true }.getOrDefault(
                            false,
                        )
                    if (!handled) Toast.makeText(activity, message, duration).show()
                }
            }
        if (Looper.myLooper() == Looper.getMainLooper()) present.run() else handler.post(present)
    }
}

fun Context.animationDuration(): Int {
    return resources.getInteger(android.R.integer.config_mediumAnimTime)
}

fun Context.shortAnimationDuration(): Int {
    return resources.getInteger(android.R.integer.config_shortAnimTime)
}

fun Context.longAnimationDuration(): Int {
    return resources.getInteger(android.R.integer.config_longAnimTime)
}

fun Context.displayDetailsSettingsScreen() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.fromParts("package", packageName, null)
    startActivity(intent)
}
