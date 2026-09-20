package com.tagsmith.ui.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tagsmith.ui.components.EmptyState
import com.tagsmith.ui.components.Kicker
import com.tagsmith.ui.components.ScreenTitle
import com.tagsmith.ui.theme.Tagsmith

/**
 * The destinations v1 does not build yet. They keep their place in the bar so
 * the shape of the finished app stays visible, and say plainly what is coming.
 */
@Composable
fun ComingSoonScreen(
    title: String,
    kicker: String,
    body: String,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Tagsmith.colors.ground)
            .statusBarsPadding(),
    ) {
        ScreenTitle(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Kicker(kicker, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        EmptyState(
            title = "Next version",
            body = body,
            icon = icon,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.weight(1f).padding(bottom = 132.dp),
        )
    }
}
