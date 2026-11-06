package com.jpetrydesign.pyng.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

private const val SIGN_IN_URL = "https://accounts.google.com/AccountChooser?continue=https://messages.google.com/web/config"
private val NEEDED = listOf("SID", "HSID", "SSID", "APISID", "SAPISID", "OSID")
private val WANTED = NEEDED + "__Secure-1PSIDTS"

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun IconTile(glyph: String) {
    Box(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 38.sp, color = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
fun WelcomeScreen(error: String?, onGoogle: () -> Unit, onQr: () -> Unit, onLog: () -> Unit) {
    Centered {
        IconTile("💬")
        Spacer(Modifier.height(24.dp))
        Text("Link Google Messages", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Messages works alongside Google Messages, the same way Messages for Web does. " +
                "Sign in with the Google account Google Messages uses, then confirm on this phone.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Step("1", "Make sure Google Messages is your texting app and chat features are on.")
        Step("2", "Sign in with Google on the next screen.")
        Step("3", "Google Messages shows a prompt. Tap the emoji that matches.")
        Spacer(Modifier.height(24.dp))
        if (error != null) {
            Text(
                error,
                color = Color(0xFFC8372D),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x14C8372D))
                    .padding(14.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onGoogle,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) { Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onQr) { Text("Use a QR code instead") }
        TextButton(onClick = onLog) {
            Text("Debug log", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(n, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.size(12.dp))
        Text(text, modifier = Modifier.padding(top = 4.dp), lineHeight = 20.sp)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(onBack: () -> Unit, onCookies: (String) -> Unit) {
    var sent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Sign in with Google", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (sent) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Contacting Google Messages…")
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.flush()
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        }
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                check(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                check(url)
                            }

                            private fun check(url: String?) {
                                if (sent || url == null || !url.startsWith("https://messages.google.com")) return
                                cm.flush()
                                val jar = parse(cm.getCookie("https://messages.google.com/web/config")) +
                                    parse(cm.getCookie("https://accounts.google.com"))
                                if (NEEDED.all { !jar[it].isNullOrEmpty() }) {
                                    sent = true
                                    val o = JSONObject()
                                    WANTED.forEach { k -> jar[k]?.let { o.put(k, it) } }
                                    onCookies(o.toString())
                                }
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                    }
                },
            )
        }
    }
}

private fun parse(raw: String?): Map<String, String> =
    raw.orEmpty().split(";").mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
    }.toMap()

@Composable
fun EmojiScreen(emoji: String, onCancel: () -> Unit) {
    Centered {
        Box(
            Modifier.size(150.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 76.sp) }
        Spacer(Modifier.height(28.dp))
        Text("Tap this emoji in Google Messages", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Google Messages is asking to link a new device. Open it from the notification or the app, " +
                "and tap the emoji that matches the one above.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text("Waiting for your phone…")
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
fun QrScreen(url: String, onCancel: () -> Unit) {
    val bitmap = remember(url) { qr(url, 720) }
    Centered {
        Text("Scan with Google Messages", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "In Google Messages: profile photo › Device pairing › QR code scanner. A phone can't scan its own screen, " +
                "so take a screenshot and open it on another screen to scan it.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Pairing QR code",
            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)) {
            Text("Cancel")
        }
    }
}

private fun qr(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}
