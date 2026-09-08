package ink.x2.subnetdrop.resources

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationTest {
    @Test
    fun providesRepresentativeEnglishChineseAndJapaneseStrings() {
        assertEquals("Nearby", strings("values").getValue("nearby_devices"))
        assertEquals("附近设备", strings("values-zh").getValue("nearby_devices"))
        assertEquals("近くのデバイス", strings("values-ja").getValue("nearby_devices"))
        assertEquals("System default", strings("values").getValue("language_system"))
        assertEquals("跟随系统", strings("values-zh").getValue("language_system"))
        assertEquals("システム設定", strings("values-ja").getValue("language_system"))
    }

    @Test
    fun localizedFilesContainTheSameNonBlankKeys() {
        val english = strings("values")
        val chinese = strings("values-zh")
        val japanese = strings("values-ja")

        assertEquals(english.keys, chinese.keys)
        assertEquals(english.keys, japanese.keys)
        assertEquals(AppString.entries.size, english.size)
        assertTrue(english.values.all(String::isNotBlank))
        assertTrue(chinese.values.all(String::isNotBlank))
        assertTrue(japanese.values.all(String::isNotBlank))
    }

    @Test
    fun dynamicResourcesUseMatchingPlaceholders() {
        val resourceDirectories = listOf("values", "values-zh", "values-ja")

        resourceDirectories.forEach { directory ->
            val localized = strings(directory)
            assertEquals(setOf("%1\$d", "%2\$d"), placeholders(localized.getValue("file_size_range")))
            assertEquals(
                setOf("%1\$s", "%2\$s", "%3\$s"),
                placeholders(localized.getValue("receive_file_message")),
            )
        }
    }

    private fun strings(directory: String): Map<String, String> {
        val resourceFile = File("src/commonMain/composeResources/$directory/strings.xml")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(resourceFile).getElementsByTagName("string")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }
    }

    private fun placeholders(value: String): Set<String> = PLACEHOLDER.findAll(value).map { it.value }.toSet()

    private companion object {
        val PLACEHOLDER = Regex("%\\d+\\$[sd]")
    }
}
