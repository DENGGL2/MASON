package com.denggl2.mason

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.denggl2.mason.navigation.MasonNavGraph
import com.denggl2.mason.tool.BatteryOptimizationTool
import com.denggl2.mason.tool.ScreenshotTool
import com.denggl2.mason.ui.theme.MasonTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var batteryOptimizationTool: BatteryOptimizationTool

    @Inject
    lateinit var screenshotTool: ScreenshotTool

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MasonTheme {
                MasonNavGraph()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ScreenshotTool.REQUEST_CODE_MEDIA_PROJECTION -> {
                ScreenshotTool.onAuthResult(resultCode, data)
            }
        }
    }
}
