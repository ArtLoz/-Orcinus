package app.orcinus.shadow.core.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * Android's share sheet for [document], a file of type [mimeType] the app
 * offers to other apps (FileShare): the app the user picks may read it.
 */
fun Context.shareDocument(document: ExternalDocumentReference, mimeType: String) {
    val uri = Uri.parse(document.value)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}
