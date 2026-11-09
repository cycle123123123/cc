package com.jmreader.core

import java.util.Locale

/** 6128 -> "6128"，12345 -> "1.2 萬"，123456789 -> "1.2 億"。 */
internal fun formatCount(value: Int): String = when {
    value >= 100_000_000 -> String.format(Locale.US, "%.1f 億", value / 100_000_000.0)
    value >= 10_000 -> String.format(Locale.US, "%.1f 萬", value / 10_000.0)
    else -> value.toString()
}
