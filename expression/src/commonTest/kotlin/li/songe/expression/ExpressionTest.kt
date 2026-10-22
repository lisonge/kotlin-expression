package li.songe.expression

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpressionTest {
    private val compiler = Expression
    private fun evaluate(source: String): Double = compiler.compile(source).evaluate(functions = ExpressionFunctions.standard)

    @Test
    fun precedenceAndAssociativity() {
        val cases = mapOf(
            "-pow(2,2)" to -4.0, "pow(-2,2)" to 4.0, "pow(2,-2)" to 0.25,
            "pow(2,pow(3,2))" to 512.0, "pow(2,-pow(2,2))" to 0.0625, "-pow(2,-2)" to -0.25,
            "1+2*3" to 7.0, "(1+2)*3" to 9.0, "8/4/2" to 1.0,
            "8-4-2" to 2.0, "2*-3" to -6.0, "2--3" to 5.0,
            "2++3" to 5.0, "+pow(2,2)" to 4.0, "7%3*2" to 2.0,
            "-7%3" to -1.0, "7%-3" to 1.0, "-7%-3" to -1.0,
            "pow(0,0)" to 1.0, "pow(9,0.5)" to 3.0,
        )
        for ((source, expected) in cases) assertEquals(expected, evaluate(source), 1e-12, source)
    }

    @Test
    fun numericLiteralsAndWhitespace() {
        assertEquals(12.0, evaluate("0012"))
        assertEquals(125.501, evaluate(" \t1.25E+2 + 0.5\r\n+ 1e-3 "))
        assertEquals((-0.0).toBits(), evaluate("-0").toBits())
        assertEquals(0.0, evaluate("1e-9999"))
        assertTrue(evaluate("1e9999").isInfinite())
    }

    @Test
    fun functionsAndNonFiniteValues() {
        assertEquals(3.0, evaluate("max(min(5,3),2)"))
        assertEquals(2.0, evaluate("clamp(-1,2,4)"))
        assertEquals(4.0, evaluate("clamp(5,2,4)"))
        assertEquals(3.0, evaluate("clamp(3,2,4)"))
        assertEquals(2.0, evaluate("clamp(100,2,2)"))
        assertTrue(evaluate("pow(-2,0.5)").isNaN())
        assertTrue(evaluate("pow(10,1000)").isInfinite())
        assertTrue(evaluate("min(pow(-2,0.5),1)").isNaN())
        assertTrue(evaluate("clamp(1,pow(-2,0.5),3)").isNaN())
        assertTrue(evaluate("clamp(1,0,pow(-2,0.5))").isNaN())
        assertTrue(evaluate("1e9999-1e9999").isNaN())
    }

    @Test
    fun variablesAreCollectedAndBindingsArePerEvaluation() {
        val configured = Expression
        val source = "min(width * 0.5, screenWidth) + min + _x1"
        val expression = configured.compile(source)
        assertEquals(setOf("width", "screenWidth", "min", "_x1"), expression.variables)
        assertEquals(source, expression.source)
        assertEquals(103.0, expression.evaluate(mapOf("width" to 200.0, "screenWidth" to 1080.0, "min" to 1.0, "_x1" to 2.0), ExpressionFunctions.standard))
        assertEquals(53.0, expression.evaluate(mapOf("width" to 200.0, "screenWidth" to 50.0, "min" to 1.0, "_x1" to 2.0, "extra" to 9.0), ExpressionFunctions.standard))
        assertEquals(1.0, configured.compile("1").evaluate(functions = ExpressionFunctions.standard))
        val error = assertFailsWith<ExpressionEvaluationException> { expression.evaluate(functions = ExpressionFunctions.standard) }
        assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE, error.code)
        assertEquals(4, error.startIndex)
        assertEquals(9, error.endIndex)
        val caseError = assertFailsWith<ExpressionEvaluationException> {
            configured.compile("Width").evaluate(mapOf("width" to 1.0))
        }
        assertEquals(ExpressionEvaluationErrorCode.MISSING_VARIABLE, caseError.code)
        assertTrue(configured.compile("width").evaluate(mapOf("width" to Double.NaN)).isNaN())
    }

    @Test
    fun variableDiscoveryIsIndependentAndDeduplicated() {
        val expression = Expression.compile("max(b,a)+b+min+_x1+A")
        assertEquals(listOf("b", "a", "min", "_x1", "A"), expression.variables.toList())
        assertEquals(emptySet(), Expression.compile("min(1,2)").variables)
        assertEquals(setOf("other"), Expression.compile("other").variables)
        val names = expression.variables
        assertSame(names, expression.variables)
        assertEquals(setOf("b", "a", "min", "_x1", "A"), expression.variables)
        assertEquals(setOf("true", "NaN"), Expression.compile("true+NaN").variables)
    }

    @Test
    fun rejectsUnsupportedSyntax() {
        for (source in listOf("", " ", ".5", "2.", "1e", "1e+", "1.2.3", "2x", "2(3)",
            "(1)(2)", "--2", "++2", "+-2", "-+2", "2^--2", "1+", "()", "(1", "1)",
            "1,2", "min(1,)", "min(,1)", "2**3", "x=1", "\"x\"", "1<2", "1\u00a0+2", "变量")) {
            assertFailsWith<ExpressionParseException>(source) { compiler.compile(source) }
        }
    }

    @Test
    fun errorsCarryOriginalSourceRanges() {
        fun check(source: String, code: ExpressionParseErrorCode, start: Int, end: Int) {
            val error = assertFailsWith<ExpressionParseException>(source) { compiler.compile(source) }
            assertEquals(code, error.code, source)
            assertEquals(start, error.startIndex, source)
            assertEquals(end, error.endIndex, source)
        }
        check("1 +", ExpressionParseErrorCode.UNEXPECTED_TOKEN, 3, 3)
        check("1 @ 2", ExpressionParseErrorCode.INVALID_CHARACTER, 2, 3)
        check(" 1e+", ExpressionParseErrorCode.INVALID_NUMBER, 1, 4)
        check("(1", ExpressionParseErrorCode.UNEXPECTED_TOKEN, 2, 2)
    }

    @Test
    fun arithmeticErrors() {
        for (source in listOf("1/0", "0/0", "1/-0", "1%0", "1%-0")) {
            val error = assertFailsWith<ExpressionEvaluationException> { evaluate(source) }
            assertEquals(ExpressionEvaluationErrorCode.DIVISION_BY_ZERO, error.code)
            assertEquals(1, error.startIndex)
            assertEquals(2, error.endIndex)
        }
        val error = assertFailsWith<ExpressionEvaluationException> { evaluate("  clamp(0,2,1)") }
        assertEquals(ExpressionEvaluationErrorCode.INVALID_CLAMP_BOUNDS, error.code)
        assertEquals(2, error.startIndex)
        assertEquals(7, error.endIndex)
    }

    @Test
    fun complexityLimitsPreventStackOverflow() {
        for (source in listOf("(".repeat(2000) + "1" + ")".repeat(2000),
            "pow(1,".repeat(1000) + "1" + ")".repeat(1000), "1+".repeat(2000) + "1",
            "min(1,".repeat(1000) + "1" + ")".repeat(1000), "1".repeat(16_385))) {
            val error = assertFailsWith<ExpressionParseException> { compiler.compile(source) }
            assertEquals(ExpressionParseErrorCode.LIMIT_EXCEEDED, error.code)
        }
        assertEquals(1.0, evaluate("(".repeat(127) + "1" + ")".repeat(127)))
        assertEquals(128.0, evaluate(List(128) { "1" }.joinToString("+")))
        assertEquals(1.0, evaluate(" ".repeat(16_383) + "1"))
        assertEquals(2.0, evaluate("1+1"))
    }

    @Test
    fun generatedExpressionsAgreeWithDirectArithmetic() {
        val configured = Expression
        val expressions = listOf(
            configured.compile("x+y*2"), configured.compile("pow(x-y,2)"),
            configured.compile("x/(y+20)"), configured.compile("clamp(x,-3,4)"),
        )
        for (x in -10..10) for (y in -9..9) {
            val a = x.toDouble()
            val b = y.toDouble()
            val expected = listOf(a + b * 2, (a - b) * (a - b), a / (b + 20), a.coerceIn(-3.0, 4.0))
            expressions.forEachIndexed { index, expression ->
                assertTrue(abs(expected[index] - expression.evaluate(mapOf("x" to a, "y" to b), ExpressionFunctions.standard)) < 1e-10)
            }
        }
    }
}
