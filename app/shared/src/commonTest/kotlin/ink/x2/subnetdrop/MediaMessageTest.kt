package ink.x2.subnetdrop

import ink.x2.subnetdrop.ui.MediaMessageKind
import ink.x2.subnetdrop.ui.mediaMessageAspectRatio
import ink.x2.subnetdrop.ui.mediaMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaMessageTest {
    @Test
    fun mimeTypeTakesPriorityOverExtension() {
        assertEquals(MediaMessageKind.IMAGE, mediaMessageKind("image/jpeg", "photo.bin"))
        assertEquals(MediaMessageKind.VIDEO, mediaMessageKind("video/mp4; charset=binary", "clip.data"))
        assertEquals(MediaMessageKind.FILE, mediaMessageKind("application/pdf", "document.jpg"))
    }

    @Test
    fun genericMimeTypeFallsBackToCaseInsensitiveExtension() {
        assertEquals(MediaMessageKind.IMAGE, mediaMessageKind("application/octet-stream", "PHOTO.HEIC"))
        assertEquals(MediaMessageKind.VIDEO, mediaMessageKind(null, "recording.MOV"))
        assertEquals(MediaMessageKind.FILE, mediaMessageKind("binary/octet-stream", "archive.zip"))
    }

    @Test
    fun imageAndVideoMessagesUseIndependentAspectRatios() {
        assertEquals(4f / 3f, mediaMessageAspectRatio(MediaMessageKind.IMAGE))
        assertEquals(16f / 9f, mediaMessageAspectRatio(MediaMessageKind.VIDEO))
    }
}
