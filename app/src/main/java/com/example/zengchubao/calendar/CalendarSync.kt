package com.example.zengchubao.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
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

    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    fun hasPermission(context: Context): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** 插入或更新存单到期事件，返回 eventId；无可用日历或权限时返回 null */
    fun syncDepositEvent(context: Context, deposit: Deposit): Long? {
        if (!hasPermission(context)) return null
        val calendarId = findWritableCalendarId(context) ?: return null

        val startMillis = parseEndDateAt9AM(deposit.endDate) ?: return null
        val endMillis = startMillis + 30 * 60 * 1000L

        val fmt = NumberFormat.getNumberInstance(Locale.CHINA).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "${deposit.productName}到期")
            put(
                CalendarContract.Events.DESCRIPTION,
                "银行：${deposit.bankName}\n本金：¥${fmt.format(deposit.principal)}\n到期本息：¥${fmt.format(deposit.maturityAmount)}"
            )
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        val existingId = deposit.calendarEventId
        val eventId: Long? = if (existingId != null) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) existingId else null
        } else null

        val finalId = eventId ?: run {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } ?: return null

        upsertReminder(context, finalId)
        return finalId
    }

    /** 删除存单对应的日历事件 */
    fun deleteDepositEvent(context: Context, eventId: Long) {
        if (!hasPermission(context)) return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null)
    }

    // ── 内部 ──

    /** 到期日 09:00 的本地时间毫秒 */
    private fun parseEndDateAt9AM(endDate: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse("$endDate 09:00")?.time
        } catch (e: Exception) {
            null
        }
    }

    /** 找到第一个可写的本地日历（优先本机账户） */
    private fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        var fallbackId: Long? = null
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) != 1) continue // 只要可见日历
                val id = c.getLong(0)
                val accountType = c.getString(2) ?: ""
                if (accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) return id
                if (fallbackId == null) fallbackId = id
            }
        }
        return fallbackId
    }

    /** 保证事件恰好有一条「事件发生时」提醒 */
    private fun upsertReminder(context: Context, eventId: Long) {
        // 清掉旧提醒再插入，避免重复
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
    }
}
