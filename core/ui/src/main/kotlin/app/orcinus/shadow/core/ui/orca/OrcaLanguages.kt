package app.orcinus.shadow.core.ui.orca

import java.util.Locale

/**
 * A language of PreferencesDialog::create_item_language_combobox(): its
 * catalogue (localization/i18n/<catalog>), the locale the app takes for it,
 * the name the combo box shows, and wxLanguageInfo::CanonicalName, which
 * OrcaSlicer.conf keeps as "language".
 */
data class OrcaLanguage(val catalog: String, val tag: String, val name: String, val canonicalName: String)

/**
 * English and the languages OrcaSlicer has a catalogue of and lists
 * (supported_languages), under the names the combo box gives them, in the
 * order of their English descriptions (wxLanguageInfo::Description).
 */
val ORCA_LANGUAGES = listOf(
    OrcaLanguage("ca", "ca", "Catalan", "ca_ES"),
    // wxLANGUAGE_CHINESE ("Chinese") is the catalogue zh_TW.
    OrcaLanguage("zh_TW", "zh-TW", "中文(繁體)", "zh_TW"),
    OrcaLanguage("zh_CN", "zh-CN", "中文(简体)", "zh_CN"),
    OrcaLanguage("cs", "cs", "Czech", "cs_CZ"),
    OrcaLanguage("nl", "nl", "Nederlands", "nl_NL"),
    OrcaLanguage("en", "en", "English", "en_GB"),
    OrcaLanguage("fr", "fr", "Français", "fr_FR"),
    OrcaLanguage("de", "de", "Deutsch", "de_DE"),
    OrcaLanguage("hu", "hu", "Magyar", "hu_HU"),
    OrcaLanguage("it", "it", "italiano", "it_IT"),
    OrcaLanguage("ja", "ja", "日本語", "ja_JP"),
    OrcaLanguage("ko", "ko", "한국어", "ko_KR"),
    OrcaLanguage("lt", "lt", "Lietuvių", "lt_LT"),
    OrcaLanguage("pl", "pl", "Polski", "pl_PL"),
    OrcaLanguage("pt_BR", "pt-BR", "Português (Brasil)", "pt_BR"),
    OrcaLanguage("ru", "ru", "Русский", "ru_RU"),
    OrcaLanguage("es", "es", "Español", "es_ES"),
    OrcaLanguage("sv", "sv", "Svenska", "sv_SE"),
    OrcaLanguage("th", "th", "ไทย", "th_TH"),
    OrcaLanguage("tr", "tr", "Turkish", "tr_TR"),
    OrcaLanguage("uk", "uk", "Ukrainian", "uk_UA"),
    OrcaLanguage("vi", "vi", "Tiếng Việt", "vi_VN"),
)

/**
 * GUI_App::load_language(): the language of [locale] among OrcaSlicer's, by
 * its region where OrcaSlicer's catalogues differ by one (Chinese), English
 * for a language OrcaSlicer does not translate.
 */
fun orcaLanguageOf(locale: Locale): OrcaLanguage {
    val catalog = when (locale.language) {
        "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh_TW" else "zh_CN"
        "pt" -> "pt_BR"
        else -> locale.language
    }
    return ORCA_LANGUAGES.firstOrNull { it.catalog == catalog } ?: ORCA_LANGUAGES.first { it.catalog == "en" }
}
