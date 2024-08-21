package me.lsong.mytv.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.lsong.mytv.ui.settings.MyTvSettingsCategories

@Composable
fun LeanbackSettingsCategoryContent(
    modifier: Modifier = Modifier,
    focusedCategoryProvider: () -> MyTvSettingsCategories = { MyTvSettingsCategories.entries.first() },
) {
    val focusedCategory = focusedCategoryProvider()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
    ) {
        Column (
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ){
            Text(text = focusedCategory.title, style = MaterialTheme.typography.headlineSmall)

            when (focusedCategory) {
                MyTvSettingsCategories.ABOUT -> LeanbackSettingsCategoryAbout()
                MyTvSettingsCategories.APP -> LeanbackSettingsCategoryApp()
                MyTvSettingsCategories.IPTV -> LeanbackSettingsCategoryIptv()
                MyTvSettingsCategories.EPG -> LeanbackSettingsCategoryEpg()
            }
        }
    }
}