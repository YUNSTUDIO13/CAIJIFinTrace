package com.example.zengchubao.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.zengchubao.model.Deposit
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 系统日历同步：存单到期事件
 * 标题 = 存单名称 + "到期"，时间 = 到期日 09:00，提醒 = 事件发生时（9:00）
 */
object CalendarSync {

    private const val TAG = "CalendarSync"

    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    /** 同步结果：eventId 非空即成功；message 用于 Toast 反馈 */
    data class SyncResult(val eventId: Long?, val message: String)

    fun hasPermission(context: Context): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** 插入或更新存单到期事件 */
    fun syncDepositEvent(context: Context, deposit: Deposit): SyncResult {
        if (!hasPermission(context)) {
            Log.w(TAG, "no calendar permission, skip")
            return SyncResult(null, "未获得日历权限，未创建日历提醒")
        }
        val calendar = findWritableCalendar(context)
            ?: return SyncResult(null, "未找到可写入的日历账户，请先打开系统日历 App 初始化后再试")

        val startMillis = parseEndDateAt9AM(deposit.endDate)
            ?: return SyncResult(null, "到期日期解析失败：${deposit.endDate}")
        val endMillis = startMillis + 30 * 60 * 1000L

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendar.first)
            put(CalendarContract.Events.TITLE, "${deposit.productName}到期")
            put(CalendarContract.Events.DESCRIPTION, descriptionFor(deposit))
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        return try {
            val existingId = deposit.calendarEventId
            val updatedId: Long? = if (existingId != null) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                val rows = context.contentResolver.update(uri, values, null, null)
                Log.d(TAG, "update event $existingId rows=$rows")
                if (rows > 0) existingId else null
            } else null

            val finalId = updatedId ?: run {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val id = uri?.lastPathSegment?.toLongOrNull()
                Log.d(TAG, "insert event -> $id uri=$uri calendar=${calendar.first}(${calendar.second})")
                id
            }

            if (finalId == null) {
                SyncResult(null, "日历事件写入失败（insert 返回空）")
            } else {
                upsertReminder(context, finalId)
                SyncResult(finalId, "已写入日历「${calendar.second}」：${deposit.endDate} 09:00")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            SyncResult(null, "日历写入异常：${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** 删除存单对应的日历事件 */
    fun deleteDepositEvent(context: Context, eventId: Long) {
        if (!hasPermission(context)) return
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = context.contentResolver.delete(uri, null, null)
            Log.d(TAG, "delete event $eventId rows=$rows")
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e)
        }
    }

    /**
     * 兜底：无可写日历账户时，用 Intent 调起系统日历的"新建日程"界面（全预填，用户点保存即可）。
     * ACTION_INSERT 不需要日历权限。
     */
    fun buildInsertIntent(deposit: Deposit): android.content.Intent? {
        val startMillis = parseEndDateAt9AM(deposit.endDate) ?: return null
        return android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "${deposit.productName}到期")
            putExtra(CalendarContract.Events.DESCRIPTION, descriptionFor(deposit))
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 30 * 60 * 1000L)
        }
    }

    // ── 内部 ──

    /** 事件描述（直写与 Intent 兜底共用） */
    private fun descriptionFor(deposit: Deposit): String {
        val fmt = NumberFormat.getNumberInstance(Locale.CHINA).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }
        return "银行：${deposit.bankName}\n本金：¥${fmt.format(deposit.principal)}\n到期本息：¥${fmt.format(deposit.maturityAmount)}"
    }

    /** 到期日 09:00 的本地时间毫秒 */
    private fun parseEndDateAt9AM(endDate: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.isLenient = false
            sdf.parse("$endDate 09:00")?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 找可写日历，返回 (calendarId, 显示名)。
     * 优先级：可见+本地账户 > 可见+可写 > 任何可写。
     * 不做 selection 过滤（部分国产 ROM 的 access level 标记不规范），全部拉出逐个判断并打日志。
     */
    private fun findWritableCalendar(context: Context): Pair<Long, String>? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,                              // 0
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,            // 1
            CalendarContract.Calendars.ACCOUNT_NAME,                     // 2
            CalendarContract.Calendars.ACCOUNT_TYPE,                     // 3
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,            // 4
            CalendarContract.Calendars.VISIBLE                           // 5
        )

        var visibleWritable: Pair<Long, String>? = null
        var anyWritable: Pair<Long, String>? = null

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
            )?.use { c ->
                Log.d(TAG, "calendars count=${c.count}")
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: "?"
                    val account = c.getString(2) ?: "?"
                    val type = c.getString(3) ?: "?"
                    val access = c.getInt(4)
                    val visible = c.getInt(5) == 1
                    Log.d(TAG, "calendar id=$id name=$name account=$account type=$type access=$access visible=$visible")

                    if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    if (visible && type == CalendarContract.ACCOUNT_TYPE_LOCAL) {
                        return id to name
                    }
                    if (visible && visibleWritable == null) visibleWritable = id to name
                    if (anyWritable == null) anyWritable = id to name
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "query calendars failed", e)
            return null
        }
        return visibleWritable ?: anyWritable
    }

    /** 保证事件恰好有一条「事件发生时」提醒 */
    private fun upsertReminder(context: Context, eventId: Long) {
        try {
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString())
            )
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 0) // 9:00 事件开始时提醒
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        } catch (e: Exception) {
            // 提醒失败不影响事件本身
            Log.e(TAG, "reminder failed", e)
        }
    }
}
