package com.example.zengchubao.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

private val CN_LOCALE = NumberFormat.getNumberInstance(Locale.CHINA).apply { maximumFractionDigits = 0 }

// ─── 常量（1:1 复刻自 Add Fire Goal Card/src/App.tsx） ────────────────────────
private val FireOrange = Color(0xFFFB923C)   // rgba(251,146,60,1)
private val FireAmber = Color(0xFFFBBF24)    // #fbbf24
private val FireDeepOrange = Color(0xFFF97316) // #f97316
private val FireIndigo = Color(0xFF6366F1)   // rgba(99,102,241,1)

// ─── 五彩斑斓的黑 卡片壳 ─────────────────────────────────────────────────────
@Composable
private fun FireCardShell(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0D0D0F), Color(0xFF111218), Color(0xFF0F1014)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        // glows: indigo 15% / orange 85% / emerald bottom
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(FireIndigo.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.5f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.15f, size.height * 0.5f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(FireOrange.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.4f),
                    radius = size.width * 0.5f
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.85f, size.height * 0.4f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF10B981).copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.5f, size.height)
            )
        }
        // 顶部彩虹折光细线
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to FireOrange.copy(alpha = 0.4f),
                        0.7f to FireIndigo.copy(alpha = 0.4f),
                        1f to Color.Transparent
                    )
                )
        )
        content()
    }
}

// ─── 波浪进度条 ──────────────────────────────────────────────────────────────
@Composable
private fun FireProgressBar(pct: Float) {
    val clamped = pct.coerceIn(0f, 100f)

    // 波浪相位动画（3.2s 线性）+ 辉光呼吸（2.6s）+ 镐子上下（2.6s）
    val transition = rememberInfiniteTransition(label = "fire")
    val wavePhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOut), RepeatMode.Reverse),
        label = "glow"
    )
    val pickBob by transition.animateFloat(
        initialValue = 0f, targetValue = -2f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOut), RepeatMode.Reverse),
        label = "bob"
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        val barW = maxWidth
        // ── 渐变填充层（clip 到 pct） ──
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped / 100f)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.horizontalGradient(
                        0f to FireDeepOrange,
                        0.6f to FireOrange,
                        1f to FireAmber
                    )
                )
        )
        // ── 波浪（两层，仅画在填充范围内，clip 圆角防尾部越过锤子） ──
        if (clamped > 0f) {
            Canvas(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped / 100f)
                    .clip(RoundedCornerShape(11.dp))
            ) {
                val w = size.width
                val h = size.height
                val period = w * 0.9f // 单个波周期
                val shift = wavePhase * period
                // 波1 rgba(255,210,80,0.45)
                drawPath(
                    buildWavePath(w, h, shift, period, h * 0.5f, h * 0.42f),
                    color = Color(0xFFFFD250).copy(alpha = 0.45f)
                )
                // 波2 rgba(255,140,30,0.30)
                drawPath(
                    buildWavePath(w, h, shift * 1.25f, period * 1.1f, h * 0.68f, h * 0.32f),
                    color = Color(0xFFFF8C1E).copy(alpha = 0.30f)
                )
            }
        }
        // ── 端点辉光（大 36 / 小 16） ──
        if (clamped > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width * (clamped / 100f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0xFFFFDC64).copy(alpha = 0.9f * glowAlpha),
                            FireOrange.copy(alpha = 0.5f * glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(cx, size.height / 2),
                        radius = 18.dp.toPx()
                    ),
                    radius = 18.dp.toPx(),
                    center = Offset(cx, size.height / 2)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0xFFFFFFB4).copy(alpha = 0.95f * glowAlpha),
                            Color(0xFFFFA028).copy(alpha = 0.6f * glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(cx, size.height / 2),
                        radius = 8.dp.toPx()
                    ),
                    radius = 8.dp.toPx(),
                    center = Offset(cx, size.height / 2)
                )
            }
            // ── 镐子 ──
            Box(
                Modifier
                    .fillMaxSize()
            ) {
                Text(
                    "⛏️",
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = barW * (clamped / 100f) - 9.dp, y = pickBob.dp)
                )
            }
        }
    }
}

