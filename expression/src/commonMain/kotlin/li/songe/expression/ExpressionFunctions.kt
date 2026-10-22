package li.songe.expression

/** Optional function collections for explicit evaluation bindings. */
public object ExpressionFunctions {
    /**
     * Standard min/max (one or more arguments), clamp (three), and pow (two).
     * A shared read-only map. Implementations are stateless.
     * Pass explicitly to evaluate/evaluateResult, or combine with custom bindings using `+`.
     */
    public val standard: Map<String, ExpressionFunction> =
        BuiltinFunction.entries.associate { it.sourceName to ExpressionFunction(it) }
}
