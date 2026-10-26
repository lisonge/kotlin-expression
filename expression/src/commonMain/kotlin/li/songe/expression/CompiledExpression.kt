package li.songe.expression

/** An immutable calculation tree with no stored bindings, cache or evaluation state. */
public class CompiledExpression internal constructor(
    /** Original, unmodified expression text. */
    public val source: String,
    private val root: ExpressionNode,
    variables: Set<String>,
    functions: Set<String>,
) {
    /**
     * Referenced variable names, deduplicated in first-appearance order.
     * Dotted paths such as `a.b.c` are complete names matched against map keys.
     * Function names are excluded. The read-only set is reused across accesses.
     */
    public val variables: Set<String> = variables

    /**
     * Referenced function names, deduplicated in first-appearance order.
     * Dotted paths such as `a.b.c` are complete names matched against function map keys.
     * Includes standard and custom calls, independently of evaluation bindings.
     * The read-only set is reused across accesses.
     */
    public val functions: Set<String> = functions

    /**
     * Checks allowed variable names, function bindings and every call's argument count.
     * Returns all binding errors in source order, or an empty list on success.
     * Checks each function before its arguments, continuing into invalid calls.
     * Each erroneous occurrence retains its full name's source range.
     * Does not read variable values, execute functions or check arithmetic errors.
     * Bindings are not retained; evaluation continues to use its supplied maps.
     * Exceptions from supplied collections propagate unchanged.
     * Errors describe missing variables, unknown functions or incorrect argument counts.
     */
    public fun validateBindings(
        variables: Set<String> = emptySet(),
        functions: Map<String, ExpressionFunction> = emptyMap(),
    ): List<ExpressionEvaluationException> {
        val errors = mutableListOf<ExpressionEvaluationException>()
        root.validateBindings(variables, functions, errors)
        return errors
    }

    /**
     * Reports division or remainder operators whose divisor is a constant zero,
     * including negative zero and folded numeric expressions such as `(2 - 2)`.
     * Returns all such errors in source order, with each range covering its operator.
     * Visits every operand and function argument, regardless of binding errors.
     * Does not read variable values, execute functions or infer algebraic identities.
     * An empty list does not rule out errors depending on runtime values or bindings.
     * Compilation and evaluation are unchanged; earlier evaluation errors can prevent
     * a reported operator from being reached. Only [ExpressionEvaluationErrorCode.DIVISION_BY_ZERO]
     * is reported; clamp bounds, NaN and infinities are not checked.
     */
    public fun validateArithmetic(): List<ExpressionEvaluationException> {
        val errors = mutableListOf<ExpressionEvaluationException>()
        root.validateArithmetic(errors)
        return errors
    }

    /**
     * Evaluates using [variables]. Only referenced variables need bindings; extra keys
     * are ignored. Independent calls can run concurrently. The caller must not mutate
     * its maps during an evaluation. NaN and infinities are preserved.
     * [functions] supplies all implementations; by default no functions are bound.
     * Pass [ExpressionFunctions.standard] to use min/max/clamp/pow.
     * @throws ExpressionEvaluationException for missing bindings, zero divisors or
     * reversed clamp bounds, unknown functions or incorrect argument counts.
     */
    public fun evaluate(
        variables: Map<String, Double> = emptyMap(),
        functions: Map<String, ExpressionFunction> = emptyMap(),
    ): Double = try {
        root.evaluate(variables, functions)
    } catch (error: EvaluationCallbackException) {
        throw error.original
    }

    /**
     * Evaluates with [variables], returning a value or a typed evaluation error.
     * Has the same semantics as [evaluate], including successful NaN and infinite values.
     * Engine-generated [ExpressionEvaluationException] is converted to [ExpressionResult.Failure].
     * Exceptions from custom functions or binding map lookups propagate unchanged, including expression errors.
     * [functions] has the same binding and override behavior as in [evaluate].
     */
    public fun evaluateResult(
        variables: Map<String, Double> = emptyMap(),
        functions: Map<String, ExpressionFunction> = emptyMap(),
    ): ExpressionResult<Double, ExpressionEvaluationException> =
        try {
            ExpressionResult.Success(root.evaluate(variables, functions))
        } catch (error: EvaluationCallbackException) {
            throw error.original
        } catch (error: ExpressionEvaluationException) {
            ExpressionResult.Failure(error)
        }
}
