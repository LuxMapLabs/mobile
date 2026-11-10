package com.luxmap.feature.survey.ui.polephoto

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PoleVideoFileProviderTest {
    @Test
    fun getUriForFile_resolves_a_file_under_pole_videos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.filesDir, "pole-videos/WO-1")
        dir.mkdirs()
        val file = File(dir, "clip.mp4").apply { writeBytes(byteArrayOf(1)) }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
