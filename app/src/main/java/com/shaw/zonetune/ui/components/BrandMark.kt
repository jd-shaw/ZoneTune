package com.shaw.zonetune.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shaw.zonetune.ui.theme.BodyFontFamily
import com.shaw.zonetune.ui.theme.ChineseDisplayFontFamily

enum class BrandMarkSize {
    Hero,
    Compact,
}

/**
 * Brand lockup: Chinese name leads, English sits smaller beside it.
 */
@Composable
fun BrandMark(
    size: BrandMarkSize = BrandMarkSize.Hero,
    modifier: Modifier = Modifier,
) {
    val chineseStyle: TextStyle
    val englishStyle: TextStyle
    val gap: Dp

    when (size) {
        BrandMarkSize.Hero -> {
            chineseStyle = TextStyle(
                fontFamily = ChineseDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = 1.5.sp,
            )
            englishStyle = TextStyle(
                fontFamily = BodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.2.sp,
            )
            gap = 8.dp
        }
        BrandMarkSize.Compact -> {
            chineseStyle = TextStyle(
                fontFamily = ChineseDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                letterSpacing = 1.sp,
            )
            englishStyle = TextStyle(
                fontFamily = BodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.2.sp,
            )
            gap = 6.dp
        }
    }

    Row(modifier = modifier) {
        Text(
            text = "听域",
            style = chineseStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignBy(LastBaseline),
        )
        Spacer(modifier = Modifier.width(gap))
        Text(
            text = "ZoneTune",
            style = englishStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignBy(LastBaseline),
        )
    }
}
