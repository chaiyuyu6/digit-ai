package digitai.data;

import java.awt.image.BufferedImage;

/**
 * 画板图像 → MNIST 输入向量的预处理。
 *
 * <p>网络训练时看到的是 MNIST：黑色笔画、白色背景、数字居中、主体约 20×20 像素。
 * 用户用鼠标画出来的数字大小/位置/粗细都不同，所以推理前必须做同样的规范化：</p>
 * <ol>
 *   <li>把像素转成“墨水浓度”（0=白底，1=纯黑）；</li>
 *   <li>裁掉四周空白；</li>
 *   <li>等比缩放到最长边 20 像素；</li>
 *   <li>放到 28×28 中央，并让墨迹重心落在中心；</li>
 *   <li>归一化亮度。</li>
 * </ol>
 */
public final class Preprocessor {

    public static final int SIZE = 28;
    private static final int TARGET_BOX = 20; // MNIST 数字主体大约占 20×20
    private static final double INK_NOISE = 0.02;
    private static final double BBOX_THRESHOLD = 0.04;

    private Preprocessor() {
    }

    /** 从 AWT 像素数组（0xAARRGGBB）转换。 */
    public static double[] fromArgb(int width, int height, int[] argbPixels) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("宽高必须 >= 1");
        }
        if (argbPixels == null || argbPixels.length < width * height) {
            throw new IllegalArgumentException("像素数组长度不足");
        }

        int total = width * height;
        int opaque = 0;
        for (int i = 0; i < total; i++) {
            if (((argbPixels[i] >>> 24) & 0xFF) >= 250) {
                opaque++;
            }
        }
        // 绝大多数 UI 画板是不透明白底；若大量透明像素则按“透明底 + 有色笔画”处理
        boolean transparentBackground = opaque < total * 0.8;

        double[][] ink = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = argbPixels[y * width + x];
                int alpha = (pixel >>> 24) & 0xFF;
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                double value;
                if (transparentBackground) {
                    // 透明处=白，黑色 alpha 越高墨水越浓
                    value = (alpha / 255.0) * (1.0 - luminance);
                } else {
                    value = 1.0 - luminance;
                }
                if (value < INK_NOISE) {
                    value = 0;
                }
                ink[y][x] = Math.min(1.0, Math.max(0.0, value));
            }
        }
        return normalize(ink);
    }

    public static double[] fromImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        return fromArgb(w, h, pixels);
    }

    /** 已经是 28×28、白底黑字格式的图片（例如 MNIST 原图）也可走同一套流程。 */
    public static double[] normalize(double[][] ink) {
        int height = ink.length;
        int width = ink[0].length;

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ink[y][x] > BBOX_THRESHOLD) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < 0) {
            return new double[SIZE * SIZE];
        }

        int boxW = right - left + 1;
        int boxH = bottom - top + 1;
        double scale = (double) TARGET_BOX / Math.max(boxW, boxH);
        int targetW = Math.max(1, Math.min(TARGET_BOX, (int) Math.round(boxW * scale)));
        int targetH = Math.max(1, Math.min(TARGET_BOX, (int) Math.round(boxH * scale)));

        // 把 bbox 双线性缩放到 targetW × targetH
        double[][] scaled = new double[targetH][targetW];
        for (int ty = 0; ty < targetH; ty++) {
            double sy = top + (ty + 0.5) * (boxH / (double) targetH) - 0.5;
            for (int tx = 0; tx < targetW; tx++) {
                double sx = left + (tx + 0.5) * (boxW / (double) targetW) - 0.5;
                scaled[ty][tx] = sampleBilinear(ink, sx, sy);
            }
        }

        // 放进 28×28 画布，先按 bbox 居中
        double[][] canvas = new double[SIZE][SIZE];
        int ox = (SIZE - targetW) / 2;
        int oy = (SIZE - targetH) / 2;
        for (int ty = 0; ty < targetH; ty++) {
            System.arraycopy(scaled[ty], 0, canvas[oy + ty], ox, targetW);
        }

        // 计算重心，把墨迹重心精确移到 (14, 14)
        double sum = 0;
        double cx = 0;
        double cy = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double v = canvas[y][x];
                sum += v;
                cx += (x + 0.5) * v;
                cy += (y + 0.5) * v;
            }
        }
        if (sum <= 0) {
            return new double[SIZE * SIZE];
        }
        int dx = (int) Math.round(SIZE / 2.0 - cx / sum);
        int dy = (int) Math.round(SIZE / 2.0 - cy / sum);

        double[] out = new double[SIZE * SIZE];
        double max = 0;
        for (int y = 0; y < SIZE; y++) {
            int sy = y - dy;
            if (sy < 0 || sy >= SIZE) continue;
            for (int x = 0; x < SIZE; x++) {
                int sx = x - dx;
                if (sx < 0 || sx >= SIZE) continue;
                double v = canvas[sy][sx];
                out[y * SIZE + x] = v;
                if (v > max) max = v;
            }
        }
        // 让最浓的笔画达到 1，消除不同笔刷/压力带来的亮度差异
        if (max > 1e-6) {
            double inv = 1.0 / max;
            for (int i = 0; i < out.length; i++) {
                out[i] *= inv;
            }
        }
        return out;
    }

    private static double sampleBilinear(double[][] image, double x, double y) {
        int height = image.length;
        int width = image[0].length;
        double cx = Math.max(0, Math.min(width - 1, x));
        double cy = Math.max(0, Math.min(height - 1, y));
        int x0 = (int) Math.floor(cx);
        int y0 = (int) Math.floor(cy);
        int x1 = Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);
        double wx = cx - x0;
        double wy = cy - y0;
        double top = image[y0][x0] * (1 - wx) + image[y0][x1] * wx;
        double bottom = image[y1][x0] * (1 - wx) + image[y1][x1] * wx;
        return top * (1 - wy) + bottom * wy;
    }

    /** 28×28 白底黑字图片，用于界面预览。 */
    public static BufferedImage toImage(double[] vector) {
        return toImage(vector, 1);
    }

    /** 生成 scale 倍最近邻放大的预览图，便于看清像素。 */
    public static BufferedImage toImage(double[] vector, int scale) {
        if (vector == null || vector.length != SIZE * SIZE) {
            throw new IllegalArgumentException("向量必须是 784 维");
        }
        if (scale < 1) {
            throw new IllegalArgumentException("scale 必须 >= 1");
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double v = Math.min(1, Math.max(0, vector[y * SIZE + x]));
                int gray = 255 - (int) Math.round(v * 255);
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        if (scale == 1) {
            return image;
        }
        BufferedImage big = new BufferedImage(SIZE * scale, SIZE * scale, BufferedImage.TYPE_INT_RGB);
        var g = big.createGraphics();
        try {
            g.drawImage(image, 0, 0, SIZE * scale, SIZE * scale, null);
        } finally {
            g.dispose();
        }
        return big;
    }
}