/** 构造正弦感波浪 Path：沿 x 以 period 为周期起伏，baseY 为中轴，amp 为振幅 */
private fun buildWavePath(w: Float, h: Float, shift: Float, period: Float, baseY: Float, amp: Float): Path {
    val p = Path()
    p.moveTo(-period + shift, baseY)
    var x = -period + shift
    while (x < w + period) {
        // 用 cubic 模拟：控制点在中点上下偏移
        val nextX = x + period / 2f
        p.cubicTo(
            x + period * 0.25f, baseY - amp,
            x + period * 0.25f, baseY + amp,
            nextX, baseY
        )
        x = nextX
    }
    p.lineTo(w, h)
    p.lineTo(0f, h)
    p.close()
    return p
}

// ─── 已配置状态 ──────────────────────────────────────────────────────────────
@Composable
private fun FireCardConfigured(target: Double, current: Double, onEdit: () -> Unit) {
    val pct = if (target > 0) (current / target) * 100.0 else 0.0
    FireCardShell {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 13.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左：🔥 FIRE目标 | 目标 ¥x（weight 限定，右组保底）
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥 FIRE目标", fontSize = 8.sp, fontWeight = FontWeight.W500,
                        color = Color.White.copy(alpha = 0.45f), letterSpacing = 0.3.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(1.dp).height(10.dp).background(Color.White.copy(alpha = 0.12f)))
                    Spacer(Modifier.width(8.dp))
                    Text("目标", fontSize = 8.sp, color = Color.White.copy(alpha = 0.35f), maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Text("¥${CN_LOCALE.format(target)}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.75f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                // 右：¥current pct% ✏️
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¥${CN_LOCALE.format(current)}", fontSize = 7.sp,
                        color = Color.White.copy(alpha = 0.35f), maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${"%.1f".format(pct)}%",
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 12.sp,
                        maxLines = 1, softWrap = false,
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                listOf(FireAmber, FireDeepOrange)
                            )
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    // 半透明小铅笔按钮
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onEdit() }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("✏️", fontSize = 7.sp, lineHeight = 7.sp)
                    }
                }
            }
            FireProgressBar(min(pct, 100.0).toFloat())
        }
    }
}

// ─── 未配置状态 ──────────────────────────────────────────────────────────────
@Composable
private fun FireCardEmpty(onSetup: () -> Unit) {
    FireCardShell {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：说明文字
            Column {
                Text("🔥 FIRE目标", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 0.3.sp)
                Spacer(Modifier.height(4.dp))
                Text("设定财务自由目标", fontSize = 13.sp, color = Color.White.copy(alpha = 0.25f),
                    lineHeight = 18.sp)
                Text("开始追踪你的 FIRE 之路", fontSize = 11.sp, color = Color.White.copy(alpha = 0.25f))
            }
            Spacer(Modifier.width(12.dp))
            // 中：虚线占位轨道（最小宽度保底，防挤压消失）
            Box(
                Modifier
                    .weight(1f)
                    .widthIn(min = 56.dp)
                    .height(22.dp)
                    .dashedBorderFix(),
                contentAlignment = Alignment.Center
            ) {
                Text("— — —", fontSize = 11.sp, color = Color.White.copy(alpha = 0.2f),
                    letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.width(12.dp))
            // 右：立即设定
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(FireOrange.copy(alpha = 0.1f))
                    .border(1.dp, FireOrange.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { onSetup() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("立即设定", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = FireAmber, letterSpacing = 0.3.sp)
            }
        }
    }
}

/** 虚线边框（Compose 原生 border 只支持实线，用 Canvas 覆盖画虚线圆角框） */
private fun Modifier.dashedBorderFix(): Modifier = this.then(
    Modifier.drawBehind {
        val stroke = 1.5.dp.toPx()
        val radius = 11.dp.toPx()
        val rect = Rect(0f, 0f, size.width, size.height)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
        )
    }
)

