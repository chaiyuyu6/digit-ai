package digitai.tools;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.data.MnistLoader;
import digitai.data.Preprocessor;
import digitai.nn.Network;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 预处理质量自测：模拟“用户在 320×320 白色画板上用黑色笔写数字”，
 * 走完整的 Preprocessor 流程后喂给模型，检查准确率是否合理。
 */
public final class PreprocessCheck {

    private static final String[] FONTS = {
            "Segoe Print", "Segoe Script", "Comic Sans MS", "Ink Free",
            "Bradley Hand ITC", "SansSerif", "Serif"
    };

    public static void main(String[] args) throws Exception {
        Path model = args.length > 0 ? Path.of(args[0]) : AppPaths.modelFile();
        Path dataDir = AppPaths.dataDir();
        Path outDir = Path.of("docs", "samples");
        Files.createDirectories(outDir);

        Network network = Network.load(model);
        System.out.println("Model: " + model + "  params=" + network.parameterCount());

        // 1) MNIST 原图 -> 画板尺寸 -> Preprocessor -> 模型
        Dataset test = MnistLoader.loadTest(dataDir);
        int limit = Math.min(2000, test.size());
        int rawCorrect = 0;
        int directCorrect = 0;
        int preprocessedCorrect = 0;
        for (int i = 0; i < limit; i++) {
            double[] raw = test.image(i);
            int label = test.label(i);
            if (argmax(network.forward(raw)) == label) rawCorrect++;

            double[] direct = Preprocessor.fromImage(Preprocessor.toImage(raw));
            if (argmax(network.forward(direct)) == label) directCorrect++;

            BufferedImage board = renderBoardFromMnist(raw, 280);
            double[] processed = Preprocessor.fromImage(board);
            if (argmax(network.forward(processed)) == label) preprocessedCorrect++;
            if (i < 10) {
                BufferedImage preview = Preprocessor.toImage(processed, 10);
                ImageIO.write(preview, "png", outDir.resolve("preprocessed-" + i + "-" + label + ".png").toFile());
            }
        }
        System.out.printf(Locale.ROOT, "MNIST %d 张：原始 %.2f%%，28×28 直接预处理 %.2f%%，放大到画板后预处理 %.2f%%%n",
                limit, 100.0 * rawCorrect / limit, 100.0 * directCorrect / limit,
                100.0 * preprocessedCorrect / limit);

        // 2) 字体渲染的手写风格数字
        int total = 0;
        int correct = 0;
        int[] perDigitTotal = new int[10];
        int[] perDigitCorrect = new int[10];
        int index = 0;
        for (int digit = 0; digit <= 9; digit++) {
            for (String fontName : FONTS) {
                for (boolean bold : new boolean[]{false, true}) {
                    for (int fontSize : new int[]{150, 190, 230}) {
                        int offsetX = ((digit * 37 + index * 13) % 31) - 15;
                        int offsetY = ((digit * 19 + index * 7) % 25) - 12;
                        BufferedImage board = renderDigit(digit, fontName, bold, fontSize, offsetX, offsetY);
                        double[] input = Preprocessor.fromImage(board);
                        int predicted = argmax(network.forward(input));
                        total++;
                        perDigitTotal[digit]++;
                        if (predicted == digit) {
                            correct++;
                            perDigitCorrect[digit]++;
                        } else if (total <= 20) {
                            System.out.printf("  miss: true=%d pred=%d font=%s bold=%s size=%d%n",
                                    digit, predicted, fontName, bold, fontSize);
                        }
                        if (index < 12 || predicted != digit) {
                            ImageIO.write(board, "png", outDir.resolve("font-" + digit + "-" + index + ".png").toFile());
                            ImageIO.write(Preprocessor.toImage(input, 10), "png",
                                    outDir.resolve("font-processed-" + digit + "-" + index + ".png").toFile());
                        }
                        index++;
                    }
                }
            }
        }
        System.out.printf(Locale.ROOT, "字体渲染 %d 张：%.2f%%%n", total, 100.0 * correct / total);
        for (int d = 0; d < 10; d++) {
            System.out.printf(Locale.ROOT, "  %d: %d/%d%n", d, perDigitCorrect[d], perDigitTotal[d]);
        }
    }

    private static BufferedImage renderBoardFromMnist(double[] image, int size) {
        BufferedImage small = Preprocessor.toImage(image);
        BufferedImage board = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = board.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.drawImage(small, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return board;
    }

    private static BufferedImage renderDigit(int digit, String fontName, boolean bold, int fontSize,
                                             int offsetX, int offsetY) {
        int size = 320;
        BufferedImage board = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = board.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setColor(Color.BLACK);
            Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(font);
            String text = Integer.toString(digit);
            FontMetrics metrics = g.getFontMetrics();
            int x = (size - metrics.stringWidth(text)) / 2 + offsetX;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent() + offsetY;
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
        return board;
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }
}
