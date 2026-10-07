package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.OrcaHint
import app.orcinus.shadow.core.ui.orca.OrcaHints
import app.orcinus.shadow.core.ui.orca.OrcaHintsFile
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.openInBrowser

/**
 * HintDatabase's place among the hints, which lasts as long as the app: the
 * panel opens on a random hint (init_random_hint_id()), every slice that
 * begins draws another one (update_slicing_notif_dailytips()), and its arrows
 * step through them in the file's order.
 */
object DailyTips {
    private var index by mutableIntStateOf(-1)

    fun current(count: Int): Int {
        if (count == 0) return -1
        if (index !in 0 until count) index = (0 until count).random()
        return index
    }

    /** HintDataNavigation::Random */
    fun random(count: Int) {
        if (count > 0) index = (0 until count).random()
    }

    fun next(count: Int) {
        if (count > 0) index = if (current(count) < count - 1) index + 1 else 0
    }

    fun previous(count: Int) {
        if (count > 0) index = if (current(count) > 0) index - 1 else count - 1
    }
}

/**
 * DailyTipsPanel under the slicing progress (can_expand): collapsed, "Daily
 * Tips" with its arrow; expanded ([expanded], show_hints), the hint — its
 * headline marked, its <b> parts marked, and "For more information, please
 * check out Wiki" with its link — over "Collapse", the hint's number of all
 * and the arrows to the previous and the next.
 */
@Composable
fun DailyTipsPanel(expanded: Boolean, onExpand: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = OrcaTheme.colors
    val hints by produceState(OrcaHintsFile.loaded.orEmpty(), context) { value = OrcaHintsFile.load(context.applicationContext) }
    if (hints.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (expanded) {
            hints.getOrNull(DailyTips.current(hints.size))?.let { HintText(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onExpand(!expanded) }
                    .padding(vertical = 6.dp),
            ) {
                if (expanded) {
                    Text(orcaString("Collapse"), color = colors.textSide, style = OrcaTheme.typography.body13)
                } else {
                    Text(orcaString("Daily Tips"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold))
                }
                Image(
                    painterResource(if (expanded) DesignR.drawable.orca_notification_collapse else DesignR.drawable.orca_notification_expand),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
            }
            if (expanded) {
                Text("${DailyTips.current(hints.size) + 1}/${hints.size}", color = colors.onCanvasPanel, style = OrcaTheme.typography.body13)
                Arrow(DesignR.drawable.orca_notification_arrow_left) { DailyTips.previous(hints.size) }
                Arrow(DesignR.drawable.orca_notification_arrow_right) { DailyTips.next(hints.size) }
            }
        }
    }
}

/**
 * DailyTipsWindow of Help's "Show Tip of the Day": "Daily Tips" over the
 * hint the panel is on (retrieve_data_from_hint_database(Curr)), its number
 * of all and the arrows; a sheet on a phone, and on a larger window a dialog
 * in the middle as DailyTipsWindow::render() centres its window over the
 * canvas, its panel 400 wide with 25 of padding at each side.
 */
@Composable
fun DailyTipsWindow(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = OrcaTheme.colors
    val hints by produceState(OrcaHintsFile.loaded.orEmpty(), context) { value = OrcaHintsFile.load(context.applicationContext) }
    OrcaSheet(onDismissRequest = onDismiss, dialogWidth = TIPS_WINDOW_WIDTH) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(orcaString("Daily Tips"), color = colors.text, style = OrcaTheme.typography.head16, modifier = Modifier.padding(bottom = 12.dp))
            hints.getOrNull(DailyTips.current(hints.size))?.let { HintText(it, colors.text) }
            if (hints.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text("${DailyTips.current(hints.size) + 1}/${hints.size}", color = colors.text, style = OrcaTheme.typography.body13)
                    Arrow(DesignR.drawable.orca_notification_arrow_left) { DailyTips.previous(hints.size) }
                    Arrow(DesignR.drawable.orca_notification_arrow_right) { DailyTips.next(hints.size) }
                }
            }
        }
    }
}

/** DailyTipsDataRenderer::render_text() */
@Composable
private fun HintText(hint: OrcaHint, textColor: Color = OrcaTheme.colors.onCanvasPanel) {
    val context = LocalContext.current
    val colors = OrcaTheme.colors
    val text = OrcaHints.mainText(orcaString(hint.text))
    val title = text.substringBefore('\n')
    val body = text.substringAfter('\n', "")
    Text(marked(title, colors.accent, whole = true), color = textColor, style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold))
    if (body.isNotEmpty()) {
        Text(marked(body, colors.accent), color = textColor, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 2.dp))
    }
    if (hint.documentationLink.isNotEmpty()) {
        val line = orcaString("For more information, please check out Wiki")
        val wiki = orcaString("Wiki")
        val link = LinkAnnotation.Clickable(
            tag = "wiki",
            styles = TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)),
        ) { openInBrowser(context, hint.documentationLink) }
        Text(
            buildAnnotatedString {
                // The line up to "Wiki", whole where the translation has no "Wiki" in it,
                // and "Wiki" as its link, which wraps with the line.
                append(line.substringBefore(wiki))
                withLink(link) { append(wiki) }
            },
            color = textColor,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The colour markers the hints' <b> marks become; [whole] marks all of it, as the headline is. */
private fun marked(text: String, color: Color, whole: Boolean = false): AnnotatedString = buildAnnotatedString {
    if (whole) {
        withStyle(SpanStyle(color = color)) { append(text.replace("<b>", "").replace("</b>", "")) }
        return@buildAnnotatedString
    }
    var rest = text
    while (rest.isNotEmpty()) {
        val start = rest.indexOf("<b>")
        if (start < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, start))
        val end = rest.indexOf("</b>", start)
        val inner = if (end < 0) rest.substring(start + 3) else rest.substring(start + 3, end)
        withStyle(SpanStyle(color = color)) { append(inner) }
        rest = if (end < 0) "" else rest.substring(end + 4)
    }
}

@Composable
private fun Arrow(icon: Int, onClick: () -> Unit) {
    Image(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(28.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp),
    )
}

/** DailyTipsWindow::render(): the panel's 400 and the window's padding of 25 at each side. */
private val TIPS_WINDOW_WIDTH = 450.dp
