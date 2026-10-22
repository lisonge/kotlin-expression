# kotlin-expression

[![Maven Central](https://img.shields.io/maven-central/v/li.songe/expression.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe/expression)

[English](README.md) | 简体中文

用于计算含变量数学表达式的 Kotlin Multiplatform 库。表达式只需编译一次，即可传入不同数值重复或并发求值。运行时仅依赖 Kotlin 标准库。

## 安装

添加 Maven Central 仓库：

```kotlin
repositories {
    mavenCentral()
}
```

JVM 或 Android 项目引入：

```kotlin
dependencies {
    implementation("li.songe:expression:0.1.0")
}
```

Kotlin Multiplatform 项目在 `commonMain` 中引入：

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("li.songe:expression:0.1.0")
        }
    }
}
```

## 使用

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionFunctions

val expression = Expression.compile("min(width * 0.5, screenWidth)")
val variables = expression.variables // setOf("width", "screenWidth")
val functions = expression.functions // setOf("min")
val result = expression.evaluate(
    variables = mapOf("width" to 200.0, "screenWidth" to 1080.0),
    functions = ExpressionFunctions.standard,
) // 100.0
```

## 支持的语法

### 数字

数字字面量使用 ASCII 数字 `0`–`9`，采用十进制，解析为 `Double`。

| 形式 | 规则 | 示例 |
| --- | --- | --- |
| 整数 | 至少一位数字，允许前导零 | `0`、`12`、`0012` |
| 小数 | 小数点两侧都至少有一位数字 | `0.5`、`12.0` |
| 科学计数法 | 整数或小数后接 `e`/`E`、可选正负号、至少一位指数数字 | `1e3`、`1E-3`、`1.25e+2` |

前置正负号属于一元运算符，因此 `-2`、`+0.5` 是表达式。`.5`、`2.`、`1e`、十六进制、数字分隔符和数字类型后缀不符合文法。`NaN`、`Infinity` 按名称解析，不是数字字面量；计算结果可以是 NaN 或无穷大。数字溢出产生无穷大，下溢可以产生零。

### 名称与调用

名称的每一段以 ASCII 字母（`a`–`z`、`A`–`Z`）或 `_` 开头，后续可接零个或多个 ASCII 字母、数字或下划线。名称区分大小写。各段通过紧邻的点连接：`a.b.c` 是一个完整名称，内部没有空白。

| 形式 | 含义 | 示例 |
| --- | --- | --- |
| `name` | 按完整名称查找变量 | `width`、`screen.width` |
| `name(...)` | 函数调用，参数为逗号分隔的表达式 | `f()`、`min(x, 10)`、`math.pow(x, 2)` |
| `(expression)` | 显式分组 | `(x + 1) * 2` |

函数调用语法允许零个或多个参数，实际接受的数量由求值时绑定的函数决定。每个逗号后都需有参数。变量和函数分别使用各自的绑定 Map。

### 运算符与空白

优先级从高到低如下：

| 优先级 | 语法 | 结合方向 |
| --- | --- | --- |
| 1 | 分组 `(…)`、函数调用 `f(…)` | 显式分组 |
| 2 | 一元正负号 `+`、`-` | 数字、名称、调用或分组前的单个前缀 |
| 3 | `*`、`/`、`%` | 左结合 |
| 4 | `+`、`-` | 左结合 |

`2--3` 表示 `2 - (-3)`；`--2` 非法，`-(-2)` 则使用显式分组。乘法通过 `*` 书写，例如 `2 * x`。幂通过显式绑定的 `pow` 函数计算。`%` 使用向零截断除法对应的余数：`-7 % 3` 为 `-1`。除数或取余的右操作数为正零或负零时产生求值错误。

ASCII 空格、制表符（`\t`）、回车（`\r`）和换行（`\n`）可出现在词法单元之间，包括函数名与 `(` 之间。数字字面量和点分名称内部连续书写。文法不包含赋值、比较、逻辑运算、字符串、注释、索引，以及 `^`/`**` 运算符。

