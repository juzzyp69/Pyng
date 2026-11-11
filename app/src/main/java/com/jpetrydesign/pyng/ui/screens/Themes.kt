package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Hsl
import com.jpetrydesign.pyng.data.SavedTheme
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.LocalTok
import com.jpetrydesign.pyng.ui.theme.color

/** Which sheet the theme library has open: naming a new theme, or managing one you saved. */
@Stable
class ThemeLibrary {
    var saving by mutableStateOf<SavedTheme?>(null)
    var managing by mutableStateOf<SavedTheme?>(null)
}

fun SavedTheme.brush(): Brush = when (mode) {
    "Mono" -> Brush.linearGradient(listOf(c1.color(), c1.color()))
    "Gradient" -> Brush.linearGradient(listOf(c1.color(), c2.color()))
    else -> Brush.linearGradient(0f to c1.color(), 0.5f to c1.color(), 0.5f to c2.color(), 1f to c2.color())
}

/** The color family a theme belongs to, from its main color. */
fun family(h: Hsl): String = when {
    h.s < 0.14f || h.l < 0.13f || h.l > 0.93f -> "Neutrals"
    h.h < 15f || h.h >= 340f -> "Reds"
    h.h < 42f -> "Oranges"
    h.h < 68f -> "Yellows"
    h.h < 160f -> "Greens"
    h.h < 195f -> "Teals"
    h.h < 255f -> "Blues"
    h.h < 292f -> "Purples"
    else -> "Pinks"
}

private val FAMILY_ORDER = listOf("Reds", "Oranges", "Yellows", "Greens", "Teals", "Blues", "Purples", "Pinks", "Neutrals")

/** A name to start from: the color family and the kind, kept unique. */
fun suggestName(mode: String, c1: Hsl, taken: List<String>): String {
    val base = family(c1).removeSuffix("s").let { if (it == "Neutral") "Soft neutral" else it } + when (mode) { "Duotone" -> " duotone"; "Gradient" -> " gradient"; else -> "" }
    if (base !in taken) return base
    var n = 2
    while ("$base $n" in taken) n++
    return "$base $n"
}

/**
 * Your saved themes as a row of swatches, sorted by most recent, by name, or grouped by color.
 * Tap one to use it; hold it to rename, duplicate or delete. [onSave] saves the look you're on now.
 */
@Composable
fun MyThemes(lib: ThemeLibrary, isOn: (SavedTheme) -> Boolean, onPick: (SavedTheme) -> Unit, onSave: (() -> Unit)?, modifier: Modifier = Modifier, side: Dp = 16.dp) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val all = p.savedThemes
    if (all.isEmpty() && onSave == null) return
    val sort = p.opt("themeSort").ifEmpty { "Recent" }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = side, end = (side - 6.dp).coerceAtLeast(0.dp)), verticalAlignment = Alignment.CenterVertically) {
            T("My themes", size = 13.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.weight(1f))
            if (all.size > 1) {
                // Tap to cycle how they're ordered.
                Row(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(t.tone)
                        .clickable { repo.prefs.setOpt("themeSort", when (sort) { "Recent" -> "Name"; "Name" -> "Color"; else -> "Recent" }) }
                        .padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.sort, t.p, 13.dp, 2.2f); Spacer(Modifier.width(5.dp))
                    T(when (sort) { "Name" -> "A to Z"; "Color" -> "By color"; else -> "Recent" }, size = 12.5.sp, weight = FontWeight.SemiBold, color = t.p)
                }
            }
            if (onSave != null) {
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(t.tone).clickable(onClick = onSave).padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.plus, t.p, 13.dp, 2.4f); Spacer(Modifier.width(5.dp))
                    T("Save this look", size = 12.5.sp, weight = FontWeight.SemiBold, color = t.p)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (all.isEmpty()) {
            T("Make a look you love, then save it here to use again in any chat or for the whole app.", size = 12.5.sp, color = t.soft, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = side))
            return@Column
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (sort) {
                "Color" -> all.groupBy { family(it.c1) }.toSortedMap(compareBy { FAMILY_ORDER.indexOf(it) }).forEach { (fam, list) ->
                    Column {
                        T(fam, size = 10.5.sp, weight = FontWeight.SemiBold, color = t.faint, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.sortedBy { it.c1.h }.forEach { st -> ThemeSwatch(st, isOn(st), { onPick(st) }, { lib.managing = st }) }
                        }
                    }
                    Box(Modifier.padding(top = 22.dp).width(1.dp).height(40.dp).background(t.hair))
                }
                "Name" -> all.sortedBy { it.name.lowercase() }.forEach { st -> ThemeSwatch(st, isOn(st), { onPick(st) }, { lib.managing = st }) }
                else -> all.sortedByDescending { it.at }.forEach { st -> ThemeSwatch(st, isOn(st), { onPick(st) }, { lib.managing = st }) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeSwatch(st: SavedTheme, on: Boolean, onClick: () -> Unit, onHold: () -> Unit) {
    val t = LocalTok.current
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onHold),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp).then(if (on) Modifier.border(2.dp, t.ink, CircleShape) else Modifier).padding(4.dp).clip(CircleShape).background(st.brush()).border(1.dp, t.hair, CircleShape)) {
            // Themes with their own part-by-part colors show a small dot of the received bubble color.
            st.custom["inBg"]?.let { c -> Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(12.dp).clip(CircleShape).background(c.color()).border(1.5.dp, androidx.compose.ui.graphics.Color.White, CircleShape)) }
        }
        Spacer(Modifier.height(4.dp))
        T(st.name, size = 11.sp, weight = FontWeight.Medium, color = if (on) t.ink else t.soft, maxLines = 1, align = TextAlign.Center)
    }
}

