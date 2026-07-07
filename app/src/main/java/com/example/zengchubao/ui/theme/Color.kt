package com.example.zengchubao.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

// ── 金融蓝主色调 ──
val Blue50 = Color(0xFFEFF6FF)
val Blue100 = Color(0xFFDBEAFE)
val Blue200 = Color(0xFFBFDBFE)
val Blue300 = Color(0xFF93C5FD)
val Blue400 = Color(0xFF60A5FA)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
val Blue700 = Color(0xFF1D4ED8)
val Blue800 = Color(0xFF1E40AF)
val Blue900 = Color(0xFF1E3A8A)

// ── 辅助色 ──
val Emerald500 = Color(0xFF10B981)
val Emerald50 = Color(0xFFECFDF5)
val Emerald700 = Color(0xFF047857)
val Amber500 = Color(0xFFF59E0B)
val Amber50 = Color(0xFFFFFBEB)
val Amber600 = Color(0xFFD97706)
val Red500 = Color(0xFFEF4444)
val Red400 = Color(0xFFF87171)
val Red50 = Color(0xFFFEF2F2)
val Purple500 = Color(0xFF8B5CF6)
val Purple50 = Color(0xFFF5F3FF)

// ── 中性色 ──
val Gray50 = Color(0xFFF9FAFB)
val Gray100 = Color(0xFFF3F4F6)
val Gray200 = Color(0xFFE5E7EB)
val Gray300 = Color(0xFFD1D5DB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray600 = Color(0xFF4B5563)
val Gray700 = Color(0xFF374151)
val Gray800 = Color(0xFF1F2937)
val Gray900 = Color(0xFF111827)

// ── 图表色板 ──
val ChartBlue = Color(0xFF3B82F6)
val ChartGreen = Color(0xFF10B981)
val ChartAmber = Color(0xFFF59E0B)
val ChartPurple = Color(0xFF8B5CF6)
val ChartRed = Color(0xFFEF4444)
val ChartCyan = Color(0xFF06B6D4)
val ChartPink = Color(0xFFEC4899)

val CHART_COLORS = listOf(ChartBlue, ChartGreen, ChartAmber, ChartPurple, ChartRed, ChartCyan, ChartPink)

// ── 银行颜色映射 ──
val BANK_COLORS = mapOf(
    "工商银行" to Color(0xFFCC0000),
    "农业银行" to Color(0xFF009944),
    "中国银行" to Color(0xFFB81C22),
    "建设银行" to Color(0xFF0066CC),
    "交通银行" to Color(0xFF003399),
    "邮储银行" to Color(0xFF009944),
    "招商银行" to Color(0xFFCC0000)
)

fun getBankColor(bankName: String): Color {
    return BANK_COLORS[bankName] ?: Blue500
}

// ── 渐变背景修饰符 ──

/** 金融蓝色渐变背景 */
fun Modifier.financeGradient() = this.then(
    Modifier.drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Blue600, Blue800),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )
    }
)

/** 卡片渐变背景 */
fun Modifier.cardGradient() = this.then(
    Modifier.drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Blue500, Blue700),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height * 1.5f)
            )
        )
    }
)

/** Liquid Glass 颜色常量（与SoloChef一致） */
val LiquidGlassStart = Color(0xCCFFFFFF)
val LiquidGlassEnd   = Color(0xA3FFFFFF)
