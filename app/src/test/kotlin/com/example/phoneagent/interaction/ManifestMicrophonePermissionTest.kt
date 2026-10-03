package com.example.phoneagent.interaction

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestMicrophonePermissionTest {
    @Test
    fun `manifest declares record audio permission`() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        val permissions =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(manifest)
                .getElementsByTagName("uses-permission")
        val recordAudio =
            (0 until permissions.length).map {
                permissions
                    .item(it)
                    .attributes
                    .getNamedItem("android:name")
                    ?.nodeValue
            }

        assertEquals(1, recordAudio.count { it == "android.permission.RECORD_AUDIO" })
    }
}
