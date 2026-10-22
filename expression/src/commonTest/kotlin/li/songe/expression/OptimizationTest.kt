package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OptimizationTest {
    @Test
    fun literalArithmeticFoldsWithoutChangingFloatingPointOrder() {
        assertIs<ExpressionNode.Number>(Parser("(2 + 3) * 4 - 8 / 2 + 7 % 3").parse())
        assertEquals(17.0, Expression.compile("(2 + 3) * 4 - 8 / 2 + 7 % 3").evaluate())
        assertEquals(0.0, Expression.compile("(1e16 + 1) - 1e16").evaluate())
        assertEquals(1.0, Expression.compile("(1e16 - 1e16) + 1").evaluate())
        assertEquals((-0.0).toBits(), Expression.compile("-0 * (2 + 3)").evaluate().toBits())
        assertTrue(Expression.compile("1e999 - 1e999").evaluate().isNaN())
        assertEquals(128, Parser(List(128) { "1" }.joinToString("+")).parse().depth)
        assertEquals(ExpressionParseErrorCode.LIMIT_EXCEEDED,
            assertFailsWith<ExpressionParseException> { Expression.compile(List(129) { "1" }.joinToString("+")) }.code)
    }

    @Test
    fun errorsRemainAtEvaluationTimeAndRespectEarlierEffects() {
        for (operator in listOf("/", "%")) {
            val source = "mark() + 1 $operator (2 - 2)"
            val expression = assertIs<ExpressionResult.Success<CompiledExpression>>(Expression.compileResult(source)).value
            var calls = 0
            val functions = mapOf("mark" to ExpressionFunction(0) { calls++; 1.0 })
            val error = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(functions = functions),
            ).error
            assertEquals(1, calls)
            assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, error.code)
            assertEquals(source.indexOf(operator), error.startIndex)
        }
        val expression = Expression.compile("missing + 1 / 0")
        assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult()).error.code)
    }

    @Test
    fun constantsAndFunctionsRemainDynamicallyBound() {
        val expression = Expression.compile("PI * (2 + 3) + pow(2, 3)")
        assertEquals(setOf("PI"), expression.variables)
        assertEquals(setOf("pow"), expression.functions)
        assertEquals(18.0, expression.evaluate(mapOf("PI" to 2.0), ExpressionFunctions.standard))
        assertEquals(20.0, expression.evaluate(mapOf("PI" to 3.0),
            mapOf("pow" to ExpressionFunction(2) { it.sum() })))
    }

    @Test
    fun standardFunctionsEvaluateEveryArgumentInOrderBeforeErrors() {
        for (name in listOf("min", "max", "clamp", "pow")) {
            val visited = mutableListOf<Double>()
            val functions = ExpressionFunctions.standard + mapOf("mark" to ExpressionFunction(1) {
                visited.add(it[0]); it[0]
            })
            val arguments = if (name == "pow") "mark(3),mark(2)" else "mark(1),mark(3),mark(2)"
            val result = Expression.compile("$name($arguments)").evaluateResult(functions = functions)
            assertEquals(if (name == "pow") listOf(3.0, 2.0) else listOf(1.0, 3.0, 2.0), visited)
            if (name == "clamp") assertEquals(ExpressionEvaluationErrorCode.INVALID_CLAMP_BOUNDS,
                assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(result).error.code)
        }
    }
}
