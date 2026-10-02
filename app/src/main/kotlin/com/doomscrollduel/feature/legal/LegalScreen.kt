package com.doomscrollduel.feature.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

/** The two documents bundled in the app. [key] is the navigation argument. */
enum class LegalDoc(
    val key: String,
    @StringRes val title: Int,
    @StringRes val summaryTitle: Int,
    @StringRes val summary: Int,
    val sections: List<Pair<Int, Int>>,
) {
    PRIVACY(
        key = "privacy",
        title = R.string.legal_privacy_title,
        summaryTitle = R.string.legal_privacy_summary_title,
        summary = R.string.legal_privacy_summary,
        sections = listOf(
            R.string.legal_p1_title to R.string.legal_p1_body,
            R.string.legal_p2_title to R.string.legal_p2_body,
            R.string.legal_p3_title to R.string.legal_p3_body,
            R.string.legal_p4_title to R.string.legal_p4_body,
            R.string.legal_p5_title to R.string.legal_p5_body,
            R.string.legal_p6_title to R.string.legal_p6_body,
            R.string.legal_p7_title to R.string.legal_p7_body,
            R.string.legal_p8_title to R.string.legal_p8_body,
            R.string.legal_p9_title to R.string.legal_p9_body,
        ),
    ),
    TERMS(
        key = "terms",
        title = R.string.legal_terms_title,
        summaryTitle = R.string.legal_terms_summary_title,
        summary = R.string.legal_terms_summary,
        sections = listOf(
            R.string.legal_t1_title to R.string.legal_t1_body,
            R.string.legal_t2_title to R.string.legal_t2_body,
            R.string.legal_t3_title to R.string.legal_t3_body,
            R.string.legal_t4_title to R.string.legal_t4_body,
            R.string.legal_t5_title to R.string.legal_t5_body,
            R.string.legal_t6_title to R.string.legal_t6_body,
            R.string.legal_t7_title to R.string.legal_t7_body,
            R.string.legal_t8_title to R.string.legal_t8_body,
            R.string.legal_t9_title to R.string.legal_t9_body,
        ),
    );

    companion object {
        const val ARG = "doc"
        fun fromKey(key: String?): LegalDoc = entries.firstOrNull { it.key == key } ?: PRIVACY
    }
}

/** A readable, offline page for the Privacy policy or the Terms of use. */

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit, modifier: Modifier = Modifier) {
    KitPage(modifier = modifier, title = stringResource(doc.title), onBack = onBack) {
        NText(stringResource(R.string.legal_updated), 12.sp, color = Neon.Muted)
        Spacer(Modifier.height(16.dp))
        KitCard(
            radius = 24.dp,
            fill = Brush.horizontalGradient(listOf(Kit.Gold.copy(alpha = 0.22f), Kit.Surface)),
            edge = Kit.Gold.copy(alpha = 0.4f),
        ) {
            NText(stringResource(doc.summaryTitle), 18.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            NText(stringResource(doc.summary), 14.sp, weight = FontWeight.SemiBold, lineHeight = 21.sp)
        }
        Spacer(Modifier.height(22.dp))
        doc.sections.forEach { (title, body) ->
            NText(
                text = stringResource(title),
                size = 17.sp,
                weight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            NText(stringResource(body), 14.sp, color = Neon.Muted, lineHeight = 21.sp)
            Spacer(Modifier.height(22.dp))
        }
    }
}
