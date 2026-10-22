package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExpressionResultTest {
    @Test
    fun successfulCompilationAndRepeatedEvaluation() {
        val result: ExpressionResult<CompiledExpression, ExpressionParseException> =
            Expression.compileResult("x*x+1")
        val expression = when (result) {
            is ExpressionResult.Success -> result.value
            is ExpressionResult.Failure -> throw result.error
        }
        assertEquals("x*x+1", expression.source)
        for (x in listOf(-2.0, 0.0, 3.0)) {
            val evaluated: ExpressionResult<Double, ExpressionEvaluationException> =
                expression.evaluateResult(mapOf("x" to x))
            val value = when (evaluated) {
                is ExpressionResult.Success -> evaluated.value
                is ExpressionResult.Failure -> throw evaluated.error
            }
            assertEquals(x * x + 1, value)
        }
        assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult())
        assertEquals(ExpressionResult.Success(5.0), expression.evaluateResult(mapOf("x" to 2.0)))
    }

    @Test
    fun compilationFailuresPreserveExceptionDetails() {
        val compiler = Expression
        for (source in listOf("1 @ 2", "1e+", "1+", "1".repeat(16_385))) {
            val thrown = assertFailsWith<ExpressionParseException> { compiler.compile(source) }
            val result = compiler.compileResult(source)
            val error: ExpressionParseException =
                assertIs<ExpressionResult.Failure<ExpressionParseException>>(result).error
            assertErrorEquals(thrown, error)
        }
        assertIs<ExpressionResult.Success<CompiledExpression>>(compiler.compileResult("1+2"))
    }

    @Test
    fun evaluationFailuresPreserveExceptionDetails() {
        val compiler = Expression
        for (source in listOf("x+1", "1/0", "1%-0", "clamp(1,3,2)", "abs(1)", "min()")) {
            val expression = compiler.compile(source)
            val thrown = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = ExpressionFunctions.standard) }
            val result = expression.evaluateResult(functions = ExpressionFunctions.standard)
            val error: ExpressionEvaluationException =
                assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(result).error
            assertErrorEquals(thrown, error)
        }
    }

    @Test
    fun nonFiniteValuesAreSuccessfulResults() {
        val compiler = Expression
        val nan = assertIs<ExpressionResult.Success<Double>>(compiler.compile("pow(-2,0.5)").evaluateResult(functions = ExpressionFunctions.standard))
        assertTrue(nan.value.isNaN())
        assertEquals(ExpressionResult.Success(Double.POSITIVE_INFINITY), compiler.compile("pow(10,1000)").evaluateResult(functions = ExpressionFunctions.standard))
        assertEquals(ExpressionResult.Success(3.0), compiler.compile("1+2").evaluateResult())
    }

    @Test
    fun unrelatedExceptionsFromBindingsPropagate() {
        val expression = Expression.compile("x")
        val failure = IllegalStateException("Binding lookup failed")
        val bindings = object : Map<String, Double> by emptyMap() {
            override fun get(key: String): Double? = throw failure
        }
        assertSame(failure, assertFailsWith<IllegalStateException> { expression.evaluateResult(bindings) })
    }

    @Test
    fun expressionExceptionsFromBothBindingMapsPropagateUnchanged() {
        val failure = assertFailsWith<ExpressionEvaluationException> { Expression.compile("1/0").evaluate() }
        val variables = object : Map<String, Double> by emptyMap() {
            override fun get(key: String): Double? = throw failure
        }
        val functions = object : Map<String, ExpressionFunction> by emptyMap() {
            override fun get(key: String): ExpressionFunction? = throw failure
        }
        val variableExpression = Expression.compile("x")
        val functionExpression = Expression.compile("f()")
        assertSame(failure, assertFailsWith<ExpressionEvaluationException> { variableExpression.evaluate(variables) })
        assertSame(failure, assertFailsWith<ExpressionEvaluationException> { variableExpression.evaluateResult(variables) })
        assertSame(failure, assertFailsWith<ExpressionEvaluationException> { functionExpression.evaluate(functions = functions) })
        assertSame(failure, assertFailsWith<ExpressionEvaluationException> { functionExpression.evaluateResult(functions = functions) })
    }

    private fun assertErrorEquals(expected: ExpressionException, actual: ExpressionException) {
        when (expected) {
            is ExpressionParseException -> assertEquals(expected.code, assertIs<ExpressionParseException>(actual).code)
            is ExpressionEvaluationException -> assertEquals(expected.code, assertIs<ExpressionEvaluationException>(actual).code)
        }
        assertEquals(expected.startIndex, actual.startIndex)
        assertEquals(expected.endIndex, actual.endIndex)
        assertEquals(expected.message, actual.message)
    }
}
