package li.songe.expression

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal enum class BuiltinFunction(val sourceName: String, val arity: IntRange) {
    MIN("min", 1..Int.MAX_VALUE), MAX("max", 1..Int.MAX_VALUE), CLAMP("clamp", 3..3), POW("pow", 2..2);

    inline fun evaluate(size: Int, token: Token, argument: (Int) -> Double): Double {
        val first = argument(0)
        return when (this) {
            POW -> first.pow(argument(1))
            MIN, MAX -> {
                var result = first
                for (index in 1 until size) {
                    val value = argument(index)
                    result = if (this == MIN) min(result, value) else max(result, value)
                }
                result
            }
            CLAMP -> {
                val second = argument(1)
                val third = argument(2)
                if (second > third) token.evaluationFailure(
                    ExpressionEvaluationErrorCode.INVALID_CLAMP_BOUNDS, "Clamp lower bound exceeds upper bound",
                )
                min(max(first, second), third)
            }
        }
    }
}
