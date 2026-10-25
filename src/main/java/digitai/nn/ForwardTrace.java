package digitai.nn;

/**
 * 一次前向传播的完整轨迹，供界面可视化使用。
 *
 * <p>层索引从 0 开始：{@code activations(0)} 是第一个全连接层（通常是隐藏层）的输出，
 * 最后一个全连接层的输出即 softmax 概率。输入图片通过 {@link #input()} 获取。</p>
 */
public final class ForwardTrace {

    private final double[][] activations;    // 长度 L+1，[0] 是输入
    private final double[][] preActivations; // 长度 L
    private final int[] sizes;               // 长度 L+1

    ForwardTrace(double[][] activations, double[][] preActivations, int[] sizes) {
        this.activations = activations;
        this.preActivations = preActivations;
        this.sizes = sizes.clone();
    }

    /** 输入层的 784 个像素值（副本）。 */
    public double[] input() {
        return activations[0].clone();
    }

    /** 全连接层数量（不含输入层），例如 784-64-32-10 返回 3。 */
    public int layerCount() {
        return preActivations.length;
    }

    public int[] sizes() {
        return sizes.clone();
    }

    /** 第 layer 个全连接层的激活值（layer 从 0 开始），返回副本。 */
    public double[] activations(int layer) {
        checkLayer(layer);
        return activations[layer + 1].clone();
    }

    /** 第 layer 个全连接层激活函数之前的 z 值，返回副本。 */
    public double[] preActivations(int layer) {
        checkLayer(layer);
        return preActivations[layer].clone();
    }

    public int predicted() {
        return argmax(activations[activations.length - 1]);
    }

    public double confidence() {
        double[] p = activations[activations.length - 1];
        double max = 0;
        for (double value : p) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private void checkLayer(int layer) {
        if (layer < 0 || layer >= preActivations.length) {
            throw new IndexOutOfBoundsException("layer 超出范围: " + layer);
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
}
