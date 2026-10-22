package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExpressionExceptionTest {
    @Test
    fun errorsHavePhaseSpecificCodesAndSharedRanges() {
        val parse = assertIs<ExpressionResult.Failure<ExpressionParseException>>(Expression.compileResult("1 +")).error
        val parseCode: ExpressionParseErrorCode = parse.code
        assertEquals(ExpressionParseErrorCode.UNEXPECTED_TOKEN, parseCode)
        val evaluation = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
            Expression.compile("x").evaluateResult(),
        ).error
        val evaluationCode: ExpressionEvaluationErrorCode = evaluation.code
        assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE, evaluationCode)
        fun describe(error: ExpressionException): String = when (error) {
            is ExpressionParseException -> "parse:${error.code.name}"
            is ExpressionEvaluationException -> "evaluation:${error.code.name}"
        }
        assertEquals("parse:UNEXPECTED_TOKEN", describe(parse))
        assertEquals("evaluation:MISSING_VARIABLE", describe(evaluation))
        assertEquals(3, parse.startIndex)
        assertEquals(3, parse.endIndex)
        assertEquals(0, evaluation.startIndex)
        assertEquals(1, evaluation.endIndex)
    }

    @Test
    fun constructorErrorsAreApiArgumentErrors() {
        val error = assertFailsWith<IllegalArgumentException> { ExpressionFunction(-1) { 0.0 } }
        assertTrue(error !is ExpressionException)
    }

    @Test
    fun parsingErrorsFromCallbacksAreNotEvaluationFailures() {
        val error = assertFailsWith<ExpressionParseException> { Expression.compile("(") }
        val functions = mapOf("f" to ExpressionFunction(0) { throw error })
        val expression = Expression.compile("f()")
        assertSame(error, assertFailsWith<ExpressionParseException> { expression.evaluate(functions = functions) })
        assertSame(error, assertFailsWith<ExpressionParseException> { expression.evaluateResult(functions = functions) })
    }
}
