package digitai.data;

import java.util.Arrays;
import java.util.Random;

/**
 * 不可变手写数字数据集。
 *
 * <p>图片统一展平为长度 784（28×28）的 double 数组，像素值范围 0..1。
 * 为了节省内存，构造器不复制图片数组，调用方必须把 {@link #image(int)} 返回的数组视为只读。</p>
 */
public final class Dataset {

    public static final int IMAGE_SIZE = 28 * 28;

    private final double[][] images;
    private final int[] labels;

    public Dataset(double[][] images, int[] labels) {
        if (images == null || labels == null) {
            throw new IllegalArgumentException("images/labels 不能为 null");
        }
        if (images.length != labels.length) {
            throw new IllegalArgumentException("图片数量与标签数量不一致: " + images.length + " vs " + labels.length);
        }
        for (int i = 0; i < images.length; i++) {
            if (images[i] == null || images[i].length != IMAGE_SIZE) {
                throw new IllegalArgumentException("第 " + i + " 张图片不是 784 维");
            }
            if (labels[i] < 0 || labels[i] > 9) {
                throw new IllegalArgumentException("第 " + i + " 个标签不是 0..9: " + labels[i]);
            }
        }
        this.images = images;
        this.labels = labels;
    }

    public int size() {
        return labels.length;
    }

    /** 返回内部数组，只读。 */
    public double[] image(int index) {
        return images[index];
    }

    public int label(int index) {
        return labels[index];
    }

    public Dataset slice(int fromInclusive, int toExclusive) {
        if (fromInclusive < 0 || toExclusive > size() || fromInclusive > toExclusive) {
            throw new IndexOutOfBoundsException("非法切片 [" + fromInclusive + ", " + toExclusive + "), size=" + size());
        }
        int n = toExclusive - fromInclusive;
        double[][] imgs = new double[n][];
        int[] labs = new int[n];
        System.arraycopy(images, fromInclusive, imgs, 0, n);
        System.arraycopy(labels, fromInclusive, labs, 0, n);
        return new Dataset(imgs, labs);
    }

    public Dataset shuffled(long seed) {
        int n = size();
        double[][] imgs = images.clone();
        int[] labs = labels.clone();
        Random random = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            double[] ti = imgs[i];
            imgs[i] = imgs[j];
            imgs[j] = ti;
            int tl = labs[i];
            labs[i] = labs[j];
            labs[j] = tl;
        }
        return new Dataset(imgs, labs);
    }

    public Dataset subset(int count, long seed) {
        if (count >= size()) {
            return this;
        }
        return shuffled(seed).slice(0, count);
    }

    @Override
    public String toString() {
        return "Dataset{size=" + size() + "}";
    }

    /** 仅用于测试/调试。 */
    public static Dataset of(double[][] images, int[] labels) {
        return new Dataset(images, labels);
    }

    public int[] labelsCopy() {
        return Arrays.copyOf(labels, labels.length);
    }
}
