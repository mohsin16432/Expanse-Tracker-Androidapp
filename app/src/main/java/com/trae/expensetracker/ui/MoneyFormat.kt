package com.trae.expensetracker.ui

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MoneyFormat {
    fun format(currencyCode: String, amountMinor: Long): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val major = amountMinor.toDouble() / 100.0
        val symbol = when (currencyCode.uppercase(Locale.US)) {
            "PKR" -> "PKR"
            "USD" -> "USD"
            else -> currencyCode.uppercase(Locale.US)
        }
        return "$symbol ${nf.format(major)}"
    }
}

