package com.musibox.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.musibox.app.download.Platform
import java.io.File

/** Ações que abrem outros apps (abrir, compartilhar, plataformas). */
object ExternalActions {

    /** Abre o app oficial da plataforma (ou o site) para o usuário escolher o conteúdo e compartilhar. */
    fun openPlatform(context: Context, platform: Platform): Boolean {
        val (packages, site) = when (platform) {
            Platform.YOUTUBE -> listOf("com.google.android.youtube") to "https://m.youtube.com"
            Platform.TIKTOK -> listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill") to "https://www.tiktok.com"
            Platform.INSTAGRAM -> listOf("com.instagram.android") to "https://www.instagram.com"
            Platform.FACEBOOK -> listOf("com.facebook.katana") to "https://m.facebook.com"
            else -> emptyList<String>() to "https://www.google.com"
        }
        for (pkg in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
            }
        }
        return openUrl(context, site)
    }

    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun openContent(context: Context, uri: Uri, mime: String?): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "Abrir com").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun shareContent(context: Context, uris: List<Uri>, mime: String?): Boolean = try {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mime ?: "*/*"
        intent.clipData = ClipData.newRawUri("", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(intent, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
