package com.pishimovie.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.pishimovie.R
import com.pishimovie.data.model.FontSettings
import com.pishimovie.data.model.FontType
import com.pishimovie.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FontManager — resolves the active [FontFamily] for the Material 3 theme.
 *
 * The app ships with the Vazirmatn Persian font (open-source
 * IranSans-equivalent). Vazirmatn is loaded with the proper weight hierarchy:
 *
 *   - Light      (300) — captions, tertiary text
 *   - Regular    (400) — body text
 *   - Medium     (500) — titles, labels
 *   - SemiBold   (600) — emphasized titles
 *   - Bold       (700) — headings, screen titles
 *   - ExtraBold  (800) — display numbers
 *   - Black      (900) — splash / hero wordmark
 *
 * Persian glyphs render correctly at every weight because Vazirmatn is a
 * hand-tuned Persian font (unlike Latin-only system fonts that fall back
 * to a generic Naskh shape for Persian letters).
 */
object FontManager {

    private var vazirmatnFontFamily: FontFamily? = null

    fun loadFontFamily(context: Context, fontType: FontType): FontFamily? {
        return when (fontType) {
            FontType.DEFAULT -> null
            FontType.VAZIRMATN -> getVazirmatn()
        }
    }

    @Synchronized
    private fun getVazirmatn(): FontFamily {
        if (vazirmatnFontFamily != null) return vazirmatnFontFamily!!
        vazirmatnFontFamily = FontFamily(
            Font(R.font.vazirmatn_thin, FontWeight.Thin, FontStyle.Normal),
            Font(R.font.vazirmatn_extralight, FontWeight.ExtraLight, FontStyle.Normal),
            Font(R.font.vazirmatn_light, FontWeight.Light, FontStyle.Normal),
            Font(R.font.vazirmatn_regular, FontWeight.Normal, FontStyle.Normal),
            Font(R.font.vazirmatn_medium, FontWeight.Medium, FontStyle.Normal),
            Font(R.font.vazirmatn_semibold, FontWeight.SemiBold, FontStyle.Normal),
            Font(R.font.vazirmatn_bold, FontWeight.Bold, FontStyle.Normal),
            Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
            Font(R.font.vazirmatn_black, FontWeight.Black, FontStyle.Normal),
        )
        return vazirmatnFontFamily!!
    }
}

@Composable
fun rememberFontSettings(): FontSettings {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return produceState(initialValue = FontSettings(), context) {
        value = withContext(Dispatchers.IO) { StorageUtils.loadFontSettings(context) }
    }.value
}
