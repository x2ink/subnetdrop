package ink.x2.subnetdrop.ui

internal expect val supportsFileManagerReveal: Boolean

internal expect suspend fun revealFileInManager(path: String)
