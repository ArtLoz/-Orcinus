package app.orcinus.shadow.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme

@Preview(name = "Light", widthDp = 420)
@Preview(name = "Dark", widthDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChromeAndButtonsPreview() = OrcinusTheme {
    Column(Modifier.background(OrcaTheme.colors.window)) {
        OrcaTabBar(
            tabs = listOf(OrcaTab("Подготовка", R.drawable.orca_tab_3d_active), OrcaTab("Просмотр нарезки", R.drawable.orca_tab_preview_active)),
            selectedIndex = 0,
            onSelect = {},
        ) {
            OrcaButton("Нарезать", onClick = {})
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OrcaButton("Нарезать", onClick = {})
            OrcaButton("Отмена", onClick = {}, style = OrcaButtonStyle.Regular)
            OrcaButton("Удалить", onClick = {}, style = OrcaButtonStyle.Alert, size = OrcaButtonSize.Compact)
            OrcaIconButton(R.drawable.orca_cog, contentDescription = "", onClick = {})
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OrcaButton("Выбор", onClick = {}, style = OrcaButtonStyle.Regular, size = OrcaButtonSize.Choice)
            OrcaOutlinedButton("0.20mm Standard", onClick = {})
            OrcaButton("Недоступно", onClick = {}, enabled = false)
        }
    }
}

@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SidebarPreview() = OrcinusTheme {
    Column(Modifier.background(OrcaTheme.colors.window)) {
        OrcaSidebarTitle("Принтер", R.drawable.orca_printer) {
            OrcaIconButton(R.drawable.orca_cog, contentDescription = "", onClick = {})
        }
        OrcaSidebarSection { OrcaComboField("Creality K2 Plus 0.4 nozzle", onClick = {}) }
        OrcaSidebarTitle("Материал", R.drawable.orca_filament)
        OrcaSidebarSection {
            OrcaComboField("Generic PLA", onClick = {}) {
                OrcaFilamentSlot(1)
                Box(Modifier.width(8.dp))
            }
        }
        OrcaSidebarTitle("Настройки", R.drawable.orca_process) {
            OrcaSegmentedSwitch(listOf("Общие", "Модели"), selectedIndex = 0, onSelect = {})
        }
        OrcaUnderlineTabs(listOf("Вид", "Прочность", "Поддержки", "Прочее"), selectedIndex = 0, onSelect = {})
        OrcaParameterGroupHeader("Высота слоя", R.drawable.orca_param_layer_height)
        OrcaParameterRow("Высота слоя") { OrcaTextField("0.2", onValueChange = {}, unit = "мм") }
        OrcaParameterRow("Только один периметр сверху") { OrcaCheckBox(checked = true, onCheckedChange = {}) }
        OrcaParameterRow("Точные стенки") { OrcaCheckBox(checked = false, onCheckedChange = {}) }
        OrcaParameterRow("Поддержки") { OrcaSwitch(checked = true, onCheckedChange = {}) }
    }
}

@Preview(name = "Light", widthDp = 420, heightDp = 420)
@Preview(name = "Dark", widthDp = 420, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CanvasPreview() = OrcinusTheme {
    OrcaCanvas(Modifier.fillMaxWidth().height(420.dp)) {
        OrcaCanvasToolbar(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
            OrcaCanvasTool(R.drawable.orca_toolbar_open, "", onClick = {})
            OrcaCanvasTool(R.drawable.orca_toolbar_arrange, "", onClick = {})
            OrcaCanvasTool(R.drawable.orca_toolbar_orient, "", onClick = {}, enabled = false)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OrcaProgressNotification("Нарезка: 45%", "Generating infill toolpath", 0.45f, "Отмена", onCancel = {})
            OrcaNotification {
                OrcaNotificationText("Имя модели: A.stl", emphasized = true)
                OrcaNotificationText("Размер: 28.56 × 33.87 × 12.00 мм")
            }
        }
        OrcaCanvasRoundButton(R.drawable.orca_canvas_zoom, "", onClick = {}, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

@Preview(name = "Light", widthDp = 420, heightDp = 520)
@Preview(name = "Dark", widthDp = 420, heightDp = 520, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SlidersPreview() = OrcinusTheme {
    OrcaCanvas(Modifier.fillMaxWidth().height(520.dp)) {
        OrcaLayerRangeSlider(
            layerCount = 100,
            lower = 0,
            higher = 42,
            oneLayer = false,
            onRangeChange = { _, _ -> },
            onOneLayerChange = {},
            label = { layer -> "${layer + 1} · 0.20 мм" },
            contentDescription = "",
            stepUpDescription = "",
            stepDownDescription = "",
            oneLayerDescription = "",
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 8.dp, bottom = 80.dp).fillMaxHeight(),
        )
        OrcaMovePlayer(
            moveCount = 144,
            position = 60,
            onPositionChange = {},
            label = "61/144",
            contentDescription = "",
            playDescription = "",
            previousDescription = "",
            nextDescription = "",
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
        )
    }
}

@Preview(name = "Light", widthDp = 420, heightDp = 640)
@Preview(name = "Dark", widthDp = 420, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LegendSheetPreview() = OrcinusTheme {
    Column(Modifier.fillMaxWidth().background(OrcaTheme.colors.window)) {
        OrcaSheetHandle(Modifier.align(Alignment.CenterHorizontally))
        OrcaSummaryRow(
            listOf(
                OrcaSummaryItem(R.drawable.orca_monitor_item_prediction, "12m11s", "Время печати"),
                OrcaSummaryItem(R.drawable.orca_filament, "1.21 m", "3.64g"),
                OrcaSummaryItem(R.drawable.orca_param_layer_height, "100", "Слоёв"),
            ),
        )
        OrcaChoiceChips(listOf("Тип линии", "Сводка", "Материал", "Скорость"), selected = 0, onSelect = {}, modifier = Modifier.padding(top = 16.dp))
        OrcaLegendSection("Тип линии") {
            OrcaLegendItem(Color(0xFFFFE64D), "Внутренние периметры", "2m15s · 18.6%", 0.186f, "0.24m", "0.72g", visible = true, onToggle = {})
            OrcaLegendItem(Color(0xFF383ED9), "Перемещения", "29s · 4.0%", 0.04f, "4.57m", "786", visible = false, onToggle = {})
        }
        OrcaLegendSection("Скорость (мм/с)") {
            OrcaColorScale(listOf(Color(0xFF0B2C7A), Color(0xFF1FA84F), Color(0xFFFFE64D), Color(0xFFD6301B)), "10", "255", "500")
        }
        OrcaLegendSection("Общая оценка") {
            OrcaLegendValue("Общий расход:", "1.21 m · 3.64g")
            OrcaLegendValue("Общее время печати:", "12m11s", emphasized = true)
        }
    }
}
