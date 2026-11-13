package com.ultimate.codecreator.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ultimate.codecreator.model.SavedSnippet
import java.io.File

object DocumentIo {
    const val MAX_TEXT_CHARACTERS = 200_000

    fun readText(context: Context, uri: Uri, maxCharacters: Int = MAX_TEXT_CHARACTERS): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Could not open file.")
        input.reader(Charsets.UTF_8).buffered().use { reader ->
            val builder = StringBuilder()
            val buffer = CharArray(8_192)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                builder.append(buffer, 0, read)
                require(builder.length <= maxCharacters) { "File is too large for the mobile editor." }
            }
            return builder.toString()
        }
    }

    fun writeText(context: Context, uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalArgumentException("Could not open destination.")
        output.writer(Charsets.UTF_8).buffered().use { it.write(text) }
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }

    fun shareSnippetFile(context: Context, snippet: SavedSnippet) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "snippet.${snippet.language.fileExtension}")
        file.writeText(snippet.code, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, snippet.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, snippet.title))
    }
}
