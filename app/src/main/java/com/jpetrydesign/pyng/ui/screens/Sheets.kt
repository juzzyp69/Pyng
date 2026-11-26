package com.jpetrydesign.pyng.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.data.Conv
import com.jpetrydesign.pyng.data.Msg
import com.jpetrydesign.pyng.ui.kit.Avatar
import com.jpetrydesign.pyng.ui.kit.EmojiData
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.SheetAction
import com.jpetrydesign.pyng.ui.kit.SheetGroup
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.kit.press
import com.jpetrydesign.pyng.ui.theme.Tok
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Long-press on a message, like Google Messages: the chat blurs, the message stays where it was,
 * reactions sit above it (with an emoji button for the full set) and actions sit below.
 */
@Composable
fun MessageMenu(
    m: Msg?,
    t: Tok,
    conv: Conv?,
    anchor: androidx.compose.ui.geometry.Rect?,
    rowCtx: RowCtx?,
    onDismiss: () -> Unit,
    onReact: (Msg, String) -> Unit,
    onReply: (Msg) -> Unit,
    onCopy: (Msg) -> Unit,
    onForward: (Msg) -> Unit,
    onStar: (Msg) -> Unit,
    onSelect: (Msg) -> Unit,
    onDetails: (Msg) -> Unit,
    onDelete: (Msg) -> Unit,
    onSelectText: (Msg) -> Unit = {},
    /** The photos sent together with this one, if it's part of a group. */
    group: List<Pair<Msg, com.jpetrydesign.pyng.data.MediaRef>>? = null,
    onSave: (List<com.jpetrydesign.pyng.data.MediaRef>) -> Unit = {},
    onShare: (List<com.jpetrydesign.pyng.data.MediaRef>) -> Unit = {},
    onForwardAll: (List<Msg>) -> Unit = {},
    /** Opened from the reactions under a message: show who reacted above the bar. */
    showWho: Boolean = false,
) {
    val repo = LocalRepo.current
    val ctx = LocalContext.current
    val motion = com.jpetrydesign.pyng.ui.kit.LocalMotion.current
    var last by remember { mutableStateOf(m) }
    var lastAnchor by remember { mutableStateOf(anchor) }
    var lastRow by remember { mutableStateOf(rowCtx) }
    var lastGroup by remember { mutableStateOf(group) }
    if (m != null) { last = m; lastAnchor = anchor; lastRow = rowCtx; lastGroup = group?.takeIf { it.size > 1 } }
    var more by remember(m) { mutableStateOf(false) }
    var reminding by remember(m) { mutableStateOf(false) }
    if (m != null) androidx.activity.compose.BackHandler { if (more) more = false else onDismiss() }
    val k = remember { androidx.compose.animation.core.Animatable(0f) }
    // Liquid motion: the reaction bar rises out of the message on a neck and the menu drips out below,
    // then both are pulled back into the message when you let go.
    val mo = com.jpetrydesign.pyng.ui.kit.LocalMo.current
    val liquid = mo.liquid
    val stage = remember { com.jpetrydesign.pyng.ui.kit.LiquidStage() }
    val barA = remember { androidx.compose.animation.core.Animatable(if (liquid) 0f else 1f) }
    val actA = remember { androidx.compose.animation.core.Animatable(if (liquid) 0f else 1f) }
    var geo by remember { mutableStateOf(MenuGeo()) }
    var budded by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density0 = androidx.compose.ui.platform.LocalDensity.current
    val glass0 = popGlass(t)
    val settle = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    androidx.compose.runtime.LaunchedEffect(m != null) {
        if (m != null) {
            if (liquid) {
                barA.snapTo(0f); actA.snapTo(0f); budded = false
                scope.launch {
                    val g = androidx.compose.runtime.snapshotFlow { geo }.first { it.bub != null && (it.bar != null || it.act != null) }
                    val d = density0.density
                    val src = com.jpetrydesign.pyng.ui.kit.LRect.of(g.bub!!, with(density0) { 20.dp.toPx() }, d)
                    budded = true
                    kotlinx.coroutines.coroutineScope {
                        g.bar?.let { r ->
                            launch {
                                val sim = com.jpetrydesign.pyng.ui.kit.budSim(src, com.jpetrydesign.pyng.ui.kit.LRect.of(r, r.height / 2, d), false, mo)
                                sim.onReveal = { scope.launch { barA.animateTo(1f, androidx.compose.animation.core.tween(if (mo == com.jpetrydesign.pyng.ui.kit.Mo.Drop) 360 else 300, easing = settle)) } }
                                stage.play(sim, glass0, t.edgeLine); if (barA.value < 1f) barA.animateTo(1f, androidx.compose.animation.core.tween(140))
                            }
                        }
                        g.act?.let { r ->
                            launch {
                                kotlinx.coroutines.delay(60)
                                val sim = com.jpetrydesign.pyng.ui.kit.budSim(src, com.jpetrydesign.pyng.ui.kit.LRect.of(r, with(density0) { 22.dp.toPx() }, d), false, mo)
                                sim.onReveal = { scope.launch { actA.animateTo(1f, androidx.compose.animation.core.tween(if (mo == com.jpetrydesign.pyng.ui.kit.Mo.Drop) 360 else 300, easing = settle)) } }
                                stage.play(sim, glass0, t.edgeLine); if (actA.value < 1f) actA.animateTo(1f, androidx.compose.animation.core.tween(140))
                            }
                        }
                    }
                }
            }
            if (motion) k.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) else k.snapTo(1f)
        } else if (liquid && budded) {
            // The bar and menu fade, then drain back into the message while the dim lifts.
            val g = geo
            val d = density0.density
            launch { k.animateTo(0f, androidx.compose.animation.core.tween(380)) }
            kotlinx.coroutines.coroutineScope { launch { barA.animateTo(0f, androidx.compose.animation.core.tween(110)) }; actA.animateTo(0f, androidx.compose.animation.core.tween(110)) }
            val bub = g.bub
            if (bub != null) kotlinx.coroutines.coroutineScope {
                val src = com.jpetrydesign.pyng.ui.kit.LRect.of(bub, with(density0) { 20.dp.toPx() }, d)
                g.bar?.let { r -> launch { stage.play(com.jpetrydesign.pyng.ui.kit.budSim(src, com.jpetrydesign.pyng.ui.kit.LRect.of(r, r.height / 2, d), true, mo), glass0, t.edgeLine) } }
                g.act?.let { r -> launch { stage.play(com.jpetrydesign.pyng.ui.kit.budSim(src, com.jpetrydesign.pyng.ui.kit.LRect.of(r, with(density0) { 22.dp.toPx() }, d), true, mo), glass0, t.edgeLine) } }
            }
            budded = false
            if (k.value > 0f) k.animateTo(0f, androidx.compose.animation.core.tween(120))
        }
        else if (motion) k.animateTo(0f, androidx.compose.animation.core.tween(160)) else k.snapTo(0f)
    }
    val closed by remember { androidx.compose.runtime.derivedStateOf { k.value <= 0.01f } }
    if (m == null && closed && !stage.busy && !budded) return
    val cur = last ?: return
    val p = repo.prefs.state
    val myId = conv?.me?.id.orEmpty()
    val mine = cur.reactions.firstOrNull { myId.isNotEmpty() && myId in it.by }?.emoji ?: repo.myReaction(cur)
    val canReact = cur.id.isNotEmpty() && conv?.rcs == true
    val me = cur.fromMe
    val density = androidx.compose.ui.platform.LocalDensity.current
    val close = { more = false; onDismiss() }
    val style = p.opt("reactStyle").ifBlank { "Faces" }
    val fits = remember(style) { menuFits(style) }
    val reactors = reactorsOf(cur, conv, repo, t)
    val who = showWho && reactors.isNotEmpty()
    val glass = popGlass(t)
    val swallow = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}

    Box(
        Modifier.fillMaxSize().zIndex(25f)
            .graphicsLayer { if (!liquid) alpha = k.value.coerceIn(0f, 1f) }
            .drawBehind { drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = (if (t.dark) 0.4f else 0.18f) * (if (liquid) k.value.coerceIn(0f, 1f) else 1f))) }
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { close() },
    ) {
        if (liquid) com.jpetrydesign.pyng.ui.kit.LiquidCanvas(stage, Modifier.fillMaxSize())
        val topSafe = WindowInsets.statusBars.getTop(density) + with(density) { 12.dp.roundToPx() }
        val bottomSafe = WindowInsets.navigationBars.getBottom(density) + with(density) { 12.dp.roundToPx() }
        val whoPanel: @Composable (MenuFit) -> Unit = { f ->
            WhoReacted(reactors, style, f, t) { repo.react(cur, mine ?: return@WhoReacted); repo.toast("Reaction removed"); close() }
        }
        val bar: @Composable (MenuFit) -> Unit = { f ->
            if (canReact) {
                Row(
                    Modifier.clip(RoundedCornerShape(32.dp)).background(glass)
                        .border(1.dp, t.edgeLine, RoundedCornerShape(32.dp)).then(swallow).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    p.quickReacts.forEachIndexed { i, e ->
                        val on = mine == e
                        Box(
                            Modifier.enter(i, pop = true).size(f.cell).clip(CircleShape)
                                .background(if (on) t.p.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if (on) Modifier.border(1.5.dp, t.p, CircleShape) else Modifier)
                                .press(0.8f) {
                                    if (on) repo.toast("Reaction removed") else noteReaction(repo, e)
                                    onReact(cur, e); close()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            T(e, size = f.barEm)
                            // Yours gets a small dot under it; tap it again to take it off.
                            if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(4.dp).clip(CircleShape).background(t.p))
                        }
                    }
                    Box(Modifier.size(f.cell).clip(CircleShape).clickable { more = true }, contentAlignment = Alignment.Center) { Ico(Icons.emojiAdd, t.soft, 22.dp, 1.8f) }
                }
            }
        }
        // Only the end of a very long message shows, fading out, when space is short.
        val capH = remember { intArrayOf(Int.MAX_VALUE) }
        val bubble: @Composable () -> Unit = {
            val rc = lastRow
            Box(
                Modifier.graphicsLayer { val sc = 1f + 0.035f * k.value; scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (me) 1f else 0f, 0.5f); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (size.height >= capH[0] - 1) drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Black, (1f - 28.dp.toPx() / size.height).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    }
                    .clip(RoundedCornerShape(20.dp)).then(swallow),
                contentAlignment = if (me) Alignment.TopEnd else Alignment.TopStart,
            ) {
                val g = lastGroup
                if (g != null) GatherStack(g, cur.key, t)
                else if (rc != null) MenuBubble(cur, rc)
                else T(msgPreview(cur), size = 15.sp, color = if (me) t.outInk else t.inInk, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(t.inBg).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        // Everything the menu offers, shown as a list, or as one slim row when the screen is short.
        data class Act(val label: String, val icon: String, val danger: Boolean = false, val go: () -> Unit)
        val acts = buildList {
            val g = lastGroup
            val files = cur.media.filter { !it.isContact }
            val links = linksIn(cur.text)
            if (cur.id.isNotEmpty()) add(Act("Reply", Icons.reply) { onReply(cur) })
            if (g != null) {
                val kind = if (g.all { it.second.isVideo }) "videos" else if (g.any { it.second.isVideo }) "items" else "photos"
                add(Act("Save all ${g.size} $kind", Icons.download) { onSave(g.map { it.second }) })
                add(Act("Share all", Icons.share) { onShare(g.map { it.second }) })
                add(Act("Forward all", Icons.forward) { onForwardAll(g.map { it.first }.distinctBy { it.key }) })
            } else {
                add(Act("Forward", Icons.forward) { onForward(cur) })
                if (files.isNotEmpty()) {
                    add(Act("Save", Icons.download) { onSave(files) })
                    add(Act("Share", Icons.share) { onShare(files) })
                }
            }
            if (links.isNotEmpty()) {
                add(Act(if (links.size > 1) "Copy first link" else "Copy link", Icons.link) { copy(ctx, links.first()); repo.toast("Link copied") })
                add(Act("Share link", Icons.share) {
                    runCatching { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, links.first()), "Share link")) }
                })
            }
            if (cur.text.isNotEmpty()) add(Act(if (links.isNotEmpty() && cur.text.trim() != links.first()) "Copy text" else if (links.isNotEmpty()) "Copy all" else "Copy", Icons.copy) { onCopy(cur) })
            if (cur.text.length > 1) add(Act("Select text", Icons.select) { onSelectText(cur) })
            add(Act(if (p.isStarred(cur.key)) "Unstar" else "Star", Icons.star) { onStar(cur) })
            if (cur.id.isNotEmpty()) {
                val pinnedHere = p.pinnedMsgs.any { it.msgId == cur.id }
                add(Act(if (pinnedHere) "Unpin" else "Pin", Icons.pinned) {
                    repo.prefs.edit { pr ->
                        val others = pr.pinnedMsgs.filter { it.convId != cur.convId }
                        pr.copy(pinnedMsgs = if (pinnedHere) others else others + com.jpetrydesign.pyng.data.Starred(cur.id, cur.convId, cur.summary.ifEmpty { cur.text }.ifEmpty { "Attachment" }, cur.ts))
                    }
                    repo.toast(if (pinnedHere) "Unpinned" else "Pinned to the top of this chat")
                })
            }
            if (p.on("readAloud") && cur.text.isNotEmpty()) add(Act("Read aloud", Icons.volume) { Speaker.say(ctx, cur.text) })
            add(Act("Delete", Icons.trash, danger = true) { onDelete(cur) })
            add(Act("Select more", Icons.select) { onSelect(cur) })
            add(Act("Info", Icons.details) { onDetails(cur) })
        }
        val canRemind = conv != null && cur.id.isNotEmpty()
        val remindChoices: @Composable (Dp) -> Unit = { start ->
            if (reminding && conv != null) com.jpetrydesign.pyng.data.ReminderWorker.choices().forEach { (label, at) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val whoName = if (cur.fromMe) "You" else conv.people.firstOrNull { it.id == cur.participantId }?.short ?: conv.shortTitle
                        com.jpetrydesign.pyng.data.ReminderWorker.plan(ctx, conv.id, cur.id, conv.title, (if (conv.group || cur.fromMe) "$whoName: " else "") + cur.text.ifBlank { cur.summary }, at)
                        repo.toast("Reminder set for " + (if (label == "In 1 hour") clock(at) else label.substringAfter(" at ").let { l -> if (label.startsWith("Tomorrow")) "tomorrow at $l" else "tonight at $l" }))
                        close()
                    }.padding(start = start, end = 18.dp, top = 10.dp, bottom = 10.dp),
                ) { T(label, size = 14.5.sp, color = t.p, weight = FontWeight.Medium) }
            }
        }
        val actions: @Composable (MenuFit) -> Unit = { f ->
            val shape = RoundedCornerShape(if (f.strip) 22.dp else 22.dp)
            Column(
                (if (f.strip) Modifier.fillMaxWidth() else Modifier.width(220.dp)).clip(shape).background(glass)
                    .border(1.dp, t.edgeLine, shape).then(swallow),
            ) {
                if (f.strip) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        val all = acts.take(4) + (if (canRemind) listOf(Act("Remind me", Icons.bell) {}) else emptyList()) + acts.drop(4)
                        all.forEachIndexed { i, a ->
                            Column(
                                Modifier.enter(i).width(66.dp).clip(RoundedCornerShape(16.dp))
                                    .clickable { if (a.label == "Remind me") reminding = !reminding else { a.go(); close() } }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Ico(a.icon, if (a.danger) t.danger else t.ink, 19.dp)
                                Spacer(Modifier.height(4.dp))
                                T(a.label, size = 11.sp, weight = FontWeight.Medium, color = if (a.danger) t.danger else t.ink, maxLines = 1)
                            }
                        }
                    }
                    remindChoices(18.dp)
                } else {
                    Column(Modifier.heightIn(max = 430.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 4.dp)) {
                        var i = 0
                        @Composable fun row(a: Act, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
                            Row(
                                Modifier.enter(i++).fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = f.rowPad),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Ico(a.icon, if (a.danger) t.danger else t.ink, 19.dp)
                                Spacer(Modifier.width(14.dp))
                                T(a.label, size = 15.sp, weight = FontWeight.Medium, color = if (a.danger) t.danger else t.ink, modifier = Modifier.weight(1f))
                                trailing?.invoke()
                            }
                        }
                        // Remind me sits after Star and opens up into three times right here.
                        val starAt = acts.indexOfFirst { it.icon == Icons.star }
                        acts.forEachIndexed { j, a ->
                            row(a) { a.go(); close() }
                            if (j == starAt && canRemind) {
                                row(Act("Remind me", Icons.bell) {}, { Ico(if (reminding) "M6 15l6-6 6 6" else "M6 9l6 6 6-6", t.soft, 16.dp, 2.2f) }) { reminding = !reminding }
                                remindChoices(51.dp)
                            }
                        }
                    }
                }
            }
        }
        val gap = with(density) { 8.dp.roundToPx() }
        val side = with(density) { 12.dp.roundToPx() }
        androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxSize()) { cons ->
            val loose = cons.copy(minWidth = 0, minHeight = 0)
            val H = cons.maxHeight; val W = cons.maxWidth
            val avail = H - topSafe - bottomSafe
            val inner = loose.copy(maxWidth = maxOf(0, W - 2 * side))
            // Same width limit as bubbles in the chat, so the text wraps the same way. A very long
            // message never takes more than about a third of the screen.
            val maxBub = minOf((W * 0.74f).toInt(), 420.dp.roundToPx())
            val cap = maxOf((avail * 0.34f).toInt(), minOf(300.dp.roundToPx(), (avail * 0.45f).toInt()))
            capH[0] = cap
            var u = subcompose("bubble", bubble).first().measure(loose.copy(maxWidth = maxBub, maxHeight = cap))
            // Try each size in turn and keep the first that fits on screen.
            var pick = fits.last()
            var wP: androidx.compose.ui.layout.Placeable? = null
            var bP: androidx.compose.ui.layout.Placeable? = null
            var aP: androidx.compose.ui.layout.Placeable? = null
            fun total() = listOfNotNull(wP, bP, u, aP).let { l -> l.sumOf { it.height } + gap * (l.size - 1) }
            for ((i, f) in fits.withIndex()) {
                wP = if (who) subcompose("who$i") { whoPanel(f) }.firstOrNull()?.measure(inner) else null
                bP = subcompose("bar$i") { bar(f) }.firstOrNull()?.measure(loose)
                aP = subcompose("act$i") { actions(f) }.firstOrNull()?.measure(inner)
                pick = f
                if (total() <= avail) break
            }
            // Still too tall: trim the message preview to whatever room is left.
            val over = total() - avail
            if (over > 0) {
                val c2 = maxOf(64.dp.roundToPx(), u.height - over)
                capH[0] = c2
                u = subcompose("bubble2", bubble).first().measure(loose.copy(maxWidth = maxBub, maxHeight = c2))
            }
            layout(W, H) {
                val top0 = lastAnchor?.top?.toInt() ?: (H / 3)
                val above = listOfNotNull(wP, bP).sumOf { it.height + gap }
                val tot = total()
                // Keep the message where it was in the chat when there's room; otherwise slide everything together.
                var y = top0 - above
                if (y + tot > H - bottomSafe) y = H - bottomSafe - tot
                if (y < topSafe) y = topSafe
                val a = lastAnchor
                val bubX = (if (a != null) (if (me) a.right.toInt() - u.width else a.left.toInt()) else if (me) W - side - u.width else side).coerceIn(0, maxOf(0, W - u.width))
                val edgeX = { w: Int -> if (me) (bubX + u.width - w).coerceIn(side, maxOf(side, W - side - w)) else bubX.coerceIn(side, maxOf(side, W - side - w)) }
                val lift = if (liquid) 0 else ((1f - k.value) * 24f).toInt()
                var yy = y
                wP?.let { it.placeRelative(side, yy + lift); yy += it.height + gap }
                // Each part comes in its own way once the liquid has mostly formed it.
                fun arrive(sc: androidx.compose.ui.graphics.GraphicsLayerScope, a: Float, down: Boolean) {
                    sc.alpha = a
                    if (liquid && a < 1f) {
                        val q = 1f - a
                        when (mo) {
                            com.jpetrydesign.pyng.ui.kit.Mo.Drop -> { val z = 0.9f + 0.1f * a; sc.scaleX = z; sc.scaleY = z }
                            com.jpetrydesign.pyng.ui.kit.Mo.Pour -> sc.translationY = (if (down) -10f else 10f) * sc.density * q
                            else -> sc.translationY = 7f * sc.density * q
                        }
                        val bl = (if (mo == com.jpetrydesign.pyng.ui.kit.Mo.Drop) 7f else 5f) * sc.density * q
                        if (bl > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) sc.renderEffect = androidx.compose.ui.graphics.BlurEffect(bl, bl, androidx.compose.ui.graphics.TileMode.Decal)
                    }
                }
                var barR: androidx.compose.ui.geometry.Rect? = null
                bP?.let { val bx = edgeX(it.width); it.placeRelativeWithLayer(bx, yy + lift) { arrive(this, barA.value, false) }; barR = androidx.compose.ui.geometry.Rect(bx.toFloat(), (yy + lift).toFloat(), (bx + it.width).toFloat(), (yy + lift + it.height).toFloat()); yy += it.height + gap }
                u.placeRelative(bubX, yy)
                val bubR = androidx.compose.ui.geometry.Rect(bubX.toFloat(), yy.toFloat(), (bubX + u.width).toFloat(), (yy + u.height).toFloat())
                yy += u.height + gap
                var actR: androidx.compose.ui.geometry.Rect? = null
                aP?.let { val ax = if (pick.strip) side else edgeX(it.width); it.placeRelativeWithLayer(ax, yy - lift) { arrive(this, actA.value, true) }; actR = androidx.compose.ui.geometry.Rect(ax.toFloat(), (yy - lift).toFloat(), (ax + it.width).toFloat(), (yy - lift + it.height).toFloat()) }
                val ng = MenuGeo(bubR, barR, actR)
                if (m != null && ng != geo) geo = ng
            }
        }
        // The full picker slides up from the bottom, like the keyboard's emoji panel.
        androidx.compose.animation.AnimatedVisibility(
            more, modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.6f).shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(t.sheet)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 16.dp),
            ) { ReactionPicker(t, mine) { e -> onReact(cur, e); close() } }
        }
    }
}

