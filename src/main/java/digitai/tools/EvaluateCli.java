package digitai.tools;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.data.MnistLoader;
import digitai.nn.Evaluation;
import digitai.nn.Network;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/** 命令行模型评估：输出总体准确率、每个数字准确率与最易混淆的数字对。 */
public final class EvaluateCli {

    private EvaluateCli() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path model = AppPaths.modelFile();
        Path dataDir = AppPaths.dataDir();
        int limit = 10000;
        String split = "test";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model" -> model = Path.of(require(args, ++i, "--model"));
                case "--data" -> dataDir = Path.of(require(args, ++i, "--data"));
                case "--limit" -> limit = Integer.parseInt(require(args, ++i, "--limit"));
                case "--split" -> split = require(args, ++i, "--split");
                default -> {
                    err.println("未知参数: " + args[i]);
                    return 2;
                }
            }
        }
        try {
            Network network = Network.load(model);
            Dataset data = "train".equalsIgnoreCase(split)
                    ? MnistLoader.loadTrain(dataDir) : MnistLoader.loadTest(dataDir);
            int n = Math.min(limit <= 0 ? data.size() : limit, data.size());
            long start = System.nanoTime();
            Evaluation evaluation = evaluateAll(network, data, n);
            double seconds = (System.nanoTime() - start) / 1e9;
            out.printf("模型: %s%n", model.toAbsolutePath());
            out.printf("数据: %s，%d 张，用时 %.2fs%n", split, n, seconds);
            out.printf(Locale.ROOT, "准确率: %.2f%%  (%d/%d)   平均损失: %.4f%n%n",
                    evaluation.accuracy() * 100, evaluation.correct(), evaluation.total(), evaluation.loss());
            out.println("每个数字:");
            for (int digit = 0; digit < 10; digit++) {
                int total = 0;
                for (int p = 0; p < 10; p++) total += evaluation.predicted(digit, p);
                int correct = evaluation.predicted(digit, digit);
                out.printf(Locale.ROOT, "  %d: %5.2f%%  (%d/%d)%n",
                        digit, total == 0 ? 0 : 100.0 * correct / total, correct, total);
            }
            out.println();
            out.println("最容易混淆的 10 对（真实 -> 预测）:");
            record Pair(int actual, int predicted, int count) {
            }
            java.util.List<Pair> pairs = new java.util.ArrayList<>();
            for (int a = 0; a < 10; a++) {
                for (int p = 0; p < 10; p++) {
                    if (a != p && evaluation.predicted(a, p) > 0) {
                        pairs.add(new Pair(a, p, evaluation.predicted(a, p)));
                    }
                }
            }
            pairs.sort((x, y) -> Integer.compare(y.count(), x.count()));
            for (int i = 0; i < Math.min(10, pairs.size()); i++) {
                Pair pair = pairs.get(i);
                out.printf("  %d -> %d : %d 次%n", pair.actual(), pair.predicted(), pair.count());
            }
            return 0;
        } catch (IOException e) {
            err.println("评估失败: " + e.getMessage());
            return 1;
        }
    }

    private static Evaluation evaluateAll(Network network, Dataset data, int limit) {
        int[] confusion = new int[100];
        double loss = 0;
        for (int i = 0; i < limit; i++) {
            double[] p = network.forward(data.image(i));
            int actual = data.label(i);
            int predicted = argmax(p);
            confusion[actual * 10 + predicted]++;
            loss -= Math.log(Math.max(p[actual], 1e-12));
        }
        int correct = 0;
        for (int d = 0; d < 10; d++) correct += confusion[d * 10 + d];
        return new Evaluation(correct / (double) Math.max(1, limit), loss / Math.max(1, limit), confusion);
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    private static String require(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " 缺少参数值");
        }
        return args[index];
    }
}
