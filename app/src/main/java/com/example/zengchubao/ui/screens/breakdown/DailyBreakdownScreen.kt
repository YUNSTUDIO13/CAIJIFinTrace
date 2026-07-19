package com.example.zengchubao.ui.screens.breakdown

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zengchubao.model.*
import com.example.zengchubao.ui.screens.home.RateIcon
import com.example.zengchubao.ui.screens.home.WalletIcon
import java.text.NumberFormat
import java.util.Locale

private val CN_2 = NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }

/** 单日收益：日期 → 当日有收益的存单列表（每条带收益金额） */
data class DailyIncomeEntry(val date: String, val deposits: List<Pair<Deposit, Double>>) {
    val total: Double get() = deposits.sumOf { it.second }
    fun hasIncome(): Boolean = deposits.isNotEmpty()
}

/** 月度数据：年-月 → 当月每日收益 */
data class MonthlyIncome(val year: Int, val month: Int, val byDate: Map<String, DailyIncomeEntry>) {
    val monthTotal: Double get() = byDate.values.sumOf { it.total }
    val activeDays: Int get() = byDate.values.count { it.hasIncome() }
}

/** 每日单存单收益 */
fun dailyIncomeForDepositOnDate(dep: Deposit, date: String): Double {
    if (date < dep.startDate || date > dep.endDate) return 0.0
    val basis = yearBasis(dep.calcMethod).toDouble()
    return dep.principal * (dep.annualRate / 100.0) / basis
}

/** 构建某年某月每天的收益明细（仅含持有中存单） */
fun buildMonthlyIncome(holding: List<Deposit>, year: Int, month: Int): MonthlyIncome {
    val daysInMonth = daysInMonth(year, month)
    val byDate = mutableMapOf<String, DailyIncomeEntry>()
    for (d in 1..daysInMonth) {
        val date = "%04d-%02d-%02d".format(year, month, d)
        val entries = holding.mapNotNull { dep ->
            val income = dailyIncomeForDepositOnDate(dep, date)
            if (income > 0.0) dep to income else null
        }
        byDate[date] = DailyIncomeEntry(date, entries)
    }
    return MonthlyIncome(year, month, byDate)
}

/** 指定年-月有多少天 */
fun daysInMonth(year: Int, month: Int): Int {
    val nextMonth = if (month == 12) Pair(year + 1, 1) else Pair(year, month + 1)
    val firstOfNext = "%04d-%02d-01".format(nextMonth.first, nextMonth.second)
    val lastOfThis = addDays(firstOfNext, -1)
    return lastOfThis.substring(8, 10).toInt()
}

/** 某年某月1日是星期几 (1=周一 ... 7=周日) → 返回 [0..6] 偏移（日一二三四五六） */
fun firstDayOfMonthOffset(year: Int, month: Int): Int {
    val ymd = "%04d-%02d-01".format(year, month)
    val parts = ymd.split("-").map { it.toInt() }
    val cal = java.util.GregorianCalendar(parts[0], parts[1] - 1, parts[2])
    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
    return if (dow == java.util.Calendar.SUNDAY) 0 else dow - 1
}

