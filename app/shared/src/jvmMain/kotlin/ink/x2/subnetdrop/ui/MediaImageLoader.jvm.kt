package ink.x2.subnetdrop.ui

import coil3.ImageLoader
import coil3.PlatformContext
import io.github.vinceglb.filekit.coil.addPlatformFileSupport

internal actual fun createMediaImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
    .components {
        addPlatformFileSupport()
    }
    .build()
