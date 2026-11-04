package nobility.downloader.utils

import AppInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.entities.Series
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ImageUtils {

    //it's way faster to preload it and re-use it everywhere.
    val noImage = loadImageBitmapFromResource(AppInfo.NO_IMAGE_PATH)

    suspend fun downloadSeriesImage(series: Series) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ThumbnailCache.ensure(File(series.imagePath), series.imageLink)
        }
    }

    fun loadImageBitmapFromFile(
        filePath: String
    ): ImageBitmap {
        val file = File(filePath)
        if (!file.exists()) {
            return noImage
        }
        return ImageIO.read(
            file
        ).toComposeImageBitmap()
    }

    fun loadImageBitmapFromResource(resourcePath: String): ImageBitmap {
        val inputStream = checkNotNull(ClassLoader.getSystemResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }
        val bufferedImage: BufferedImage = ImageIO.read(inputStream)
        return bufferedImage.toComposeImageBitmap()
    }

    fun loadPainterFromResource(resourcePath: String): Painter {
        val inputStream = checkNotNull(ClassLoader.getSystemResourceAsStream(resourcePath)) {
            "Resource not found: $resourcePath"
        }
        val image: BufferedImage = ImageIO.read(inputStream)
        return BitmapPainter(image.toComposeImageBitmap())
    }
}