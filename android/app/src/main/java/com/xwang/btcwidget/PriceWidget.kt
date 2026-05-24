package com.xwang.btcwidget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.color.ColorProvider
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background

class PriceWidget : GlanceAppWidget() {

    companion object {
        // 颜色常量
        private val PRIMARY_TEXT = ColorProvider(day = Color.Black, night = Color.White)
        private val SECONDARY_TEXT = ColorProvider(day = Color(0xFF666666), night = Color(0xFF888888))
        private val BG = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1A1A2E))
        private val PROFIT_COLOR = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF4CAF50))
        private val LOSS_COLOR = ColorProvider(day = Color(0xFFC62828), night = Color(0xFFF44336))
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val symbol = PriceRepository.getSelectedSymbol(context)
        val icon = PriceRepository.iconFor(symbol)
        val price = PriceRepository.getCurrentPrice(context)
        val history = PriceRepository.getPriceHistory(context)
        val purchasePrice = PriceRepository.getPurchasePrice(context, symbol)
        val showPnL = PriceRepository.getShowPnL(context)

        val priceStr = price?.let { PriceRepository.formatPrice(it) } ?: "Loading..."
        val chartBitmap = ChartBitmap.create(context, history)
        val change24h = PriceRepository.get24hChange(context)
        
        // 计算 P&L
        val (pnlStr, isProfit) = buildPnlString(
            showPnL = showPnL,
            price = price,
            purchasePrice = purchasePrice,
            change24h = change24h
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(BG)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Header: icon + price
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$icon $symbol",
                        style = TextStyle(
                            color = PRIMARY_TEXT,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = priceStr,
                        style = TextStyle(
                            color = PRIMARY_TEXT,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // P&L line
                if (pnlStr.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = pnlStr,
                        style = TextStyle(
                            color = if (isProfit) PROFIT_COLOR else LOSS_COLOR,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Chart
                Image(
                    provider = ImageProvider(bitmap = chartBitmap),
                    contentDescription = "Price chart",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .cornerRadius(8.dp)
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Bottom buttons
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "🔄 Refresh",
                        style = TextStyle(
                            color = SECONDARY_TEXT,
                            fontSize = 12.sp
                        ),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>())
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "⚙ Settings",
                        style = TextStyle(
                            color = SECONDARY_TEXT,
                            fontSize = 12.sp
                        ),
                        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                    )
                }
            }
        }
    }

    /**
     * 计算 P&L 显示字符串
     * @return Pair<显示文本, 是否盈利>
     */
    private fun buildPnlString(
        showPnL: Boolean,
        price: Double?,
        purchasePrice: Double?,
        change24h: Double
    ): Pair<String, Boolean> {
        if (!showPnL || price == null) return "" to false
        
        return if (purchasePrice != null && purchasePrice > 0) {
            val change = ((price - purchasePrice) / purchasePrice) * 100
            val isProfit = change >= 0
            val arrow = if (isProfit) "▲" else "▼"
            "$arrow ${"%.2f".format(change)}%  Cost: ${PriceRepository.formatPrice(purchasePrice)}" to isProfit
        } else {
            // 如果没有设置买入价，默认显示 24h 涨跌幅
            val isProfit = change24h >= 0
            val arrow = if (isProfit) "▲" else "▼"
            "$arrow ${"%.2f".format(change24h)}% (24h)" to isProfit
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val symbol = PriceRepository.getSelectedSymbol(context)
            val (price, change24h) = PriceRepository.fetchPriceData(symbol)
            PriceRepository.savePrice(context, price, change24h)
        } catch (e: Exception) {
            Log.e("PriceWidget", "Refresh failed", e)
        }
        PriceWidget().updateAll(context)
    }
}
