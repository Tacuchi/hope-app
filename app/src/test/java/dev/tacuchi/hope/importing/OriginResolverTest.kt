package dev.tacuchi.hope.importing

import dev.tacuchi.hope.data.AssetOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginResolverTest {

    @Test
    fun knownAppsByReferrer() {
        assertEquals(AssetOrigin.WhatsApp, OriginResolver.fromReferrer("android-app://com.whatsapp"))
        assertEquals(AssetOrigin.WhatsApp, OriginResolver.fromReferrer("android-app://com.whatsapp.w4b"))
        assertEquals(AssetOrigin.Telegram, OriginResolver.fromReferrer("android-app://org.telegram.messenger"))
        assertEquals(AssetOrigin.TikTok, OriginResolver.fromReferrer("android-app://com.zhiliaoapp.musically"))
        assertEquals(AssetOrigin.Gallery, OriginResolver.fromReferrer("android-app://com.google.android.apps.photos"))
    }

    @Test
    fun unknownOrMissingReferrerIsShared() {
        assertEquals(AssetOrigin.Shared, OriginResolver.fromReferrer(null))
        assertEquals(AssetOrigin.Shared, OriginResolver.fromReferrer(""))
        assertEquals(AssetOrigin.Shared, OriginResolver.fromReferrer("android-app://com.example.notas"))
        assertEquals(AssetOrigin.Shared, OriginResolver.fromReferrer("https://example.com/"))
    }
}
