package ink.x2.subnetdrop.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jcodec.api.awt.AWTFrameGrab
import org.jetbrains.skia.Image as SkiaImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@Composable
internal actual fun PlatformVideoMessagePreview(localPath: String, fileName: String, modifier: Modifier) {
    val state by produceState<VideoThumbnailState>(VideoThumbnailState.Loading, localPath) {
        value = loadVideoThumbnail(localPath)
    }
    when (val thumbnail = state) {
        VideoThumbnailState.Loading -> MediaPreviewFallback(MediaMessageKind.VIDEO, loading = true)
        VideoThumbnailState.Unavailable -> MediaPreviewFallback(MediaMessageKind.VIDEO)
        is VideoThumbnailState.Ready -> Image(
            bitmap = thumbnail.bitmap,
            contentDescription = fileName,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private suspend fun loadVideoThumbnail(localPath: String): VideoThumbnailState = withContext(Dispatchers.IO) {
    try {
        val frame = AWTFrameGrab.getFrame(File(localPath), 0) ?: return@withContext VideoThumbnailState.Unavailable
        val bytes = ByteArrayOutputStream().use { output ->
            if (!ImageIO.write(frame, "png", output)) return@withContext VideoThumbnailState.Unavailable
            output.toByteArray()
        }
        VideoThumbnailState.Ready(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        VideoThumbnailState.Unavailable
    }
}

private sealed interface VideoThumbnailState {
    data object Loading : VideoThumbnailState
    data object Unavailable : VideoThumbnailState
    data class Ready(val bitmap: ImageBitmap) : VideoThumbnailState
}
