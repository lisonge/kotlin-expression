package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PowFunctionTest {
    @Test
    fun powPreservesDoubleSemantics() {
        for ((source, expected) in mapOf(
            "pow(2,3)" to 8.0, "pow(2,-2)" to 0.25,
            "-pow(2,2)" to -4.0, "pow(-2,2)" to 4.0,
            "pow(2,pow(3,2))" to 512.0, "pow(0,0)" to 1.0,
            "pow(9,0.5)" to 3.0, "pow(10,1000)" to Double.POSITIVE_INFINITY,
        )) {
            val expression = Expression.compile(source)
            assertEquals(expected, expression.evaluate(functions = ExpressionFunctions.standard))
            assertEquals(ExpressionResult.Success(expected), expression.evaluateResult(functions = ExpressionFunctions.standard))
        }
        assertTrue(Expression.compile("pow(-2,0.5)").evaluate(functions = ExpressionFunctions.standard).isNaN())
    }

    @Test
    fun oldOperatorIsAParseErrorAtOriginalPosition() {
        for (source in listOf("2^3", "2 ^ 3", "2^3^2")) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.INVALID_CHARACTER, error.code)
            assertEquals(source.indexOf('^'), error.startIndex)
            assertEquals(error.startIndex + 1, error.endIndex)
            assertEquals(error.code, assertIs<ExpressionResult.Failure<ExpressionParseException>>(Expression.compileResult(source)).error.code)
        }
    }

    @Test
    fun powUsesExplicitBindingsAndExactlyTwoArguments() {
        for (source in listOf("pow()", "pow(2)", "pow(2,3,4)")) {
            val expression = Expression.compile(source)
            val error = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(functions = ExpressionFunctions.standard),
            ).error
            assertEquals(ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, error.code)
            assertEquals(0, error.startIndex)
            assertEquals(3, error.endIndex)
        }
        val expression = Expression.compile("pow(base,exponent)")
        assertEquals(setOf("base", "exponent"), expression.variables)
        assertEquals(setOf("pow"), expression.functions)
        val variables = mapOf("base" to 2.0, "exponent" to 3.0)
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(variables)).error.code)
        val custom = mapOf("pow" to ExpressionFunction(2) { it.sum() })
        assertEquals(5.0, expression.evaluate(variables, ExpressionFunctions.standard + custom))
        assertEquals(8.0, expression.evaluate(variables, ExpressionFunctions.standard))
    }
}
