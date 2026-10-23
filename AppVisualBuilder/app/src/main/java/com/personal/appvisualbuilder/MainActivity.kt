package com.personal.appvisualbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.personal.appvisualbuilder.ui.AppTheme
import com.personal.appvisualbuilder.ui.AppViewModel
import com.personal.appvisualbuilder.ui.CoordsPreviewScreen
import com.personal.appvisualbuilder.ui.FlowPreviewScreen
import com.personal.appvisualbuilder.ui.HomeScreen
import com.personal.appvisualbuilder.ui.PageEditorScreen
import com.personal.appvisualbuilder.ui.ProjectScreen
import com.personal.appvisualbuilder.ui.Screen
import com.personal.appvisualbuilder.ui.SetupScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(vm) { finish() }
                }
            }
        }
    }
}

@Composable
private fun Root(vm: AppViewModel, exit: () -> Unit) {
    BackHandler { if (!vm.back()) exit() }

    when (val s = vm.screen) {
        is Screen.Home -> HomeScreen(vm)
        is Screen.ProjectHome -> ProjectScreen(vm)
        is Screen.Setup -> SetupScreen(vm)
        is Screen.PageEditor -> PageEditorScreen(vm, s.pageId)
        is Screen.CoordsPreview -> CoordsPreviewScreen(vm, s.pageId)
        is Screen.FlowPreview -> FlowPreviewScreen(vm, s.startIndex)
    }
}
