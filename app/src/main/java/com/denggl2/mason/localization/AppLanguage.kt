package com.denggl2.mason.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.denggl2.mason.data.LanguagePreference
import com.denggl2.masonremote.ui.RemoteStrings
import com.denggl2.masonremote.ui.resolveRemoteStrings
import com.denggl2.masonremote.ui.settings.RemoteLanguagePreference
import java.util.Locale

fun LanguagePreference.toRemoteLanguagePreference(): RemoteLanguagePreference = when (this) {
    LanguagePreference.SYSTEM -> RemoteLanguagePreference.SYSTEM
    LanguagePreference.CHINESE -> RemoteLanguagePreference.CHINESE
    LanguagePreference.ENGLISH -> RemoteLanguagePreference.ENGLISH
}

fun Context.resolveAppStrings(preference: LanguagePreference): RemoteStrings =
    resolveRemoteStrings(
        preference = preference.toRemoteLanguagePreference(),
        systemLanguage = resources.configuration.locales[0]?.language.orEmpty(),
    )

/** Keep Android-owned dialogs and service labels aligned with the in-app choice. */
fun Context.applyPlatformLanguage(preference: LanguagePreference) {
    val languageTags = when (preference) {
        LanguagePreference.SYSTEM -> ""
        LanguagePreference.CHINESE -> "zh-CN"
        LanguagePreference.ENGLISH -> "en"
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeManager = getSystemService(LocaleManager::class.java)
        if (localeManager.applicationLocales.toLanguageTags() != languageTags) {
            localeManager.applicationLocales = LocaleList.forLanguageTags(languageTags)
        }
        return
    }

    val locale = when (preference) {
        LanguagePreference.SYSTEM -> Resources.getSystem().configuration.locales[0]
        LanguagePreference.CHINESE -> Locale.SIMPLIFIED_CHINESE
        LanguagePreference.ENGLISH -> Locale.ENGLISH
    }
    if (resources.configuration.locales[0] == locale) return
    Locale.setDefault(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(
        Configuration(resources.configuration).apply { setLocale(locale) },
        resources.displayMetrics,
    )
}
