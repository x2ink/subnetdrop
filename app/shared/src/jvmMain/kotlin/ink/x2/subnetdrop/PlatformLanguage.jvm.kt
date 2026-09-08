package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.AppLanguage
import java.util.Locale

private val systemLocale: Locale = Locale.getDefault()

internal actual fun applyPlatformLanguage(language: AppLanguage) {
    Locale.setDefault(language.languageTag?.let(Locale::forLanguageTag) ?: systemLocale)
}
