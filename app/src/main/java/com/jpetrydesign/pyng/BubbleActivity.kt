package com.jpetrydesign.pyng

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.jpetrydesign.pyng.ui.Nav
import com.jpetrydesign.pyng.ui.PyngRoot
import com.jpetrydesign.pyng.ui.Route
import com.jpetrydesign.pyng.ui.kit.LocalRepo
import com.jpetrydesign.pyng.ui.rememberTok
import com.jpetrydesign.pyng.ui.theme.LocalTok

/** A chat floating over other apps as a bubble. It opens straight into that one conversation. */
class BubbleActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as PyngApp).repo
        val chat = intent?.getStringExtra("chat")
        if (chat.isNullOrEmpty() || !repo.paired) { finish(); return }
        val nav = Nav(Route.Thread(chat))
        setContent {
            val t = rememberTok(repo)
            CompositionLocalProvider(LocalTok provides t, LocalRepo provides repo) { PyngRoot(repo, nav) }
        }
    }
}
