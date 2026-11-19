package me.lsong.mytv.branding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import me.lsong.mytv.R
import me.lsong.mytv.app.AppConstants
import me.lsong.mytv.ui.settings.components.AboutSettings
import me.lsong.mytv.ui.theme.TvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BrandingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemLoadsNewNameAndBothLauncherAssets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.applicationInfo
        assertEquals("OhMyTV", context.packageManager.getApplicationLabel(info).toString())
        assertEquals("OhMyTV", context.getString(R.string.app_name))
        val icon = context.packageManager.getApplicationIcon(info)
        val banner = context.packageManager.getApplicationBanner(info)
        assertNotNull(banner)
        if (Build.VERSION.SDK_INT >= 26) {
            assertTrue(icon is AdaptiveIconDrawable)
            assertTrue(banner is AdaptiveIconDrawable)
        }
        val bitmap = Bitmap.createBitmap(960, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(241,242,244))
        icon.setBounds(20,20,300,300); icon.draw(canvas)
        banner!!.setBounds(320,20,960,380); banner.draw(canvas)
        java.io.File(context.cacheDir,"ohmytv-system-branding.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun composeAboutPageRendersTheVectorLogo() {
        compose.setContent { TvTheme { AboutSettings() } }
        compose.onNodeWithContentDescription(AppConstants.APP_NAME).assertIsDisplayed()
        compose.onNodeWithText(AppConstants.APP_NAME).assertIsDisplayed()
    }
}
