package li.songe.expression

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentEvaluationTest {
    @Test
    fun optimizedTreesSupportConcurrentBindingsAndReentrantCallbacks() {
        val expression = Expression.compile("max(adjust(x),min(x,10)) + pow(x,2) + (2+3)*4")
        val standard = ExpressionFunctions.standard
        val executor = Executors.newFixedThreadPool(8)
        try {
            executor.invokeAll((0..999).map { index -> Callable {
                val x = (index % 20).toDouble()
                val functions = standard + mapOf("adjust" to ExpressionFunction(1) { args ->
                    val original = args[0]
                    val nested = expression.evaluate(mapOf("x" to 0.0),
                        standard + mapOf("adjust" to ExpressionFunction(1) { 0.0 }))
                    assertEquals(20.0, nested)
                    assertEquals(original, args[0])
                    original + 1
                })
                assertEquals(x + 1 + x * x + 20, expression.evaluate(mapOf("x" to x), functions))
                assertEquals(ExpressionResult.Success(x + 1 + x * x + 20),
                    expression.evaluateResult(mapOf("x" to x), functions))
            } }).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun compilerAndExpressionCanBeSharedAcrossThreads() {
        val compiler = Expression
        val expression = compiler.compile("x*x+1")
        val customExpression = compiler.compile("adjust(x)")
        val standardExpression = compiler.compile("clamp(adjust(x),0,100)")
        val standard = ExpressionFunctions.standard
        val executor = Executors.newFixedThreadPool(8)
        try {
            val jobs = (0..999).map { value ->
                Callable {
                    val bindings = mapOf("x" to value.toDouble())
                    val expected = value.toDouble() * value + 1
                    assertEquals(expected, expression.evaluate(bindings))
                    assertEquals(expected, compiler.compile("x*x+1").evaluate(bindings))
                    val functions = mapOf("adjust" to ExpressionFunction(1) { it[0] + value })
                    assertEquals(value * 2.0, customExpression.evaluate(bindings, functions))
                    assertEquals(ExpressionResult.Success(value * 2.0), customExpression.evaluateResult(bindings, functions))
                    assertEquals((value * 2.0).coerceIn(0.0, 100.0), standardExpression.evaluate(bindings, standard + functions))
                }
            }
            executor.invokeAll(jobs).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