@Composable
fun DetailsSheet(m: Msg?, conv: Conv?, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    Sheet(visible = m != null, onDismiss = onDismiss) {
        val cur = last ?: return@Sheet
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val rows = buildList {
            add("Type" to when {
                cur.isSys -> "Update"
                conv?.rcs == true && !cur.isSms && !cur.isMms -> "Chat message (RCS)"
                cur.isMms || cur.media.isNotEmpty() -> "Picture message (MMS)"
                else -> "Text message (SMS)"
            })
            add((if (cur.fromMe) "To" else "From") to if (cur.fromMe) (conv?.title ?: "") else cur.sender.ifBlank { conv?.title ?: "" })
            if (cur.ts > 0) add((if (cur.fromMe) "Sent" else "Received") to fmt.format(cur.ts))
            if (cur.fromMe) add("Status" to when {
                cur.failed -> "Not sent" + (cur.error.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
                cur.pending -> "Sending"
                cur.readByThem -> "Read"
                cur.delivered -> "Delivered"
                else -> "Sent"
            })
            cur.media.forEach { add("Attachment" to (it.name.ifBlank { it.mime } + if (it.size > 0) " · " + sizeText(it.size) else "")) }
            if (cur.reactions.isNotEmpty()) add("Reactions" to cur.reactions.joinToString("  ") { it.emoji + if (it.count > 1) " ${it.count}" else "" })
        }
        T("Message details", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        SheetGroup {
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp)) {
                    T(k, size = 14.sp, color = t.soft, modifier = Modifier.width(100.dp))
                    T(v, size = 14.sp, weight = FontWeight.Medium)
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION") repo
    }
}

@Composable
fun ForwardSheet(m: Msg?, onPick: (String?, Msg) -> Unit) {
    val repo = LocalRepo.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    Sheet(visible = m != null, onDismiss = { last?.let { onPick(null, it) } }, maxHeightFraction = 0.8f) {
        val cur = last ?: return@Sheet
        T("Forward to", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        val convs = repo.convs.values.filter { it.status == "active" }.sortedByDescending { it.ts }
        LazyColumn(Modifier.fillMaxWidth().height(460.dp)) {
            items(convs, key = { it.id }) { c ->
                val w = whoOf(c)
                Row(Modifier.fillMaxWidth().clickable { onPick(c.id, cur) }.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WhoAvatar(w, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    T(c.title, size = 15.5.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    Ico(Icons.send, t.p, 18.dp, 2f)
                }
            }
        }
    }
}

@Composable
fun CallSheet(open: Boolean, conv: Conv?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    Sheet(visible = open, onDismiss = onDismiss) {
        val c = conv ?: return@Sheet
        val w = whoOf(c)
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WhoAvatar(w, 64.dp, 22.sp)
            Spacer(Modifier.height(8.dp))
            T(c.title, size = 18.sp, weight = FontWeight.SemiBold)
            T(c.number, size = 13.5.sp, color = t.soft)
        }
        SheetGroup {
            SheetAction("Voice call", Icons.phone, { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' }))) }; onDismiss() }, first = true)
            SheetAction("Video call", Icons.video, {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + c.number.filter { it.isDigit() || it == '+' })).putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", 3))
                }
                onDismiss()
            }, index = 1)
            SheetAction("Copy number", Icons.copy, { copy(ctx, c.number); onDismiss() }, index = 2)
        }
    }
}

/** Reads a message out loud with the phone's text-to-speech voice. */
object Speaker {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun say(ctx: android.content.Context, text: String) {
        val cur = tts
        if (cur != null && ready) { cur.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "msg"); return }
        pending = text
        if (cur == null) tts = android.speech.tts.TextToSpeech(ctx.applicationContext) { status ->
            ready = status == android.speech.tts.TextToSpeech.SUCCESS
            if (ready) pending?.let { tts?.speak(it, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "msg") }
            pending = null
        }
    }
}


/** Shows a message's words so you can pick just the part you want and copy it. */
@Composable
fun SelectTextSheet(m: Msg?, onDismiss: () -> Unit) {
    val t = com.jpetrydesign.pyng.ui.theme.LocalTok.current
    val ctx = LocalContext.current
    var last by remember { mutableStateOf(m) }
    if (m != null) last = m
    com.jpetrydesign.pyng.ui.kit.Sheet(m != null, onDismiss) {
        val cur = last ?: return@Sheet
        T("Select text", size = 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 2.dp))
        T("Hold a word, then drag the handles.", size = 12.5.sp, color = t.soft, lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(t.surface)
                .heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.material3.Text(
                    cur.text,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = com.jpetrydesign.pyng.ui.theme.Geist, fontSize = 17.sp, lineHeight = 25.sp, color = t.ink),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        com.jpetrydesign.pyng.ui.kit.PillButton("Copy all", { copy(ctx, cur.text); onDismiss() }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), primary = false, height = 50.dp)
    }
}

/** Where the held message, its reaction bar and its menu sit on screen, for the liquid to join them. */
private data class MenuGeo(
    val bub: androidx.compose.ui.geometry.Rect? = null,
    val bar: androidx.compose.ui.geometry.Rect? = null,
    val act: androidx.compose.ui.geometry.Rect? = null,
)
