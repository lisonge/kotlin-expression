package li.songe.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PathVariableTest {
    @Test
    fun pathsUseCompleteCaseSensitiveNamesAndFirstAppearanceOrder() {
        val expression = Expression.compile("a.b.c + _a.b2.C + a.b.c + a.B.c + a")
        assertEquals(listOf("a.b.c", "_a.b2.C", "a.B.c", "a"), expression.variables.toList())
        assertEquals(emptySet(), expression.functions)
        assertEquals(15.0, expression.evaluate(mapOf(
            "a.b.c" to 2.0, "_a.b2.C" to 3.0, "a.B.c" to 7.0, "a" to 1.0,
        )))
    }

    @Test
    fun pathsWorkWithFunctionsNumbersAndRepeatedBindings() {
        val expression = Expression.compile("max(a.b.c * 0.5, 1e-3) + offset")
        assertEquals(setOf("max"), expression.functions)
        for (value in listOf(2.0, 4.0)) {
            assertEquals(value * 0.5 + 1, expression.evaluate(
                mapOf("a.b.c" to value, "offset" to 1.0), ExpressionFunctions.standard,
            ))
        }
        val compiled = assertIs<ExpressionResult.Success<CompiledExpression>>(Expression.compileResult("a.b.c"))
        assertEquals(ExpressionResult.Success(3.0), compiled.value.evaluateResult(mapOf("a.b.c" to 3.0)))
    }

    @Test
    fun missingPathReportsFullSourceRangeWithoutPrefixFallback() {
        val expression = Expression.compile("  a.b.c + 1")
        val bindings = mapOf("a" to 1.0, "a.b" to 2.0, "c" to 3.0)
        val thrown = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(bindings) }
        val result = assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(expression.evaluateResult(bindings))
        for (error in listOf(thrown, result.error)) {
            assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE, error.code)
            assertEquals(2, error.startIndex)
            assertEquals(7, error.endIndex)
        }
    }

    @Test
    fun invalidSegmentsReportTheSeparatingDot() {
        for (source in listOf("a.", "a..b", "a.1", "a. b", "a.\nb")) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.UNEXPECTED_TOKEN, error.code)
            assertEquals(1, error.startIndex)
            assertEquals(2, error.endIndex)
            val result = assertIs<ExpressionResult.Failure<ExpressionParseException>>(Expression.compileResult(source))
            assertEquals(error.code, result.error.code)
            assertEquals(error.startIndex, result.error.startIndex)
            assertEquals(error.endIndex, result.error.endIndex)
        }
    }

    @Test
    fun pathsAreContiguousNames() {
        for (source in listOf(".a", "a .b", "a\t.b", "a[b]")) {
            assertFailsWith<ExpressionParseException> { Expression.compile(source) }
        }
    }

    @Test
    fun laterInvalidSegmentsKeepOriginalSourcePositions() {
        for (source in listOf("  a.b.", "  a.b..c", "  a.b.1", "  a.b. c", "  a.b.()")) {
            val error = assertFailsWith<ExpressionParseException> { Expression.compile(source) }
            assertEquals(ExpressionParseErrorCode.UNEXPECTED_TOKEN, error.code, source)
            assertEquals(5, error.startIndex, source)
            assertEquals(6, error.endIndex, source)
        }
    }

    @Test
    fun numericLookingSegmentsRemainNamesAndNumericLiteralsStaySeparate() {
        assertEquals(5.5, Expression.compile("a.e3 + a.E2 * 1e-3 + 0.5").evaluate(
            mapOf("a.e3" to 3.0, "a.E2" to 2000.0),
        ))
        for (source in listOf("1.a", "1.2.a", "1e3.a", "a.2e3")) {
            assertFailsWith<ExpressionParseException>(source) { Expression.compile(source) }
        }
    }

    @Test
    fun manySegmentsDoNotAddParserOrTreeDepth() {
        val name = List(200) { "a" }.joinToString(".")
        val expression = Expression.compile(name)
        assertEquals(setOf(name), expression.variables)
        assertEquals(5.0, expression.evaluate(mapOf(name to 5.0)))
    }
}
