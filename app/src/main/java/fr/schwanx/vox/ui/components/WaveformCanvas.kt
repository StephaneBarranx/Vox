package fr.schwanx.vox.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformCanvas(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth().height(120.dp),
    barColor: Color = MaterialTheme.colorScheme.primary,
    maxBars: Int = 40
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val displayAmplitudes = if (amplitudes.size > maxBars) {
            amplitudes.takeLast(maxBars)
        } else {
            amplitudes
        }

        if (displayAmplitudes.isEmpty()) {
            val defaultBarWidth = 4.dp.toPx()
            val space = 6.dp.toPx()
            val count = (width / (defaultBarWidth + space)).toInt()
            for (i in 0 until count) {
                val x = i * (defaultBarWidth + space) + space / 2f
                val h = 6.dp.toPx()
                drawRoundRect(
                    color = barColor.copy(alpha = 0.3f),
                    topLeft = Offset(x, centerY - h / 2f),
                    size = Size(defaultBarWidth, h),
                    cornerRadius = CornerRadius(defaultBarWidth / 2f, defaultBarWidth / 2f)
                )
            }
            return@Canvas
        }

        val barWidth = 4.dp.toPx()
        val spacing = 6.dp.toPx()
        val totalWidthPerBar = barWidth + spacing

        displayAmplitudes.forEachIndexed { index, amp ->
            val x = index * totalWidthPerBar + spacing
            val minHeight = 6.dp.toPx()
            val maxBarHeight = height * 0.85f
            val h = (amp * maxBarHeight).coerceAtLeast(minHeight)

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
