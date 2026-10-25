package digitai.nn;

import digitai.data.Dataset;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * 从零实现的全连接神经网络（MLP）。
 *
 * <p>结构：输入 784 → 若干隐藏层（ReLU）→ 输出 10（Softmax）。
 * 训练使用 mini-batch 反向传播 + 交叉熵损失，可选 Adam 或 SGD。
 * 不依赖任何第三方库。</p>
 *
 * <p>本类不是线程安全的：同一时刻只应由一个训练线程调用训练方法。</p>
 */
public final class Network {

    private static final int MAGIC = 0x44474C42; // "DGLB"
    private static final int VERSION = 1;
    private static final double ADAM_BETA1 = 0.9;
    private static final double ADAM_BETA2 = 0.999;
    private static final double ADAM_EPS = 1e-8;
    private static final double LOSS_EPS = 1e-12;

    private final int[] sizes;
    private final double[][][] weights; // weights[l][out][in]
    private final double[][] biases;    // biases[l][out]
    private final long seed;

    // 训练过程的临时缓冲（按需分配，避免每个 batch 重复申请大数组）
    private double[][][] gradWeights;
    private double[][] gradBiases;
    private double[][][] adamM;
    private double[][][] adamV;
    private double[][] adamMB;
    private double[][] adamVB;
    private int adamStep;

    public Network(int... layerSizes) {
        this(System.nanoTime(), layerSizes);
    }

    public Network(long seed, int... layerSizes) {
        if (layerSizes == null || layerSizes.length < 2) {
            throw new IllegalArgumentException("至少需要输入层和输出层");
        }
        int[] copy = layerSizes.clone();
        for (int size : copy) {
            if (size < 1) {
                throw new IllegalArgumentException("每层大小必须 >= 1: " + size);
            }
        }
        this.sizes = copy;
        this.seed = seed;
        this.weights = new double[copy.length - 1][][];
        this.biases = new double[copy.length - 1][];

        Random random = new Random(seed);
        for (int l = 0; l < copy.length - 1; l++) {
            int in = copy[l];
            int out = copy[l + 1];
            weights[l] = new double[out][in];
            biases[l] = new double[out];
            // He 初始化：适合 ReLU 隐藏层，方差 = 2 / fanIn
            double std = Math.sqrt(2.0 / in);
            for (int o = 0; o < out; o++) {
                for (int i = 0; i < in; i++) {
                    weights[l][o][i] = random.nextGaussian() * std;
                }
                biases[l][o] = 0.01;
            }
        }
    }

    private Network(int[] sizes, double[][][] weights, double[][] biases, long seed) {
        this.sizes = sizes.clone();
        this.weights = weights;
        this.biases = biases;
        this.seed = seed;
    }

    // ------------------------------------------------------------------
    // 基本属性
    // ------------------------------------------------------------------

    public int[] sizes() {
        return sizes.clone();
    }

    public int inputSize() {
        return sizes[0];
    }

    public int outputSize() {
        return sizes[sizes.length - 1];
    }

    /** 全连接层数量（不含输入层）。 */
    public int denseLayerCount() {
        return sizes.length - 1;
    }

    public long parameterCount() {
        long count = 0;
        for (int l = 0; l < weights.length; l++) {
            count += (long) weights[l].length * weights[l][0].length;
            count += biases[l].length;
        }
        return count;
    }

    public long seed() {
        return seed;
    }

    // ------------------------------------------------------------------
    // 前向传播
    // ------------------------------------------------------------------

    /** 返回 10 个概率值，和为 1。 */
    public double[] forward(double[] input) {
        checkInput(input);
        double[] a = input;
        for (int l = 0; l < weights.length; l++) {
            double[] z = affine(a, l);
            a = (l == weights.length - 1) ? softmax(z) : relu(z);
        }
        return a;
    }

