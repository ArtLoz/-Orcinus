package app.orcinus.shadow.core.model

/**
 * What a login outside the app came to (OAuthDialog, 3DPrinterOS's
 * TokenAuthDialog): the host keeps the login, or says why there is none.
 */
sealed interface CloudLoginOutcome {
    data object Success : CloudLoginOutcome

    data class Failure(val message: String) : CloudLoginOutcome
}

/** A project of the 3DPrinterOS cloud, which a project file goes into. */
data class CloudProject(val id: String, val name: String)

/** A printer type of the 3DPrinterOS cloud, which the uploaded file is set to. */
data class CloudPrinterType(val id: String, val description: String)

/** 3DPrinterOS's session check and the lists UploadOptionsDialog offers. */
sealed interface Printer3dOsListsOutcome {
    data class Success(val projects: List<CloudProject>, val printerTypes: List<CloudPrinterType>) : Printer3dOsListsOutcome {
        /**
         * UploadOptionsDialog's printer type at first: the only one, or with
         * several the first naming the printer's model (Find(), which an empty
         * model always matches) — with several, it asks to check it
         * ([asksForPrinterType]) even then.
         */
        fun initialPrinterType(printerModel: String): Int? =
            if (printerTypes.size == 1) 0 else printerTypes.indexOfFirst { it.description.contains(printerModel) }.takeIf { it >= 0 }

        /** "Printer type not found, please select manually.", shown whenever there is more than one. */
        val asksForPrinterType: Boolean get() = printerTypes.size > 1

        /**
         * upload(): the project typed or picked is found by its name, or made
         * with it; the printer type by its description.
         */
        fun choice(project: String, printerType: CloudPrinterType): Printer3dOsChoice = Printer3dOsChoice(
            projectId = if (project.isEmpty()) "" else projects.firstOrNull { it.name == project }?.id.orEmpty(),
            projectName = project,
            printerTypeId = printerTypes.firstOrNull { it.description == printerType.description }?.id.orEmpty(),
        )
    }

    data class Failure(val message: String) : Printer3dOsListsOutcome
}

/**
 * UploadOptionsDialog's choice: a single file (no project), a project of the
 * cloud ([projectId]) or else a new one of [projectName], and the printer type.
 */
data class Printer3dOsChoice(val projectId: String = "", val projectName: String = "", val printerTypeId: String)

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
