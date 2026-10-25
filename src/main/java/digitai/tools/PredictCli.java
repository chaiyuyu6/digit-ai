package digitai.tools;

import digitai.data.AppPaths;
import digitai.data.Preprocessor;
import digitai.nn.Network;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * 命令行识别单张图片：图片应为白底黑字（手写数字截图即可）。
 *
 * <pre>java -cp out/classes digitai.Main predict my-digit.png</pre>
 */
public final class PredictCli {

    private PredictCli() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path model = AppPaths.modelFile();
        Path image = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model" -> model = Path.of(require(args, ++i, "--model"));
                default -> {
                    if (image == null) {
                        image = Path.of(args[i]);
                    } else {
                        err.println("只能指定一张图片: " + args[i]);
                        return 2;
                    }
                }
            }
        }
        if (image == null) {
            err.println("用法: digitai.Main predict <图片路径> [--model 模型文件]");
            return 2;
        }
        try {
            Network network = Network.load(model);
            BufferedImage img = ImageIO.read(image.toFile());
            if (img == null) {
                err.println("无法读取图片: " + image);
                return 1;
            }
            double[] input = Preprocessor.fromImage(img);
            double[] p = network.forward(input);
            int best = 0;
            for (int d = 1; d < p.length; d++) {
                if (p[d] > p[best]) best = d;
            }
            out.println("图片: " + image.toAbsolutePath());
            out.printf("预测: %d   置信度: %.2f%%%n", best, p[best] * 100);
            out.println("全部概率:");
            for (int d = 0; d < 10; d++) {
                out.printf("  %d: %6.2f%%  %s%n", d, p[d] * 100, bar(p[d]));
            }
            return 0;
        } catch (IOException e) {
            err.println("识别失败: " + e.getMessage());
            return 1;
        }
    }

    private static String bar(double p) {
        int width = (int) Math.round(p * 30);
        return "#".repeat(Math.max(0, width));
    }

    private static String require(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " 缺少参数值");
        }
        return args[index];
    }
}
