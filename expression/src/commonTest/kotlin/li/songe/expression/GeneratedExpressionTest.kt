package li.songe.expression

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GeneratedExpressionTest {
    // The oracle uses Kotlin arithmetic and closures, never the engine's parser or nodes.
    private class ZeroDivisor : RuntimeException()
    private class Sample(
        val source: String,
        val variables: List<String> = emptyList(),
        val functions: List<String> = emptyList(),
        val compute: (Map<String, Double>) -> Double,
    )

    private fun generate(random: Random, depth: Int): Sample {
        if (depth == 0 || random.nextInt(5) == 0) {
            if (random.nextBoolean()) {
                val name = listOf("x", "data.y", "_z.a2")[random.nextInt(3)]
                return Sample(name, listOf(name)) { it.getValue(name) }
            }
            val value = random.nextInt(0, 17)
            val literal = when (random.nextInt(3)) {
                0 -> "$value"
                1 -> "$value.0"
                else -> "${value}e0"
            }
            return Sample(literal) { value.toDouble() }
        }
        val left = generate(random, depth - 1)
        if (random.nextInt(5) == 0) {
            val negative = random.nextBoolean()
            return Sample("${if (negative) "-" else "+"}(${left.source})", left.variables, left.functions) {
                val value = left.compute(it)
                if (negative) -value else value
            }
        }
        val right = generate(random, depth - 1)
        if (random.nextInt(4) == 0) {
            val name = listOf("min", "max", "custom.sum")[random.nextInt(3)]
            val arguments = listOf(left, right, generate(random, depth - 1))
            return Sample("$name(${arguments.joinToString(", ") { it.source }})",
                arguments.flatMap { it.variables }, listOf(name) + arguments.flatMap { it.functions }) { bindings ->
                val a = arguments[0].compute(bindings)
                val b = arguments[1].compute(bindings)
                val c = arguments[2].compute(bindings)
                when (name) {
                    "min" -> min(min(a, b), c)
                    "max" -> max(max(a, b), c)
                    else -> (a + b) + c
                }
            }
        }
        val operator = "+-*/%"[random.nextInt(5)]
        val space = listOf("", " ", "\t", "\n")[random.nextInt(4)]
        return Sample("(${left.source}$space$operator$space${right.source})",
            left.variables + right.variables, left.functions + right.functions) { bindings ->
            val a = left.compute(bindings)
            val b = right.compute(bindings)
            when (operator) {
                '+' -> a + b
                '-' -> a - b
                '*' -> a * b
                '/' -> { if (b == 0.0) throw ZeroDivisor(); a / b }
                else -> { if (b == 0.0) throw ZeroDivisor(); a % b }
            }
        }
    }

    @Test
    fun generatedTreesAgreeWithIndependentCalculationsAndNameDiscovery() {
        val seed = 20261022
        val random = Random(seed)
        val functions = ExpressionFunctions.standard + mapOf(
            "custom.sum" to ExpressionFunction(3) { (it[0] + it[1]) + it[2] },
        )
        val edgeValues = listOf(0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE,
            Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)
        repeat(600) { case ->
            val sample = generate(random, 4)
            val context = "seed=$seed case=$case source=${sample.source}"
            val expression = assertIs<ExpressionResult.Success<CompiledExpression>>(
                Expression.compileResult(sample.source), context,
            ).value
            assertEquals(sample.variables.distinct(), expression.variables.toList(), context)
            assertEquals(sample.functions.distinct(), expression.functions.toList(), context)
            repeat(3) { bindingCase ->
                val bindings = listOf("x", "data.y", "_z.a2").associateWith {
                    if (bindingCase == 0) random.nextInt(-20, 21).toDouble()
                    else edgeValues[random.nextInt(edgeValues.size)]
                }
                val detail = "$context bindings=$bindings"
                val expected = try { sample.compute(bindings) } catch (_: ZeroDivisor) { null }
                val actual = expression.evaluateResult(bindings, functions)
                if (expected == null) {
                    assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO,
                        assertIs<ExpressionResult.Failure<ExpressionEvaluationException>>(actual, detail).error.code, detail)
                    assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO,
                        assertFailsWith<ExpressionEvaluationException>(detail) { expression.evaluate(bindings, functions) }.code)
                } else {
                    // toBits distinguishes signed zero and canonicalizes NaN.
                    assertEquals(expected.toBits(), assertIs<ExpressionResult.Success<Double>>(actual, detail).value.toBits(), detail)
                    assertEquals(expected.toBits(), expression.evaluate(bindings, functions).toBits(), detail)
                }
            }
        }
    }

    @Test
    fun unparenthesizedExpressionsRespectPrecedenceAndLeftAssociativity() {
        val seed = 17041
        val random = Random(seed)
        repeat(500) { case ->
            val a = random.nextInt(-50, 51).toDouble()
            val b = random.nextInt(-50, 51).toDouble()
            val c = random.nextInt(1, 20).toDouble()
            val d = random.nextInt(1, 20).toDouble()
            val bindings = mapOf("a" to a, "b" to b, "c" to c, "d" to d)
            val examples = listOf(
                "a + b * c - d" to ((a + (b * c)) - d),
                "a - b - c - d" to (((a - b) - c) - d),
                "a / c / d" to ((a / c) / d),
                "a % c * d + b" to (((a % c) * d) + b),
                "-a * b + c / d" to (((-a) * b) + (c / d)),
            )
            for ((source, expected) in examples) {
                assertEquals(expected.toBits(), Expression.compile(source).evaluate(bindings).toBits(),
                    "seed=$seed case=$case source=$source bindings=$bindings")
            }
        }
    }

    @Test
    fun deterministicInvalidMutationsAreRejectedWithOriginalRanges() {
        val seed = 90127
        val random = Random(seed)
        repeat(200) { case ->
            val valid = generate(random, 3).source
            val sources = listOf("@($valid)", "($valid", "($valid)+", "a..b + ($valid)")
            for ((mutation, source) in sources.withIndex()) {
                val detail = "seed=$seed case=$case source=$source"
                val thrown = assertFailsWith<ExpressionParseException>(detail) { Expression.compile(source) }
                val returned = assertIs<ExpressionResult.Failure<ExpressionParseException>>(Expression.compileResult(source), detail).error
                val position = when (mutation) { 0 -> 0; 3 -> 1; else -> source.length }
                assertEquals(if (mutation == 0) ExpressionParseErrorCode.INVALID_CHARACTER
                    else ExpressionParseErrorCode.UNEXPECTED_TOKEN, thrown.code, detail)
                assertEquals(position, thrown.startIndex, detail)
                assertEquals(if (mutation == 0 || mutation == 3) position + 1 else position, thrown.endIndex, detail)
                assertEquals(thrown.code, returned.code, detail)
                assertEquals(thrown.startIndex, returned.startIndex, detail)
                assertEquals(thrown.endIndex, returned.endIndex, detail)
            }
        }
    }
}
