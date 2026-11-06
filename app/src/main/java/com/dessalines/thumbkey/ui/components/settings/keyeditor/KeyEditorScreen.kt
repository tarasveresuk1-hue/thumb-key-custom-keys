package com.dessalines.thumbkey.ui.components.settings.keyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.db.AppSettingsViewModel
import com.dessalines.thumbkey.db.KeyModificationsUpdate
import com.dessalines.thumbkey.utils.KeyActionSerializable
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyCSerializable
import com.dessalines.thumbkey.utils.KeyDisplay
import com.dessalines.thumbkey.utils.KeyItemC
import com.dessalines.thumbkey.utils.KeyItemCSerializable
import com.dessalines.thumbkey.utils.KeyboardCSerializable
import com.dessalines.thumbkey.utils.KeyboardDefinitionModesSerializable
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.SimpleTopAppBar
import com.dessalines.thumbkey.utils.deserializeKeyModifications
import com.dessalines.thumbkey.utils.getYaml
import com.dessalines.thumbkey.utils.keyEventFromSerializable
import com.dessalines.thumbkey.utils.resolveKeyboardDefinition
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val KEY_PRESETS =
    listOf(
        "Home" to "MOVE_HOME",
        "End" to "MOVE_END",
        "PgUp" to "PAGE_UP",
        "PgDn" to "PAGE_DOWN",
        "←" to "DPAD_LEFT",
        "→" to "DPAD_RIGHT",
        "↑" to "DPAD_UP",
        "↓" to "DPAD_DOWN",
        "Del" to "FORWARD_DEL",
        "Bksp" to "DEL",
        "Tab" to "TAB",
        "Esc" to "ESCAPE",
        "Enter" to "ENTER",
        "Ins" to "INSERT",
    )

private val ACTION_TYPES =
    listOf(
        "text" to "Текст",
        "key" to "Клавіша / комбінація",
        "action" to "Готова дія",
        "empty" to "Порожньо",
        "resetDir" to "Скинути напрямок",
        "resetKey" to "Скинути всю клавішу",
    )

private val DIR_GRID =
    listOf(
        listOf("topLeft", "top", "topRight"),
        listOf("left", "center", "right"),
        listOf("bottomLeft", "bottom", "bottomRight"),
    )

private val DIR_ARROWS =
    mapOf(
        "topLeft" to "↖",
        "top" to "↑",
        "topRight" to "↗",
        "left" to "←",
        "center" to "●",
        "right" to "→",
        "bottomLeft" to "↙",
        "bottom" to "↓",
        "bottomRight" to "↘",
    )

private fun txtOf(c: KeyC?): String {
    val d = c?.display
    return when (d) {
        is KeyDisplay.TextDisplay -> d.text
        is KeyDisplay.IconDisplay -> "▣"
        null -> "–"
    }
}

private fun dirKeyC(
    k: KeyItemC,
    dir: String,
): KeyC? =
    when (dir) {
        "topLeft" -> k.topLeft
        "top" -> k.top
        "topRight" -> k.topRight
        "left" -> k.left
        "center" -> k.center
        "right" -> k.right
        "bottomLeft" -> k.bottomLeft
        "bottom" -> k.bottom
        else -> k.bottomRight
    }

private fun getModeSer(
    m: KeyboardDefinitionModesSerializable,
    n: String,
): KeyboardCSerializable? =
    when (n) {
        "shifted" -> m.shifted
        "numeric" -> m.numeric
        else -> m.main
    }

private fun setModeSer(
    m: KeyboardDefinitionModesSerializable,
    n: String,
    v: KeyboardCSerializable?,
): KeyboardDefinitionModesSerializable =
    when (n) {
        "shifted" -> m.copy(shifted = v)
        "numeric" -> m.copy(numeric = v)
        else -> m.copy(main = v)
    }

private fun getKeyItem(
    kb: KeyboardCSerializable,
    name: String,
): KeyItemCSerializable? = KeyboardCSerializable::class.memberProperties.find { it.name == name }?.get(kb) as? KeyItemCSerializable

private fun setKeyItem(
    kb: KeyboardCSerializable,
    name: String,
    item: KeyItemCSerializable?,
): KeyboardCSerializable {
    val ctor = KeyboardCSerializable::class.primaryConstructor!!
    val args =
        ctor.parameters.associateWith { p ->
            if (p.name == name) item else getKeyItem(kb, p.name!!)
        }
    return ctor.callBy(args)
}

