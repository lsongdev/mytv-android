package me.lsong.mytv.playback.danmaku

import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Physical TV screen savers must not replace the test activity between fixtures. */
internal fun keepDanmakuTestDisplayAwake() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 224"))
        .use { it.readBytes() }
    instrumentation.runOnMainSync {
        listOf(Stage.RESUMED,Stage.PAUSED,Stage.STARTED).flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
            .forEach { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