/** The naming sheet for a new theme and the hold menu for a saved one. Put this at the screen's root. */
@Composable
fun ThemeLibrarySheets(lib: ThemeLibrary) {
    val repo = LocalRepo.current
    val t = LocalTok.current
    val p = repo.prefs.state
    val saving = lib.saving
    NameSheet(
        visible = saving != null, title = "Save theme", note = "It shows up under My themes in every chat's theme and in App color.",
        initial = saving?.name.orEmpty(), action = "Save",
        onDismiss = { lib.saving = null },
    ) { name ->
        val st = lib.saving ?: return@NameSheet
        repo.prefs.edit { it.copy(savedThemes = it.savedThemes + st.copy(name = name.ifBlank { st.name }, at = System.currentTimeMillis())) }
        repo.toast("Saved to My themes")
        lib.saving = null
    }
    val m = lib.managing
    var renaming by remember { mutableStateOf(false) }
    com.jpetrydesign.pyng.ui.kit.Sheet(m != null && !renaming, { lib.managing = null }) {
        if (m != null) Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(m.brush()))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T(m.name, size = 18.sp, weight = FontWeight.Bold, maxLines = 1)
                    T(m.mode + " · " + family(m.c1).lowercase().replaceFirstChar { it.uppercase() }, size = 13.sp, color = t.soft)
                }
            }
            Spacer(Modifier.height(16.dp))
            com.jpetrydesign.pyng.ui.kit.ListRow("Rename", icon = Icons.pencil, onClick = { renaming = true }, trailing = null)
            com.jpetrydesign.pyng.ui.kit.ListRow("Duplicate", icon = Icons.copy, onClick = {
                val copy = m.copy(id = java.util.UUID.randomUUID().toString(), name = "${m.name} copy", at = System.currentTimeMillis())
                repo.prefs.edit { it.copy(savedThemes = it.savedThemes + copy) }
                lib.managing = null; repo.toast("Duplicated")
            }, trailing = null)
            com.jpetrydesign.pyng.ui.kit.ListRow("Delete", icon = Icons.trash, danger = true, onClick = {
                repo.prefs.edit { it.copy(savedThemes = it.savedThemes.filter { x -> x.id != m.id }) }
                lib.managing = null; repo.toast("Theme deleted")
            }, trailing = null)
            Spacer(Modifier.height(8.dp))
        }
    }
    NameSheet(
        visible = m != null && renaming, title = "Rename theme", note = null, initial = m?.name.orEmpty(), action = "Save",
        onDismiss = { renaming = false; lib.managing = null },
    ) { name ->
        val id = m?.id
        if (id != null && name.isNotBlank()) repo.prefs.edit { it.copy(savedThemes = it.savedThemes.map { x -> if (x.id == id) x.copy(name = name.trim()) else x }) }
        renaming = false; lib.managing = null
    }
}

@Composable
private fun NameSheet(visible: Boolean, title: String, note: String?, initial: String, action: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val t = LocalTok.current
    var v by remember(visible, initial) { mutableStateOf(initial) }
    com.jpetrydesign.pyng.ui.kit.Sheet(visible, onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            T(title, size = 18.sp, weight = FontWeight.Bold)
            if (note != null) T(note, size = 13.sp, color = t.soft, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(t.field).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                if (v.isEmpty()) T("Name this theme", size = 15.sp, color = t.soft)
                androidx.compose.foundation.text.BasicTextField(v, { v = it.take(32) }, singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(t.p),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = 15.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f), primary = false)
                PillButton(action, onClick = { onDone(v.trim()) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
