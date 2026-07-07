package com.example.zengchubao.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.zengchubao.model.*
import com.example.zengchubao.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

// ── 格式化 ──
private val CN = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
private val CN_INT = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 0; maximumFractionDigits = 0 }
private fun fmtI(v: Double) = "¥${CN_INT.format(v)}"
private fun fmtD(v: Double) = CN.format(v)

// ── 首页（精确对标参考图） ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    deposits: List<Deposit>,
    onNewDeposit: () -> Unit,
    onDepositClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val holdingDeposits = remember(deposits) {
        deposits.filter { it.status == DepositStatus.HOLDING || it.status == DepositStatus.MATURED }
    }

    val bankNames = remember(holdingDeposits) { holdingDeposits.map { it.bankName }.distinct() }
    // 银行多选（空集合 = 全部银行）
    var selectedBanks by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 时间筛选：全部 / 本月到期 / 本年到期
    var timeFilter by remember { mutableIntStateOf(0) } // 0=全部, 1=本月, 2=本年

    // 银行筛选后的列表（未加时间筛选）
    val bankFiltered = remember(holdingDeposits, selectedBanks) {
        if (selectedBanks.isEmpty()) holdingDeposits
        else holdingDeposits.filter { it.bankName in selectedBanks }
    }

    val filteredList = remember(bankFiltered, timeFilter) {
        val today = todayString()
        val currentMonth = today.take(7)
        val currentYear = today.take(4)
        val timeFiltered = when (timeFilter) {
            1 -> bankFiltered.filter { it.endDate.startsWith(currentMonth) }
            2 -> bankFiltered.filter { it.endDate.startsWith(currentYear) }
            else -> bankFiltered
        }
        timeFiltered.sortedBy { it.endDate }
    }

    val assetBalance: Double = remember(bankFiltered) { calculateAssetBalance(bankFiltered) }
    val weightedRate: Double = remember(bankFiltered) { calculateWeightedRate(bankFiltered) }
    val annualExpectedYield: Double = remember(bankFiltered) { calculateAnnualExpectedYield(bankFiltered) }
    val holdingTotalYield: Double = remember(bankFiltered) {
        bankFiltered.sumOf { calculateAccruedInterest(it.principal, it.annualRate, it.startDate, it.termDays) }
    }

    // 页面可见/切回前台时自动刷新（倒计时、归档状态每日更新）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("增储宝", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text("存单全生命周期管理", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.padding(top = 8.dp).size(36.dp)
                    .shadow(3.dp, CircleShape, ambientColor = Color(0x1A000000))
                    .clip(CircleShape).background(Color.White)
                    .border(1.5.dp, Color(0xFF2563EB), CircleShape)
                    .clickable { onNewDeposit() }) {
                Icon(Icons.Filled.Add, "新建", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
            // ── Hero 卡片（对标参考图：紧凑，大金额字） ──
            item {
                Box(modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)).heroGradient()
                    .padding(top = 11.dp, bottom = 13.dp, start = 18.dp, end = 18.dp)) {
                    Column {
                        Text("全部持有", fontSize = 9.sp, fontWeight = FontWeight.W500,
                            color = Color(0xFFBFDBFE))
                        Spacer(Modifier.height(4.dp))
                        Text(fmtI(assetBalance), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, lineHeight = 34.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            HeroMetric("% 综合年化", "${"%.2f".format(weightedRate)}%", Modifier.weight(1f))
                            HeroMetric("今年预估收益", fmtD(annualExpectedYield), Modifier.weight(1f))
                            HeroMetric("持有中累计收益", fmtI(holdingTotalYield), Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── 银行筛选 Chips（"全部银行"单选，其他银行多选） ──
            item {
                Row(modifier = Modifier.padding(horizontal = 18.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PillChip("全部银行", selectedBanks.isEmpty()) { selectedBanks = emptySet() }
                    bankNames.forEach { bank ->
                        val sel = bank in selectedBanks
                        PillChip(bank, sel) {
                            selectedBanks = if (sel) selectedBanks - bank else selectedBanks + bank
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(9.dp)) }

            // ── 统计小卡片（与银行筛选联动） ──
            item {
                val today = todayString()
                val currentMonth = today.take(7)
                val currentYear = today.take(4)
                val monthCount = remember(bankFiltered) {
                    bankFiltered.count { it.endDate.startsWith(currentMonth) }
                }
                val yearCount = remember(bankFiltered) {
                    bankFiltered.count { it.endDate.startsWith(currentYear) }
                }
                Row(modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatMiniCard("全部", "${bankFiltered.size} 笔",
                        selected = timeFilter == 0, modifier = Modifier.weight(1f),
                        onClick = { timeFilter = 0 })
                    StatMiniCard("本月到期", "$monthCount 笔",
                        selected = timeFilter == 1, modifier = Modifier.weight(1f),
                        textColor = Color(0xFFF59E0B), onClick = { timeFilter = 1 })
                    StatMiniCard("本年到期", "$yearCount 笔",
                        selected = timeFilter == 2, modifier = Modifier.weight(1f),
                        textColor = Color(0xFF10B981), onClick = { timeFilter = 2 })
                }
            }

            item { Spacer(Modifier.height(10.dp)) }

            // ── 持有列表标题 ──
            item {
                Text("持有列表", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), modifier = Modifier.padding(horizontal = 18.dp))
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── 存单列表 / 空状态（对标参考图卡片结构） ──
            if (filteredList.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AccountBalance, null, Modifier.size(46.dp), tint = Color(0xFFCBD5E1))
                        Spacer(Modifier.height(10.dp))
                        Text("还没有存单记录", fontSize = 14.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { deposit ->
                    RefDepositCard(deposit = deposit, onClick = { onDepositClick(deposit.id) },
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 7.dp))
                }
            }
        }
    }
}

// ── Hero 指标（紧凑间距） ──

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.W500,
            color = Color(0xFFBFDBFE).copy(alpha = 0.75f), lineHeight = 9.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = Color.White, lineHeight = 14.sp)
    }
}

// ── 药丸 Chip（对标参考图：圆角胶囊+边框） ──

@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFEFF6FF) else Color.White,
        border = BorderStroke(if (selected) 1.2.dp else 0.6.dp,
            if (selected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
        shadowElevation = 0.dp) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.W600,
            color = if (selected) Color(0xFF2563EB) else Color(0xFF64748B),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp))
    }
}

