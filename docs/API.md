# DigitLab 核心 API（UI 侧冻结契约）

> 版本 1.0。`digitai.ui` 包**只依赖**本文件列出的类型与方法。核心实现由 Lead 负责，接口变更会提前同步。
> 所有类位于 `src/main/java/` 下，Java 17+，仅使用 JDK（Swing / AWT / java.io / java.util）。

## 1. 包 `digitai.data`

### `public final class Dataset`
不可变数据集。

```java
public Dataset(double[][] images, int[] labels)   // images[i] 长度 784，像素 0..1
public int size()
public double[] image(int index)                   // 直接返回内部数组，调用方只读
public int label(int index)
public Dataset slice(int fromInclusive, int toExclusive)
public Dataset subset(int count, long seed)        // 随机取 count 个样本
public Dataset shuffled(long seed)
```

### `public final class MnistLoader`
读取 IDX 文件（支持 `.gz` 与解压后的文件）。

```java
public static Dataset loadTrain(Path dataDir) throws IOException   // 返回 60,000 张
public static Dataset loadTest(Path dataDir) throws IOException    // 返回 10,000 张
public static Dataset load(Path imagesFile, Path labelsFile) throws IOException
```

### `public final class Preprocessor`
把任意画板图像转换为 MNIST 风格 28×28 输入向量（去边框、等比缩放、重心居中）。

```java
public static final int SIZE = 28;                         // 784
public static double[] fromArgb(int width, int height, int[] argbPixels)  // 0xAARRGGBB，支持白底黑字或透明底
public static double[] fromImage(java.awt.image.BufferedImage image)
public static java.awt.image.BufferedImage toImage(double[] vector)      // 28×28，白底黑字
public static java.awt.image.BufferedImage toImage(double[] vector, int scale) // 最近邻放大，便于显示
```

### `public final class AppPaths`
运行位置无关的路径解析：依次查系统属性、环境变量、当前目录、jar 所在目录附近。

```java
public static java.nio.file.Path projectRoot()
public static java.nio.file.Path dataDir()        // 默认 <root>/data/mnist
public static java.nio.file.Path modelFile()      // 默认 <root>/models/digit-mlp.bin
public static boolean hasData()
public static boolean hasModel()
```

## 2. 包 `digitai.nn`

### `public final class Network`
全连接 MLP，隐藏层 ReLU，输出层 Softmax + 交叉熵。实现完全手写，无第三方库。

```java
public Network(int... layerSizes)                    // 如 new Network(784, 64, 32, 10)，He 初始化
public Network(long seed, int... layerSizes)         // 可复现初始化

public static Network load(java.nio.file.Path file) throws IOException
public void save(java.nio.file.Path file) throws IOException

public int[] sizes()                 // 包含输入/输出，如 {784,64,32,10}
public int inputSize()               // 784
public int outputSize()              // 10
public int denseLayerCount()         // 3（隐层数 + 输出层）
public long parameterCount()         // 权重 + 偏置总数

public double[] forward(double[] input)              // 返回 10 个概率，和为 1
public ForwardTrace trace(double[] input)            // 一次前向，保留每层激活值

// 可视化用只读访问（不要修改数组内容）
public double[][] layerWeights(int layerIndex)       // 第 layerIndex 个全连接层，[out][in]，0 起
public double[]  layerBiases(int layerIndex)         // 长度 = 该层输出维度

public double[] outputForDigit(double[] input, int digit) // 便捷：forward(input)[digit]
```

### `public final class ForwardTrace`
一次前向传播的完整轨迹，用于可视化。

```java
public double[] input()               // 784
public int layerCount()               // 全连接层数量
public int[] sizes()                  // {784,64,32,10}
public double[] activations(int layer)     // layer=0 是第 1 个全连接层输出；最后一个是 softmax 概率
public double[] preActivations(int layer)  // 对应层 ReLU/Softmax 前的 z 值
public int predicted()                // argmax 概率
public double confidence()            // 最大概率
```

### `public enum OptimizerType`
```java
ADAM, SGD
```

### `public record TrainConfig(double learningRate, int batchSize, int epochs,
                            double l2, OptimizerType optimizer, long seed)`
```java
public static TrainConfig defaults()  // Adam, lr=0.001, batch=64, epochs=8, l2=1e-4
public TrainConfig withLearningRate(double lr)
public TrainConfig withBatchSize(int batchSize)
public TrainConfig withEpochs(int epochs)
public TrainConfig withOptimizer(OptimizerType type)
```

### `public final class Trainer`
一次训练会话（保存 Adam/SGD 状态）。更换网络或重新训练时新建实例。

```java
public Trainer(Network network, TrainConfig config)
public Network network()
public TrainConfig config()

// 训练一个 epoch；listener 在每个 batch 结束回调（可返回 false 中止本 epoch）
public EpochResult trainEpoch(Dataset train, BatchListener listener)
public Evaluation evaluate(Dataset data)                 // 全量评估
public Evaluation evaluate(Dataset data, int limit)      // 前 limit 张

@FunctionalInterface
public interface BatchListener {
    boolean onBatch(int batchIndex, int totalBatches, double runningLoss);
    BatchListener NONE = (i, n, l) -> true;
}
```

### `public record EpochResult(double loss, double trainAccuracy, int batches, long millis)`
### `public record Evaluation(double accuracy, double loss, int[] confusion)` // confusion[actual*10+predicted]
```java
public int predicted(int actual, int predicted)  // 取混淆矩阵值
public int total()
```

## 3. 线程与用法约定

- 所有 Swing 组件只在 EDT 创建/修改。
- 加载 MNIST / 训练 / 保存模型放到 `SwingWorker`，通过 `publish/process` 或 `SwingUtilities.invokeLater` 回 UI。
- 单次前向（含 trace）在毫秒级，可以直接在 EDT 调用；但绘制时应使用 `javax.swing.Timer` 做 120–180ms 防抖。
- 训练中暂停：在 `BatchListener` 中等待（`while (paused) Thread.sleep(50)`）或返回 false 中止当前 epoch。停止：listener 返回 false 且丢弃结果。
- 模型文件不存在/损坏时 UI 必须优雅降级：显示提示、允许进入训练页从头训练，不得抛异常崩溃。
- 数据集已随项目附带；`AppPaths.hasData()` 为 false 时训练页显示错误提示即可。

## 4. 入口

`digitai.Main` 由 Lead 提供，只做：命令行模式分发，以及
```java
SwingUtilities.invokeLater(() -> new digitai.ui.MainFrame().setVisible(true));
```
`MainFrame` 必须有无参构造函数，内部自行加载模型（失败则降级），窗口关闭时 `EXIT_ON_CLOSE`。
