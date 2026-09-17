package com.airgesture.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AermotusPalette {
    val Void = Color(0xFF020609)
    val Deep = Color(0xFF061017)
    val DeepBlue = Color(0xFF0A1B23)
    val Steel = Color(0xFF29414A)
    val SteelBright = Color(0xFFB8D2D8)
    val Glass = Color(0xF008151B)
    val GlassLight = Color(0xEE10242D)
    val GlassHighlight = Color(0xFF1A3944)
    val Cyan = Color(0xFF9EEEF1)
    val CyanBright = Color(0xFFE9FFFF)
    val CyanDim = Color(0xFF54C7D1)
    val CyanDeep = Color(0xFF1E7B8B)
    val Gold = Color(0xFFFFE7AE)
    val GoldDeep = Color(0xFFD6AB58)
    val Mint = Color(0xFF7BF1CE)
    val Warning = Color(0xFFFFC178)
    val TextPrimary = Color(0xFFF4FAFB)
    val TextSecondary = Color(0xFFBCD0D4)
    val TextMuted = Color(0xFF789198)
    val Hairline = Color(0x66C8FBFF)
    val Silver = Color(0xFFD0E1E5)
}

private val AergisFont = FontFamily(
    Font(
        resId = R.font.aergis_display,
        weight = FontWeight.SemiBold
    )
)

private val LockedTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        letterSpacing = 3.5.sp,
        shadow = Shadow(
            color = AermotusPalette.Cyan.copy(alpha = .42f),
            offset = Offset(0f, 1f),
            blurRadius = 12f
        )
    ),
    headlineMedium = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = 2.2.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 1.4.sp
    ),
    titleLarge = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        letterSpacing = .9.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = .45.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = .15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = .15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 1.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AergisFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun AergisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AermotusPalette.Cyan,
            secondary = AermotusPalette.Gold,
            tertiary = AermotusPalette.Mint,
            background = AermotusPalette.Void,
            surface = AermotusPalette.DeepBlue,
            onPrimary = AermotusPalette.Void,
            onSecondary = AermotusPalette.Void,
            onSurface = AermotusPalette.TextPrimary,
            onBackground = AermotusPalette.TextPrimary
        ),
        typography = LockedTypography,
        content = content
    )
}

@Composable
fun AermotusTheme(content: @Composable () -> Unit) = AergisTheme(content)

@Composable
fun AergisBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF102A34),
                        Color(0xFF071922),
                        Color(0xFF030A0E),
                        Color(0xFF010406)
                    )
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AermotusPalette.Cyan.copy(alpha = .17f),
                            Color.Transparent
                        ),
                        center = Offset(980f, 40f),
                        radius = 840f
                    )
                )
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizon = h * .62f

            repeat(9) { i ->
                val t = i / 8f
                val y = horizon + (h - horizon) * t * t
                drawLine(
                    color = AermotusPalette.Cyan.copy(alpha = .04f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }
            repeat(11) { i ->
                val x = w * (i / 10f)
                val topX = w * .5f + (x - w * .5f) * .24f
                val bottomX = w * .5f + (x - w * .5f) * 1.82f
                drawLine(
                    color = AermotusPalette.Cyan.copy(alpha = .035f),
                    start = Offset(topX, horizon),
                    end = Offset(bottomX, h),
                    strokeWidth = 1f
                )
            }

            drawLine(
                color = AermotusPalette.Silver.copy(alpha = .10f),
                start = Offset(w * .60f, 0f),
                end = Offset(w * .94f, h * .22f),
                strokeWidth = 2f
            )
            drawLine(
                color = AermotusPalette.Cyan.copy(alpha = .13f),
                start = Offset(w * .68f, 0f),
                end = Offset(w, h * .18f),
                strokeWidth = 1f
            )

            repeat(32) { i ->
                val x = ((i * 137) % 101) / 100f * w
                val y = ((i * 71 + 17) % 91) / 100f * h * .56f
                drawCircle(
                    color = AermotusPalette.CyanBright.copy(alpha = if (i % 5 == 0) .24f else .09f),
                    radius = if (i % 5 == 0) 1.7f else .9f,
                    center = Offset(x, y)
                )
            }
        }
        content()
    }
}

