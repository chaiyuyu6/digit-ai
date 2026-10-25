package digitai.nn;

/**
 * 训练超参数。
 *
 * @param learningRate 学习率
 * @param batchSize    每个 batch 的样本数
 * @param epochs       计划训练轮数（Trainer 本身不强制，GUI/CLI 用来控制循环）
 * @param l2           L2 正则系数
 * @param optimizer    优化器
 * @param seed         随机种子（打乱数据与初始化）
 */
public record TrainConfig(double learningRate, int batchSize, int epochs,
                          double l2, OptimizerType optimizer, long seed) {

    public TrainConfig {
        if (!(learningRate > 0) || Double.isInfinite(learningRate)) {
            throw new IllegalArgumentException("learningRate 必须为正数: " + learningRate);
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize 必须 >= 1: " + batchSize);
        }
        if (epochs < 1) {
            throw new IllegalArgumentException("epochs 必须 >= 1: " + epochs);
        }
        if (l2 < 0) {
            throw new IllegalArgumentException("l2 必须 >= 0: " + l2);
        }
        if (optimizer == null) {
            throw new IllegalArgumentException("optimizer 不能为 null");
        }
    }

    public static TrainConfig defaults() {
        return new TrainConfig(0.001, 64, 8, 1e-4, OptimizerType.ADAM, 42L);
    }

    public TrainConfig withLearningRate(double value) {
        return new TrainConfig(value, batchSize, epochs, l2, optimizer, seed);
    }

    public TrainConfig withBatchSize(int value) {
        return new TrainConfig(learningRate, value, epochs, l2, optimizer, seed);
    }

    public TrainConfig withEpochs(int value) {
        return new TrainConfig(learningRate, batchSize, value, l2, optimizer, seed);
    }

    public TrainConfig withOptimizer(OptimizerType value) {
        return new TrainConfig(learningRate, batchSize, epochs, l2, value, seed);
    }

    public TrainConfig withSeed(long value) {
        return new TrainConfig(learningRate, batchSize, epochs, l2, optimizer, value);
    }
}
