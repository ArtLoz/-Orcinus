package app.orcinus.shadow.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import java.util.Locale

/**
 * The language a number is written in. `Locale.getDefault()` is read once and
 * never again, so a composable that formats with it keeps the old language
 * after the phone's language changes; the locale of the composition does not.
 */
@Composable
fun textLocale(): Locale {
    val tag = ComposeLocale.current.toLanguageTag()
    return remember(tag) { Locale.forLanguageTag(tag) }
}
