package dev.tacuchi.hope.importing

import dev.tacuchi.hope.data.AssetOrigin

/**
 * Origen de lo compartido según el paquete que Android informa como referrer. Si no hay referrer
 * o no es una app conocida, el origen es «Compartido»: mejor genérico que inventado.
 */
object OriginResolver {

    private val knownPackages = mapOf(
        "com.whatsapp" to AssetOrigin.WhatsApp,
        "com.whatsapp.w4b" to AssetOrigin.WhatsApp,
        "org.telegram.messenger" to AssetOrigin.Telegram,
        "org.telegram.messenger.web" to AssetOrigin.Telegram,
        "org.thunderdog.challegram" to AssetOrigin.Telegram,
        "com.zhiliaoapp.musically" to AssetOrigin.TikTok,
        "com.ss.android.ugc.trill" to AssetOrigin.TikTok,
        "com.google.android.apps.photos" to AssetOrigin.Gallery,
    )

    /** [referrer] es lo que da `Activity.getReferrer()`, por ejemplo `android-app://com.whatsapp`. */
    fun fromReferrer(referrer: String?): AssetOrigin {
        val packageName = referrer
            ?.removePrefix("android-app://")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
            ?: return AssetOrigin.Shared
        return knownPackages[packageName] ?: AssetOrigin.Shared
    }
}
