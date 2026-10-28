package cz.mares.pdfstudio

import androidx.compose.ui.window.ComposeUIViewController
import cz.mares.pdfstudio.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
