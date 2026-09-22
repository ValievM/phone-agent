package com.example.phoneagent.tools.alarm

public sealed interface AlarmValidationResult {
    public data object Valid : AlarmValidationResult

    public data class Invalid(
        public val reason: String,
    ) : AlarmValidationResult
}

public fun validateAlarmArgs(args: AlarmArgs): AlarmValidationResult =
    when {
        args.hour !in MIN_HOUR..MAX_HOUR -> AlarmValidationResult.Invalid("Час должен быть от 0 до 23")
        args.minute !in MINUTE_RANGE -> AlarmValidationResult.Invalid("Минуты должны быть от 0 до 59")
        else -> AlarmValidationResult.Valid
    }

private const val MIN_HOUR = 0
private const val MAX_HOUR = 23
private val MINUTE_RANGE: IntRange = 0..59
