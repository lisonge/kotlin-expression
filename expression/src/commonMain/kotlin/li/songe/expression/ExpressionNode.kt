package li.songe.expression

internal sealed class ExpressionNode(val depth: Int) {
    abstract fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double

    class Number(val value: Double, depth: Int = 1) : ExpressionNode(depth) {
        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double = value
    }

    class Variable(private val token: Token) : ExpressionNode(1) {
        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double =
            invokeEvaluationCallback { variables[token.text] }
            ?: token.evaluationFailure(ExpressionEvaluationErrorCode.MISSING_VARIABLE, "Missing variable '${token.text}'")
    }

    class Unary private constructor(private val token: Token, private val operand: ExpressionNode) : ExpressionNode(operand.depth + 1) {
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
        override fun evaluate(variables: Map<String, Double>, functions: Map<String, ExpressionFunction>): Double {
            val function = invokeEvaluationCallback { functions[token.text] } ?: token.evaluationFailure(
                ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, "Unknown function '${token.text}'",
            )
            val arity = function.arity
            if (arguments.size !in arity) token.evaluationFailure(
                ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, "${token.text} expects $arity arguments",
            )
            return function.invoke(arguments, variables, functions, token)
        }
    }
}
