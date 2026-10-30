package it.mattia.libreria.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BookIcon: ImageVector by lazy {
    ImageVector.Builder("book", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(8f, 21f)
            quadTo(6f, 21f, 6f, 19f); close()
            moveTo(6f, 19f); quadTo(6f, 17f, 8f, 17f); lineTo(19f, 17f)
        }
    }.build()
}

val BarsIcon: ImageVector by lazy {
    ImageVector.Builder("bars", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 20f); lineTo(6f, 11f)
            moveTo(12f, 20f); lineTo(12f, 4f)
            moveTo(18f, 20f); lineTo(18f, 14f)
        }
    }.build()
}
