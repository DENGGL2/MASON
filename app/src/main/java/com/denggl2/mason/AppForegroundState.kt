package com.denggl2.mason

/** Process-local visibility used only to avoid duplicating in-app status with system notifications. */
object AppForegroundState {
    @Volatile
    var isForeground: Boolean = false
        private set

    fun onActivityResumed() {
        isForeground = true
    }

    fun onActivityPaused() {
        isForeground = false
    }
}
