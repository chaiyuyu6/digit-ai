package digitai.tools;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.data.MnistLoader;
import digitai.nn.EpochResult;
import digitai.nn.Evaluation;
import digitai.nn.Network;
import digitai.nn.OptimizerType;
import digitai.nn.TrainConfig;
import digitai.nn.Trainer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 命令行训练工具。
 *
 * <pre>
 * java -cp out/classes digitai.Main train --epochs 8 --hidden 64,32
 * </pre>
 */
public final class TrainCli {

    private TrainCli() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                printUsage(out);
                return 0;
            }
            return train(options, out, err);
        } catch (IllegalArgumentException e) {
            err.println("参数错误: " + e.getMessage());
            printUsage(err);
            return 2;
        } catch (IOException e) {
            err.println("训练失败: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("训练异常: " + e);
            e.printStackTrace(err);
            return 1;
        }
    }

    private static int train(Options options, PrintStream out, PrintStream err) throws IOException {
        Path dataDir = options.dataDir == null ? AppPaths.dataDir() : options.dataDir;
        Path modelFile = options.modelFile == null ? AppPaths.modelFile() : options.modelFile;

        out.printf("DigitLab 训练器%n");
        out.printf("  数据目录 : %s%n", dataDir.toAbsolutePath());
        out.printf("  模型输出 : %s%n", modelFile.toAbsolutePath());
        out.printf("  网络结构 : 784 -> %s -> 10%n", java.util.Arrays.toString(options.hidden)
                .replace("[", "").replace("]", "").replace(" ", ""));
        out.printf("  超参数   : epochs=%d, lr=%s, batch=%d, optimizer=%s, l2=%s%n",
                options.epochs, options.learningRate, options.batchSize, options.optimizer, options.l2);

        long t0 = System.nanoTime();
        Dataset train = MnistLoader.loadTrain(dataDir);
        Dataset test = MnistLoader.loadTest(dataDir);
        if (options.samples > 0 && options.samples < train.size()) {
            train = train.shuffled(options.seed).slice(0, options.samples);
        }
        int testLimit = options.testLimit <= 0 ? test.size() : Math.min(options.testLimit, test.size());
        out.printf("  数据加载完成: 训练 %,d 张 / 测试 %,d 张，用时 %.2fs%n",
                train.size(), testLimit, (System.nanoTime() - t0) / 1e9);

        int[] layers = new int[2 + options.hidden.length];
        layers[0] = 784;
        System.arraycopy(options.hidden, 0, layers, 1, options.hidden.length);
        layers[layers.length - 1] = 10;

        Network network = new Network(options.seed, layers);
        out.printf("  参数量   : %,d%n", network.parameterCount());

        TrainConfig config = new TrainConfig(options.learningRate, options.batchSize, options.epochs,
                options.l2, options.optimizer, options.seed);
        Trainer trainer = new Trainer(network, config);

        Evaluation initial = trainer.evaluate(test, testLimit);
        out.printf("  初始测试准确率: %.2f%%（随机初始化，应约 10%%）%n%n", initial.accuracy() * 100);

        long trainStart = System.nanoTime();
        Evaluation last = initial;
        for (int epoch = 1; epoch <= options.epochs; epoch++) {
            final int currentEpoch = epoch;
            long epochStart = System.nanoTime();
            EpochResult result = trainer.trainEpoch(train, (batch, total, loss) -> {
                if (!options.quietBatches && (batch % 100 == 0 || batch == total - 1)) {
                    out.printf("\r  epoch %d/%d  batch %4d/%d  loss=%.4f   ",
                            currentEpoch, options.epochs, batch + 1, total, loss);
                }
                return true;
            });
            if (!options.quietBatches) {
                out.printf("\r%80s\r", "");
            }
            Evaluation evaluation = trainer.evaluate(test, testLimit);
            last = evaluation;
            network.save(modelFile);
            out.printf(Locale.ROOT,
                    "Epoch %2d/%d   loss=%.4f   trainAcc=%6.2f%%   testAcc=%6.2f%%   %5.1fs   -> 已保存%n",
                    epoch, options.epochs, result.loss(), result.trainAccuracy() * 100,
                    evaluation.accuracy() * 100, (System.nanoTime() - epochStart) / 1e9);
        }

        long totalMillis = (System.nanoTime() - trainStart) / 1_000_000L;
        out.printf(Locale.ROOT, "%n训练完成，总用时 %.1fs%n", totalMillis / 1000.0);
        out.printf(Locale.ROOT, "最终测试准确率: %.2f%%（%d / %d）%n",
                last.accuracy() * 100, last.correct(), last.total());
        out.printf("模型已保存到: %s%n", modelFile.toAbsolutePath());
        out.println();
        out.println("每个数字的正确率:");
        for (int digit = 0; digit < 10; digit++) {
            int correct = last.predicted(digit, digit);
            int total = 0;
            for (int predicted = 0; predicted < 10; predicted++) {
                total += last.predicted(digit, predicted);
            }
            out.printf(Locale.ROOT, "  %d: %5.2f%%  (%d/%d)%n",
                    digit, total == 0 ? 0 : 100.0 * correct / total, correct, total);
        }
        return 0;
    }

    private static void printUsage(PrintStream out) {
        out.println("""
                用法: java -cp out/classes digitai.Main train [选项]

                  --data <目录>        MNIST 数据目录（默认自动查找 data/mnist）
                  --out <文件>         模型输出文件（默认 models/digit-mlp.bin）
                  --epochs <n>         训练轮数（默认 8）
                  --hidden <列表>      隐藏层，如 64,32（默认 64,32）
                  --lr <数值>          学习率（默认 0.001）
                  --batch <n>          batch size（默认 64）
                  --optimizer <名称>   adam 或 sgd（默认 adam）
                  --l2 <数值>          L2 正则（默认 0.0001）
                  --samples <n>        只用前 n 张训练（默认 0 = 全部）
                  --test-limit <n>     测试集评估张数（默认 10000）
                  --seed <n>           随机种子（默认 42）
                  --quiet-batches      不打印 batch 进度
                """);
    }

    private static final class Options {
        Path dataDir;
        Path modelFile;
        int epochs = 8;
        int[] hidden = {64, 32};
        double learningRate = 0.001;
        int batchSize = 64;
        OptimizerType optimizer = OptimizerType.ADAM;
        double l2 = 1e-4;
        int samples = 0;
        int testLimit = 10000;
        long seed = 42;
        boolean quietBatches;
        boolean help;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> options.help = true;
                    case "--data" -> options.dataDir = Path.of(requireValue(args, ++i, arg));
                    case "--out" -> options.modelFile = Path.of(requireValue(args, ++i, arg));
                    case "--epochs" -> options.epochs = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--hidden" -> options.hidden = parseHidden(requireValue(args, ++i, arg));
                    case "--lr" -> options.learningRate = Double.parseDouble(requireValue(args, ++i, arg));
                    case "--batch" -> options.batchSize = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--optimizer" -> options.optimizer = parseOptimizer(requireValue(args, ++i, arg));
                    case "--l2" -> options.l2 = Double.parseDouble(requireValue(args, ++i, arg));
                    case "--samples" -> options.samples = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--test-limit" -> options.testLimit = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--seed" -> options.seed = Long.parseLong(requireValue(args, ++i, arg));
                    case "--quiet-batches" -> options.quietBatches = true;
                    default -> throw new IllegalArgumentException("未知参数: " + arg);
                }
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " 缺少参数值");
            }
            return args[index];
        }

        private static int[] parseHidden(String text) {
            String[] parts = text.split(",");
            int[] values = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i].trim());
                if (values[i] < 1 || values[i] > 2048) {
                    throw new IllegalArgumentException("隐藏层大小应在 1..2048: " + values[i]);
                }
            }
            return values;
        }

        private static OptimizerType parseOptimizer(String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "adam" -> OptimizerType.ADAM;
                case "sgd" -> OptimizerType.SGD;
                default -> throw new IllegalArgumentException("优化器只能是 adam 或 sgd: " + text);
            };
        }
    }
}
