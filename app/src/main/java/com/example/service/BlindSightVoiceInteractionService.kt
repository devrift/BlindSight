package com.example.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.example.MainActivity

class BlindSightVoiceInteractionService : VoiceInteractionService()

class BlindSightVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return object : VoiceInteractionSession(this) {
            override fun onShow(args: Bundle?, showFlags: Int) {
                super.onShow(args, showFlags)
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("START_ASSISTANT_LISTENING", true)
                }
                context.startActivity(intent)
                finish()
            }
        }
    }
}
