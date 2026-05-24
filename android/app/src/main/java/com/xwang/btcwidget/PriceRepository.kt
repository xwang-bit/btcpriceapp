package com.xwang.btcwidget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SupportedCoin(
    val symbol: String,
    val name: String,
    val icon: String
)

object PriceRepository {

    val supportedCoins = listOf(
        SupportedCoin("BTC", "Bitcoin", "₿"),
        SupportedCoin("ETH", "Ethereum", "Ξ"),
        SupportedCoin("SOL", "Solana", "◎"),
        SupportedCoin("BNB", "BNB", "◆"),
        SupportedCoin("XRP", "XRP", "✕"),
        SupportedCoin("DOGE", "Dogecoin", "Ð"),
        SupportedCoin("ADA", "Cardano", "₳"),
        SupportedCoin("AVAX", "Avalanche", "▲"),
        SupportedCoin("DOT", "Polkadot", "●"),
        SupportedCoin("MATIC", "Polygon", "⬡"),
        SupportedCoin("LINK", "Chainlink", "⬡"),
        SupportedCoin("UNI", "Uniswap", "🦄"),
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("btc_widget", Context.MODE_PRIVATE)

    suspend fun fetchPriceData(symbol: String): Pair<Double, Double> = withContext(Dispatchers.IO) {
        val url = URL("https://data-api.binance.vision/api/v3/ticker/24hr?symbol=${symbol}USDT")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val price = json.getString("lastPrice").toDouble()
            val change = json.getString("priceChangePercent").toDouble()
            Pair(price, change)
        } finally {
            conn.disconnect()
        }
    }

    fun getSelectedSymbol(context: Context): String =
        prefs(context).getString("selected_symbol", "BTC") ?: "BTC"

    fun setSelectedSymbol(context: Context, symbol: String) {
        prefs(context).edit()
            .putString("selected_symbol", symbol)
            .remove("price_history")
            .apply()
    }

    fun getPurchasePrice(context: Context, symbol: String): Double? {
        val price = prefs(context).getFloat("purchase_$symbol", 0f)
        return if (price > 0f) price.toDouble() else null
    }

    fun setPurchasePrice(context: Context, symbol: String, price: Double) {
        prefs(context).edit().putFloat("purchase_$symbol", price.toFloat()).apply()
    }

    fun getShowPnL(context: Context): Boolean =
        prefs(context).getBoolean("show_pnl", true)

    fun setShowPnL(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean("show_pnl", show).apply()
    }

    fun getRefreshInterval(context: Context): Long =
        prefs(context).getLong("refresh_interval", 15)

    fun setRefreshInterval(context: Context, minutes: Long) {
        prefs(context).edit().putLong("refresh_interval", minutes).apply()
    }

    fun getNotificationSound(context: Context): Boolean =
        prefs(context).getBoolean("notification_sound", true)

    fun setNotificationSound(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("notification_sound", enabled).apply()
    }

    fun getAlertThreshold(context: Context, symbol: String): Double? {
        val price = prefs(context).getFloat("alert_$symbol", 0f)
        return if (price > 0f) price.toDouble() else null
    }

    fun setAlertThreshold(context: Context, symbol: String, price: Double) {
        prefs(context).edit().putFloat("alert_$symbol", price.toFloat()).apply()
    }

    fun savePrice(context: Context, price: Double, change24h: Double = 0.0) {
        val editor = prefs(context).edit()
            .putFloat("current_price", price.toFloat())
        if (change24h != 0.0) {
            editor.putFloat("change_24h", change24h.toFloat())
        }
        editor.apply()
        appendPriceHistory(context, price)
    }

    fun getCurrentPrice(context: Context): Double? {
        val price = prefs(context).getFloat("current_price", 0f)
        return if (price > 0f) price.toDouble() else null
    }

    fun get24hChange(context: Context): Double {
        return prefs(context).getFloat("change_24h", 0f).toDouble()
    }

    private fun appendPriceHistory(context: Context, price: Double) {
        val p = prefs(context)
        val history = getPriceHistory(context).toMutableList()
        history.add(price)
        if (history.size > 60) history.removeAt(0)
        val arr = JSONArray()
        history.forEach { arr.put(it) }
        p.edit().putString("price_history", arr.toString()).apply()
    }

    fun getPriceHistory(context: Context): List<Double> {
        val str = prefs(context).getString("price_history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            (0 until arr.length()).map { arr.getDouble(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun iconFor(symbol: String): String =
        supportedCoins.firstOrNull { it.symbol == symbol }?.icon ?: "●"

    fun formatPrice(price: Double): String {
        return if (price >= 1000) {
            "$%,.2f".format(price)
        } else if (price >= 1) {
            "$%.2f".format(price)
        } else {
            "$%.4f".format(price)
        }
    }

    fun formatShortPrice(price: Double): String {
        return if (price >= 1000) {
            val k = price / 1000
            "$%.1fK".format(k)
        } else {
            formatPrice(price)
        }
    }

    fun getThemeMode(context: Context): String =
        prefs(context).getString("theme_mode", "system") ?: "system"

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString("theme_mode", mode).apply()
    }
}
