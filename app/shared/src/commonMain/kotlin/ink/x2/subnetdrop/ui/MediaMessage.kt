package ink.x2.subnetdrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import io.github.vinceglb.filekit.PlatformFile
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.appString

internal enum class MediaMessageKind {
    IMAGE,
    VIDEO,
    FILE,
}

internal fun FileTransfer.mediaMessageKind(): MediaMessageKind = mediaMessageKind(contentType, fileName)

internal fun mediaMessageKind(contentType: String?, fileName: String): MediaMessageKind {
    val normalizedType = contentType?.substringBefore(';')?.trim()?.lowercase()
    return when {
        normalizedType?.startsWith("image/") == true -> MediaMessageKind.IMAGE
        normalizedType?.startsWith("video/") == true -> MediaMessageKind.VIDEO
        normalizedType == null || normalizedType in GENERIC_CONTENT_TYPES -> mediaKindFromExtension(fileName)
        else -> MediaMessageKind.FILE
    }
}

@Composable
internal fun MediaTransferMessage(
    transfer: FileTransfer,
    kind: MediaMessageKind,
    outgoing: Boolean,
    expired: Boolean,
    canOpen: Boolean,
    interactionModifier: Modifier,
    onCancel: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val preferredWidth = (maxWidth * MEDIA_MESSAGE_WIDTH_FRACTION).coerceAtMost(MAX_MEDIA_MESSAGE_WIDTH)
        val cardWidth = preferredWidth.coerceAtLeast(minOf(MIN_MEDIA_MESSAGE_WIDTH, maxWidth))
        Surface(
            modifier = Modifier
                .align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .width(cardWidth)
                .aspectRatio(mediaMessageAspectRatio(kind))
                .then(interactionModifier),
            shape = MEDIA_MESSAGE_SHAPE,
            color = if (outgoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (canOpen && transfer.localPath != null) {
                    CompletedMediaContent(transfer, kind)
                } else {
                    MediaTransferStateContent(transfer, kind, expired, onCancel)
                }
            }
        }
    }
}

@Composable
private fun CompletedMediaContent(transfer: FileTransfer, kind: MediaMessageKind) {
    val localPath = requireNotNull(transfer.localPath)
    Box(Modifier.fillMaxSize()) {
        when (kind) {
            MediaMessageKind.IMAGE -> ImageMessagePreview(localPath, transfer.fileName, Modifier.fillMaxSize())
            MediaMessageKind.VIDEO -> PlatformVideoMessagePreview(localPath, transfer.fileName, Modifier.fillMaxSize())
            MediaMessageKind.FILE -> Unit
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.58f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        if (kind == MediaMessageKind.VIDEO) {
            VideoPlayIndicator(transfer.fileName, Modifier.align(Alignment.Center))
        }
        CompletedMediaCaption(transfer, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun VideoPlayIndicator(fileName: String, modifier: Modifier) {
    Surface(
        modifier = modifier.size(52.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.Black.copy(alpha = 0.56f),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = fileName,
            modifier = Modifier.padding(12.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun CompletedMediaCaption(transfer: FileTransfer, modifier: Modifier) {
    Column(modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = transfer.fileName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            text = formatFileSize(transfer.size),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.84f),
        )
    }
}

@Composable
private fun MediaTransferStateContent(
    transfer: FileTransfer,
    kind: MediaMessageKind,
    expired: Boolean,
    onCancel: () -> Unit,
) {
    val cancellable = transfer.status.isCancellable()
    val failed = expired || transfer.status == FileTransferStatus.FAILED
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = transfer.fileName,
                modifier = Modifier.padding(end = if (cancellable) 38.dp else 0.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (cancellable) {
                    CircularProgressIndicator(
                        progress = { transfer.progress },
                        modifier = Modifier.size(62.dp),
                        strokeWidth = 4.dp,
                    )
                }
                Icon(
                    imageVector = if (failed) Icons.Outlined.ErrorOutline else kind.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = transfer.summary(expired),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cancellable) {
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                )
            }
            transfer.error?.let { error ->
                Text(
                    text = error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (cancellable) {
            IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.Close, contentDescription = appString(AppString.CANCEL_FILE_TRANSFER))
            }
        }
    }
}

@Composable
private fun ImageMessagePreview(localPath: String, fileName: String, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val thumbnailRequest = remember(context, localPath) {
        ImageRequest.Builder(context)
            .data(PlatformFile(localPath))
            .size(IMAGE_THUMBNAIL_WIDTH_PX, IMAGE_THUMBNAIL_HEIGHT_PX)
            .scale(Scale.FILL)
            .precision(Precision.EXACT)
            .build()
    }
    SubcomposeAsyncImage(
        model = thumbnailRequest,
        contentDescription = fileName,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { MediaPreviewFallback(MediaMessageKind.IMAGE, loading = true) },
        error = { MediaPreviewFallback(MediaMessageKind.IMAGE) },
    )
}

@Composable
internal fun MediaPreviewFallback(kind: MediaMessageKind, loading: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        } else {
            Icon(
                imageVector = kind.icon(),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal expect fun PlatformVideoMessagePreview(localPath: String, fileName: String, modifier: Modifier)

private fun FileTransferStatus.isCancellable(): Boolean = this == FileTransferStatus.PREPARING ||
    this == FileTransferStatus.WAITING_FOR_ACCEPTANCE || this == FileTransferStatus.TRANSFERRING

private fun MediaMessageKind.icon() = when (this) {
    MediaMessageKind.IMAGE -> Icons.Outlined.Image
    MediaMessageKind.VIDEO -> Icons.Outlined.Videocam
    MediaMessageKind.FILE -> Icons.Outlined.ErrorOutline
}

private fun mediaKindFromExtension(fileName: String): MediaMessageKind {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (extension) {
        in IMAGE_EXTENSIONS -> MediaMessageKind.IMAGE
        in VIDEO_EXTENSIONS -> MediaMessageKind.VIDEO
        else -> MediaMessageKind.FILE
    }
}

internal fun mediaMessageAspectRatio(kind: MediaMessageKind): Float = when (kind) {
    MediaMessageKind.IMAGE,
    MediaMessageKind.FILE,
    -> IMAGE_MESSAGE_ASPECT_RATIO
    MediaMessageKind.VIDEO -> VIDEO_MESSAGE_ASPECT_RATIO
}

private val GENERIC_CONTENT_TYPES = setOf("application/octet-stream", "binary/octet-stream", "application/binary")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heif", "heic", "avif", "svg")
private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mov", "m4v", "mkv", "webm", "avi", "wmv", "flv", "mpeg", "mpg", "3gp", "ts", "m2ts", "ogv", "av1",
)
private const val MEDIA_MESSAGE_WIDTH_FRACTION = 0.76f
private const val IMAGE_MESSAGE_ASPECT_RATIO = 4f / 3f
private const val VIDEO_MESSAGE_ASPECT_RATIO = 16f / 9f
private const val IMAGE_THUMBNAIL_WIDTH_PX = 720
private const val IMAGE_THUMBNAIL_HEIGHT_PX = 540
private val MIN_MEDIA_MESSAGE_WIDTH = 220.dp
private val MAX_MEDIA_MESSAGE_WIDTH = 360.dp
private val MEDIA_MESSAGE_SHAPE = RoundedCornerShape(12.dp)
