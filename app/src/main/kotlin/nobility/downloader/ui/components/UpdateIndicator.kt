package nobility.downloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nobility.downloader.core.updates.AppUpdateStatus

@Composable
fun UpdateIndicator() {
    if (AppUpdateStatus.available) {
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(9.dp).background(Color(0xFFD32F2F), CircleShape)
            .semantics { contentDescription = "Update ${AppUpdateStatus.availableVersion} available" })
    }
}
