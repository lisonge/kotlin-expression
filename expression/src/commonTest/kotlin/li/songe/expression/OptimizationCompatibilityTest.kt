package li.songe.expression

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class OptimizationCompatibilityTest {
    @Test
    fun foldedAndRuntimeArithmeticAgreeAtFloatingPointBoundaries() {
        val literals = listOf("0", "-0", "5e-324", "-5e-324", "1e-300", "1e308", "-1e308", "1e999", "-1e999", "(1e999-1e999)")
        for (operator in listOf("+", "-", "*", "/", "%")) {
            val runtime = Expression.compile("a $operator b")
            for (a in literals) for (b in literals) {
                val source = "($a) $operator ($b)"
                val folded = Expression.compile(source)
                val bindings = mapOf("a" to Expression.compile(a).evaluate(), "b" to Expression.compile(b).evaluate())
                val expected = runtime.evaluateResult(bindings)
                val actual = folded.evaluateResult()
                when (expected) {
                    is ExpressionResult.Success -> assertEquals(expected.value.toBits(),
                        assertIs<ExpressionResult.Success<Double>>(actual).value.toBits(), source)
                    is ExpressionResult.Failure -> assertEquals(expected.error.code,
                        assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(actual).error.code, source)
                }
            }
        }
    }

    @Test
    fun standardFastPathMatchesArrayImplementationsAtFloatingPointBoundaries() {
        val reference = mapOf(
            "min" to ExpressionFunction(1..Int.MAX_VALUE) { args -> args.reduce { a, b -> min(a, b) } },
            "max" to ExpressionFunction(1..Int.MAX_VALUE) { args -> args.reduce { a, b -> max(a, b) } },
            "pow" to ExpressionFunction(2) { it[0].pow(it[1]) },
            "clamp" to ExpressionFunction(3) { min(max(it[0], it[1]), it[2]) },
        )
        val values = listOf(0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)
        for (name in listOf("min", "max", "pow", "clamp")) {
            val expression = Expression.compile(if (name == "clamp") "$name(a,b,b)" else "$name(a,b)")
            for (a in values) for (b in values) {
                val bindings = mapOf("a" to a, "b" to b)
                assertEquals(expression.evaluate(bindings, reference).toBits(),
                    expression.evaluate(bindings, ExpressionFunctions.standard).toBits(), "$name($a,$b)")
            }
        }
    }

    @Test
    fun validationAndArgumentFailuresRetainPriorityAndOriginalExceptions() {
        val cases = mapOf(
            "unknown(1/0)" to ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            "pow(1/0)" to ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
            "clamp(1/0,3,2)" to ExpressionEvaluationErrorCode.DIVISION_BY_ZERO,
            "clamp(1,3,1/0)" to ExpressionEvaluationErrorCode.DIVISION_BY_ZERO,
            "min(1/0,missing)" to ExpressionEvaluationErrorCode.DIVISION_BY_ZERO,
            "max(missing,1/0)" to ExpressionEvaluationErrorCode.MISSING_VARIABLE,
        )
        for ((source, code) in cases) {
            val expression = Expression.compile(source)
            val thrown = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = ExpressionFunctions.standard) }
            val returned = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(functions = ExpressionFunctions.standard),
            ).error
            assertEquals(code, thrown.code, source)
            assertEquals(thrown.code, returned.code)
            assertEquals(thrown.startIndex, returned.startIndex)
            assertEquals(thrown.endIndex, returned.endIndex)
        }
        val failure = assertFailsWith<ExpressionEvaluationException> { Expression.compile("1/0").evaluate() }
        for (source in listOf("min(0/1,fail(),missing)", "max(1e999-1e999,fail(),missing)",
            "clamp(1,3,fail())", "pow(1,fail())")) {
            val functions = ExpressionFunctions.standard + mapOf("fail" to ExpressionFunction(0) { throw failure })
            val expression = Expression.compile(source)
            assertSame(failure, assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = functions) })
            assertSame(failure, assertFailsWith<ExpressionEvaluationException> { expression.evaluateResult(functions = functions) })
        }
    }

    @Test
    fun bindingLookupOrderAndCountsAreUnchanged() {
        val visits = mutableListOf<String>()
        val variables = object : Map<String, Double> by emptyMap() {
            override fun get(key: String): Double { visits.add("variable:$key"); return 2.0 }
        }
        val standard = ExpressionFunctions.standard
        val functions = object : Map<String, ExpressionFunction> by standard {
            override fun get(key: String): ExpressionFunction? { visits.add("function:$key"); return standard[key] }
        }
        Expression.compile("min(x,x + (2 * 3),max(x,x))").evaluate(variables, functions)
        assertEquals(listOf("function:min", "variable:x", "variable:x", "function:max", "variable:x", "variable:x"), visits)
    }
}
