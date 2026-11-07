package com.jpetrydesign.pyng.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jpetrydesign.pyng.ui.kit.Hairline
import com.jpetrydesign.pyng.ui.kit.Ico
import com.jpetrydesign.pyng.ui.kit.Icons
import com.jpetrydesign.pyng.ui.kit.PillButton
import com.jpetrydesign.pyng.ui.kit.Sheet
import com.jpetrydesign.pyng.ui.kit.SheetGroup
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.kit.enter
import com.jpetrydesign.pyng.ui.theme.LocalTok

/** One thing the app asks Android for, and why. */
class Perm(val icon: String, val title: String, val why: String, val perms: List<String>)

fun allPerms(): List<Perm> = listOfNotNull(
    if (Build.VERSION.SDK_INT >= 33) Perm(Icons.bell, "Notifications", "Alerts for new messages, with quick reply", listOf(Manifest.permission.POST_NOTIFICATIONS)) else null,
    Perm(Icons.contact, "Contacts", "Share contact cards and match names", listOf(Manifest.permission.READ_CONTACTS)),
    Perm(
        Icons.gallery, "Photos & videos", "Send photos straight from the message box",
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
    ),
    Perm(Icons.mic, "Microphone", "Record voice messages", listOf(Manifest.permission.RECORD_AUDIO)),
    Perm(Icons.pin, "Location", "Share where you are", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
)

fun granted(ctx: Context, p: Perm): Boolean = p.perms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } ||
    (p.title == "Photos & videos" && Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(ctx, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)

fun missingPerms(ctx: Context) = allPerms().filter { !granted(ctx, it) }

/** The rows of permissions with Allow buttons; used in setup, the home prompt and Settings. */
@Composable
fun PermissionRows(onAnyResult: () -> Unit = {}) {
    val ctx = LocalContext.current
    val t = LocalTok.current
    var tick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++; onAnyResult() }
    @Suppress("UNUSED_EXPRESSION") tick
    allPerms().forEachIndexed { i, perm ->
        if (i > 0) Hairline()
        val on = granted(ctx, perm)
        Row(Modifier.enter(i).fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(t.tone), contentAlignment = Alignment.Center) { Ico(perm.icon, t.p, 19.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                T(perm.title, size = 15.5.sp, weight = FontWeight.SemiBold)
                T(perm.why, size = 12.5.sp, color = t.soft)
            }
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) t.p else t.tone)
                    .clickable(enabled = !on) {
                        val asked = ctx.getSharedPreferences("perm-asked", Context.MODE_PRIVATE)
                        val permanentlyDenied = asked.getBoolean(perm.title, false) && perm.perms.none { p -> (ctx as? android.app.Activity)?.shouldShowRequestPermissionRationale(p) == true }
                        asked.edit().putBoolean(perm.title, true).apply()
                        if (permanentlyDenied) openAppSettings(ctx) else launcher.launch(perm.perms.toTypedArray())
                    }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { T(if (on) "Allowed" else "Allow", size = 13.5.sp, weight = FontWeight.SemiBold, color = if (on) t.onFill else t.p) }
        }
    }
}

fun openAppSettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Shown once on Home when something the app uses hasn't been allowed yet. */
@Composable
fun PermissionSheet(visible: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTok.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }
    Sheet(visible = visible, onDismiss = onDone) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 14.dp)) {
            T("Finish setting up", size = 20.sp, weight = FontWeight.Bold)
            T("A few things still need your OK so every feature works.", size = 13.5.sp, color = t.soft, modifier = Modifier.padding(top = 4.dp))
        }
        SheetGroup { PermissionRows() }
        Spacer(Modifier.height(16.dp))
        PillButton("Allow all", {
            launcher.launch(missingPerms(ctx).flatMap { it.perms }.toTypedArray())
        }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), height = 52.dp)
        T("Not now", size = 14.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).clickable(onClick = onDone).padding(10.dp))
    }
}
