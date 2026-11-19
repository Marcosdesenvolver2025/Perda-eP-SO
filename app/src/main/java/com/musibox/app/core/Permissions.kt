package com.musibox.app.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {
    val audio: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    /** Fotos/vídeos do celular: só no Android 12 ou mais antigo (no 13+ o app não pede acesso à galeria). */
    val visualMedia: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) emptyArray() else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasAudio(context: Context) = granted(context, audio)

    fun hasImages(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 && granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasVideos(context: Context): Boolean = hasImages(context)

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
