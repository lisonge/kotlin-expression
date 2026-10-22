package li.songe.expression

import kotlin.math.E
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ExpressionConstantsTest {
    @Test
    fun constantsRequireExplicitBindingsAndRemainVariables() {
        assertEquals(mapOf("PI" to PI, "E" to E), ExpressionConstants.standard)
        for (name in listOf("PI", "E")) {
            val expression = assertIs<ExpressionResult.Success<CompiledExpression>>(
                Expression.compileResult("  $name"),
            ).value
            assertEquals(setOf(name), expression.variables)
            assertEquals(emptySet(), expression.functions)
            val thrown = assertFailsWith<ExpressionEvaluationException> { expression.evaluate() }
            val returned = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(functions = ExpressionFunctions.standard),
            ).error
            for (error in listOf(thrown, returned)) {
                assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE, error.code)
                assertEquals(2, error.startIndex)
                assertEquals(2 + name.length, error.endIndex)
            }
            val expected = ExpressionConstants.standard.getValue(name)
            assertEquals(expected, expression.evaluate(ExpressionConstants.standard))
            assertEquals(ExpressionResult.Success(expected), expression.evaluateResult(ExpressionConstants.standard))
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult())
        }
    }

    @Test
    fun constantsCombineWithVariablesAndExplicitFunctions() {
        val expression = Expression.compile("PI * pow(radius, 2) + E")
        assertEquals(setOf("PI", "radius", "E"), expression.variables)
        val variables = ExpressionConstants.standard + mapOf("radius" to 3.0)
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(variables),
            ).error.code)
        assertEquals(PI * 9 + E, expression.evaluate(variables, ExpressionFunctions.standard))
    }

    @Test
    fun sharedConstantsCanBeCombinedFilteredAndRenamed() {
        val standard = ExpressionConstants.standard
        val expression = Expression.compile("PI")
        assertEquals(3.0, expression.evaluate(standard + mapOf("PI" to 3.0)))
        assertEquals(PI, expression.evaluate(mapOf("PI" to 3.0) + standard))
        assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(standard - "PI"),
            ).error.code)
        assertEquals(PI + E, Expression.compile("math.PI + math.E").evaluate(
            standard.mapKeys { (name, _) -> "math.$name" },
        ))
        for (name in listOf("pi", "e")) {
            assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE,
                assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                    Expression.compile(name).evaluateResult(standard),
                ).error.code)
        }
        assertSame(standard, ExpressionConstants.standard)
        assertEquals(mapOf("PI" to PI, "E" to E), ExpressionConstants.standard)
    }
}
