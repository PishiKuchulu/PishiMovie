package com.pishimovie.data.model

import kotlinx.serialization.Serializable

/**
 * Font configuration for PishiMovie.
 *
 * The app ships with the Vazirmatn Persian font (the open-source
 * IranSans-equivalent widely used for Persian UIs). It is applied globally
 * by default so Persian text renders with proper letter shapes — including
 * correct joining, digit forms, and kerning.
 *
 * Users can switch back to the system default via Settings, but the default
 * is VAZIRMATN so the app feels natively Persian out of the box.
 */
@Serializable
data class FontSettings(
    val fontType: FontType = FontType.VAZIRMATN
) {
    companion object {
        /** Default font settings — Vazirmatn (Persian UI font). */
        val DEFAULT = FontSettings()
    }
}

@Serializable
enum class FontType {
    /** System default font (Latin-only, no Persian shaping). */
    DEFAULT,
    /** Vazirmatn — open-source Persian font, IranSans-equivalent. */
    VAZIRMATN,
}
