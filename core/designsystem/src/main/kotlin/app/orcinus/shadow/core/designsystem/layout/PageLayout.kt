package app.orcinus.shadow.core.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** The widest a page's content gets on a large window. */
object OrcaPageWidth {
    /**
     * A settings page: OrcaSlicer's label (OptionsGroup::label_width, 20 em)
     * and field close together, as in ParamsDialog and PreferencesDialog
     * (DESIGN_WINDOW_SIZE, 640 px), with room for a finger.
     */
    val Settings: Dp = 600.dp

    /** A page of text and lists, such as About: lines a reader can follow. */
    val Text: Dp = 720.dp

    /** The list of pages beside a page (Tab::m_tabctrl, 20 em, and touch room). */
    val PageList: Dp = 240.dp
}

/** The padding at each side that centres content of at most [maxWidth] in [width]. */
fun centringPadding(width: Dp, maxWidth: Dp): Dp = ((width - maxWidth) / 2).coerceAtLeast(0.dp)

/**
 * A box whose content, such as a lazy list, gets [contentPadding] that keeps
 * its rows at most [maxWidth] wide in the middle, while the list itself, and
 * so its scrolling, spans the whole box.
 */
@Composable
fun OrcaCentredContent(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(contentPadding: PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier) {
        content(PaddingValues(horizontal = centringPadding(this.maxWidth, maxWidth)))
    }
}

/** Keeps a column of content at most [maxWidth] wide, in the middle of the width it has. */
fun Modifier.orcaContentWidth(maxWidth: Dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth)

/**
 * A page with its list of pages at the start, as OrcaSlicer's settings tabs
 * and Preferences list theirs: [list] in a pane of [listWidth] on the
 * sidebar's colour, [content] in the rest.
 */
@Composable
fun OrcaListDetailPanes(
    list: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listWidth: Dp = OrcaPageWidth.PageList,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(listWidth)
                .fillMaxHeight()
                .background(OrcaTheme.colors.sidebarBackground),
        ) {
            list()
        }
        VerticalDivider(color = OrcaTheme.colors.separator)
        content()
    }
}

/**
 * The list of a window's pages down its side (TabCtrl as Tab and
 * PreferencesDialog fill it): the selected page in bold on the accent's tint,
 * a page with a modified value in the colour of a modified value
 * (Tab::update_changed_tree_ui()). [icons] stand before the titles where the
 * page has one.
 */
@Composable
fun OrcaPageList(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    modified: List<Boolean> = emptyList(),
    icons: List<Int?> = emptyList(),
) {
    val colors = OrcaTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            .selectableGroup(),
    ) {
        val withIcons = icons.any { it != null }
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .background(if (selected) colors.accentSelected else Color.Transparent)
                    .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (withIcons) {
                    val icon = icons.getOrNull(index)
                    if (icon != null) {
                        Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(OrcaTheme.dimensions.icon))
                    } else {
                        Box(Modifier.size(OrcaTheme.dimensions.icon))
                    }
                }
                Text(
                    text = title,
                    color = when {
                        modified.getOrElse(index) { false } -> colors.labelModified
                        selected -> colors.text
                        else -> colors.textDimmed
                    },
                    style = if (selected) OrcaTheme.typography.head14 else OrcaTheme.typography.body14,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (withIcons) 12.dp else 0.dp),
                )
            }
        }
    }
}
