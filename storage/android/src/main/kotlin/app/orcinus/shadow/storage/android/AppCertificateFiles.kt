package app.orcinus.shadow.storage.android

import android.content.Context
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.CertificateFiles

/** Copies in files/certificates. */
class AppCertificateFiles(context: Context) : CertificateFiles {
    private val applicationContext = context.applicationContext

    override suspend fun keep(document: ExternalDocumentReference): String? = applicationContext.keepDocumentCopy(document, "certificates")
}
