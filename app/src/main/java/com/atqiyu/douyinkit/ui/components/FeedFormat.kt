package com.atqiyu.douyinkit.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun feedLocalDate(epochMillis: Long): LocalDate {
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}

fun feedDateSectionTitle(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> if (date.year == today.year) {
            "${date.monthValue}月${date.dayOfMonth}日"
        } else {
            "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        }
    }
}

fun feedDateFilterLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    return feedDateSectionTitle(date, today)
}

/** A one-character section key: A-Z for Latin, "#" for digits, otherwise the first character. */
fun feedInitialOf(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return "#"
    val first = trimmed.first()
    return when {
        first in 'a'..'z' -> first.uppercaseChar().toString()
        first in 'A'..'Z' -> first.toString()
        first in '0'..'9' -> "#"
        else -> first.toString()
    }
}
