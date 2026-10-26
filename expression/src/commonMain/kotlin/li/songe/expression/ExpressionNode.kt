package li.songe.expression

internal sealed class ExpressionNode(val depth: Int) {
    abstract fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double
    abstract fun validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>, errors: MutableList<ExpressionEvaluationException>)
    abstract fun validateArithmetic(errors: MutableList<ExpressionEvaluationException>)

    class Number(val value: Double, depth: Int = 1) : ExpressionNode(depth) {
        override fun validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>, errors: MutableList<ExpressionEvaluationException>) = Unit
        override fun validateArithmetic(errors: MutableList<ExpressionEvaluationException>) = Unit
        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double = value
    }

    class Variable(private val token: Token) : ExpressionNode(1) {
        override fun validateArithmetic(errors: MutableList<ExpressionEvaluationException>) = Unit
        override fun validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>, errors: MutableList<ExpressionEvaluationException>) {
            if (token.text !in variables) errors.add(ExpressionEvaluationException(
                ExpressionEvaluationErrorCode.MISSING_VARIABLE, token.start, token.end, "Missing variable '${token.text}'",
            ))
        }
        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double =
            invokeEvaluationCallback { variables[token.text] }
            ?: token.evaluationFailure(ExpressionEvaluationErrorCode.MISSING_VARIABLE, "Missing variable '${token.text}'")
    }

    class Unary private constructor(private val token: Token, private val operand: ExpressionNode) : ExpressionNode(operand.depth + 1) {
        override fun validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>, errors: MutableList<ExpressionEvaluationException>) =
            operand.validateBindings(variables, functions, errors)
        override fun validateArithmetic(errors: MutableList<ExpressionEvaluationException>) =
            operand.validateArithmetic(errors)
        companion object {
            fun create(token: Token, operand: ExpressionNode): ExpressionNode =
                if (operand is Number) Number(apply(token, operand.value), operand.depth + 1) else Unary(token, operand)

            private fun apply(token: Token, value: Double): Double = if (token.kind == TokenKind.MINUS) -value else value
        }

        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double {
            val value = operand.evaluate(variables, functions)
            return apply(token, value)
        }
    }

    class Binary private constructor(
        private val token: Token,
        private val left: ExpressionNode,
        private val right: ExpressionNode,
    ) : ExpressionNode(maxOf(left.depth, right.depth) + 1) {
        override fun validateArithmetic(errors: MutableList<ExpressionEvaluationException>) {
            left.validateArithmetic(errors)
            if ((token.kind == TokenKind.SLASH || token.kind == TokenKind.PERCENT) && right is Number && right.value == 0.0) {
                errors.add(ExpressionEvaluationException(
                    ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, token.start, token.end, "Division or remainder by zero",
                ))
            }
            right.validateArithmetic(errors)
        }

        override fun validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>, errors: MutableList<ExpressionEvaluationException>) {
            left.validateBindings(variables, functions, errors)
            right.validateBindings(variables, functions, errors)
        }
        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double {
            val a = left.evaluate(variables, functions)
            val b = right.evaluate(variables, functions)
            return apply(token, a, b)
        }

        companion object {
            fun create(token: Token, left: ExpressionNode, right: ExpressionNode): ExpressionNode {
                if (left is Number && right is Number) {
                    // Division errors stay at evaluation time; folded nodes retain source depth.
                    val zeroDivisor = (token.kind == TokenKind.SLASH || token.kind == TokenKind.PERCENT) && right.value == 0.0
                    if (!zeroDivisor) return Number(apply(token, left.value, right.value), maxOf(left.depth, right.depth) + 1)
                }
                return Binary(token, left, right)
            }

            private fun apply(token: Token, a: Double, b: Double): Double {
                return when (token.kind) {
                    TokenKind.PLUS -> a + b
                    TokenKind.MINUS -> a - b
                    TokenKind.STAR -> a * b
                    TokenKind.SLASH, TokenKind.PERCENT -> {
                        if (b == 0.0) token.evaluationFailure(
                            ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, "Division or remainder by zero",
                        )
                        if (token.kind == TokenKind.SLASH) a / b else a % b
                    }
                    else -> error("Not a binary operator")
                }
            }
        }
    }

    class Call(
        private val token: Token,
        private val arguments: List<ExpressionNode>,
    ) : ExpressionNode((arguments.maxOfOrNull { it.depth } ?: 0) + 1) {
        override fun validateArithmetic(errors: MutableList<ExpressionEvaluationException>) {
            for (argument in arguments) argument.validateArithmetic(errors)
        }

        override fun validateBindings(variables: Set<String>, functions: Map<String, ExpressionFunction>, errors: MutableList<ExpressionEvaluationException>) {
            val function = functions[token.text]
            val error = bindingError(function)
            if (error != null) errors.add(error)
            for (argument in arguments) argument.validateBindings(variables, functions, errors)
        }

        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double {
            val function = checkedFunction(invokeEvaluationCallback { functions[token.text] })
            return function.invoke(arguments, variables, functions, token)
        }

        private fun bindingError(function: ExpressionFunction?): ExpressionEvaluationException? = when {
            function == null -> ExpressionEvaluationException(
                ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, token.start, token.end, "Unknown function '${token.text}'",
            )
            arguments.size !in function.arity -> ExpressionEvaluationException(
                ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, token.start, token.end,
                "${token.text} expects ${function.arity} arguments",
            )
            else -> null
        }

        private fun checkedFunction(binding: ExpressionFunction?): ExpressionFunction {
            val error = bindingError(binding)
            if (error != null) throw error
            return binding!!
        }
    }
}
