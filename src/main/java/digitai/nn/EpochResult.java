package digitai.nn;

/** 一个 epoch 的训练结果。 */
public record EpochResult(double loss, double trainAccuracy, int batches, long millis) {
}
