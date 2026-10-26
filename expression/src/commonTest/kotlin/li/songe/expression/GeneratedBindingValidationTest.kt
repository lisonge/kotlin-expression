package li.songe.expression

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedBindingValidationTest {
    private val variableNames = listOf("x", "data.y", "f", "math.g")
    private val functionNames = listOf("f", "math.g", "_sum2", "F")
    private val ranges = listOf(0..0, 1..1, 2..2, 1..3, 0..2, 2..Int.MAX_VALUE)

    private data class Occurrence(val name: String, val start: Int, val end: Int, val argumentCount: Int?)
    private data class Sample(val source: String, val occurrences: List<Occurrence>)
    private data class Diagnostic(val code: ExpressionEvaluationErrorCode, val start: Int, val end: Int)

    private fun generate(random: Random): Sample {
        val source = StringBuilder()
        val occurrences = mutableListOf<Occurrence>()

        fun whitespace() {
            source.append(listOf("", " ", "\t", "\r\n")[random.nextInt(4)])
        }

        fun name(text: String, argumentCount: Int? = null) {
            val start = source.length
            source.append(text)
            occurrences.add(Occurrence(text, start, source.length, argumentCount))
        }

        fun expression(depth: Int) {
            when (random.nextInt(if (depth == 0) 2 else 5)) {
                0 -> source.append(listOf("0", "1.5", "2e3", "(1 / 0)")[random.nextInt(4)])
                1 -> name(variableNames[random.nextInt(variableNames.size)])
                2 -> {
                    source.append(if (random.nextBoolean()) "-(" else "+(")
                    expression(depth - 1)
                    source.append(')')
                }
                3 -> {
                    source.append('(')
                    expression(depth - 1)
                    whitespace()
                    source.append("+-*/%"[random.nextInt(5)])
                    whitespace()
                    expression(depth - 1)
                    source.append(')')
                }
                else -> {
                    val argumentCount = random.nextInt(5)
                    name(functionNames[random.nextInt(functionNames.size)], argumentCount)
                    whitespace()
                    source.append('(')
                    repeat(argumentCount) { index ->
                        if (index > 0) source.append(',')
                        whitespace()
                        expression(depth - 1)
                    }
                    whitespace()
                    source.append(')')
                }
            }
        }

        whitespace()
        expression(4)
        whitespace()
        return Sample(source.toString(), occurrences)
    }

    // The oracle filters a flat ledger captured while emitting source, independently
    // of the production lexer, parser, AST and validation implementation.
    private fun expectedErrors(
        occurrences: List<Occurrence>,
        variables: Set<String>,
        functions: Map<String, IntRange>,
    ): List<Diagnostic> = occurrences.mapNotNull { occurrence ->
        val count = occurrence.argumentCount
        val range = functions[occurrence.name]
        val code = if (count == null) {
            if (occurrence.name in variables) null else ExpressionEvaluationErrorCode.MISSING_VARIABLE
        } else if (range == null) {
            ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION
        } else if (count < range.first || count > range.last) {
            ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT
        } else {
            null
        }
        code?.let { Diagnostic(it, occurrence.start, occurrence.end) }
    }

    @Test
    fun generatedBindingsMatchCompleteIndependentDiagnosticLists() {
        val seenCodes = mutableSetOf<ExpressionEvaluationErrorCode>()
        val seenArgumentCounts = mutableSetOf<Int>()
        val seenRanges = mutableSetOf<IntRange>()
        val seenArityBoundaries = mutableSetOf<String>()
        var sawRepeatedErrors = false
        var sawNestedCallErrors = false
        var sawSameNameWithDifferentCounts = false

        for (seed in listOf(20261023, 71329)) {
            val random = Random(seed)
            repeat(200) { case ->
                val sample = generate(random)
                val context = "seed=$seed case=$case source=${sample.source}"
                val expression = Expression.compile(sample.source)
                val calls = sample.occurrences.filter { it.argumentCount != null }
                seenArgumentCounts.addAll(calls.map { it.argumentCount!! })
                sawSameNameWithDifferentCounts = sawSameNameWithDifferentCounts || calls.groupBy { it.name }
                    .values.any { references -> references.map { it.argumentCount }.distinct().size > 1 }

                repeat(6) { bindingCase ->
                    val variables = when (bindingCase) {
                        0, 3, 4 -> variableNames.toSet()
                        1 -> emptySet()
                        else -> variableNames.filter { random.nextBoolean() }.toSet()
                    }
                    val functionArities = functionNames.mapNotNull { name ->
                        when {
                            bindingCase == 1 -> null
                            (bindingCase == 3 || bindingCase == 5) && random.nextBoolean() -> null
                            bindingCase == 0 || bindingCase == 2 || bindingCase == 3 -> name to (0..Int.MAX_VALUE)
                            else -> name to ranges[random.nextInt(ranges.size)]
                        }
                    }.toMap()
                    for (call in calls) {
                        val range = functionArities[call.name] ?: continue
                        val count = call.argumentCount!!
                        seenRanges.add(range)
                        if (count == range.first) seenArityBoundaries.add("lower")
                        if (count == range.last) seenArityBoundaries.add("upper")
                        if (count < range.first) seenArityBoundaries.add("below")
                        if (count > range.last) seenArityBoundaries.add("above")
                    }
                    val functions = functionArities.mapValues { (_, arity) ->
                        ExpressionFunction(arity) { error("Validation executed a function: $context") }
                    }
                    val expected = expectedErrors(sample.occurrences, variables, functionArities)
                    val detail = "$context bindingCase=$bindingCase variables=$variables functions=$functionArities"
                    val actual = expression.validateBindings(variables, functions)
                        .map { Diagnostic(it.code, it.startIndex, it.endIndex) }
                    assertEquals(expected, actual, detail)

                    seenCodes.addAll(expected.map { it.code })
                    val namesInErrors = expected.map { sample.source.substring(it.start, it.end) }
                    sawRepeatedErrors = sawRepeatedErrors || namesInErrors.size != namesInErrors.distinct().size
                    // This child call precedes its parent's first closing parenthesis.
                    // In the empty-binding case both must appear in the error list.
                    if (bindingCase == 1) {
                        sawNestedCallErrors = sawNestedCallErrors || calls.zipWithNext().any { (outer, inner) ->
                            inner.start < sample.source.indexOf(')', outer.end)
                        }
                    }
                }
                // Reusing the compiled expression after failures retains no validation state.
                assertEquals(emptyList(), expression.validateBindings(variableNames.toSet(),
                    functionNames.associateWith { ExpressionFunction(0..Int.MAX_VALUE) { error("Executed") } }), context)
            }
        }
        assertEquals(setOf(ExpressionEvaluationErrorCode.MISSING_VARIABLE, ExpressionEvaluationErrorCode.UNKNOWN_FUNCTION,
            ExpressionEvaluationErrorCode.INVALID_ARGUMENT_COUNT), seenCodes)
        assertEquals((0..4).toSet(), seenArgumentCounts)
        assertTrue(seenRanges.containsAll(ranges), "Generated calls must exercise every arity range")
        assertEquals(setOf("lower", "upper", "below", "above"), seenArityBoundaries)
        assertTrue(sawRepeatedErrors, "Generated errors must include repeated references")
        assertTrue(sawNestedCallErrors, "Generated errors must include nested calls")
        assertTrue(sawSameNameWithDifferentCounts, "Generated calls must reuse a name with different argument counts")
    }
}
