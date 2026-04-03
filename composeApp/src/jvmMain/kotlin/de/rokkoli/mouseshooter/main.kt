package de.rokkoli.mouseshooter

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mouse Shooter – Battle Royale",
        state = WindowState(size = DpSize(1280.dp, 800.dp)),
        resizable = true
    ) {
        App()
    }
}