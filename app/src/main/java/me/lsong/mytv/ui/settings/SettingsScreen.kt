package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.lsong.mytv.rememberLeanbackChildPadding
import me.lsong.mytv.ui.settings.LeanbackSettingsCategoryContent
import me.lsong.mytv.ui.settings.MyTvSettingsCategories
import me.lsong.mytv.ui.settings.MyTvSettingsCategoryList
import me.lsong.mytv.ui.theme.LeanbackTheme

@Composable
fun MyTvSettingsScreen(
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var focusedCategory by remember { mutableStateOf(MyTvSettingsCategories.entries.first()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            MyTvSettingsCategoryList(
                modifier = Modifier.width(180.dp),
                focusedCategoryProvider = { focusedCategory },
                onFocused = { focusedCategory = it },
            )

            LeanbackSettingsCategoryContent(
                focusedCategoryProvider = { focusedCategory },
            )
        }
    }
}

@Preview(device = "id:pixel_5")
@Composable
private fun LeanbackSettingsScreenPreview() {
    LeanbackTheme {
        MyTvSettingsScreen()
    }
}