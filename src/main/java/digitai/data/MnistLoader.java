package digitai.data;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * MNIST IDX 格式读取器。支持原始 IDX 文件以及 gzip 压缩版本。
 *
 * <p>文件格式（大端）：<br>
 * 图片：magic 0x00000803, count, rows, cols, 像素字节<br>
 * 标签：magic 0x00000801, count, 标签字节</p>
 */
public final class MnistLoader {

    private static final int IMAGE_MAGIC = 0x00000803;
    private static final int LABEL_MAGIC = 0x00000801;

    private MnistLoader() {
    }

    public static Dataset loadTrain(Path dataDir) throws IOException {
        return load(
                resolve(dataDir, "train-images-idx3-ubyte", "train-images.idx3-ubyte"),
                resolve(dataDir, "train-labels-idx1-ubyte", "train-labels.idx1-ubyte"));
    }

    public static Dataset loadTest(Path dataDir) throws IOException {
        return load(
                resolve(dataDir, "t10k-images-idx3-ubyte", "t10k-images.idx3-ubyte"),
                resolve(dataDir, "t10k-labels-idx1-ubyte", "t10k-labels.idx1-ubyte"));
    }

    public static Dataset load(Path imagesFile, Path labelsFile) throws IOException {
        double[][] images = readImages(imagesFile);
        int[] labels = readLabels(labelsFile);
        if (images.length != labels.length) {
            throw new IOException("图片数量（" + images.length + "）与标签数量（" + labels.length + "）不一致");
        }
        return new Dataset(images, labels);
    }

    /** 在数据目录中寻找文件；自动尝试 .gz 版本。 */
    public static Path resolve(Path dataDir, String... candidateNames) throws IOException {
        if (dataDir == null) {
            throw new IOException("数据目录为 null");
        }
        for (String name : candidateNames) {
            Path raw = dataDir.resolve(name);
            if (Files.isRegularFile(raw)) {
                return raw;
            }
            Path gz = dataDir.resolve(name + ".gz");
            if (Files.isRegularFile(gz)) {
                return gz;
            }
        }
        throw new IOException("在 " + dataDir.toAbsolutePath() + " 中找不到 " + String.join(" / ", candidateNames)
                + "（也尝试了 .gz）。请先下载 MNIST 数据。");
    }

    private static double[][] readImages(Path file) throws IOException {
        try (DataInputStream in = open(file)) {
            int magic = in.readInt();
            if (magic != IMAGE_MAGIC) {
                throw new IOException("不是 MNIST 图片文件（magic=" + Integer.toHexString(magic) + "）: " + file);
            }
            int count = in.readInt();
            int rows = in.readInt();
            int cols = in.readInt();
            if (count < 0 || rows < 1 || cols < 1) {
                throw new IOException("MNIST 图片头非法: count=" + count + ", rows=" + rows + ", cols=" + cols);
            }
            if ((long) rows * cols != Dataset.IMAGE_SIZE) {
                throw new IOException("仅支持 28×28 MNIST 图片，实际为 " + rows + "×" + cols);
            }
            byte[] buffer = new byte[count * Dataset.IMAGE_SIZE];
            in.readFully(buffer);

            double[][] images = new double[count][];
            for (int i = 0; i < count; i++) {
                double[] image = new double[Dataset.IMAGE_SIZE];
                int base = i * Dataset.IMAGE_SIZE;
                for (int p = 0; p < image.length; p++) {
                    image[p] = (buffer[base + p] & 0xFF) / 255.0;
                }
                images[i] = image;
            }
            return images;
        }
    }

    private static int[] readLabels(Path file) throws IOException {
        try (DataInputStream in = open(file)) {
            int magic = in.readInt();
            if (magic != LABEL_MAGIC) {
                throw new IOException("不是 MNIST 标签文件（magic=" + Integer.toHexString(magic) + "）: " + file);
            }
            int count = in.readInt();
            if (count < 0) {
                throw new IOException("MNIST 标签数量非法: " + count);
            }
            byte[] buffer = new byte[count];
            in.readFully(buffer);
            int[] labels = new int[count];
            for (int i = 0; i < count; i++) {
                labels[i] = buffer[i] & 0xFF;
                if (labels[i] > 9) {
                    throw new IOException("标签越界: " + labels[i]);
                }
            }
            return labels;
        }
    }

    private static DataInputStream open(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("文件不存在: " + file);
        }
        InputStream raw = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
        raw.mark(2);
        int b1 = raw.read();
        int b2 = raw.read();
        raw.reset();
        InputStream decoded = (b1 == 0x1F && b2 == 0x8B) ? new GZIPInputStream(raw, 1 << 16) : raw;
        return new DataInputStream(new BufferedInputStream(decoded, 1 << 16));
    }

    /** 数据目录是否包含训练与测试所需的四个文件。 */
    public static boolean isDataAvailable(Path dataDir) {
        if (dataDir == null) {
            return false;
        }
        return exists(dataDir.resolve("train-images-idx3-ubyte"), dataDir.resolve("train-images-idx3-ubyte.gz"))
                && exists(dataDir.resolve("train-labels-idx1-ubyte"), dataDir.resolve("train-labels-idx1-ubyte.gz"))
                && exists(dataDir.resolve("t10k-images-idx3-ubyte"), dataDir.resolve("t10k-images-idx3-ubyte.gz"))
                && exists(dataDir.resolve("t10k-labels-idx1-ubyte"), dataDir.resolve("t10k-labels-idx1-ubyte.gz"));
    }

    private static boolean exists(Path a, Path b) {
        return Files.isRegularFile(a) || Files.isRegularFile(b);
    }
}