@Composable
fun AermotusBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = AergisBackground(modifier, content)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val edge = accent ?: AermotusPalette.Cyan
    val outer = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        AermotusPalette.Silver.copy(alpha = .72f),
                        edge.copy(alpha = .78f),
                        AermotusPalette.Gold.copy(alpha = .25f),
                        AermotusPalette.Silver.copy(alpha = .30f)
                    )
                ),
                shape = outer
            ),
        color = Color.Transparent,
        shape = outer,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .04f))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xF1183039),
                            Color(0xF50C2028),
                            Color(0xFA06141A),
                            Color(0xFC02090D)
                        )
                    )
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = Color.White.copy(alpha = .28f),
                    start = Offset(12f, 1f),
                    end = Offset(w * .52f, 1f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = edge.copy(alpha = .72f),
                    start = Offset(w * .63f, 1f),
                    end = Offset(w - 12f, 1f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = AermotusPalette.Gold.copy(alpha = .48f),
                    start = Offset(1f, h * .63f),
                    end = Offset(1f, h - 12f),
                    strokeWidth = 2f
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SectionEyebrow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 20.dp, height = 2.dp)
                .background(AermotusPalette.Cyan.copy(alpha = .92f))
        )
        Text(
            text = text.uppercase(),
            color = AermotusPalette.Cyan,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.7.sp,
                shadow = Shadow(
                    color = AermotusPalette.Cyan.copy(alpha = .32f),
                    blurRadius = 6f
                )
            )
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = AermotusPalette.TextPrimary,
            style = MaterialTheme.typography.titleLarge
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = AermotusPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (active) AermotusPalette.Mint else AermotusPalette.Cyan
    Surface(
        modifier = modifier,
        color = Color(0xD9071A21),
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    accent.copy(alpha = .82f),
                    AermotusPalette.Silver.copy(alpha = .30f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(5.dp).background(accent, CircleShape))
            Text(text = text, color = accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun OrbitMark(
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val main = if (active) AermotusPalette.Mint else AermotusPalette.Cyan
    Canvas(modifier.size(62.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * .25f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    AermotusPalette.CyanBright.copy(alpha = .96f),
                    main.copy(alpha = .54f),
                    AermotusPalette.CyanDeep.copy(alpha = .24f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.6f
            ),
            radius = radius * 1.6f,
            center = center
        )
        drawCircle(
            color = AermotusPalette.CyanBright.copy(alpha = .92f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.8f)
        )
        drawOval(
            color = AermotusPalette.Gold.copy(alpha = .98f),
            topLeft = Offset(size.width * .01f, size.height * .35f),
            size = Size(size.width * .98f, size.height * .28f),
            style = Stroke(width = 2.8f)
        )
        drawOval(
            color = AermotusPalette.CyanBright.copy(alpha = .46f),
            topLeft = Offset(size.width * .12f, size.height * .26f),
            size = Size(size.width * .76f, size.height * .48f),
            style = Stroke(width = 1.2f)
        )
        drawCircle(
            color = AermotusPalette.CyanBright,
            radius = size.minDimension * .045f,
            center = Offset(size.width * .84f, size.height * .41f)
        )
    }
}

@Composable
fun LuminousActionButton(
    text: String,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val start = if (gold) AermotusPalette.Gold else AermotusPalette.CyanBright
    val end = if (gold) AermotusPalette.GoldDeep else AermotusPalette.Cyan
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(start, end, end.copy(alpha = .86f))
                ),
                shape
            )
            .border(1.dp, Color.White.copy(alpha = .76f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AermotusPalette.Void,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun MetallicViewport(
    modifier: Modifier = Modifier,
    accent: Color = AermotusPalette.Cyan,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B2731),
                        Color(0xFF06161D),
                        Color(0xFF02080C)
                    )
                ),
                shape
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        AermotusPalette.Silver.copy(alpha = .64f),
                        accent.copy(alpha = .82f),
                        AermotusPalette.Gold.copy(alpha = .30f)
                    )
                ),
                shape
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val corner = minOf(w, h) * .07f
            val c = accent.copy(alpha = .75f)
            drawLine(c, Offset(0f, corner), Offset(0f, 0f), 2f)
            drawLine(c, Offset(0f, 0f), Offset(corner, 0f), 2f)
            drawLine(c, Offset(w - corner, 0f), Offset(w, 0f), 2f)
            drawLine(c, Offset(w, 0f), Offset(w, corner), 2f)
            drawLine(c, Offset(0f, h - corner), Offset(0f, h), 2f)
            drawLine(c, Offset(0f, h), Offset(corner, h), 2f)
            drawLine(c, Offset(w - corner, h), Offset(w, h), 2f)
            drawLine(c, Offset(w, h), Offset(w, h - corner), 2f)
        }
        content()
    }
}
