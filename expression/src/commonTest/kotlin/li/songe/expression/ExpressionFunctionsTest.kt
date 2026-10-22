package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ExpressionFunctionsTest {
    @Test
    fun standardFunctionsRequireExplicitBindings() {
        for (source in listOf("min(1,2)", "max(1,2)", "clamp(1,0,2)", "pow(2,3)")) {
            val expression = Expression.compile(source)
            val error = assertFailsWith<ExpressionEvaluationException> { expression.evaluate() }
            assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, error.code)
            assertEquals(0, error.startIndex)
            assertEquals(source.indexOf('('), error.endIndex)
            assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
                assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult()).error.code)
            assertIs<ExpressionResult.Success<Double>>(expression.evaluateResult(functions = ExpressionFunctions.standard))
        }
        assertEquals(3.0, Expression.compile("1+2").evaluate())
    }

    @Test
    fun customBindingsDoNotImplicitlyEnableStandardFunctions() {
        val expression = Expression.compile("min(custom(),2)")
        val custom = mapOf("custom" to ExpressionFunction(0) { 1.0 })
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(functions = custom)).error.code)
        assertEquals(ExpressionResult.Success(1.0),
            expression.evaluateResult(functions = ExpressionFunctions.standard + custom))
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult()).error.code)
    }

    @Test
    fun standardCollectionCanBeFilteredOverriddenAndRenamed() {
        val standard = ExpressionFunctions.standard
        assertEquals(setOf("min", "max", "clamp", "pow"), standard.keys)
        val expression = Expression.compile("min(1,2)")
        val custom = mapOf("min" to ExpressionFunction(2) { it.sum() })
        assertEquals(3.0, expression.evaluate(functions = standard + custom))
        assertEquals(1.0, expression.evaluate(functions = custom + standard))
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(functions = standard - "min")).error.code)
        assertSame(standard, ExpressionFunctions.standard)
        assertEquals(1.0, expression.evaluate(functions = ExpressionFunctions.standard))
        val clamp = Expression.compile("  bound(0,2,1)")
        val functions = mapOf("bound" to ExpressionFunctions.standard.getValue("clamp"))
        val error = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(clamp.evaluateResult(functions = functions)).error
        assertEquals(ExpressionEvaluationErrorCode.INVALID_CLAMP_BOUNDS, error.code)
        assertEquals(2, error.startIndex)
        assertEquals(7, error.endIndex)
    }
}
