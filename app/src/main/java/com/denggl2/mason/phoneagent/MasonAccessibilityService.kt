package com.denggl2.mason.phoneagent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MasonAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var controller: PhoneAgentController

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        controller.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        controller.onInterrupt()
    }

    override fun onDestroy() {
        controller.detach(this)
        super.onDestroy()
    }
}
