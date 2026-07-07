package com.denggl2.mason

import android.app.Application
import com.denggl2.mason.crashguard.CrashGuard
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MasonApp : Application() {

    @Inject
    lateinit var crashGuard: CrashGuard

    override fun onCreate() {
        super.onCreate()
        crashGuard.init()
    }
}
