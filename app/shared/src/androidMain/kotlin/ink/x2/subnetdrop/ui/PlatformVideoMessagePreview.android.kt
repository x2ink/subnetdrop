package ink.x2.subnetdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFrameMillis
import io.github.vinceglb.filekit.PlatformFile

@Composable
internal actual fun PlatformVideoMessagePreview(localPath: String, fileName: String, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val request = ImageRequest.Builder(context)
        .data(PlatformFile(localPath))
        .videoFrameMillis(0)
        .crossfade(true)
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = fileName,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { MediaPreviewFallback(MediaMessageKind.VIDEO, loading = true) },
        error = { MediaPreviewFallback(MediaMessageKind.VIDEO) },
    )
}
