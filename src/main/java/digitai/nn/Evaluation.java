package digitai.nn;

import java.util.Arrays;

/**
 * 模型在某个数据集上的评估结果。
 *
 * @param accuracy  准确率 0..1
 * @param loss      平均交叉熵损失
 * @param confusion 长度 100 的混淆矩阵，索引为 actual*10 + predicted
 */
public record Evaluation(double accuracy, double loss, int[] confusion) {

    public Evaluation {
        if (confusion == null || confusion.length != 100) {
            throw new IllegalArgumentException("confusion 必须是长度 100 的数组");
        }
        confusion = Arrays.copyOf(confusion, confusion.length);
    }

    @Override
    public int[] confusion() {
        return Arrays.copyOf(confusion, confusion.length);
    }

    public int predicted(int actual, int predicted) {
        return confusion[actual * 10 + predicted];
    }

    public int total() {
        int sum = 0;
        for (int value : confusion) {
            sum += value;
        }
        return sum;
    }

    public int correct() {
        int sum = 0;
        for (int d = 0; d < 10; d++) {
            sum += confusion[d * 10 + d];
        }
        return sum;
    }
}
