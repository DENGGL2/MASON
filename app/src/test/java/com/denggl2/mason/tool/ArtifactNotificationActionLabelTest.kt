package com.denggl2.mason.tool

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactNotificationActionLabelTest {
    @Test
    fun selectsAnActionForKnownArtifactTypes() {
        assertEquals("查看图片", artifactNotificationActionLabel("/artifacts/picture.PNG"))
        assertEquals("查看文档", artifactNotificationActionLabel("/artifacts/report.md"))
        assertEquals("播放音频", artifactNotificationActionLabel("/artifacts/briefing.m4a"))
        assertEquals("查看视频", artifactNotificationActionLabel("/artifacts/demo.mp4"))
    }

    @Test
    fun fallsBackToGenericFileAction() {
        assertEquals("查看文件", artifactNotificationActionLabel("/artifacts/archive.bin"))
    }
}