## 内置标准

库提供标准常量和标准函数，分别通过 `variables` 和 `functions` 显式绑定，默认均为空 Map。名称区分大小写。

### 常量

`ExpressionConstants.standard` 提供 `Map<String, Double>`：

| 名称 | 含义 | 数值 |
| --- | --- | --- |
| `PI` | 圆周率，圆的周长与直径之比 | `kotlin.math.PI` ≈ 3.141592653589793 |
| `E` | 自然常数，自然对数的底数 | `kotlin.math.E` ≈ 2.718281828459045 |

### 函数

`ExpressionFunctions.standard` 提供 `Map<String, ExpressionFunction>`：

| 函数 | 参数数量 | 功能 | 示例 |
| --- | --- | --- | --- |
| `min(a, …)` | 至少一个 | 返回最小值 | `min(3, 1, 2)` → `1` |
| `max(a, …)` | 至少一个 | 返回最大值 | `max(3, 1, 2)` → `3` |
| `clamp(value, min, max)` | 固定三个 | 将数值限制在闭区间内，上下界反转时报错 | `clamp(12, 0, 10)` → `10` |
| `pow(base, exponent)` | 固定两个 | 计算指定底数的幂 | `pow(2, 3)` → `8` |

### 绑定示例

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionConstants
import li.songe.expression.ExpressionFunctions

val expression = Expression.compile("PI * pow(radius, 2)")
val variables = expression.variables // setOf("PI", "radius")
val result = expression.evaluate(
    variables = ExpressionConstants.standard + mapOf("radius" to 3.0),
    functions = ExpressionFunctions.standard,
) // 28.274333882308138
```

名称为大写，区分大小写。常量作为普通变量绑定，出现在 `expression.variables` 中；缺少绑定时报告 `MISSING_VARIABLE`。`evaluateResult` 接受相同的 Map。各次访问共享同一个只读 Map，可用 `+` 合并自定义值（右侧同名值优先）、用 `- "PI"` 移除条目，或将键重命名为 `math.PI` 等路径。

## 路径变量

使用 `a.b.c` 这样的点分名称组织变量绑定。每一段遵循上述标识符规则，点与各段紧邻。完整名称是 `Map<String, Double>` 中的一个键，求值时直接查找该键，而非逐层访问对象。函数名也支持点分路径，通过 `functions` Map 绑定完整名称。

```kotlin
val expression = Expression.compile("a.b.c * 2 + screen.width")
val variables = expression.variables // setOf("a.b.c", "screen.width")
val result = expression.evaluate(
    mapOf("a.b.c" to 3.0, "screen.width" to 100.0),
) // 106.0
```

缺少绑定时返回 `MISSING_VARIABLE`，源码区间覆盖完整路径。`compileResult` 和 `evaluateResult` 支持相同的语法与绑定方式。

## 自定义函数

`expression.functions` 按首次出现顺序列出引用的函数名并去重，包括标准函数和自定义调用。与 `variables` 一样，各次访问复用同一个只读 Set。

通过 `ExpressionFunction(arity) { args -> … }` 在求值时绑定函数。同一编译结果可以在不同调用中使用不同的函数实现。

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionFunction

val expression = Expression.compile("lerp(start, end, progress)")
val functions = mapOf(
    "lerp" to ExpressionFunction(arity = 3) { args ->
        args[0] + (args[1] - args[0]) * args[2]
    },
)
val result = expression.evaluate(
    variables = mapOf("start" to 10.0, "end" to 20.0, "progress" to 0.5),
    functions = functions,
) // 15.0
```

`arity = 3` 表示固定三个参数，`arity = 1..3` 表示接受一到三个参数，包含两端。`arity` 属性统一为 `IntRange`，`Int` 构造方式会将 `n` 转换为 `n..n`。范围需非空且非负。

