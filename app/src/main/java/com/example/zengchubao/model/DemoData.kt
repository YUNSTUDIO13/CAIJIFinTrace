package com.example.zengchubao.model

import androidx.compose.ui.graphics.Color

// ── 预置银行 ──

val PRESET_BANK_COLORS = listOf(
    Color.White,  // 占位，从 index=0 开始
    Color(0xFFEF4444),  // 红
    Color(0xFFF97316),  // 橙红
    Color(0xFFF59E0B),  // 橙
    Color(0xFFEAB308),  // 橙黄
    Color(0xFF84CC16),  // 黄
    Color(0xFF22C55E),  // 黄绿
    Color(0xFF10B981),  // 绿
    Color(0xFF14B8A6),  // 蓝绿
    Color(0xFF3B82F6),  // 蓝
    Color(0xFF8B5CF6),  // 蓝紫
    Color(0xFFA855F7),  // 紫
    Color(0xFFEC4899)   // 紫红
)

val PRESET_BANKS = listOf(
    Bank("bank_icbc", "工商银行", isPreset = true, sortOrder = 1, colorHex = "#EF4444"),
    Bank("bank_abc", "农业银行", isPreset = true, sortOrder = 2, colorHex = "#22C55E"),
    Bank("bank_boc", "中国银行", isPreset = true, sortOrder = 3, colorHex = "#F97316"),
    Bank("bank_ccb", "建设银行", isPreset = true, sortOrder = 4, colorHex = "#3B82F6"),
    Bank("bank_bcm", "交通银行", isPreset = true, sortOrder = 5, colorHex = "#8B5CF6"),
    Bank("bank_psbc", "邮储银行", isPreset = true, sortOrder = 6, colorHex = "#10B981"),
    Bank("bank_cmb", "招商银行", isPreset = true, sortOrder = 7, colorHex = "#EAB308")
)

// ── 预置产品模板 ──

val PRESET_PRODUCTS = listOf(
    Product("prod_001", "bank_icbc", "工银3年整存整取", 1080, CalcMethod.ANNUAL_MATCH, ProductType.FIXED_DEPOSIT),
    Product("prod_002", "bank_icbc", "工银1年整存整取", 360, CalcMethod.ANNUAL_MATCH, ProductType.FIXED_DEPOSIT),
    Product("prod_003", "bank_ccb", "建行1年定存", 360, CalcMethod.ACTUAL_DAYS, ProductType.FIXED_DEPOSIT),
    Product("prod_004", "bank_abc", "农行大额存单3年", 1080, CalcMethod.ACTUAL_DAYS, ProductType.FIXED_DEPOSIT),
    Product("prod_005", "bank_bcm", "交行稳利半年", 180, CalcMethod.ACTUAL_DAYS, ProductType.WEALTH_MGMT),
    Product("prod_006", "bank_cmb", "招行月月宝", 30, CalcMethod.ACTUAL_DAYS, ProductType.WEALTH_MGMT)
)
