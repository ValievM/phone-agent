package com.example.phoneagent.tools.status

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal object DeviceStatusCopy {
    const val MODEL_DESCRIPTION: String = "Получить заряд аккумулятора, состояние зарядки, экономный режим и свободное место на телефоне."
    const val READ_ERROR: String = "Не удалось прочитать состояние телефона."
    const val CARD_TITLE: String = "Состояние телефона"

    fun result(status: DeviceStatus): String =
        buildList {
            status.batteryPercent?.let { percent ->
                add("Заряд $percent%${status.charging?.let(::chargingSuffix).orEmpty()}")
            } ?: status.charging?.let { add(chargingState(it)) }
            status.powerSaver?.let { add("Экономный режим ${if (it) "включён" else "выключен"}") }
            add("Свободно ${formatStorage(status.freeStorageMb)} из ${formatStorage(status.totalStorageMb)}")
        }.joinToString(separator = ". ", postfix = ".")

    fun cardSummary(status: DeviceStatus): String =
        buildString {
            status.batteryPercent?.let { append("Заряд $it%, ") }
            append("свободно ${formatStorage(status.freeStorageMb)}")
        }

    private fun chargingSuffix(charging: Boolean): String = ", ${chargingState(charging).lowercase(RUSSIAN_LOCALE)}"

    private fun chargingState(charging: Boolean): String = if (charging) "Заряжается" else "Не заряжается"

    private fun formatStorage(megabytes: Long): String =
        if (megabytes < MEGABYTES_PER_GIGABYTE) {
            "$megabytes МБ"
        } else {
            val symbols = DecimalFormatSymbols.getInstance(RUSSIAN_LOCALE).apply { groupingSeparator = ' ' }
            "${DecimalFormat("#,##0.0", symbols).format(megabytes / MEGABYTES_PER_GIGABYTE.toDouble())} ГБ"
        }

    private const val MEGABYTES_PER_GIGABYTE: Long = 1_024L
    private val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
}
