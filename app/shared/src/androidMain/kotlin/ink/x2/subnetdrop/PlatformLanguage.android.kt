package ink.x2.subnetdrop

import android.content.res.Resources
import android.os.LocaleList
import ink.x2.subnetdrop.domain.model.AppLanguage
import java.util.Locale

internal actual fun applyPlatformLanguage(language: AppLanguage) {
    val locales = language.languageTag
        ?.let(Locale::forLanguageTag)
        ?.let(::LocaleList)
        ?: Resources.getSystem().configuration.locales
    LocaleList.setDefault(locales)
}