```kotlin
val functions = mapOf(
    "sum" to ExpressionFunction(arity = 0..Int.MAX_VALUE) { it.sum() },
    "average" to ExpressionFunction(arity = 1..3) { it.average() },
)
Expression.compile("sum(1, 2, 3)").evaluate(functions = functions) // 6.0
Expression.compile("sum()").evaluate(functions = functions) // 0.0
Expression.compile("average(2, 4)").evaluate(functions = functions) // 3.0
```

参数从左到右求值，自定义函数接收独立的 `DoubleArray`。标准函数 `min`、`max` 接受至少一个参数，`clamp` 固定接受三个参数。使用 `ExpressionFunctions.standard + customFunctions` 合并绑定，右侧同名函数覆盖左侧；使用 `ExpressionFunctions.standard - "min"` 可移除指定函数。各次访问 `standard` 复用同一个只读 Map。`evaluateResult()` 同样接受 `functions` 参数，实参数量超出范围时返回 `INVALID_ARGUMENT_COUNT`。

点分函数名与路径变量遵循相同的分段规则，例如：

```kotlin
val expression = Expression.compile("a.b.c(3)")
val functions = expression.functions // setOf("a.b.c")
val result = expression.evaluate(
    functions = mapOf("a.b.c" to ExpressionFunction(1) { args -> args[0] * 2 }),
) // 6.0
```

函数名区分大小写。缺失函数和参数个数错误在求值时报告，源码位置指向函数名。自定义实现或绑定 Map 查找抛出的异常在两个求值接口中均原样传播。并发调用时，可使用线程安全的函数实现，或为每次调用分别提供绑定。

## 错误与复用

`compileResult()` 和 `evaluateResult()` 返回 `ExpressionResult<T, E>`。使用穷尽的 `when` 分别处理 `Success.value` 和 `Failure.error`。编译阶段的错误类型为 `ExpressionParseException`，求值阶段为 `ExpressionEvaluationException`。

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionResult

when (val compiled = Expression.compileResult("x / 2")) {
    is ExpressionResult.Success -> {
        when (val evaluated = compiled.value.evaluateResult(mapOf("x" to 10.0))) {
            is ExpressionResult.Success -> println(evaluated.value) // 5.0
            is ExpressionResult.Failure -> println(evaluated.error.code)
        }
    }
    is ExpressionResult.Failure -> println(compiled.error.code)
}
```

原有的 `compile()` 和 `evaluate()` 继续提供抛异常接口，用法如下。

编译时检查语法，将完整变量名和函数名分别收集到 `expression.variables` 和 `expression.functions`。求值时解析函数、检查参数个数及缺失的变量绑定，多余绑定被忽略。需要限制名称时，可在求值前将这两个集合与业务允许的名称集合比较。
编译结果可以重复使用，也可以跨线程共享，每次求值期间保持传入的变量绑定不变。

```kotlin
import li.songe.expression.Expression
import li.songe.expression.ExpressionException
import li.songe.expression.ExpressionParseException
import li.songe.expression.ExpressionEvaluationException

try {
    Expression.compile("1 / 0").evaluate()
} catch (error: ExpressionException) {
    val code = when (error) {
        is ExpressionParseException -> error.code.name
        is ExpressionEvaluationException -> error.code.name
    }
    println("$code: [${error.startIndex}, ${error.endIndex})")
    // DIVISION_BY_ZERO: [2, 3)
}
```

`ExpressionParseException` 使用 `ExpressionParseErrorCode` 表示语法及复杂度错误；`ExpressionEvaluationException` 使用 `ExpressionEvaluationErrorCode` 表示绑定、参数数量和算术错误。两者继承 `ExpressionException`，共享消息及原始源码区间。下标从 0 开始，使用 UTF-16 单元，左闭右开。

开发相关说明见 [docs](docs/README.md)。

## 许可证

[Apache License 2.0](LICENSE)。
