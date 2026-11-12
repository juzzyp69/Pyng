package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Repo
import com.jpetrydesign.pyng.ui.kit.EmojiData
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.Geist
import com.jpetrydesign.pyng.ui.theme.Tok
import kotlinx.coroutines.launch

/** Emoji you've reacted with lately, newest first. */
fun reactRecents(repo: Repo): List<String> = repo.prefs.state.opt("reactRecents").split(' ').filter { it.isNotBlank() }

/** Emoji you held to keep at the top of the reaction picker. */
fun reactFavorites(repo: Repo): List<String> = repo.prefs.state.opt("reactFavs").split(' ').filter { it.isNotBlank() }

/** Remembers a reaction for the Recent row. */
fun noteReaction(repo: Repo, e: String) {
    repo.prefs.setOpt("reactRecents", (listOf(e) + reactRecents(repo).filter { it != e }).take(16).joinToString(" "))
}

private fun toggleFavorite(repo: Repo, e: String) {
    val favs = reactFavorites(repo)
    val on = e in favs
    repo.prefs.setOpt("reactFavs", (if (on) favs - e else (favs + e).takeLast(24)).joinToString(" "))
    repo.toast(if (on) "Removed from favorites" else "Added to favorites")
}

/**
 * The full reaction picker, opened from the + at the end of the quick reactions: search, your
 * favorites (hold any emoji to add or remove it), what you've used lately, then everything else.
 */
@Composable
fun ReactionPicker(t: Tok, mine: String?, onPick: (String) -> Unit) {
    val repo = LocalRepo.current
    var q by remember { mutableStateOf("") }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val favs = reactFavorites(repo)
    val recent = reactRecents(repo)
    fun pick(e: String) { noteReaction(repo, e); onPick(e) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("React", size = 17.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            T("Hold an emoji to favorite it", size = 12.sp, color = t.soft)
        }
        Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(t.field).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.search, t.soft, 17.dp, 2.2f); Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) T("Search emoji", size = 14.5.sp, color = t.soft)
                BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(t.p), textStyle = TextStyle(fontFamily = Geist, fontSize = 14.5.sp, color = t.ink), modifier = Modifier.fillMaxWidth())
            }
            if (q.isNotEmpty()) Box(Modifier.size(26.dp).clip(CircleShape).clickable { q = "" }, contentAlignment = Alignment.Center) { Ico(Icons.close, t.soft, 12.dp, 2.4f) }
        }
        Spacer(Modifier.height(8.dp))
        val term = q.trim().lowercase()
        // Each section: a title, a tab icon, and its emoji. Favorites always shows, so people find out about holding.
        val sections: List<Triple<String, String, List<String>>> = if (term.isNotEmpty()) {
            listOf(Triple("Results", Icons.search, EmojiData.cats.flatMap { it.items }.filter { it.second.contains(term) }.map { it.first }.distinct()))
        } else {
            listOf(Triple("Favorites", Icons.star, favs.distinct())) +
                (if (recent.isNotEmpty()) listOf(Triple("Recent", Icons.clock, recent.distinct())) else emptyList()) +
                EmojiData.cats.map { Triple(it.label, it.icon, it.items.map { e -> e.first }.distinct()) }
        }
        // Where each section starts in the grid, counting its title row (and the hint row when Favorites is empty).
        val starts = remember(sections) {
            var n = 0
            sections.map { s -> n.also { n += 1 + (if (s.third.isEmpty() && s.first == "Favorites") 1 else s.third.size) } }
        }
        if (term.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                sections.forEachIndexed { i, s ->
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { scope.launch { grid.animateScrollToItem(starts[i]) } }, contentAlignment = Alignment.Center) {
                        Ico(s.second, t.soft, 18.dp, 1.9f)
                    }
                }
            }
        }
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize(), state = grid) {
            sections.forEach { (label, _, list) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    T(label, size = 12.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp))
                }
                if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    T(
                        if (label == "Favorites") "Hold any emoji below to keep it here." else "Nothing matches that.",
                        size = 13.sp, color = t.faint, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                }
                items(list, key = { "$label:$it" }) { e ->
                    Box(
                        Modifier.aspectRatio(1f).clip(CircleShape).background(if (e == mine) t.tone else Color.Transparent)
                            .holdable(onLong = { toggleFavorite(repo, e) }, onClick = { pick(e) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(e, size = 26.sp)
                        if (label != "Favorites" && e in favs) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(6.dp).clip(CircleShape).background(t.p))
                    }
                }
            }
        }
    }
}