    /** 执行一次前向并保留每一层的激活值，供可视化使用。 */
    public ForwardTrace trace(double[] input) {
        checkInput(input);
        int layers = weights.length;
        double[][] acts = new double[layers + 1][];
        double[][] pres = new double[layers][];
        acts[0] = input.clone();
        for (int l = 0; l < layers; l++) {
            double[] z = affine(acts[l], l);
            pres[l] = z;
            acts[l + 1] = (l == layers - 1) ? softmax(z) : relu(z);
        }
        return new ForwardTrace(acts, pres, sizes);
    }

    public double[] outputForDigit(double[] input, int digit) {
        if (digit < 0 || digit >= outputSize()) {
            throw new IllegalArgumentException("digit 超出范围: " + digit);
        }
        double[] p = forward(input);
        return new double[]{p[digit]};
    }

    private double[] affine(double[] input, int layer) {
        double[] w = null;
        double[] z = new double[sizes[layer + 1]];
        for (int o = 0; o < z.length; o++) {
            double sum = biases[layer][o];
            w = weights[layer][o];
            for (int i = 0; i < input.length; i++) {
                sum += w[i] * input[i];
            }
            z[o] = sum;
        }
        return z;
    }

    private static double[] relu(double[] z) {
        double[] a = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            a[i] = z[i] > 0 ? z[i] : 0;
        }
        return a;
    }

    private static double[] softmax(double[] z) {
        double max = z[0];
        for (double value : z) {
            if (value > max) {
                max = value;
            }
        }
        double[] out = new double[z.length];
        double sum = 0;
        for (int i = 0; i < z.length; i++) {
            out[i] = Math.exp(z[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 训练
    // ------------------------------------------------------------------

    /** 重新初始化优化器状态。新建一次训练会话时调用。 */
    public void resetOptimizer() {
        int layers = weights.length;
        adamM = new double[layers][][];
        adamV = new double[layers][][];
        adamMB = new double[layers][];
        adamVB = new double[layers][];
        for (int l = 0; l < layers; l++) {
            int out = weights[l].length;
            int in = weights[l][0].length;
            adamM[l] = new double[out][in];
            adamV[l] = new double[out][in];
            adamMB[l] = new double[out];
            adamVB[l] = new double[out];
        }
        adamStep = 0;
    }

    /**
     * 在一个 mini-batch 上做前向 + 反向传播并更新参数。由 {@link Trainer} 调用。
     */
    BatchStats trainBatch(double[][] inputs, int[] labels, TrainConfig config) {
        int batch = inputs.length;
        if (batch == 0) {
            throw new IllegalArgumentException("空 batch");
        }
        if (labels.length != batch) {
            throw new IllegalArgumentException("输入与标签数量不一致");
        }
        ensureTrainingBuffers();
        zeroGradients();

        int layers = weights.length;
        double[][] acts = new double[layers + 1][];
        double[][] pres = new double[layers][];
        double totalLoss = 0;
        int correct = 0;

        for (int s = 0; s < batch; s++) {
            double[] x = inputs[s];
            if (x == null || x.length != inputSize()) {
                throw new IllegalArgumentException("输入维度错误，应为 " + inputSize());
            }
            int label = labels[s];
            if (label < 0 || label >= outputSize()) {
                throw new IllegalArgumentException("标签越界: " + label);
            }

            // 前向
            acts[0] = x;
            for (int l = 0; l < layers; l++) {
                double[] z = affine(acts[l], l);
                pres[l] = z;
                acts[l + 1] = (l == layers - 1) ? softmax(z) : relu(z);
            }

            double[] probs = acts[layers];
            totalLoss -= Math.log(Math.max(probs[label], LOSS_EPS));
            if (argmax(probs) == label) {
                correct++;
            }

            // 反向：输出层 delta = softmax - onehot（交叉熵的优雅结论）
            double[] delta = probs.clone();
            delta[label] -= 1.0;

            for (int l = layers - 1; l >= 0; l--) {
                double[] prevAct = acts[l];
                double[][] gradW = gradWeights[l];
                double[] gradB = gradBiases[l];
                double[][] w = weights[l];
                int out = w.length;
                int in = w[0].length;
                for (int o = 0; o < out; o++) {
                    double d = delta[o];
                    gradB[o] += d;
                    double[] row = gradW[o];
                    for (int i = 0; i < in; i++) {
                        row[i] += d * prevAct[i];
                    }
                }
                if (l > 0) {
                    double[] prevDelta = new double[in];
                    for (int o = 0; o < out; o++) {
                        double d = delta[o];
                        double[] row = w[o];
                        for (int i = 0; i < in; i++) {
                            prevDelta[i] += row[i] * d;
                        }
                    }
                    double[] prevZ = pres[l - 1];
                    for (int i = 0; i < in; i++) {
                        if (prevZ[i] <= 0) {
                            prevDelta[i] = 0;
                        }
                    }
                    delta = prevDelta;
                }
            }
        }

        applyGradients(batch, config);
        return new BatchStats(totalLoss / batch, correct, batch);
    }

    private void applyGradients(int batch, TrainConfig config) {
        double scale = 1.0 / batch;
        double lr = config.learningRate();
        double l2 = config.l2();
        boolean adam = config.optimizer() == OptimizerType.ADAM;
        if (adam) {
            adamStep++;
        }
        double biasCorrection1 = adam ? 1.0 - Math.pow(ADAM_BETA1, adamStep) : 1.0;
        double biasCorrection2 = adam ? 1.0 - Math.pow(ADAM_BETA2, adamStep) : 1.0;

        for (int l = 0; l < weights.length; l++) {
            double[][] w = weights[l];
            double[][] gw = gradWeights[l];
            int out = w.length;
            int in = w[0].length;
            if (adam) {
                double[][] m = adamM[l];
                double[][] v = adamV[l];
                for (int o = 0; o < out; o++) {
                    for (int i = 0; i < in; i++) {
                        double g = gw[o][i] * scale + l2 * w[o][i];
                        m[o][i] = ADAM_BETA1 * m[o][i] + (1 - ADAM_BETA1) * g;
                        v[o][i] = ADAM_BETA2 * v[o][i] + (1 - ADAM_BETA2) * g * g;
                        double mHat = m[o][i] / biasCorrection1;
                        double vHat = v[o][i] / biasCorrection2;
                        w[o][i] -= lr * mHat / (Math.sqrt(vHat) + ADAM_EPS);
                    }
                }
                double[] b = biases[l];
                double[] gb = gradBiases[l];
                double[] mb = adamMB[l];
                double[] vb = adamVB[l];
                for (int o = 0; o < out; o++) {
                    double g = gb[o] * scale;
                    mb[o] = ADAM_BETA1 * mb[o] + (1 - ADAM_BETA1) * g;
                    vb[o] = ADAM_BETA2 * vb[o] + (1 - ADAM_BETA2) * g * g;
                    b[o] -= lr * (mb[o] / biasCorrection1) / (Math.sqrt(vb[o] / biasCorrection2) + ADAM_EPS);
                }
            } else {
                for (int o = 0; o < out; o++) {
                    double[] row = w[o];
                    double[] gRow = gw[o];
                    for (int i = 0; i < in; i++) {
                        double g = gRow[i] * scale + l2 * row[i];
                        row[i] -= lr * g;
                    }
                    biases[l][o] -= lr * gradBiases[l][o] * scale;
                }
            }
        }
    }

    private void ensureTrainingBuffers() {
        if (gradWeights == null) {
            int layers = weights.length;
            gradWeights = new double[layers][][];
            gradBiases = new double[layers][];
            for (int l = 0; l < layers; l++) {
                gradWeights[l] = new double[weights[l].length][weights[l][0].length];
                gradBiases[l] = new double[weights[l].length];
            }
        }
    }

    private void zeroGradients() {
        for (double[][] layer : gradWeights) {
            for (double[] row : layer) {
                java.util.Arrays.fill(row, 0);
            }
        }
        for (double[] row : gradBiases) {
            java.util.Arrays.fill(row, 0);
        }
    }

    // ------------------------------------------------------------------
    // 评估 / 推理
    // ------------------------------------------------------------------

    Evaluation evaluate(Dataset data, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit 不能为负");
        }
        int n = Math.min(limit, data.size());
        int[] confusion = new int[100];
        double loss = 0;
        int correct = 0;
        for (int i = 0; i < n; i++) {
            double[] p = forward(data.image(i));
            int actual = data.label(i);
            int predicted = argmax(p);
            confusion[actual * 10 + predicted]++;
            loss -= Math.log(Math.max(p[actual], LOSS_EPS));
            if (actual == predicted) {
                correct++;
            }
        }
        double accuracy = n == 0 ? 0 : correct / (double) n;
        return new Evaluation(accuracy, n == 0 ? 0 : loss / n, confusion);
    }

    // ------------------------------------------------------------------
    // 权重可视化访问（只读）
    // ------------------------------------------------------------------

    /** 第 layer 个全连接层的权重，形状 [输出][输入]，只读。 */
    public double[][] layerWeights(int layer) {
        checkDenseLayer(layer);
        return weights[layer];
    }

    /** 第 layer 个全连接层的偏置，只读。 */
    public double[] layerBiases(int layer) {
        checkDenseLayer(layer);
        return biases[layer];
    }

    private void checkDenseLayer(int layer) {
        if (layer < 0 || layer >= weights.length) {
            throw new IndexOutOfBoundsException("全连接层索引越界: " + layer);
        }
    }

    // ------------------------------------------------------------------
    // 模型持久化（二进制格式，版本化，便于教学时对比文件）
    // ------------------------------------------------------------------

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(sizes.length);
            for (int size : sizes) {
                out.writeInt(size);
            }
            out.writeLong(seed);
            for (int l = 0; l < weights.length; l++) {
                for (double b : biases[l]) {
                    out.writeDouble(b);
                }
                for (double[] row : weights[l]) {
                    for (double value : row) {
                        out.writeDouble(value);
                    }
                }
            }
        }
    }

    public static Network load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("模型文件不存在: " + file);
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int magic;
            try {
                magic = in.readInt();
            } catch (EOFException e) {
                throw new IOException("模型文件为空或已损坏: " + file, e);
            }
            if (magic != MAGIC) {
                throw new IOException("不是 DigitLab 模型文件（magic 不匹配）: " + file);
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("不支持的模型版本: " + version);
            }
            int layerCount = in.readInt();
            if (layerCount < 2 || layerCount > 100) {
                throw new IOException("模型层数非法: " + layerCount);
            }
            int[] sizes = new int[layerCount];
            for (int i = 0; i < layerCount; i++) {
                sizes[i] = in.readInt();
                if (sizes[i] < 1 || sizes[i] > 1_000_000) {
                    throw new IOException("模型层大小非法: " + sizes[i]);
                }
            }
            long seed = in.readLong();
            double[][][] weights = new double[layerCount - 1][][];
            double[][] biases = new double[layerCount - 1][];
            for (int l = 0; l < layerCount - 1; l++) {
                int out = sizes[l + 1];
                int inSize = sizes[l];
                biases[l] = new double[out];
                for (int o = 0; o < out; o++) {
                    biases[l][o] = in.readDouble();
                }
                weights[l] = new double[out][inSize];
                for (int o = 0; o < out; o++) {
                    for (int i = 0; i < inSize; i++) {
                        weights[l][o][i] = in.readDouble();
                    }
                }
            }
            return new Network(sizes, weights, biases, seed);
        } catch (EOFException e) {
            throw new IOException("模型文件不完整: " + file, e);
        }
    }

    // ------------------------------------------------------------------

    private void checkInput(double[] input) {
        if (input == null || input.length != inputSize()) {
            throw new IllegalArgumentException("输入维度应为 " + inputSize()
                    + "，实际为 " + (input == null ? "null" : input.length));
        }
    }

    static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    /** 一个 batch 内部的统计，供 Trainer 汇总。 */
    record BatchStats(double loss, int correct, int samples) {
    }
}
