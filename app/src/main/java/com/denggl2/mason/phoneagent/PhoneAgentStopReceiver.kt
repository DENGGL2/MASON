package com.denggl2.mason.phoneagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.denggl2.mason.tool.NotificationTool
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PhoneAgentStopReceiver : BroadcastReceiver() {
    @Inject
    lateinit var controller: PhoneAgentController

    @Inject
    lateinit var overlay: PhoneAgentOverlayController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationTool.ACTION_PHONE_AGENT_STOP) return
        controller.pause()
        overlay.hide()
    }
}
