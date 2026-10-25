package digitai.nn;

import digitai.data.Dataset;

import java.util.Random;

/**
 * 训练器：负责按 batch 遍历数据集、调用网络反向传播、统计训练指标。
 *
 * <p>一个 Trainer 实例保存一份优化器状态（Adam 的 m/v）。当网络结构改变或想重新开始时，
 * 应新建 Trainer。训练循环（epoch 数、暂停/停止）由调用方（GUI / CLI）控制，
 * 这样界面可以在 batch 边界刷新进度。</p>
 */
public final class Trainer {

    private final Network network;
    private final TrainConfig config;
    private long epochCounter;

    public Trainer(Network network, TrainConfig config) {
        if (network == null || config == null) {
            throw new IllegalArgumentException("network/config 不能为 null");
        }
        this.network = network;
        this.config = config;
        this.network.resetOptimizer();
    }

    public Network network() {
        return network;
    }

    public TrainConfig config() {
        return config;
    }

    /**
     * 训练一个 epoch。
     *
     * @param train    训练集
     * @param listener 每个 batch 结束后的回调；返回 false 可提前结束（用于暂停/停止）。
     *                 传 null 等同于 {@link BatchListener#NONE}。
     */
    public EpochResult trainEpoch(Dataset train, BatchListener listener) {
        if (train == null || train.size() == 0) {
            throw new IllegalArgumentException("训练集为空");
        }
        long start = System.nanoTime();
        BatchListener callback = listener == null ? BatchListener.NONE : listener;

        int n = train.size();
        int batchSize = config.batchSize();
        int totalBatches = (n + batchSize - 1) / batchSize;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // 每个 epoch 使用不同但可复现的打乱顺序
        Random random = new Random(config.seed() ^ (++epochCounter * 0x9E3779B97F4A7C15L));
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }

        double totalLoss = 0;
        int seen = 0;
        int correct = 0;
        int doneBatches = 0;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int offset = batchIndex * batchSize;
            int count = Math.min(batchSize, n - offset);
            double[][] inputs = new double[count][];
            int[] labels = new int[count];
            for (int k = 0; k < count; k++) {
                int sample = order[offset + k];
                inputs[k] = train.image(sample);
                labels[k] = train.label(sample);
            }
            Network.BatchStats stats = network.trainBatch(inputs, labels, config);
            totalLoss += stats.loss() * stats.samples();
            seen += stats.samples();
            correct += stats.correct();
            doneBatches++;

            boolean keepGoing = callback.onBatch(batchIndex, totalBatches, totalLoss / seen);
            if (!keepGoing) {
                break;
            }
        }

        long millis = (System.nanoTime() - start) / 1_000_000L;
        return new EpochResult(totalLoss / Math.max(1, seen), correct / (double) Math.max(1, seen),
                doneBatches, millis);
    }

    public Evaluation evaluate(Dataset data) {
        return network.evaluate(data, data.size());
    }

    public Evaluation evaluate(Dataset data, int limit) {
        return network.evaluate(data, limit);
    }

    /** batch 回调；返回 false 表示请求提前结束本 epoch。 */
    @FunctionalInterface
    public interface BatchListener {
        boolean onBatch(int batchIndex, int totalBatches, double runningLoss);

        BatchListener NONE = (batchIndex, totalBatches, runningLoss) -> true;
    }
}