// ─── 输入 Sheet ──────────────────────────────────────────────────────────────
@Composable
private fun FireInputSheet(
    initial: Double?,
    onConfirm: (Double) -> Unit,
    onCancel: () -> Unit
) {
    var raw by remember { mutableStateOf(initial?.toLong()?.toString() ?: "") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val numeric = raw.replace(",", "").toDoubleOrNull()
    val valid = numeric != null && numeric >= 10000

    val shortcuts = listOf(500000.0, 1000000.0, 2000000.0, 5000000.0)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            // 毛玻璃遮罩
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onCancel() }
            )
            // 底部 Sheet
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF14151A), Color(0xFF0F1014))
                        )
                    )
                    .padding(bottom = 20.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // 拖拽把手
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                }
                // 顶部彩虹折光细线
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.3f to FireOrange.copy(alpha = 0.35f),
                                0.7f to FireIndigo.copy(alpha = 0.35f),
                                1f to Color.Transparent
                            )
                        )
                )
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text("🔥 设定 FIRE 目标金额", fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f), maxLines = 1,
                        modifier = Modifier.padding(bottom = 12.dp))

                    // 大字实时金额
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("¥", fontSize = 28.sp, fontWeight = FontWeight.Light,
                            color = if (raw.isNotEmpty()) Color.White.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.padding(bottom = 4.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = numeric?.let { CN_LOCALE.format(it) } ?: "0",
                            fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                            lineHeight = 40.sp, letterSpacing = (-1).sp,
                            maxLines = 1, softWrap = false,
                            style = if (raw.isNotEmpty()) TextStyle(
                                brush = Brush.linearGradient(listOf(FireAmber, FireDeepOrange))
                            ) else TextStyle(color = Color.White.copy(alpha = 0.15f))
                        )
                    }

                    // 下划线输入框
                    BasicTextField(
                        value = raw,
                        onValueChange = { v -> if (v.all { it.isDigit() }) raw = v },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .drawBehind {
                                drawLine(
                                    color = if (focused) FireOrange.copy(alpha = 0.7f)
                                    else Color.White.copy(alpha = 0.15f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            },
                        textStyle = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 15.sp
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        decorationBox = { inner ->
                            Box(Modifier.padding(vertical = 8.dp)) {
                                if (raw.isEmpty()) {
                                    Text("输入目标金额（≥ 10,000）", fontSize = 15.sp,
                                        color = Color.White.copy(alpha = 0.3f))
                                }
                                inner()
                            }
                        }
                    )

                    // 快捷金额
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shortcuts.forEach { v ->
                            val selected = numeric == v
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) FireOrange.copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) FireOrange.copy(alpha = 0.7f)
                                        else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { raw = v.toLong().toString() }
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (v >= 1000000) "${(v / 10000).toLong()}百万"
                                    else "${(v / 10000).toLong()}万",
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, softWrap = false,
                                    color = if (selected) FireAmber else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    // 确认目标
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (valid) Brush.horizontalGradient(listOf(FireDeepOrange, FireAmber))
                                else Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.06f),
                                        Color.White.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .clickable(enabled = valid) {
                                numeric?.let { onConfirm(it) }
                                focusManager.clearFocus()
                            }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("确认目标", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (valid) Color(0xFF0F1014) else Color.White.copy(alpha = 0.2f),
                            letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}

// ─── 编排 ────────────────────────────────────────────────────────────────────
@Composable
fun FireSection(
    fireGoal: Double,
    fireCurrent: Double,
    onSaveGoal: (Double) -> Unit
) {
    var showInput by remember { mutableStateOf(false) }

    Box(Modifier.padding(horizontal = 16.dp)) {
        if (fireGoal > 0) {
            FireCardConfigured(
                target = fireGoal,
                current = fireCurrent,
                onEdit = { showInput = true }
            )
        } else {
            FireCardEmpty(onSetup = { showInput = true })
        }
    }

    if (showInput) {
        FireInputSheet(
            initial = if (fireGoal > 0) fireGoal else null,
            onConfirm = { v ->
                onSaveGoal(v)
                showInput = false
            },
            onCancel = { showInput = false }
        )
    }
}
