package li.songe.expression.benchmark

import li.songe.expression.CompiledExpression
import li.songe.expression.Expression
import li.songe.expression.ExpressionEvaluationErrorCode
import li.songe.expression.ExpressionEvaluationException
import li.songe.expression.ExpressionFunction
import li.songe.expression.ExpressionFunctions
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
public open class ExpressionBenchmark {
    private lateinit var shortSource: String
    private lateinit var foldableSource: String
    private lateinit var functionsSource: String
    private lateinit var shortExpression: CompiledExpression
    private lateinit var repeatedVariablesExpression: CompiledExpression
    private lateinit var standardFunctionsExpression: CompiledExpression
    private lateinit var customFunctionExpression: CompiledExpression
    private lateinit var validationExpression: CompiledExpression
    private lateinit var variables: Map<String, Double>
    private lateinit var standardFunctions: Map<String, ExpressionFunction>
    private lateinit var customFunctions: Map<String, ExpressionFunction>
    private lateinit var allVariableNames: Set<String>
    private lateinit var oneMissingVariable: Set<String>
    private lateinit var noVariableNames: Set<String>

    @Setup(Level.Trial)
    public open fun setup() {
        shortSource = "width * 0.5"
        foldableSource = "width * ((2 + 3) * (4 + 5) - 8 / 2) + (7 % 3)"
        functionsSource = "min(width, max(x, y)) + clamp(x, 0, 10) + pow(y, 2)"
        shortExpression = Expression.compile(shortSource)
        repeatedVariablesExpression = Expression.compile("x*x + x*x + y*y + y*y")
        standardFunctionsExpression = Expression.compile(functionsSource)
        customFunctionExpression = Expression.compile("custom.sum(x, y, width)")
        variables = mapOf("width" to 200.0, "x" to 3.0, "y" to 7.0)
        standardFunctions = ExpressionFunctions.standard
        customFunctions = mapOf("custom.sum" to ExpressionFunction(1..Int.MAX_VALUE) { it.sum() })

        val names = List(64) { "v$it" }
        validationExpression = Expression.compile("min(${names.joinToString(",")})")
        allVariableNames = names.toSet()
        oneMissingVariable = allVariableNames - names.last()
        noVariableNames = emptySet()

        check(evaluateShort() == 100.0)
        check(evaluateRepeatedVariables() == 116.0)
        check(evaluateStandardFunctions() == 59.0)
        check(evaluateCustomFunction() == 210.0)
        check(Expression.compile(foldableSource).evaluate(variables) == 8201.0)
        check(validateNoErrors().isEmpty())
        check(validateOneError().single().code == ExpressionEvaluationErrorCode.MISSING_VARIABLE)
        val manyErrors = validateManyErrors()
        check(manyErrors.size == 64)
        check(manyErrors.all { it.code == ExpressionEvaluationErrorCode.MISSING_VARIABLE })
    }

    @Benchmark
    public open fun compileShort(): CompiledExpression = Expression.compile(shortSource)

    @Benchmark
    public open fun compileFoldable(): CompiledExpression = Expression.compile(foldableSource)

    @Benchmark
    public open fun compileFunctions(): CompiledExpression = Expression.compile(functionsSource)

    @Benchmark
    public open fun evaluateShort(): Double = shortExpression.evaluate(variables)

    @Benchmark
    public open fun evaluateRepeatedVariables(): Double = repeatedVariablesExpression.evaluate(variables)

    @Benchmark
    public open fun evaluateStandardFunctions(): Double =
        standardFunctionsExpression.evaluate(variables, standardFunctions)

    @Benchmark
    public open fun evaluateCustomFunction(): Double = customFunctionExpression.evaluate(variables, customFunctions)

    @Benchmark
    public open fun validateNoErrors(): List<ExpressionEvaluationException> =
        validationExpression.validateBindings(allVariableNames, standardFunctions)

    @Benchmark
    public open fun validateOneError(): List<ExpressionEvaluationException> =
        validationExpression.validateBindings(oneMissingVariable, standardFunctions)

    @Benchmark
    public open fun validateManyErrors(): List<ExpressionEvaluationException> =
        validationExpression.validateBindings(noVariableNames, standardFunctions)
}
