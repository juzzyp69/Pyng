package com.jpetrydesign.pyng.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpetrydesign.pyng.ui.kit.T
import com.jpetrydesign.pyng.ui.theme.LocalTok

/** The short version, for the last welcome screen. */
const val AI_NOTE_SHORT =
    "I have real concerns about AI and what it costs people and the planet. I've used it as a tool and a teacher to help build Pyng, and I want to be upfront about how."

/** The full note: who made Pyng, how AI helped, where I stand, and what it means for your messages. */
@Composable
fun AiNoteBody(modifier: Modifier = Modifier) {
    val t = LocalTok.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Part(
            "Who made Pyng",
            "I'm Justin, a designer. Pyng's look, feel and flow are mine: the pill bubbles, the layouts, the motion, and every call on what stays and what goes. I test every build on my own phone and send back what's broken and what needs work.",
        )
        Part(
            "How AI helped",
            "I'm still learning to build apps. To turn my designs into a working app, I've used Claude, an AI model, to write and fix much of the code, help test builds, and explain each change so I understand it. My goal is to learn enough to build on my own and rely on AI less and less.",
        )
        Part(
            "Where I stand",
            "I share many people's concerns about AI: the energy and water data centers use, and companies using it to replace people's work. For me it's been a tool and a teacher for a solo maker, not a replacement for anyone's craft. I'd rather tell you than hide it.",
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.tone).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T("What this means for you", size = 15.sp, weight = FontWeight.SemiBold, color = t.ink)
            T(
                "Pyng never sends your messages to an AI service. Translation runs on your phone using Google's on-device language models. Voice transcription uses your phone's built-in speech recognition, on the phone itself when it supports it. Suggested replies are simple built-in phrases.",
                size = 14.sp, color = t.soft, lineHeight = 20.sp,
            )
        }
        T("Justin", size = 14.sp, weight = FontWeight.SemiBold, color = t.soft, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun Part(title: String, body: String) {
    val t = LocalTok.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(title, size = 16.sp, weight = FontWeight.SemiBold, color = t.ink)
        T(body, size = 14.5.sp, color = t.soft, lineHeight = 21.sp)
    }
}
