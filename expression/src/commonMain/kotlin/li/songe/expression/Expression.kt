package li.songe.expression

/** Compiles expressions independently of variable and function bindings. Safe for concurrent use. */
public object Expression {

    /**
     * Compiles [source] into an immutable, reusable expression.
     * Sources are limited to 16,384 UTF-16 code units; parser nesting and AST depth
     * are each limited to 128 to prevent stack exhaustion.
     * @throws ExpressionParseException for invalid syntax, symbols or complexity.
     */
    public fun compile(source: String): CompiledExpression {
        if (source.length > 16_384) {
            throw ExpressionParseException(
                ExpressionParseErrorCode.LIMIT_EXCEEDED, 16_384, source.length,
                "Expression exceeds 16384 UTF-16 code units",
            )
        }
        val parser = Parser(source)
        val root = parser.parse()
        return CompiledExpression(source, root, parser.variables, parser.functions)
    }

    /**
     * Compiles [source], returning a value or a typed compilation error.
     * Has the same semantics and limits as [compile]. Only [ExpressionParseException]
     * is converted to [ExpressionResult.Failure]; other exceptions propagate.
     */
    public fun compileResult(source: String): ExpressionResult<CompiledExpression, ExpressionParseException> =
        try {
            ExpressionResult.Success(compile(source))
        } catch (error: ExpressionParseException) {
            ExpressionResult.Failure(error)
        }
}