/** 提取所有有收益的年月（按时间倒序） */
fun collectActiveMonths(holding: List<Deposit>): List<Pair<Int, Int>> {
    if (holding.isEmpty()) return emptyList()
    val months = mutableSetOf<Pair<Int, Int>>()
    val today = todayString()
    for (dep in holding) {
        val startY = dep.startDate.substring(0, 4).toInt()
        val startM = dep.startDate.substring(5, 7).toInt()
        val endY = (if (dep.endDate < today) dep.endDate else today).substring(0, 4).toInt()
        val endM = (if (dep.endDate < today) dep.endDate else today).substring(5, 7).toInt()
        var y = startY
        var m = startM
        while (y < endY || (y == endY && m <= endM)) {
            months.add(y to m)
            m++
            if (m > 12) { m = 1; y++ }
        }
    }
    return months.toList().sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBreakdownScreen(
    deposits: List<Deposit>,
    onBack: () -> Unit
) {
    val holding = remember(deposits) {
        deposits.filter { it.status == DepositStatus.HOLDING }
    }

    val today = todayString()
    val todayYear = today.substring(0, 4).toInt()
    val todayMonth = today.substring(5, 7).toInt()
    val todayDay = today.substring(8, 10).toInt()

    var currentYear by remember { mutableStateOf(todayYear) }
    var currentMonth by remember { mutableStateOf(todayMonth) }
    var selectedDate by remember { mutableStateOf(today) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 60.dp.toPx() }

    val activeMonths = remember(holding) { collectActiveMonths(holding) }
    val currentMonthly = remember(holding, currentYear, currentMonth) {
        buildMonthlyIncome(holding, currentYear, currentMonth)
    }
    val selectedEntry = currentMonthly.byDate[selectedDate] ?: DailyIncomeEntry(selectedDate, emptyList())

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB)).statusBarsPadding()) {
        // 顶部返回栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF1E293B), modifier = Modifier.size(20.dp))
            }
            Text("日收益明细", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        }

        // 日历卡片
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp)
            .pointerInput(currentYear, currentMonth) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccum < -swipeThreshold) {
                            // 左滑 → 下个月
                            val next = if (currentMonth == 12) Pair(currentYear + 1, 1) else Pair(currentYear, currentMonth + 1)
                            currentYear = next.first
                            currentMonth = next.second
                        } else if (dragAccum > swipeThreshold) {
                            // 右滑 → 上个月
                            val prev = if (currentMonth == 1) Pair(currentYear - 1, 12) else Pair(currentYear, currentMonth - 1)
                            currentYear = prev.first
                            currentMonth = prev.second
                        }
                        dragAccum = 0f
                    },
                    onDragStart = { dragAccum = 0f },
                    onHorizontalDrag = { _, delta -> dragAccum += delta }
                )
            }
        ) {
            CalendarCard(
                year = currentYear,
                month = currentMonth,
                monthly = currentMonthly,
                todayDay = if (currentYear == todayYear && currentMonth == todayMonth) todayDay else null,
                selectedDate = selectedDate,
                onSelectDate = { date -> selectedDate = date },
                onPrevMonth = {
                    val prev = if (currentMonth == 1) Pair(currentYear - 1, 12) else Pair(currentYear, currentMonth - 1)
                    currentYear = prev.first; currentMonth = prev.second
                },
                onNextMonth = {
                    val next = if (currentMonth == 12) Pair(currentYear + 1, 1) else Pair(currentYear, currentMonth + 1)
                    currentYear = next.first; currentMonth = next.second
                },
                onTapYearMonth = { showMonthPicker = true }
            )
        }

        Spacer(Modifier.height(12.dp))

        // 下方明细：日收益 + 当日合计
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateLabel = formatDateLong(selectedDate)
            Text("日收益 · $dateLabel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
            Text("合计 +¥${CN_2.format(selectedEntry.total)}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFDC2626))
        }

        if (selectedEntry.deposits.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("当前日暂无收益", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(selectedEntry.deposits, key = { it.first.id }) { (dep, income) ->
                    DailyIncomeRow(deposit = dep, income = income,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 7.dp))
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerSheet(
            activeMonths = activeMonths,
            currentYear = currentYear,
            currentMonth = currentMonth,
            holding = holding,
            onDismiss = { showMonthPicker = false },
            onSelect = { y, m ->
                currentYear = y; currentMonth = m
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun CalendarCard(
    year: Int,
    month: Int,
    monthly: MonthlyIncome,
    todayDay: Int?,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTapYearMonth: () -> Unit
) {
    val daysCount = daysInMonth(year, month)
    val firstOffset = firstDayOfMonthOffset(year, month)
    val totalCells = firstOffset + daysCount
    val weeks = (totalCells + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(0.6.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        // 年-月 + 月合计
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ChevronLeft, "上月", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${year}年${month}月", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B), modifier = Modifier.clickable { onTapYearMonth() })
                Text("月合计 +¥${CN_2.format(monthly.monthTotal)}", fontSize = 11.sp,
                    color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onNextMonth, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ChevronRight, "下月", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 日期格子
        var dayCounter = 1
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val cellIndex = week * 7 + dow
                    if (cellIndex < firstOffset || dayCounter > daysCount) {
                        Box(modifier = Modifier.weight(1f).height(54.dp))
                    } else {
                        val d = dayCounter
                        val date = "%04d-%02d-%02d".format(year, month, d)
                        val entry = monthly.byDate[date]
                        val isSelected = date == selectedDate
                        val isToday = d == todayDay
                        DayCell(
                            day = d,
                            income = entry?.total ?: 0.0,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    income: Double,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayColor = when {
        isSelected -> Color.White
        isToday -> Color(0xFFDC2626)
        income > 0 -> Color(0xFF1E293B)
        else -> Color(0xFFCBD5E1)
    }
    Box(
        modifier = modifier
            .height(54.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .let { if (isSelected) it.clip(CircleShape).background(Color(0xFF2563EB)) else if (isToday) it.clip(CircleShape).background(Color(0xFFEFF6FF)) else it }
                    .border(if (isToday && !isSelected) 1.dp else 0.dp, Color(0xFFDC2626), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$day", fontSize = 12.sp, color = dayColor, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(if (income > 0) "+${CN_2.format(income)}" else "", fontSize = 8.sp,
                color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DailyIncomeRow(
    deposit: Deposit,
    income: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(deposit.productName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text("+¥${CN_2.format(income)}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(deposit.bankName, fontSize = 10.sp, color = Color(0xFF94A3B8))
                Text(" · ", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                RateIcon(rate = deposit.annualRate, modifier = Modifier.size(10.dp), color = Color(0xFF94A3B8))
                Text(" ${"%.2f".format(deposit.annualRate)}%", fontSize = 10.sp, color = Color(0xFF94A3B8))
                Spacer(Modifier.weight(1f))
                WalletIcon(modifier = Modifier.size(10.dp), tint = Color(0xFF94A3B8))
                Text(" ¥${CN_2.format(deposit.principal)}", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerSheet(
    activeMonths: List<Pair<Int, Int>>,
    currentYear: Int,
    currentMonth: Int,
    holding: List<Deposit>,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    val years = activeMonths.map { it.first }.distinct().sortedDescending()
    val byYear = activeMonths.groupBy { it.first }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                TextButton(onClick = onDismiss) { Text("完成", color = Color(0xFF2563EB), fontSize = 14.sp) }
                Text("选择月份", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Spacer(Modifier.width(48.dp))
            }
            years.forEach { y ->
                YearGroup(
                    year = y,
                    months = byYear[y]?.sortedBy { it.second } ?: emptyList(),
                    holding = holding,
                    currentYear = currentYear,
                    currentMonth = currentMonth,
                    onSelect = onSelect
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun YearGroup(
    year: Int,
    months: List<Pair<Int, Int>>,
    holding: List<Deposit>,
    currentYear: Int,
    currentMonth: Int,
    onSelect: (Int, Int) -> Unit
) {
    val isCurrentYear = year == currentYear
    Column {
        Text("$year", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                .let { if (isCurrentYear) it.background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp) else it })
        months.forEach { (_, m) ->
            MonthRow(year = year, month = m, holding = holding, isCurrent = isCurrentYear && m == currentMonth, onClick = { onSelect(year, m) })
        }
    }
}

@Composable
private fun MonthRow(
    year: Int,
    month: Int,
    holding: List<Deposit>,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val monthly = remember(holding, year, month) { buildMonthlyIncome(holding, year, month) }
    val daysInM = daysInMonth(year, month)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${month}月", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                if (isCurrent) {
                    Text("当前", fontSize = 9.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium,
                        modifier = Modifier.background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("月份收益合计 +¥${CN_2.format(monthly.monthTotal)}", fontSize = 11.sp,
                color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
            // 热力图：每天一个点
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (d in 1..daysInM) {
                    val date = "%04d-%02d-%02d".format(year, month, d)
                    val has = monthly.byDate[date]?.hasIncome() == true
                    Box(modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (has) Color(0xFF2563EB) else Color(0xFFE2E8F0)))
                }
            }
        }
        Text("$year", fontSize = 12.sp, color = Color(0xFF94A3B8))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
    }
}

private fun formatDateLong(date: String): String {
    val parts = date.split("-")
    if (parts.size != 3) return date
    return "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日"
}
