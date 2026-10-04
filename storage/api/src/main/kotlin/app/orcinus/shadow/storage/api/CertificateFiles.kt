package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference

/**
 * The HTTPS CA file a print host trusts (printhost_cafile), which
 * PhysicalPrinterDialog's Browse button picks. The app keeps a copy of the
 * document under its own name, which the printer preset names by path.
 */
interface CertificateFiles {
    /** The path of the copy of [document]; null when it could not be read. */
    suspend fun keep(document: ExternalDocumentReference): String?
}