private fun setDir(
    i: KeyItemCSerializable,
    dir: String,
    v: KeyCSerializable?,
): KeyItemCSerializable =
    when (dir) {
        "topLeft" -> i.copy(topLeft = v)
        "top" -> i.copy(top = v)
        "topRight" -> i.copy(topRight = v)
        "left" -> i.copy(left = v)
        "center" -> i.copy(center = v)
        "right" -> i.copy(right = v)
        "bottomLeft" -> i.copy(bottomLeft = v)
        "bottom" -> i.copy(bottom = v)
        else -> i.copy(bottomRight = v)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEditorScreen(
    navController: NavController,
    appSettingsViewModel: AppSettingsViewModel,
) {
    val settings by appSettingsViewModel.appSettings.observeAsState()
    val modsText = settings?.keyModifications ?: ""
    val parsed: Map<String, KeyboardDefinitionModesSerializable>? =
        try {
            if (modsText.isBlank()) emptyMap() else deserializeKeyModifications(modsText)
        } catch (e: Exception) {
            null
        }

    var layoutIdx by remember { mutableIntStateOf(0) }
    var layoutMenu by remember { mutableStateOf(false) }
    var modeName by remember { mutableStateOf("main") }
    var selRow by remember { mutableIntStateOf(-1) }
    var selCol by remember { mutableIntStateOf(-1) }
    var dir by remember { mutableStateOf("center") }
    var actionType by remember { mutableStateOf("text") }
    var textValue by remember { mutableStateOf("") }
    var keyCodeValue by remember { mutableStateOf("MOVE_HOME") }
    var labelValue by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var metaKey by remember { mutableStateOf(false) }
    var actionName by remember { mutableStateOf(KeyActionSerializable.Copy.name) }
    var actionMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val layout = KeyboardLayout.entries[layoutIdx]
    val definition = resolveKeyboardDefinition(layout, modsText)
    val kb =
        when (modeName) {
            "shifted" -> definition.modes.shifted
            "numeric" -> definition.modes.numeric
            else -> definition.modes.main
        }

    fun write(map: Map<String, KeyboardDefinitionModesSerializable>) {
        val text =
            if (map.isEmpty()) {
                ""
            } else {
                getYaml().encodeToString(
                    MapSerializer(String.serializer(), KeyboardDefinitionModesSerializable.serializer()),
                    map,
                )
            }
        appSettingsViewModel.updateKeyModifications(
            KeyModificationsUpdate(id = 1, keyModifications = text),
        )
    }

    fun applyEdit(
        entry: KeyCSerializable?,
        resetWholeKey: Boolean,
    ) {
        val base = parsed
        if (base == null) {
            message = "У Modify keys є помилка, спершу виправ її"
            return
        }
        if (selRow < 0) {
            message = "Спочатку обери клавішу"
            return
        }
        if (entry != null && !entry.remove) {
            try {
                keyEventFromSerializable(entry)
            } catch (e: Exception) {
                message = e.message ?: "Невірна клавіша"
                return
            }
        }
        val keyName = "key${selRow}_$selCol"
        val modes = base[layout.name] ?: KeyboardDefinitionModesSerializable()
        val kbS = getModeSer(modes, modeName) ?: KeyboardCSerializable()
        val item = getKeyItem(kbS, keyName) ?: KeyItemCSerializable()
        val newItem: KeyItemCSerializable? =
            if (resetWholeKey) {
                null
            } else {
                setDir(item, dir, entry).takeIf { it != KeyItemCSerializable() }
            }
        val newKb = setKeyItem(kbS, keyName, newItem).takeIf { it != KeyboardCSerializable() }
        val newModes = setModeSer(modes, modeName, newKb).takeIf { it != KeyboardDefinitionModesSerializable() }
        val map = base.toMutableMap()
        if (newModes == null) map.remove(layout.name) else map[layout.name] = newModes
        write(map)
        message = "Збережено"
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                text = stringResource(R.string.key_editor),
                navController = navController,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .background(color = MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("1. Розкладка")
                Box {
                    OutlinedButton(onClick = { layoutMenu = true }) {
                        Text(layout.keyboardDefinition.title)
                    }
                    DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                        KeyboardLayout.entries.forEachIndexed { i, l ->
                            DropdownMenuItem(
                                text = { Text(l.keyboardDefinition.title) },
                                onClick = {
                                    layoutIdx = i
                                    selRow = -1
                                    selCol = -1
                                    layoutMenu = false
                                },
                            )
                        }
                    }
                }

                Text("2. Режим")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("main", "shifted", "numeric").forEach { m ->
                        FilterChip(
                            selected = modeName == m,
                            onClick = {
                                modeName = m
                                selRow = -1
                                selCol = -1
                            },
                            label = { Text(m) },
                        )
                    }
                }

                Text("3. Клавіша")
                kb.arr.forEachIndexed { r, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        rowItems.forEachIndexed { c, k ->
                            val m = Modifier.weight(k.widthMultiplier.toFloat())
                            if (r == selRow && c == selCol) {
                                Button(
                                    onClick = {},
                                    modifier = m,
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text(txtOf(k.center), maxLines = 1) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selRow = r
                                        selCol = c
                                    },
                                    modifier = m,
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text(txtOf(k.center), maxLines = 1) }
                            }
                        }
                    }
                }

                if (selRow >= 0) {
                    val item = kb.arr.getOrNull(selRow)?.getOrNull(selCol)
                    Text("4. Напрямок свайпу")
                    DIR_GRID.forEach { rowDirs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowDirs.forEach { d ->
                                val txt = (DIR_ARROWS[d] ?: "") + " " + (item?.let { txtOf(dirKeyC(it, d)) } ?: "–")
                                val m = Modifier.weight(1f)
                                if (d == dir) {
                                    Button(
                                        onClick = { dir = d },
                                        modifier = m,
                                        contentPadding = PaddingValues(0.dp),
                                    ) { Text(txt, maxLines = 1) }
                                } else {
                                    OutlinedButton(
                                        onClick = { dir = d },
                                        modifier = m,
                                        contentPadding = PaddingValues(0.dp),
                                    ) { Text(txt, maxLines = 1) }
                                }
                            }
                        }
                    }

                    Text("5. Що призначити")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ACTION_TYPES.forEach { (id, title) ->
                            FilterChip(
                                selected = actionType == id,
                                onClick = { actionType = id },
                                label = { Text(title) },
                            )
                        }
                    }

                    if (actionType == "text") {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("Текст або символ") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (actionType == "key") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KEY_PRESETS.forEach { (lbl, code) ->
                                FilterChip(
                                    selected = keyCodeValue == code,
                                    onClick = { keyCodeValue = code },
                                    label = { Text(lbl) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = keyCodeValue,
                            onValueChange = { keyCodeValue = it },
                            label = { Text("KeyCode (напр. Z, F5, MOVE_HOME)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = ctrl, onCheckedChange = { ctrl = it })
                            Text("Ctrl")
                            Checkbox(checked = shift, onCheckedChange = { shift = it })
                            Text("Shift")
                            Checkbox(checked = alt, onCheckedChange = { alt = it })
                            Text("Alt")
                            Checkbox(checked = metaKey, onCheckedChange = { metaKey = it })
                            Text("Meta")
                        }
                    }
                    if (actionType == "text" || actionType == "key") {
                        OutlinedTextField(
                            value = labelValue,
                            onValueChange = { labelValue = it },
                            label = { Text("Підпис на клавіші (необов'язково)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (actionType == "action") {
                        Box {
                            OutlinedButton(onClick = { actionMenu = true }) { Text(actionName) }
                            DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                                KeyActionSerializable.entries.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.name) },
                                        onClick = {
                                            actionName = a.name
                                            actionMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            when (actionType) {
                                "text" ->
                                    if (textValue.isEmpty()) {
                                        message = "Впиши текст"
                                    } else {
                                        applyEdit(
                                            KeyCSerializable(text = textValue, displayText = labelValue.ifBlank { null }),
                                            false,
                                        )
                                    }
                                "key" ->
                                    applyEdit(
                                        KeyCSerializable(
                                            keyCode = keyCodeValue.trim(),
                                            ctrl = ctrl,
                                            shift = shift,
                                            alt = alt,
                 


mkdir -p ~/thumb-key/app/src/main/java/com/dessalines/thumbkey/ui/components/settings/keyeditor && cat > ~/thumb-key/app/src/main/java/com/dessalines/thumbkey/ui/components/settings/keyeditor/KeyEditorScreen.kt << 'EOF'
package com.dessalines.thumbkey.ui.components.settings.keyeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.db.AppSettingsViewModel
import com.dessalines.thumbkey.db.KeyModificationsUpdate
import com.dessalines.thumbkey.utils.KeyActionSerializable
import com.dessalines.thumbkey.utils.KeyC
import com.dessalines.thumbkey.utils.KeyCSerializable
import com.dessalines.thumbkey.utils.KeyDisplay
import com.dessalines.thumbkey.utils.KeyItemC
import com.dessalines.thumbkey.utils.KeyItemCSerializable
import com.dessalines.thumbkey.utils.KeyboardCSerializable
import com.dessalines.thumbkey.utils.KeyboardDefinitionModesSerializable
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.SimpleTopAppBar
import com.dessalines.thumbkey.utils.deserializeKeyModifications
import com.dessalines.thumbkey.utils.getYaml
import com.dessalines.thumbkey.utils.keyEventFromSerializable
import com.dessalines.thumbkey.utils.resolveKeyboardDefinition
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val KEY_PRESETS =
    listOf(
        "Home" to "MOVE_HOME",
        "End" to "MOVE_END",
        "PgUp" to "PAGE_UP",
        "PgDn" to "PAGE_DOWN",
        "←" to "DPAD_LEFT",
        "→" to "DPAD_RIGHT",
        "↑" to "DPAD_UP",
        "↓" to "DPAD_DOWN",
        "Del" to "FORWARD_DEL",
        "Bksp" to "DEL",
        "Tab" to "TAB",
        "Esc" to "ESCAPE",
        "Enter" to "ENTER",
        "Ins" to "INSERT",
    )

private val ACTION_TYPES =
    listOf(
        "text" to "Текст",
        "key" to "Клавіша / комбінація",
        "action" to "Готова дія",
        "empty" to "Порожньо",
        "resetDir" to "Скинути напрямок",
        "resetKey" to "Скинути всю клавішу",
    )

private val DIR_GRID =
    listOf(
        listOf("topLeft", "top", "topRight"),
        listOf("left", "center", "right"),
        listOf("bottomLeft", "bottom", "bottomRight"),
    )

private val DIR_ARROWS =
    mapOf(
        "topLeft" to "↖",
        "top" to "↑",
        "topRight" to "↗",
        "left" to "←",
        "center" to "●",
        "right" to "→",
        "bottomLeft" to "↙",
        "bottom" to "↓",
        "bottomRight" to "↘",
    )

private fun txtOf(c: KeyC?): String {
    val d = c?.display
    return when (d) {
        is KeyDisplay.TextDisplay -> d.text
        is KeyDisplay.IconDisplay -> "▣"
        null -> "–"
    }
}

private fun dirKeyC(
    k: KeyItemC,
    dir: String,
): KeyC? =
    when (dir) {
        "topLeft" -> k.topLeft
        "top" -> k.top
        "topRight" -> k.topRight
        "left" -> k.left
        "center" -> k.center
        "right" -> k.right
        "bottomLeft" -> k.bottomLeft
        "bottom" -> k.bottom
        else -> k.bottomRight
    }

private fun getModeSer(
    m: KeyboardDefinitionModesSerializable,
    n: String,
): KeyboardCSerializable? =
    when (n) {
        "shifted" -> m.shifted
        "numeric" -> m.numeric
        else -> m.main
    }

private fun setModeSer(
    m: KeyboardDefinitionModesSerializable,
    n: String,
    v: KeyboardCSerializable?,
): KeyboardDefinitionModesSerializable =
    when (n) {
        "shifted" -> m.copy(shifted = v)
        "numeric" -> m.copy(numeric = v)
        else -> m.copy(main = v)
    }

private fun getKeyItem(
    kb: KeyboardCSerializable,
    name: String,
): KeyItemCSerializable? = KeyboardCSerializable::class.memberProperties.find { it.name == name }?.get(kb) as? KeyItemCSerializable

private fun setKeyItem(
    kb: KeyboardCSerializable,
    name: String,
    item: KeyItemCSerializable?,
): KeyboardCSerializable {
    val ctor = KeyboardCSerializable::class.primaryConstructor!!
    val args =
        ctor.parameters.associateWith { p ->
            if (p.name == name) item else getKeyItem(kb, p.name!!)
        }
    return ctor.callBy(args)
}

private fun setDir(
    i: KeyItemCSerializable,
    dir: String,
    v: KeyCSerializable?,
): KeyItemCSerializable =
    when (dir) {
        "topLeft" -> i.copy(topLeft = v)
        "top" -> i.copy(top = v)
        "topRight" -> i.copy(topRight = v)
        "left" -> i.copy(left = v)
        "center" -> i.copy(center = v)
        "right" -> i.copy(right = v)
        "bottomLeft" -> i.copy(bottomLeft = v)
        "bottom" -> i.copy(bottom = v)
        else -> i.copy(bottomRight = v)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEditorScreen(
    navController: NavController,
    appSettingsViewModel: AppSettingsViewModel,
) {
    val settings by appSettingsViewModel.appSettings.observeAsState()
    val modsText = settings?.keyModifications ?: ""
    val parsed: Map<String, KeyboardDefinitionModesSerializable>? =
        try {
            if (modsText.isBlank()) emptyMap() else deserializeKeyModifications(modsText)
        } catch (e: Exception) {
            null
        }

    var layoutIdx by remember { mutableIntStateOf(0) }
    var layoutMenu by remember { mutableStateOf(false) }
    var modeName by remember { mutableStateOf("main") }
    var selRow by remember { mutableIntStateOf(-1) }
    var selCol by remember { mutableIntStateOf(-1) }
    var dir by remember { mutableStateOf("center") }
    var actionType by remember { mutableStateOf("text") }
    var textValue by remember { mutableStateOf("") }
    var keyCodeValue by remember { mutableStateOf("MOVE_HOME") }
    var labelValue by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var metaKey by remember { mutableStateOf(false) }
    var actionName by remember { mutableStateOf(KeyActionSerializable.Copy.name) }
    var actionMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val layout = KeyboardLayout.entries[layoutIdx]
    val definition = resolveKeyboardDefinition(layout, modsText)
    val kb =
        when (modeName) {
            "shifted" -> definition.modes.shifted
            "numeric" -> definition.modes.numeric
            else -> definition.modes.main
        }

    fun write(map: Map<String, KeyboardDefinitionModesSerializable>) {
        val text =
            if (map.isEmpty()) {
                ""
            } else {
                getYaml().encodeToString(
                    MapSerializer(String.serializer(), KeyboardDefinitionModesSerializable.serializer()),
                    map,
                )
            }
        appSettingsViewModel.updateKeyModifications(
            KeyModificationsUpdate(id = 1, keyModifications = text),
        )
    }

    fun applyEdit(
        entry: KeyCSerializable?,
        resetWholeKey: Boolean,
    ) {
        val base = parsed
        if (base == null) {
            message = "У Modify keys є помилка, спершу виправ її"
            return
        }
        if (selRow < 0) {
            message = "Спочатку обери клавішу"
            return
        }
        if (entry != null && !entry.remove) {
            try {
                keyEventFromSerializable(entry)
            } catch (e: Exception) {
                message = e.message ?: "Невірна клавіша"
                return
            }
        }
        val keyName = "key${selRow}_$selCol"
        val modes = base[layout.name] ?: KeyboardDefinitionModesSerializable()
        val kbS = getModeSer(modes, modeName) ?: KeyboardCSerializable()
        val item = getKeyItem(kbS, keyName) ?: KeyItemCSerializable()
        val newItem: KeyItemCSerializable? =
            if (resetWholeKey) {
                null
            } else {
                setDir(item, dir, entry).takeIf { it != KeyItemCSerializable() }
            }
        val newKb = setKeyItem(kbS, keyName, newItem).takeIf { it != KeyboardCSerializable() }
        val newModes = setModeSer(modes, modeName, newKb).takeIf { it != KeyboardDefinitionModesSerializable() }
        val map = base.toMutableMap()
        if (newModes == null) map.remove(layout.name) else map[layout.name] = newModes
        write(map)
        message = "Збережено"
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(
                text = stringResource(R.string.key_editor),
                navController = navController,
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .background(color = MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("1. Розкладка")
                Box {
                    OutlinedButton(onClick = { layoutMenu = true }) {
                        Text(layout.keyboardDefinition.title)
                    }
                    DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                        KeyboardLayout.entries.forEachIndexed { i, l ->
                            DropdownMenuItem(
                                text = { Text(l.keyboardDefinition.title) },
                                onClick = {
                                    layoutIdx = i
                                    selRow = -1
                                    selCol = -1
                                    layoutMenu = false
                                },
                            )
                        }
                    }
                }

                Text("2. Режим")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("main", "shifted", "numeric").forEach { m ->
                        FilterChip(
                            selected = modeName == m,
                            onClick = {
                                modeName = m
                                selRow = -1
                                selCol = -1
                            },
                            label = { Text(m) },
                        )
                    }
                }

                Text("3. Клавіша")
                kb.arr.forEachIndexed { r, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        rowItems.forEachIndexed { c, k ->
                            val m = Modifier.weight(k.widthMultiplier.toFloat())
                            if (r == selRow && c == selCol) {
                                Button(
                                    onClick = {},
                                    modifier = m,
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text(txtOf(k.center), maxLines = 1) }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selRow = r
                                        selCol = c
                                    },
                                    modifier = m,
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text(txtOf(k.center), maxLines = 1) }
                            }
                        }
                    }
                }

                if (selRow >= 0) {
                    val item = kb.arr.getOrNull(selRow)?.getOrNull(selCol)
                    Text("4. Напрямок свайпу")
                    DIR_GRID.forEach { rowDirs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowDirs.forEach { d ->
                                val txt = (DIR_ARROWS[d] ?: "") + " " + (item?.let { txtOf(dirKeyC(it, d)) } ?: "–")
                                val m = Modifier.weight(1f)
                                if (d == dir) {
                                    Button(
                                        onClick = { dir = d },
                                        modifier = m,
                                        contentPadding = PaddingValues(0.dp),
                                    ) { Text(txt, maxLines = 1) }
                                } else {
                                    OutlinedButton(
                                        onClick = { dir = d },
                                        modifier = m,
                                        contentPadding = PaddingValues(0.dp),
                                    ) { Text(txt, maxLines = 1) }
                                }
                            }
                        }
                    }

                    Text("5. Що призначити")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ACTION_TYPES.forEach { (id, title) ->
                            FilterChip(
                                selected = actionType == id,
                                onClick = { actionType = id },
                                label = { Text(title) },
                            )
                        }
                    }

                    if (actionType == "text") {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = { Text("Текст або символ") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (actionType == "key") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KEY_PRESETS.forEach { (lbl, code) ->
                                FilterChip(
                                    selected = keyCodeValue == code,
                                    onClick = { keyCodeValue = code },
                                    label = { Text(lbl) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = keyCodeValue,
                            onValueChange = { keyCodeValue = it },
                            label = { Text("KeyCode (напр. Z, F5, MOVE_HOME)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = ctrl, onCheckedChange = { ctrl = it })
                            Text("Ctrl")
                            Checkbox(checked = shift, onCheckedChange = { shift = it })
                            Text("Shift")
                            Checkbox(checked = alt, onCheckedChange = { alt = it })
                            Text("Alt")
                            Checkbox(checked = metaKey, onCheckedChange = { metaKey = it })
                            Text("Meta")
                        }
                    }
                    if (actionType == "text" || actionType == "key") {
                        OutlinedTextField(
                            value = labelValue,
                            onValueChange = { labelValue = it },
                            label = { Text("Підпис на клавіші (необов'язково)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (actionType == "action") {
                        Box {
                            OutlinedButton(onClick = { actionMenu = true }) { Text(actionName) }
                            DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
                                KeyActionSerializable.entries.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.name) },
                                        onClick = {
                                            actionName = a.name
                                            actionMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            when (actionType) {
                                "text" ->
                                    if (textValue.isEmpty()) {
                                        message = "Впиши текст"
                                    } else {
                                        applyEdit(
                                            KeyCSerializable(text = textValue, displayText = labelValue.ifBlank { null }),
                                            false,
                                        )
                                    }
                                "key" ->
                                    applyEdit(
                                        KeyCSerializable(
                                            keyCode = keyCodeValue.trim(),
                                            ctrl = ctrl,
                                            shift = shift,
                                            alt = alt,
                                            meta = metaKey,
                                            displayText = labelValue.ifBlank { null },
                                        ),
                                        false,
                                    )
                                "action" ->
                                    applyEdit(
                                        KeyCSerializable(keyAction = KeyActionSerializable.valueOf(actionName)),
                                        false,
                                    )
                                "empty" -> applyEdit(KeyCSerializable(remove = true), false)
                                "resetDir" -> applyEdit(null, false)
                                "resetKey" -> applyEdit(null, true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Застосувати") }
                }

                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
    )
}
