package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuiltinFunctionTest {
    @Test
    fun minAndMaxAcceptOneOrManyArguments() {
        for ((source, expected) in mapOf(
            "min(7)" to 7.0, "max(-3)" to -3.0,
            "min(4,-2,8,0)" to -2.0, "max(4,-2,8,0)" to 8.0,
            "min(max(1,2,3),4,5)" to 3.0,
        )) {
            val expression = Expression.compile(source)
            assertEquals(expected, expression.evaluate(functions = ExpressionFunctions.standard))
            assertEquals(ExpressionResult.Success(expected), expression.evaluateResult(functions = ExpressionFunctions.standard))
        }
        val arguments = (1..200).joinToString(",")
        assertEquals(1.0, Expression.compile("min($arguments)").evaluate(functions = ExpressionFunctions.standard))
        assertEquals(200.0, Expression.compile("max($arguments)").evaluate(functions = ExpressionFunctions.standard))
    }

    @Test
    fun emptyCallsAndClampArityRemainEvaluationErrors() {
        for (source in listOf(" min()", " max()", " clamp(1)", " clamp(1,2,3,4)")) {
            val expression = Expression.compile(source)
            val error = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = ExpressionFunctions.standard) }
            assertEquals(ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, error.code)
            assertEquals(1, error.startIndex)
            assertEquals(source.indexOf('('), error.endIndex)
            val failure = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(functions = ExpressionFunctions.standard))
            assertEquals(error.code, failure.error.code)
        }
        assertEquals(2.0, Expression.compile("clamp(1,2,3)").evaluate(functions = ExpressionFunctions.standard))
    }

    @Test
    fun floatingPointSemanticsArePreserved() {
        for (name in listOf("min", "max")) {
            for (args in listOf("nan", "nan,1,2", "1,nan,2", "1,2,nan")) {
                assertTrue(Expression.compile("$name($args)").evaluate(mapOf("nan" to Double.NaN), ExpressionFunctions.standard).isNaN())
            }
            assertEquals((-0.0).toBits(), Expression.compile("$name(-0)").evaluate(functions = ExpressionFunctions.standard).toBits())
        }
        assertEquals(Double.NEGATIVE_INFINITY, Expression.compile("min(1,-1e999,1e999)").evaluate(functions = ExpressionFunctions.standard))
        assertEquals(Double.POSITIVE_INFINITY, Expression.compile("max(1,-1e999,1e999)").evaluate(functions = ExpressionFunctions.standard))
        for (args in listOf("0,-0,0", "-0,0,-0")) {
            assertEquals((-0.0).toBits(), Expression.compile("min($args)").evaluate(functions = ExpressionFunctions.standard).toBits())
            assertEquals(0.0.toBits(), Expression.compile("max($args)").evaluate(functions = ExpressionFunctions.standard).toBits())
        }
    }

    @Test
    fun allArgumentsAreEvaluatedLeftToRightEvenWithNaN() {
        for (name in listOf("min", "max")) {
            val seen = mutableListOf<Double>()
            val functions = mapOf("mark" to ExpressionFunction(1) {
                seen.add(it[0])
                if (it[0] == 1.0) Double.NaN else it[0]
            })
            assertTrue(Expression.compile("$name(mark(1),mark(2),mark(3))").evaluate(functions = ExpressionFunctions.standard + functions).isNaN())
            assertEquals(listOf(1.0, 2.0, 3.0), seen)
        }
    }
}
