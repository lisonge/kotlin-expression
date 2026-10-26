package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CustomFunctionTest {
    @Test
    fun variadicFunctionsAcceptZeroAndManyArguments() {
        val sum = ExpressionFunction(arity = 0..Int.MAX_VALUE) { it.sum() }
        val functions = mapOf("sum" to sum)
        assertEquals(0..Int.MAX_VALUE, sum.arity)
        assertEquals(0.0, Expression.compile("sum()").evaluate(functions = functions))
        assertEquals(6.0, Expression.compile("sum(1,2,3)").evaluate(functions = functions))
        assertEquals(ExpressionResult.Success(10.0),
            Expression.compile("sum(sum(1,2),3,4)").evaluateResult(functions = functions))
        assertEquals(200.0, Expression.compile("sum(${List(200) { "1" }.joinToString(",")})").evaluate(functions = functions))
    }

    @Test
    fun boundedArityChecksBothInclusiveEndpointsBeforeEvaluatingArguments() {
        var invoked = false
        var argumentEvaluated = false
        val functions = mapOf(
            "sum" to ExpressionFunction(arity = 1..3) { invoked = true; it.sum() },
            "mark" to ExpressionFunction(arity = 0) { argumentEvaluated = true; 1.0 },
        )
        for ((source, expected) in listOf("sum(1)" to 1.0, "sum(1,2)" to 3.0, "sum(1,2,3)" to 6.0)) {
            assertEquals(expected, Expression.compile(source).evaluate(functions = functions))
        }
        invoked = false
        for (source in listOf("  sum()", "  sum(mark(),2,3,4)")) {
            val expression = Expression.compile(source)
            val error = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = functions) }
            assertEquals(ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, error.code)
            assertEquals(2, error.startIndex)
            assertEquals(5, error.endIndex)
            val result = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(functions = functions))
            assertEquals(error.code, result.error.code)
        }
        assertTrue(!invoked)
        assertTrue(!argumentEvaluated)
    }

    @Test
    fun fixedConstructorAndRangeValidation() {
        assertEquals(2..2, ExpressionFunction(arity = 2) { it.sum() }.arity)
        assertEquals(0..0, ExpressionFunction(arity = 0) { 1.0 }.arity)
        for (range in listOf(-1..2, -3..-1, 2..1, IntRange.EMPTY)) {
            assertFailsWith<IllegalArgumentException> { ExpressionFunction(arity = range) { 0.0 } }
        }
        assertFailsWith<IllegalArgumentException> { ExpressionFunction(arity = -1) { 0.0 } }
        val functions = mapOf("min" to ExpressionFunction(arity = 0..Int.MAX_VALUE) { it.sum() })
        assertEquals(6.0, Expression.compile("min(1,2,3)").evaluate(functions = functions))
        assertEquals(ExpressionResult.Success(1.0), Expression.compile("min(1,2,3)").evaluateResult(functions = ExpressionFunctions.standard))
    }

    @Test
    fun referencedFunctionsAreCollectedInSourceOrder() {
        val expression = Expression.compile("outer(min(x,1),f(),min(2,3))+f+F()")
        assertEquals(listOf("outer", "min", "f", "F"), expression.functions.toList())
        assertEquals(setOf("x", "f"), expression.variables)
        assertEquals(emptySet(), Expression.compile("min+1").functions)
        assertEquals(setOf("other"), Expression.compile("other()").functions)
        val names = expression.functions
        assertSame(names, expression.functions)
        assertEquals(listOf("outer", "min", "f", "F"), expression.functions.toList())
        val result = assertIs<ExpressionResult.Success<CompiledExpression>>(Expression.compileResult("unknown()"))
        assertEquals(setOf("unknown"), result.value.functions)
        result.value.evaluateResult(functions = ExpressionFunctions.standard)
        result.value.evaluate(functions = mapOf("unknown" to ExpressionFunction(0) { 1.0 }))
        assertEquals(setOf("unknown"), result.value.functions)
    }

    @Test
    fun bindingsAreIndependentOfCompilationAndEachOther() {
        val expression = Expression.compile("lerp(start,end,progress)")
        assertEquals(setOf("start", "end", "progress"), expression.variables)
        val bindings = mapOf("start" to 10.0, "end" to 20.0, "progress" to 0.5)
        val lerp = ExpressionFunction(3) { it[0] + (it[1] - it[0]) * it[2] }
        assertEquals(15.0, expression.evaluate(bindings, mapOf("lerp" to lerp)))
        assertEquals(ExpressionResult.Success(15.0), expression.evaluateResult(bindings, mapOf("lerp" to lerp)))
        assertEquals(99.0, expression.evaluate(bindings, mapOf("lerp" to ExpressionFunction(3) { 99.0 })))
        val missing = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(bindings))
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, missing.error.code)
    }

    @Test
    fun suppliedFunctionsOverrideBuiltinsIncludingArity() {
        val expression = Expression.compile("min(10,20)")
        assertEquals(10.0, expression.evaluate(functions = ExpressionFunctions.standard))
        assertEquals(30.0, expression.evaluate(functions = mapOf("min" to ExpressionFunction(2) { it.sum() })))
        assertEquals(10.0, expression.evaluate(functions = ExpressionFunctions.standard))
        assertEquals(7.0, Expression.compile("min()").evaluate(functions = mapOf("min" to ExpressionFunction(0) { 7.0 })))
        val failure = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
            expression.evaluateResult(functions = mapOf("min" to ExpressionFunction(0) { 7.0 })),
        )
        assertEquals(ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, failure.error.code)
    }

    @Test
    fun functionErrorsOccurDuringEvaluationAndKeepSourceRanges() {
        for ((source, code) in listOf(
            "  abs(1)" to ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            "  min()" to ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
            "  max()" to ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
            "  clamp(1,2)" to ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT,
        )) {
            val compiled = assertIs<ExpressionResult.Success<CompiledExpression>>(Expression.compileResult(source)).value
            val error = assertFailsWith<ExpressionEvaluationException> { compiled.evaluate(functions = ExpressionFunctions.standard) }
            val failure = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(compiled.evaluateResult(functions = ExpressionFunctions.standard))
            assertEquals(code, error.code)
            assertEquals(2, error.startIndex)
            assertEquals(source.indexOf('('), error.endIndex)
            assertEquals(error.code, failure.error.code)
            assertEquals(error.startIndex, failure.error.startIndex)
            assertEquals(error.endIndex, failure.error.endIndex)
        }
    }

    @Test
    fun argumentsAreEvaluatedLeftToRightAfterFunctionValidation() {
        val visited = mutableListOf<Double>()
        val functions = mapOf(
            "mark" to ExpressionFunction(1) { visited.add(it[0]); it[0] },
            "combine" to ExpressionFunction(2) { it[0] * 10 + it[1] },
        )
        assertEquals(12.0, Expression.compile("combine(mark(1),mark(2))").evaluate(functions = functions))
        assertEquals(listOf(1.0, 2.0), visited)
        visited.clear()
        Expression.compile("combine(mark(1))").evaluateResult(functions = functions)
        Expression.compile("unknown(mark(2))").evaluateResult(functions = functions)
        assertTrue(visited.isEmpty())
        assertEquals(2.0, Expression.compile("max(mark(1),mark(2))").evaluate(functions = ExpressionFunctions.standard + functions))
        assertEquals(listOf(1.0, 2.0), visited)
    }

    @Test
    fun zeroArgumentsNamesAndArgumentArrays() {
        val arrays = mutableListOf<DoubleArray>()
        val function = ExpressionFunction(1) { arrays.add(it); it[0] }
        val expression = Expression.compile("f(f)+f(2)")
        assertEquals(setOf("f"), expression.variables)
        assertEquals(5.0, expression.evaluate(mapOf("f" to 3.0), mapOf("f" to function)))
        assertTrue(arrays[0] !== arrays[1])
        arrays[0][0] = 100.0
        assertEquals(5.0, expression.evaluate(mapOf("f" to 3.0), mapOf("f" to function)))
        val error = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
            Expression.compile("F()").evaluateResult(functions = mapOf("f" to ExpressionFunction(0) { 1.0 })),
        )
        assertEquals(ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION, error.error.code)
        assertEquals(ExpressionResult.Success(Double.POSITIVE_INFINITY),
            Expression.compile("f()").evaluateResult(functions = mapOf("f" to ExpressionFunction(0) { Double.POSITIVE_INFINITY })))
    }

    @Test
    fun customExceptionsPropagateUnchangedThroughBothApis() {
        val domainError = assertFailsWith<ExpressionEvaluationException> { Expression.compile("1/0").evaluate(functions = ExpressionFunctions.standard) }
        for (error in listOf(IllegalStateException("callback"), domainError)) {
            val functions = mapOf("fail" to ExpressionFunction(0) { throw error })
            val expression = Expression.compile("max(1,fail())")
            assertSame(error, assertFailsWith<Throwable> { expression.evaluate(functions = ExpressionFunctions.standard + functions) })
            assertSame(error, assertFailsWith<Throwable> { expression.evaluateResult(functions = ExpressionFunctions.standard + functions) })
        }
    }
}
