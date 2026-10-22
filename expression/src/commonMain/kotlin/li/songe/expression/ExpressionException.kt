package li.songe.expression

/** Stable categories of expression parsing failures. */
public enum class ExpressionParseErrorCode {
    /** An unsupported character was encountered. */
    INVALID_CHARACTER,
    /** A numeric literal does not match the language grammar. */
    INVALID_NUMBER,
    /** A token is missing or is not valid at this position. */
    UNEXPECTED_TOKEN,
    /** The source length or expression depth exceeds the library limit. */
    LIMIT_EXCEEDED,
}

/** Stable categories of expression evaluation failures. */
public enum class ExpressionEvaluationErrorCode {
    /** The function has no supplied binding. */
    UNKNOWN_FUNCTION,
    /** The function has the wrong number of arguments. */
    INVALID_ARGUMENT_COUNT,
    /** A referenced variable has no value in this evaluation. */
    MISSING_VARIABLE,
    /** Division or remainder by positive or negative zero. */
    DIVISION_BY_ZERO,
    /** The lower clamp bound is greater than its upper bound. */
    INVALID_CLAMP_BOUNDS,
}

/**
 * A failure located in the original source. [startIndex] is inclusive and [endIndex]
 * exclusive, measured in UTF-16 code units. EOF failures have an empty range.
 * Each subclass provides a phase-specific error code. Codes and ranges are the
 * contract; the message may change.
 */
public sealed class ExpressionException protected constructor(
    public val startIndex: Int,
    public val endIndex: Int,
    message: String,
) : IllegalArgumentException(message)

/** Invalid expression syntax or excessive complexity, detected while parsing. */
public class ExpressionParseException internal constructor(
    public val code: ExpressionParseErrorCode,
    startIndex: Int,
    endIndex: Int,
    message: String,
) : ExpressionException(startIndex, endIndex, message)

/** Missing bindings, incorrect argument counts or invalid arithmetic during evaluation. */
public class ExpressionEvaluationException internal constructor(
    public val code: ExpressionEvaluationErrorCode,
    startIndex: Int,
    endIndex: Int,
    message: String,
) : ExpressionException(startIndex, endIndex, message)
