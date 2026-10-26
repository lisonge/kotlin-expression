package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class BindingValidationTest {
    @Test
    fun nestedInvalidCallsContinueThroughEveryRepeatedOccurrence() {
        val source = "f(f(x),g(x)) + f(x)"
        val expression = Expression.compile(source)
        val bindings = mapOf("f" to ExpressionFunction(0) { error("Executed") })
        val errors = expression.validateBindings(functions = bindings)
        assertEquals(listOf(
            ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
            ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
            ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
            ExpressionEvaluationErrorCode.MISSING_VARIABLE,
        ), errors.map { it.code })
        assertEquals(listOf(0, 2, 4, 7, 9, 15, 17), errors.map { it.startIndex })
        assertEquals(listOf(1, 3, 5, 8, 10, 16, 18), errors.map { it.endIndex })
        assertEquals(errors.map { it.code }, expression.validateBindings(functions = bindings).map { it.code })
        // Dynamic evaluation still stops at the outer call, before visiting its arguments.
        val thrown = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = bindings) }
        assertEquals(errors.first().code, thrown.code)
        assertEquals(0, thrown.startIndex)
    }

    @Test
    fun collectionFailureAfterCollectedErrorsStillPropagatesAndLeavesNoState() {
        val failure = assertFailsWith<ExpressionEvaluationException> { Expression.compile("1/0").evaluate() }
        val visits = mutableListOf<String>()
        val functions = object : Map<String, ExpressionFunction> by emptyMap() {
            override fun get(key: String): ExpressionFunction? {
                visits.add(key)
                if (key == "broken") throw failure
                return null
            }
        }
        val expression = Expression.compile("unknown(x) + broken() + later()")
        assertSame(failure, assertFailsWith<ExpressionEvaluationException> {
            expression.validateBindings(functions = functions)
        })
        assertEquals(listOf("unknown", "broken"), visits)
        assertEquals(listOf(0, 8, 13, 24), expression.validateBindings().map { it.startIndex })
    }

    @Test
    fun collectsNestedAndRepeatedErrorsInSourceOrder() {
        val source = "f(x) + clamp(y, 2) + f(x)"
        val expression = Expression.compile(source)
        val errors = expression.validateBindings(functions = ExpressionFunctions.standard)
        assertEquals(listOf(
            ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, ExpressionEvaluationErrorCode.MISSING_VARIABLE,
            ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, ExpressionEvaluationErrorCode.MISSING_VARIABLE,
        ), errors.map { it.code })
        assertEquals(listOf(0, 2, 7, 13, 21, 23), errors.map { it.startIndex })
        assertEquals(listOf("f", "x", "clamp", "y", "f", "x"), errors.map { source.substring(it.startIndex, it.endIndex) })
        assertEquals(emptyList(), expression.validateBindings(setOf("x", "y"), mapOf(
            "f" to ExpressionFunction(1) { error("Executed") },
            "clamp" to ExpressionFunction(2) { error("Executed") },
        )))
        assertEquals(6, errors.size)
        val nested = Expression.compile("a.b(c.d(x),x)").validateBindings()
        assertEquals(listOf(0, 4, 8, 11), nested.map { it.startIndex })
    }

    @Test
    fun validationUsesNamesWithoutReadingValuesOrExecutingFunctions() {
        val values = object : Map<String, Double> by mapOf("a.b" to 2.0) {
            override fun get(key: String): Double = error("Variable value read")
        }
        val expression = Expression.compile("-a.b + custom.sum(a.b,1/0) + clamp(1,3,2)")
        val functions = ExpressionFunctions.standard + mapOf(
            "custom.sum" to ExpressionFunction(0..Int.MAX_VALUE) { error("Function executed") },
        )
        assertEquals(emptyList(), expression.validateBindings(values.keys, functions))
        assertEquals(emptyList(), Expression.compile("1/0").validateBindings())
        assertEquals(emptyList(), Expression.compile("(2+3)*4").validateBindings())
        assertEquals(emptyList(), Expression.compile("PI + E").validateBindings(ExpressionConstants.standard.keys))
    }

    @Test
    fun failuresRetainFullNameRangesAndVisitOrder() {
        val cases = listOf(
            Triple("  a.b.c", ExpressionEvaluationErrorCode.MISSING_VARIABLE, 2 to 7),
            Triple("  math.f(x)", ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, 2 to 8),
            Triple("  clamp(missing,2)", ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, 2 to 7),
            Triple("  min(x,missing)", ExpressionEvaluationErrorCode.MISSING_VARIABLE, 8 to 15),
            Triple("1/0 + missing", ExpressionEvaluationErrorCode.MISSING_VARIABLE, 6 to 13),
            Triple("missing + unknown()", ExpressionEvaluationErrorCode.MISSING_VARIABLE, 0 to 7),
        )
        for ((source, code, range) in cases) {
            val error = Expression.compile(source).validateBindings(setOf("x", "a.b"), ExpressionFunctions.standard).first()
            assertEquals(code, error.code, source)
            assertEquals(range.first, error.startIndex, source)
            assertEquals(range.second, error.endIndex, source)
        }
    }

    @Test
    fun everyCallIsCheckedIncludingRepeatedNamesAndVariadicBounds() {
        val functions = mapOf("f" to ExpressionFunction(1..2) { it.sum() })
        assertEquals(emptyList(), Expression.compile("f(1)+f(1,2)").validateBindings(functions = functions))
        for (source in listOf("f(1)+f()", "f(1)+f(1,2,3)")) {
            val error = Expression.compile(source).validateBindings(functions = functions).single()
            assertEquals(ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, error.code)
            assertEquals(5, error.startIndex)
            assertEquals(6, error.endIndex)
        }
        assertEquals(emptyList(), Expression.compile("f()").validateBindings(functions = mapOf("f" to ExpressionFunction(0) { 0.0 })))
    }

    @Test
    fun validationDoesNotStoreBindingsOrEnableDefaults() {
        val expression = Expression.compile("a.b(a.b)")
        val first = mapOf("a.b" to ExpressionFunction(1) { it[0] })
        assertEquals(emptyList(), expression.validateBindings(setOf("a.b", "extra"), first))
        assertEquals(6.0, expression.evaluate(mapOf("a.b" to 3.0),
            mapOf("a.b" to ExpressionFunction(1) { it[0] * 2 })))
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            expression.validateBindings(setOf("a.b")).single().code)
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            assertFailsWith<ExpressionEvaluationException> { expression.evaluate(mapOf("a.b" to 3.0)) }.code)
        assertEquals(setOf("a.b"), expression.variables)
        assertEquals(setOf("a.b"), expression.functions)
    }

    @Test
    fun collectionExceptionsPropagateUnchanged() {
        val engineError = assertFailsWith<ExpressionEvaluationException> { Expression.compile("1/0").evaluate() }
        for (failure in listOf(IllegalStateException("collection"), engineError)) {
            val names = object : Set<String> by emptySet() {
                override fun contains(element: String): Boolean = throw failure
            }
            val functions = object : Map<String, ExpressionFunction> by emptyMap() {
                override fun get(key: String): ExpressionFunction = throw failure
            }
            assertSame(failure, assertFailsWith<Throwable> { Expression.compile("x").validateBindings(names) })
            assertSame(failure, assertFailsWith<Throwable> { Expression.compile("f()").validateBindings(functions = functions) })
        }
    }
}
