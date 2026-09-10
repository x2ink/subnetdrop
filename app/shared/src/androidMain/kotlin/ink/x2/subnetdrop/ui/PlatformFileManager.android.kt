package ink.x2.subnetdrop.ui

internal actual val supportsFileManagerReveal: Boolean = false

internal actual suspend fun revealFileInManager(path: String) {
    throw UnsupportedOperationException("File manager reveal is unavailable on Android")
}
