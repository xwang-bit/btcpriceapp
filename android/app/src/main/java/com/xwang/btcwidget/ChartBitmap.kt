package com.xwang.btcwidget

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat

object ChartBitmap {

    fun create(
        context: Context,
        prices: List<Double>,
        width: Int = 400, // 降低分辨率以节省内存
        height: Int = 150
    ): Bitmap {
        // 使用 ARGB_8888 保持质量，但尺寸更小。
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 背景设为透明，这样图表可以适配 Widget 的白天/黑夜背景
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (prices.size < 2) {
            val textPaint = Paint().apply {
                color = Color.GRAY
                textSize = 24f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Waiting for data...", width / 2f, height / 2f, textPaint)
            return bitmap
        }

        val paddingVertical = 20f
        val chartLeft = 0f  // 彻底对齐左边
        val chartRight = width.toFloat() // 彻底对齐右边
        val chartTop = paddingVertical
        val chartBottom = height - paddingVertical
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val minPrice = prices.min()
        val maxPrice = prices.max()
        val priceRange = maxPrice - minPrice
        val safeRange = if (priceRange == 0.0) maxPrice * 0.01 else priceRange

        // Price change direction
        val isUp = prices.last() >= prices.first()
        val lineColor = if (isUp) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        val gradientColor = if (isUp) Color.parseColor("#204CAF50") else Color.parseColor("#20F44336")

        // Create path for line
        val path = Path()
        val points = prices.mapIndexed { index, price ->
            val x = chartLeft + (index.toFloat() / (prices.size - 1)) * chartWidth
            val y = chartBottom - ((price - minPrice) / safeRange).toFloat() * chartHeight
            PointF(x, y)
        }

        path.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val midX = (prev.x + curr.x) / 2
            path.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
        }

        // Draw gradient fill
        val fillPath = Path(path)
        fillPath.lineTo(points.last().x, chartBottom)
        fillPath.lineTo(points.first().x, chartBottom)
        fillPath.close()

        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, chartTop, 0f, chartBottom,
                gradientColor, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawPath(fillPath, gradientPaint)

        // Draw line
        val linePaint = Paint().apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 4f // 稍微加粗线条更协调
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, linePaint)

        // Draw current price dot
        val lastPoint = points.last()
        val dotPaint = Paint().apply {
            color = lineColor
            isAntiAlias = true
        }
        canvas.drawCircle(lastPoint.x, lastPoint.y, 6f, dotPaint)
        
        // Draw min/max labels (移到右侧对齐，避免干扰左侧视觉)
        val labelPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 16f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "Max: " + PriceRepository.formatShortPrice(maxPrice),
            width - 4f, chartTop - 4f, labelPaint
        )
        canvas.drawText(
            "Min: " + PriceRepository.formatShortPrice(minPrice),
            width - 4f, height - 4f, labelPaint
        )

        return bitmap
    }
}
