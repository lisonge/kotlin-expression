# JVM 性能基准

`benchmarks` 是独立的 JVM 模块，使用 [JMH](https://github.com/openjdk/jmh) 和 [Gradle JMH 插件](https://github.com/melix/jmh-gradle-plugin)。基准不参与普通测试或 `check`，没有耗时阈值；库的运行时依赖和公共 API 保持不变。

## 运行

在根目录执行，Windows 使用 `./gradlew.bat`：

```shell
./gradlew :benchmarks:jmh -PjvmOnly --no-configuration-cache
```

首次运行需要下载基准依赖。默认配置为单线程、两个独立 JVM fork，每个场景每个 fork 预热 5 轮、测量 5 轮，每轮 1 秒，堆大小固定为 512 MiB。使用执行 Gradle 的 JDK，输出中记录实际 JDK、VM 参数和 JMH 配置。完整运行需要数分钟。

每次执行该命令都会重新测量；编译任务仍可复用已有产物。

仅检查基准能否编译和运行时使用短模式：

```shell
./gradlew :benchmarks:jmh -PjvmOnly -PbenchmarkSmoke --no-configuration-cache
```

短模式使用一个 fork，预热、测量各一轮，每轮 200 毫秒，只验证运行流程，不能用于性能结论。依赖已缓存后，两种命令都可追加 `--offline`。

## 场景

| 方法 | 测量内容 |
| --- | --- |
| `compileShort` | 编译 `width * 0.5` |
| `compileFoldable` | 编译含纯数字子表达式的算式，包含常量折叠成本 |
| `compileFunctions` | 编译包含嵌套调用、标准函数和变量的表达式 |
| `evaluateShort` | 重复求值短算式 |
| `evaluateRepeatedVariables` | 同一算式中重复读取变量 |
| `evaluateStandardFunctions` | 重复求值 `min/max/clamp/pow` 调用 |
| `evaluateCustomFunction` | 自定义函数调用及独立参数数组分配 |
| `validateNoErrors` | 64 个变量全部绑定，校验返回空列表 |
| `validateOneError` | 同一表达式缺少一个变量绑定，返回一个错误 |
| `validateManyErrors` | 同一表达式缺少全部 64 个变量绑定，返回 64 个错误 |

三个校验场景共用 `min(v0, …, v63)` 的编译结果，始终绑定标准 `min`，只改变允许变量名称集合，比较缺失变量诊断的构造成本。它们不测量未知函数或参数数量错误。

表达式和绑定集合在 `@Setup` 中准备，求值及校验的计时不包括编译或 Map 构造。准备阶段还核对计算结果及预期错误数量。基准方法每次只执行一次 API 调用，并返回结果交由 JMH 消费；求值方法返回原始 `Double`，不通过 `Any` 装箱。没有手写计时循环或在测量阶段打印错误。

## 结果

完整运行生成：

- `benchmarks/build/reports/jmh/results.txt`：运行配置、逐轮结果和汇总。
- `benchmarks/build/reports/jmh/results.json`：结构化结果，包含原始测量数据。

短模式使用独立的 `smoke.txt`、`smoke.json`，不会覆盖完整结果。

主要指标：

| 指标 | 含义 |
| --- | --- |
| 主结果 `ns/op` | 每次 API 调用的平均耗时及 JMH 报告的误差区间 |
| `gc.alloc.rate.norm`（B/op） | 每次调用的分配量，由 JMH 的 GC profiler 采集 |
| `gc.count` / `gc.time` | 测量期间的垃圾回收次数与时间 |

分配量是分配速率归一化后的结果，不是调用后保留的内存。若当前 JVM 不支持某项指标，按不可用处理；不将缺失值视为零。

比较改动前后时，分别保存结果文件，使用相同机器、JDK、VM 参数和基准配置，并记录对应提交及未提交改动。观察多个 fork 的结果和误差范围，避免将系统负载或 JIT 波动解释为优化收益。这里测量的是固定场景的库调用成本，不代表完整应用或真实订阅语料的性能。
