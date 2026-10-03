package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ExternalDocumentReference
import java.io.InputStream

/**
 * The documents the app keeps lists of (MainFrame's recent projects, the
 * documents model files were copied from): the app's access to them across
 * starts, what they are called, when they last changed, and what they hold.
 */
interface DocumentAccess {
    /**
     * Keeps the app's access to [document] after it restarts for [holder], the
     * list that needs it; nothing for a document that grants none.
     */
    fun keep(document: ExternalDocumentReference, holder: String)

    /** [holder] no longer needs [document]; the access goes once no list keeps it. */
    fun release(document: ExternalDocumentReference, holder: String)

    /** The document's name and when it last changed; null while it is gone or cannot be read (wxFileExists()). */
    fun describe(document: ExternalDocumentReference): DocumentDescription?

    /** The document to read; null when it cannot be opened. */
    fun open(document: ExternalDocumentReference): InputStream?
}

/** A document's name and when it last changed, in milliseconds since the epoch; null where it does not say. */
data class DocumentDescription(val name: String, val modified: Long?)
