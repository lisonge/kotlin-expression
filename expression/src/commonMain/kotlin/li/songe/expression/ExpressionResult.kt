package li.songe.expression

/** A successful value of type [T] or an expression error of type [E]. */
public sealed interface ExpressionResult<out T, out E> {
    /** The operation completed with [value]. */
    public data class Success<out T>(public val value: T) : ExpressionResult<T, Nothing>

    /** The operation failed with [error]. */
    public data class Failure<out E>(public val error: E) : ExpressionResult<Nothing, E>
}
