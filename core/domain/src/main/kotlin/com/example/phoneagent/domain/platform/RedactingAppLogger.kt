package com.example.phoneagent.domain.platform

/**
 * Decorates any [AppLogger] with [LogRedaction], so every real implementation (Logcat, a file, a
 * future crash reporter) gets it for free instead of redacting by hand. Bind this in front of the
 * platform-specific delegate; never bind the delegate directly as [AppLogger].
 */
public class RedactingAppLogger(
    private val delegate: AppLogger,
) : AppLogger {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ) {
        delegate.log(
            level = level,
            tag = tag,
            message = LogRedaction.redact(message),
            fields =
                fields.mapValues { (key, value) ->
                    if (LogRedaction.isSensitiveFieldName(key)) REDACTED_VALUE else LogRedaction.redact(value)
                },
            throwable = throwable?.let(::redacted),
        )
    }

    // Stack traces only carry class/method/line, never local values, so they're safe to keep;
    // only the message text (which callers or libraries may have built from raw input) is redone.
    private fun redacted(throwable: Throwable): Throwable =
        Throwable(LogRedaction.redact(throwable.message.orEmpty())).apply { stackTrace = throwable.stackTrace }

    private companion object {
        private const val REDACTED_VALUE: String = "<redacted>"
    }
}
