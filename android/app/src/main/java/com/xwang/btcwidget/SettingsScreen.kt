package com.xwang.btcwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onThemeChanged: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    var selectedSymbol by remember { mutableStateOf(PriceRepository.getSelectedSymbol(context)) }
    var expanded by remember { mutableStateOf(false) }
    var purchasePriceText by remember {
        val existing = PriceRepository.getPurchasePrice(context, selectedSymbol)
        mutableStateOf(existing?.let { "%.2f".format(it) } ?: "")
    }
    var showPnL by remember { mutableStateOf(PriceRepository.getShowPnL(context)) }
    var currentPrice by remember { mutableStateOf(PriceRepository.getCurrentPrice(context)) }
    var change24h by remember { mutableStateOf(PriceRepository.get24hChange(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    var alertPriceText by remember {
        val existing = PriceRepository.getAlertThreshold(context, selectedSymbol)
        mutableStateOf(existing?.let { "%.2f".format(it) } ?: "")
    }
    var refreshInterval by remember { mutableStateOf(PriceRepository.getRefreshInterval(context)) }
    var notificationSound by remember { mutableStateOf(PriceRepository.getNotificationSound(context)) }
    var themeMode by remember { mutableStateOf(PriceRepository.getThemeMode(context)) }

    val scrollState = rememberScrollState()

    // 动态颜色
    val cardBg = if (isDark) Color(0xFF16213E) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val textSecondary = if (isDark) Color(0xFF888888) else Color(0xFF666666)
    val textTertiary = if (isDark) Color(0xFF555555) else Color(0xFF999999)
    val accent = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    val accentBlue = if (isDark) Color(0xFF2196F3) else Color(0xFF1565C0)
    val dividerColor = if (isDark) Color(0xFF2A2A4A) else Color(0xFFE0E0E0)
    val inputBorder = if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC)
    val profitColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    val lossColor = if (isDark) Color(0xFFF44336) else Color(0xFFC62828)

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            statusMessage = "通知权限被拒绝，价格提醒将无法显示"
        }
    }

    // Check permission on start
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Refresh current price on load
    LaunchedEffect(selectedSymbol) {
        isLoading = true
        try {
            val (price, c24h) = PriceRepository.fetchPriceData(selectedSymbol)
            PriceRepository.savePrice(context, price, c24h)
            currentPrice = price
            change24h = c24h
        } catch (e: Exception) {
            statusMessage = "获取价格失败"
        }
        isLoading = false
        val existingPurchase = PriceRepository.getPurchasePrice(context, selectedSymbol)
        purchasePriceText = existingPurchase?.let { "%.2f".format(it) } ?: ""

        val existingAlert = PriceRepository.getAlertThreshold(context, selectedSymbol)
        alertPriceText = existingAlert?.let { "%.2f".format(it) } ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══════════════════════════════════════
        // 标题
        // ═══════════════════════════════════════
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "BTC价格监控系统",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                text = "实时追踪加密货币行情",
                fontSize = 13.sp,
                color = textSecondary
            )
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // ═══════════════════════════════════════
        // 当前价格卡片
        // ═══════════════════════════════════════
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                val coin = PriceRepository.supportedCoins.firstOrNull { it.symbol == selectedSymbol }
                Text(
                    text = "${coin?.icon ?: ""} ${coin?.name ?: selectedSymbol}",
                    fontSize = 14.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentPrice?.let { PriceRepository.formatPrice(it) } ?: "N/A",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        // 24h 涨跌幅
                        val isUp = change24h >= 0
                        Text(
                            text = "${if (isUp) "▲" else "▼"} ${"%.2f".format(change24h)}% (24h)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isUp) profitColor else lossColor
                        )
                    }
                    // 刷新按钮
                    FilledIconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val (price, c24h) = PriceRepository.fetchPriceData(selectedSymbol)
                                    PriceRepository.savePrice(context, price, c24h)
                                    currentPrice = price
                                    change24h = c24h
                                    PriceWidget().updateAll(context)
                                    statusMessage = "价格已刷新"
                                } catch (e: Exception) {
                                    statusMessage = "刷新失败"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // 主题设置
        // ═══════════════════════════════════════
        SectionHeader(text = "外观主题", color = textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeChip("跟随系统", "system", themeMode) { newMode ->
                themeMode = newMode
                PriceRepository.setThemeMode(context, newMode)
                onThemeChanged(newMode)
            }
            ThemeChip("浅色", "light", themeMode) { newMode ->
                themeMode = newMode
                PriceRepository.setThemeMode(context, newMode)
                onThemeChanged(newMode)
            }
            ThemeChip("深色", "dark", themeMode) { newMode ->
                themeMode = newMode
                PriceRepository.setThemeMode(context, newMode)
                onThemeChanged(newMode)
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // ═══════════════════════════════════════
        // 币种选择
        // ═══════════════════════════════════════
        SectionHeader(text = "加密货币", color = textSecondary)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "${PriceRepository.iconFor(selectedSymbol)} $selectedSymbol",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = inputBorder,
                    focusedTrailingIconColor = accent,
                    unfocusedTrailingIconColor = textSecondary,
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(cardBg)
            ) {
                PriceRepository.supportedCoins.forEach { coin ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${coin.icon}  ${coin.symbol}  ·  ${coin.name}",
                                color = textPrimary
                            )
                        },
                        onClick = {
                            selectedSymbol = coin.symbol
                            PriceRepository.setSelectedSymbol(context, coin.symbol)
                            expanded = false
                            scope.launch {
                                PriceWidget().updateAll(context)
                                try {
                                    val (price, c24h) = PriceRepository.fetchPriceData(coin.symbol)
                                    PriceRepository.savePrice(context, price, c24h)
                                    currentPrice = price
                                    change24h = c24h
                                    PriceWidget().updateAll(context)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // ═══════════════════════════════════════
        // 买入价格
        // ═══════════════════════════════════════
        SectionHeader(text = "买入价格 (USD)", color = textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = purchasePriceText,
                onValueChange = { purchasePriceText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("例如: 65000", color = textTertiary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = inputBorder,
                )
            )
            Button(
                onClick = {
                    if (purchasePriceText.isEmpty()) {
                        PriceRepository.setPurchasePrice(context, selectedSymbol, 0.0)
                        statusMessage = "已清空买入价，显示24h涨跌幅"
                        scope.launch { PriceWidget().updateAll(context) }
                    } else {
                        val price = purchasePriceText.toDoubleOrNull()
                        if (price != null && price > 0) {
                            PriceRepository.setPurchasePrice(context, selectedSymbol, price)
                            statusMessage = "买入价已保存!"
                            scope.launch { PriceWidget().updateAll(context) }
                        } else {
                            statusMessage = "价格格式无效"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text("保存", fontWeight = FontWeight.Medium)
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // ═══════════════════════════════════════
        // 价格提醒
        // ═══════════════════════════════════════
        SectionHeader(text = "价格提醒 (USD)", color = textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = alertPriceText,
                onValueChange = { alertPriceText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("例如: 75000", color = textTertiary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = accentBlue,
                    unfocusedBorderColor = inputBorder,
                )
            )
            Button(
                onClick = {
                    val price = alertPriceText.toDoubleOrNull()
                    if (price != null && price > 0) {
                        PriceRepository.setAlertThreshold(context, selectedSymbol, price)
                        statusMessage = "价格提醒已设置!"
                    } else {
                        statusMessage = "提醒价格格式无效"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text("设置", fontWeight = FontWeight.Medium)
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // ═══════════════════════════════════════
        // 自动刷新间隔
        // ═══════════════════════════════════════
        SectionHeader(text = "自动刷新间隔 (1-60 分钟)", color = textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = refreshInterval.toFloat(),
                onValueChange = { refreshInterval = it.toLong() },
                valueRange = 1f..60f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent
                )
            )
            Text(
                text = "${refreshInterval}分钟",
                color = textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(65.dp),
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = {
                    PriceRepository.setRefreshInterval(context, refreshInterval)
                    val request = OneTimeWorkRequestBuilder<PriceWorker>()
                        .setInitialDelay(refreshInterval, TimeUnit.MINUTES)
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "price_refresh",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
                    statusMessage = "刷新间隔已设为 $refreshInterval 分钟"
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存", fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // ═══════════════════════════════════════
        // 开关设置
        // ═══════════════════════════════════════
        SettingToggle(
            title = "提示音",
            description = "价格变动时播放提示音",
            checked = notificationSound,
            accentColor = accent,
            textColor = textPrimary,
            descColor = textSecondary,
            onCheckedChange = {
                notificationSound = it
                PriceRepository.setNotificationSound(context, it)
            }
        )

        SettingToggle(
            title = "显示盈亏",
            description = "在小组件中显示 P&L 信息",
            checked = showPnL,
            accentColor = accent,
            textColor = textPrimary,
            descColor = textSecondary,
            onCheckedChange = {
                showPnL = it
                PriceRepository.setShowPnL(context, it)
                scope.launch { PriceWidget().updateAll(context) }
            }
        )

        // ═══════════════════════════════════════
        // 状态消息
        // ═══════════════════════════════════════
        if (statusMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1B3A1B) else Color(0xFFE8F5E9)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = statusMessage,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ═══════════════════════════════════════
        // 底部版本信息
        // ═══════════════════════════════════════
        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "v.01  Power By Xwang",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "数据来源: Binance",
                fontSize = 11.sp,
                color = textTertiary
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun ThemeChip(
    label: String,
    value: String,
    current: String,
    onSelect: (String) -> Unit
) {
    val isSelected = current == value
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    val cardBg = if (isDark) Color(0xFF16213E) else Color.White
    val textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(value) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accent else cardBg,
        border = if (!isSelected) {
            BorderStroke(1.dp, if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC))
        } else null,
        tonalElevation = if (isSelected) 0.dp else 1.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = if (isSelected) Color.White else textPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    accentColor: Color,
    textColor: Color,
    descColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text(text = description, fontSize = 12.sp, color = descColor)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accentColor,
                checkedThumbColor = Color.White
            )
        )
    }
}
