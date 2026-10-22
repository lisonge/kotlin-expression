package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PathFunctionTest {
    @Test
    fun completeNamesAreCollectedSeparatelyAndBoundAtEvaluation() {
        val expression = assertIs<ExpressionResult.Success<CompiledExpression>>(
            Expression.compileResult("a.b.c(_math.sum2(1, 2, 3)) + a.b.c + a.b.c(1)"),
        ).value
        assertEquals(listOf("a.b.c", "_math.sum2"), expression.functions.toList())
        assertEquals(setOf("a.b.c"), expression.variables)
        for (factor in listOf(2.0, 3.0)) {
            val functions = mapOf(
                "a.b.c" to ExpressionFunction(1) { it[0] * factor },
                "_math.sum2" to ExpressionFunction(1..Int.MAX_VALUE) { it.sum() },
            )
            assertEquals(7 * factor + 5, expression.evaluate(mapOf("a.b.c" to 5.0), functions))
            assertEquals(ExpressionResult.Success(7 * factor + 5),
                expression.evaluateResult(mapOf("a.b.c" to 5.0), functions))
        }
    }

    @Test
    fun unknownFunctionsAndArityErrorsCoverFullPathBeforeArguments() {
        val expression = Expression.compile("  a.b.c(missing)")
        val bindings = listOf(
            mapOf("a.b" to ExpressionFunction(1) { 1.0 }, "a.B.c" to ExpressionFunction(1) { 2.0 }),
            mapOf("a.b.c" to ExpressionFunction(0) { 3.0 }),
        )
        for ((index, functions) in bindings.withIndex()) {
            val thrown = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = functions) }
            val returned = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(
                expression.evaluateResult(functions = functions),
            ).error
            for (error in listOf(thrown, returned)) {
                assertEquals(if (index == 0) ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION
                    else ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT, error.code)
                assertEquals(2, error.startIndex)
                assertEquals(7, error.endIndex)
            }
        }
    }

    @Test
    fun zeroArgumentCallsAndRenamedStandardFunctions() {
        assertEquals(4.0, Expression.compile("a.b.c ()").evaluate(
            functions = mapOf("a.b.c" to ExpressionFunction(0) { 4.0 }),
        ))
        val functions = mapOf("math.pow" to ExpressionFunctions.standard.getValue("pow"),
            "math.clamp" to ExpressionFunctions.standard.getValue("clamp"))
        assertEquals(8.0, Expression.compile("math.pow(2,3)").evaluate(functions = functions))
        val error = assertFailsWith<ExpressionEvaluationException> {
            Expression.compile("math.clamp(1,3,2)").evaluate(functions = functions)
        }
        assertEquals(ExpressionEvaluationErrorCode.INVALID_CLAMP_BOUNDS, error.code)
        assertEquals(0, error.startIndex)
        assertEquals(10, error.endIndex)
    }

    @Test
    fun longPathsRespectSourceLengthRatherThanSegmentDepth() {
        val path = "a.".repeat(8190) + "aa"
        for (source in listOf(path, "$path()")) {
            val expression = Expression.compile(source)
            assertEquals(source, expression.source)
            assertEquals(7.0, expression.evaluate(
                variables = mapOf(path to 7.0),
                functions = mapOf(path to ExpressionFunction(0) { 7.0 }),
            ))
        }
        for (source in listOf(path + "aaa", path + "a()")) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.LIMIT_EXCEEDED, error.code)
            assertEquals(16384, error.startIndex)
            assertEquals(source.length, error.endIndex)
        }
    }

    @Test
    fun invalidFunctionPathsFailCompilation() {
        for (source in listOf("a.()", "a..b()", "a.1()", "a. b()", "a .b()", ".a()")) {
            assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertIs<ExpressionResult.Failure<ExpressionParseException>>(Expression.compileResult(source))
        }
    }
}