// ── 统计小卡片（可选中+点击） ──

@Composable
private fun StatMiniCard(title: String, value: String, selected: Boolean = false, textColor: Color = Color(0xFF1E293B), modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val borderColor = if (selected) Color(0xFF2563EB) else Color.Transparent
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.2.dp, if (borderColor != Color.Transparent) borderColor else Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        onClick = onClick ?: {}) {
        Column(Modifier.padding(vertical = 5.dp, horizontal = 8.dp)) {
            Text(title, fontSize = 8.sp, fontWeight = FontWeight.W500, color = Color(0xFF94A3B8),
                lineHeight = 10.sp)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor,
                lineHeight = 18.sp)
        }
    }
}

// ═════════════════════════ 存单卡片（v1.13 精确对标用户截图反馈） ═════════════════════════
//
// 卡片三层结构：
//   L1: 银行名(大圆角灰badge) + 起始日期 + [即将到期](条件性大圆角badge)  |  CNY(右)
//   L2: 存单名称(粗体左)         | 本金(粗体右，紧靠右边缘)
//   L3: 年利率 X.XX%(灰左)      | 到期 YYYY-MM-DD(右，颜色按状态) + N天后到期
//
// 间距规范：
//   - MiniBadge圆角: 大圆角(RoundedCornerShape)
//   - 本金必须右对齐（Column+Alignment.End）
// ════════════════════════════════════════════════════════════════════════

@Composable
fun RefDepositCard(deposit: Deposit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val remainingDays = daysUntilMaturity(deposit.endDate)
    val isExpired = deposit.status == DepositStatus.MATURED || remainingDays < 0
    val isExpiringSoon = remainingDays in 1..30

    // 到期颜色
    val dateColor = when {
        isExpired -> Color(0xFFF87171)
        isExpiringSoon -> Color(0xFFF59E0B)
        else -> Color(0xFF94A3B8)
    }

    Card(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)) {

        Column(
            Modifier.fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
        ) {

            // ══ L1: 银行名(badge,大圆角) + 起始日 + [即将到期](条件,badge) | CNY(右) ══
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    // 银行名 badge — 大圆角
                    MiniBadge(deposit.bankName, bg = Color(0xFFF1F5F9), fg = Color(0xFF64748B))
                    // 存入日期
                    Text("存入 ${deposit.startDate}", fontSize = 9.sp,
                        color = Color(0xFF94A3B8))
                }
                Text("CNY", fontSize = 9.sp, fontWeight = FontWeight.W600, color = Color(0xFFCBD5E1))
            }

            // ══ L2: 存单名称(左,粗体) | 本金(粗体,右对齐) ══
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(deposit.productName, fontSize = 14.sp, fontWeight = FontWeight.W600,
                    color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text(CN_INT.format(deposit.principal), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            }

            // ══ L3: 年利率(左) + 到期日(中) + xxx天后到期(右对齐) ══
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${"%.2f".format(deposit.annualRate)}%",
                        fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text("到期 ${deposit.endDate}", fontSize = 10.sp,
                        fontWeight = FontWeight.W500, color = dateColor)
                }
                val countdownText = when {
                    isExpired -> "已过期${-remainingDays}天"
                    isExpiringSoon -> "${remainingDays}天后到期"
                    remainingDays == 0 -> "今日到期"
                    else -> "${remainingDays}天后到期"
                }
                Text(countdownText, fontSize = 9.sp, color = dateColor)
            }
        }
    }
}

// ── 迷你 Badge（银行名/状态用，大圆角） ──

@Composable
private fun MiniBadge(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.W500, color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
            lineHeight = 11.sp)
    }
}

// ── Hero 渐变 + 光晕气泡 ──

fun Modifier.heroGradient() = this.then(
    Modifier.drawBehind {
        drawRect(brush = Brush.linearGradient(
            colors = listOf(Color(0xFF1A2F7A), Color(0xFF2E58DB), Color(0xFF4338CA)),
            start = Offset(0f, this.size.height), end = Offset(this.size.width, 0f)))
        drawCircle(brush = Brush.radialGradient(
            colors = listOf(Color(0x4D8BAFFF), Color(0x008BAFFF)),
            center = Offset(this.size.width * 0.78f, this.size.height * 0.22f),
            radius = this.size.width * 0.45f),
            radius = this.size.width * 0.45f,
            center = Offset(this.size.width * 0.78f, this.size.height * 0.22f))
        drawCircle(brush = Brush.radialGradient(
            colors = listOf(Color(0x3DA78BFA), Color(0x00A78BFA)),
            center = Offset(this.size.width * 0.85f, this.size.height * 0.72f),
            radius = this.size.width * 0.35f),
            radius = this.size.width * 0.35f,
            center = Offset(this.size.width * 0.85f, this.size.height * 0.72f))
    })
