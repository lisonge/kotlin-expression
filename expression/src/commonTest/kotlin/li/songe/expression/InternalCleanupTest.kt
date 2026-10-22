package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class InternalCleanupTest {
    @Test
    fun reentrantCallbacksKeepEvaluationErrorsOutsideResultFailures() {
        val inner = Expression.compile("1/0")
        val failure = assertFailsWith<ExpressionEvaluationException> { inner.evaluate() }
        val middle = Expression.compile("inner()")
        val outer = Expression.compile("outer() + missing")
        for (resultApi in listOf(false, true)) {
            val functions = mapOf("outer" to ExpressionFunction(0) {
                val bindings = mapOf("inner" to ExpressionFunction(0) { throw failure })
                if (resultApi) middle.evaluateResult(functions = bindings)
                else middle.evaluate(functions = bindings)
                0.0
            })
            assertSame(failure, assertFailsWith<ExpressionEvaluationException> {
                if (resultApi) outer.evaluateResult(functions = functions)
                else outer.evaluate(functions = functions)
            })
        }
    }

    @Test
    fun sourceDepthLimitsWinOverDeferredArithmeticErrors() {
        val chain = List(128) { "1" }.joinToString("+")
        for (operator in listOf("/", "%")) {
            val source = "($chain)$operator 0"
            val thrown = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            val returned = assertIs<ExpressionResult.Failure<ExpressionParseException>>(Expression.compileResult(source)).error
            assertEquals(ExpressionParseErrorCode.LIMIT_EXCEEDED, thrown.code)
            assertEquals(chain.length + 2, thrown.startIndex)
            assertEquals(thrown.code, returned.code)
            assertEquals(thrown.startIndex, returned.startIndex)
            assertEquals(thrown.endIndex, returned.endIndex)
        }
        val valid = List(127) { "1" }.joinToString("+")
        assertEquals(-127.0, Expression.compile("-($valid)").evaluate())
        assertEquals(128, Parser("-($valid)").parse().depth)
        val tooNested = "(".repeat(128) + "1" + ")".repeat(128)
        val error = assertFailsWith<ExpressionParseException> { Expression.compile(tooNested) }
        assertEquals(ExpressionParseErrorCode.LIMIT_EXCEEDED, error.code)
        assertEquals(128, error.startIndex)
    }

    @Test
    fun parsingStillPrecedesEvaluationAndCallbackFailuresStopLaterArguments() {
        for (source in listOf("1/0 + @", "f(@)")) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.INVALID_CHARACTER, error.code)
            assertEquals(source.indexOf('@'), error.startIndex)
        }
        val failure = IllegalStateException("first argument")
        var laterCalls = 0
        val functions = ExpressionFunctions.standard + mapOf(
            "fail" to ExpressionFunction(0) { throw failure },
            "later" to ExpressionFunction(0) { laterCalls++; 1.0 },
        )
        val expression = Expression.compile("min(fail(),later(),1/0)")
        assertSame(failure, assertFailsWith<IllegalStateException> { expression.evaluate(functions = functions) })
        assertSame(failure, assertFailsWith<IllegalStateException> { expression.evaluateResult(functions = functions) })
        assertEquals(0, laterCalls)
    }

    @Test
    fun callbackAndMapFailuresPreserveIdentityThroughBothApis() {
        val parse = assertFailsWith<ExpressionParseException> { Expression.compile("(") }
        val evaluation = assertFailsWith<ExpressionEvaluationException> { Expression.compile("1/0").evaluate() }
        for (failure in listOf(IllegalStateException("callback"), AssertionError("callback"), parse, evaluation)) {
            val variables = object : Map<String, Double> by emptyMap() {
                override fun get(key: String): Double = throw failure
            }
            val functions = object : Map<String, ExpressionFunction> by emptyMap() {
                override fun get(key: String): ExpressionFunction = throw failure
            }
            val callback = mapOf("f" to ExpressionFunction(0) { throw failure })
            for (resultApi in listOf(false, true)) {
                for (source in listOf("x", "f()", "min(1,f())")) {
                    val expression = Expression.compile(source)
                    val bindings = if (source == "min(1,f())") ExpressionFunctions.standard + callback else functions
                    assertSame(failure, assertFailsWith<Throwable> {
                        if (resultApi) expression.evaluateResult(variables, bindings)
                        else expression.evaluate(variables, bindings)
                    })
                }
            }
        }
    }

    @Test
    fun foldedUnaryAndBinaryNodesRetainDepthFailureLocations() {
        val chain = List(128) { "1" }.joinToString("+")
        assertIs<ExpressionNode.Number>(Parser(chain).parse())
        for ((source, index) in listOf("$chain+1" to chain.length, "-($chain)" to 0, "f($chain)" to 0)) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.LIMIT_EXCEEDED, error.code)
            assertEquals(index, error.startIndex)
            assertEquals(index + 1, error.endIndex)
        }
        for (source in listOf("1/(-0)", "1%(-0)")) {
            val expression = Expression.compile(source)
            val error = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult()).error
            assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, error.code)
            assertEquals(1, error.startIndex)
            assertEquals(2, error.endIndex)
        }
    }

    @Test
    fun functionSyntaxErrorsKeepTokenRanges() {
        for ((source, index) in listOf("f(" to 2, "f(,)" to 2, "f(1,)" to 4, "f(1" to 3, "f(g(),)" to 6)) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.UNEXPECTED_TOKEN, error.code)
            assertEquals(index, error.startIndex, source)
            assertEquals(if (index == source.length) index else index + 1, error.endIndex, source)
        }
    }
}
