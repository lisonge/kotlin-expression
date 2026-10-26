package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArithmeticValidationTest {
    @Test
    fun detectsSignedAndFoldedZeroDivisorsWithoutKnowingTheDividend() {
        val divisors = listOf("0", "-0", "+0", "(2-2)", "-(2-2)", "(0*3)", "(0/2)", "(4%2)", "1e-999", "(1/1e999)")
        for (operator in listOf("/", "%")) {
            for (dividend in listOf("1", "x", "custom.value()")) {
                for (divisor in divisors) {
                    val source = "$dividend $operator $divisor"
                    val expression = assertIs<ExpressionResult.Success<CompiledExpression>>(Expression.compileResult(source)).value
                    val error = expression.validateArithmetic().single()
                    assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, error.code, source)
                    assertEquals(dividend.length + 1, error.startIndex, source)
                    assertEquals(dividend.length + 2, error.endIndex, source)
                    assertEquals(operator, source.substring(error.startIndex, error.endIndex), source)
                }
            }
        }
    }

    @Test
    fun collectsNestedAndRepeatedOperatorsInSourceOrderInsideInvalidCalls() {
        val source = "-unknown((1/0)/0, x%(2-2)) + 4/0 + f(1/(2%0))"
        val expression = Expression.compile(source)
        // The outer division in 1/(2%0) has no constant divisor; only the inner error is known.
        val positions = listOf(11, 14, 19, 30, 41)
        repeat(2) {
            val errors = expression.validateArithmetic()
            assertEquals(positions, errors.map { it.startIndex })
            assertEquals(positions.map { it + 1 }, errors.map { it.endIndex })
            assertTrue(errors.all { it.code == ExpressionEvaluationErrorCode.DIVISION_BY_ZERO })
        }
    }

    @Test
    fun leavesDynamicDivisorsAndNonzeroConstantsForEvaluation() {
        for (source in listOf(
            "42", "x", "-x", "(2+3)*4", "1/2", "1%-2", "1/1e-300",
            "1/x", "1/(x-x)", "1/(0*x)", "1/(f()-f())", "1/min(0,0)",
            "1/clamp(0,0,0)", "1/pow(0,2)", "clamp(1,3,2)", "min()", "unknown(x)",
            "1e999*1e999", "1e999-1e999", "1/(1e999-1e999)", "pow(-1,0.5)",
        )) {
            assertEquals(emptyList(), Expression.compile(source).validateArithmetic(), source)
        }
        val expression = Expression.compile("1/x")
        assertEquals(emptyList(), expression.validateArithmetic())
        assertEquals(0.5, expression.evaluate(mapOf("x" to 2.0)))
        val error = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(mapOf("x" to 0.0)) }
        assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, error.code)
    }

    @Test
    fun validationDoesNotExecuteFunctionsOrChangeEvaluationEffectsAndErrors() {
        for (operator in listOf("/", "%")) {
            val expression = Expression.compile("mark() + 1 $operator (2-2)")
            var calls = 0
            val functions = mapOf("mark" to ExpressionFunction(0) { calls++; 1.0 })
            val diagnostic = expression.validateArithmetic().single()
            assertEquals(0, calls)
            assertEquals(emptyList(), expression.validateBindings(functions = functions))
            val error = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(functions = functions),
            ).error
            assertEquals(1, calls)
            assertEquals(diagnostic.code, error.code)
            assertEquals(diagnostic.startIndex, error.startIndex)
            assertEquals(diagnostic.endIndex, error.endIndex)
            assertEquals(diagnostic.startIndex, expression.validateArithmetic().single().startIndex)
            assertEquals(1, calls)
        }
    }

    @Test
    fun validationDoesNotChangeEarlierBindingFailures() {
        for ((source, code) in listOf(
            "missing + 1/0" to ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            "unknown(1/0)" to ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            "pow(1/0)" to ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
        )) {
            val expression = Expression.compile(source)
            assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, expression.validateArithmetic().single().code)
            val error = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = ExpressionFunctions.standard) }
            assertEquals(code, error.code, source)
        }
    }
}
