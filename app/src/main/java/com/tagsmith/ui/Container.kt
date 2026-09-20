package com.tagsmith.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tagsmith.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided — wrap the tree in TagsmithRoot.")
}

/**
 * Builds a ViewModel from the hand-wired container. Five collaborators do not
 * need an injection framework, but they do need to be constructed somewhere.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
