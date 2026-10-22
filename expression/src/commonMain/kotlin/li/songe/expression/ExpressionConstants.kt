package li.songe.expression

import kotlin.math.E
import kotlin.math.PI

/** Optional constants for explicit variable bindings. */
public object ExpressionConstants {
    /**
     * PI and E from kotlin.math, keyed by their uppercase names.
     * A shared read-only map.
     * Pass explicitly as variables to evaluate/evaluateResult, or combine with bindings using `+`.
     */
    public val standard: Map<String, Double> = mapOf("PI" to PI, "E" to E)
}
