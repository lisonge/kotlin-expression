package li.songe.expression

/**
 * A function accepting argument counts in the inclusive [arity] range.
 * The range must be nonempty and nonnegative. The implementation receives a fresh
 * array of arguments evaluated from left to right. Exceptions propagate unchanged.
 * Concurrent evaluations require a thread-safe implementation or separate bindings.
 */
public class ExpressionFunction private constructor(
    public val arity: IntRange,
    private val implementation: ((DoubleArray) -> Double)?,
    private val builtin: BuiltinFunction?,
) {
    init {
        require(!arity.isEmpty() && arity.first >= 0) { "Function arity must be a nonempty, nonnegative range" }
    }

    /** Creates a function accepting argument counts in [arity]. */
    public constructor(
        arity: IntRange,
        implementation: (DoubleArray) -> Double,
    ) : this(arity, implementation, null)

    /** Creates a function accepting exactly [arity] arguments, including zero. */
    public constructor(
        arity: Int,
        implementation: (DoubleArray) -> Double,
    ) : this(arity..arity, implementation)

    internal constructor(function: BuiltinFunction) : this(function.arity, null, function)

    internal fun invoke(
        arguments: List<ExpressionNode>,
        variables: Map<String, Double>,
        functions: Map<String, ExpressionFunction>,
        token: Token,
    ): Double {
        if (builtin != null) {
            return builtin.evaluate(arguments.size, token) { arguments[it].evaluate(variables, functions) }
        }
        val values = DoubleArray(arguments.size) { arguments[it].evaluate(variables, functions) }
        return invokeEvaluationCallback { implementation!!(values) }
    }
}

internal class EvaluationCallbackException(val original: ExpressionEvaluationException) : RuntimeException(original)

internal inline fun <T> invokeEvaluationCallback(block: () -> T): T = try {
    block()
} catch (error: ExpressionEvaluationException) {
    throw EvaluationCallbackException(error)
}
