package app.orcinus.shadow.core.model

/**
 * What a login outside the app came to (OAuthDialog, 3DPrinterOS's
 * TokenAuthDialog): the host keeps the login, or says why there is none.
 */
sealed interface CloudLoginOutcome {
    data object Success : CloudLoginOutcome

    data class Failure(val message: String) : CloudLoginOutcome
}

/** What OrcaSlicer's Obico host and PhysicalPrinterDialog build from the address of an Obico server. */
object ObicoHost {
    /** Obico::make_url(): a path of the server, http:// in front of an address without a scheme. */
    fun url(host: String, path: String): String = when {
        host.startsWith("http://") || host.startsWith("https://") -> if (host.endsWith('/')) host + path else "$host/$path"
        else -> "http://$host/$path"
    }

    /** Obico::get_login_url(): the page PrinterCloudAuthDialog logs in on, which hands back the token. */
    fun loginUrl(host: String): String = url(host, "o/authorize?response_type=token&client_id=OrcaSlicer&hide_navbar=true")

    /**
     * PhysicalPrinterDialog::update_ports(): the control page of the printer
     * chosen ("Name [id]"), which becomes the Device tab's page; empty without
     * a server, a printer or its id.
     */
    fun webUi(host: String, port: String): String {
        if (host.isEmpty() || port.isEmpty()) return ""
        val id = Regex("""\[(\d+)]""").find(port)?.groupValues?.get(1) ?: return ""
        return "$host/printers/$id/control"
    }
}
