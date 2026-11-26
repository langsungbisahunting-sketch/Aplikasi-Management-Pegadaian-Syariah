package com.example.utils

import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object FormatUtils {
    private val idLocale = Locale("id", "ID")
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    private val compactDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun rupiah(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale)
        formatter.maximumFractionDigits = 0
        val rounded = amount.roundToLong()
        return if (rounded < 0) "-Rp ${formatter.format(abs(rounded))}" else "Rp ${formatter.format(rounded)}"
    }

    fun numberId(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(idLocale)
        formatter.maximumFractionDigits = 0
        return formatter.format(amount.roundToLong())
    }

    fun formatUkuranFile(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun todayIso(): String = synchronized(isoFormat) { isoFormat.format(Date()) }

    fun todayCompact(): String = synchronized(compactDateFormat) { compactDateFormat.format(Date()) }

    fun nowTimestamp(): String = synchronized(timestampFormat) { timestampFormat.format(Date()) }

    fun firstDayOfMonthIso(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return synchronized(isoFormat) { isoFormat.format(cal.time) }
    }

    fun daysAgoIso(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return synchronized(isoFormat) { isoFormat.format(cal.time) }
    }

    fun addDaysIso(isoDate: String, days: Int): String {
        return try {
            val d = synchronized(isoFormat) { isoFormat.parse(isoDate) } ?: Date()
            val cal = Calendar.getInstance()
            cal.time = d
            cal.add(Calendar.DAY_OF_YEAR, days)
            synchronized(isoFormat) { isoFormat.format(cal.time) }
        } catch (_: Exception) {
            todayIso()
        }
    }

    fun validIsoDate(value: String): Boolean {
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(value.trim())) return false
        return try {
            synchronized(isoFormat) { isoFormat.parse(value.trim()) } != null
        } catch (_: Exception) {
            false
        }
    }

    fun diffDaysFromToday(targetIso: String): Int {
        return try {
            val today = synchronized(isoFormat) { isoFormat.parse(todayIso()) } ?: return 0
            val target = synchronized(isoFormat) { isoFormat.parse(targetIso) } ?: return 0
            ((target.time - today.time) / 86_400_000L).toInt()
        } catch (_: Exception) {
            0
        }
    }

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("amanah_gadai_salt_v1:$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, hash: String): Boolean {
        return hashPassword(password) == hash
    }
}
