package nobility.downloader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Star
import compose.icons.evaicons.fill.Bookmark
import nobility.downloader.core.*
import nobility.downloader.core.entities.Series
import nobility.downloader.core.scraper.FollowSeries
import nobility.downloader.ui.windows.database.DatabaseWindow

@Composable
fun SeriesBadges(series: Series, modifier: Modifier = Modifier) {
    val revision by LibraryChanges.revision.collectAsState()
    val favorite = remember(series.slug, revision) { BoxHelper.isSeriesFavorited(series) }
    val followed = FollowSeries.isFollowing(DatabaseWindow.followShow(series))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (favorite) Tooltip("Favorite") {
            DefaultIcon(EvaIcons.Fill.Star, iconColor = Color.Yellow, description = "Favorite", modifier = Modifier.size(20.dp))
        }
        if (followed) Tooltip("Following series") {
            DefaultIcon(EvaIcons.Fill.Bookmark, iconColor = MaterialTheme.colorScheme.primary,
                description = "Following series", modifier = Modifier.size(20.dp))
        }
    }
}
