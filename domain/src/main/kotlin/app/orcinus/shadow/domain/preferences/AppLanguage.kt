package app.orcinus.shadow.domain.preferences

/**
 * The language the app shows (GUI_App::switch_language()), which Android
 * keeps for the app; until one is chosen the app follows the system's.
 */
fun interface AppLanguage {
    /** Shows the app in the language of the BCP 47 [tag]; the screen is built again in it. */
    fun select(tag: String)
}
