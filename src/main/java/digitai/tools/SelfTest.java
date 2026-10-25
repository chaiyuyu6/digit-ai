package digitai.tools;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.data.MnistLoader;
import digitai.data.Preprocessor;
import digitai.nn.Network;
import digitai.nn.TrainConfig;
import digitai.nn.Trainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** 无第三方依赖的冒烟测试：模型加载、前向、预处理、序列化、训练可学习性。 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            t.printStackTrace();
            failed++;
        }
        System.out.println();
        System.out.printf(Locale.ROOT, "结果: %d 通过, %d 失败%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path modelPath = Path.of(argsOr(args, 0, AppPaths.modelFile().toString()));
        Path dataPath = Path.of(argsOr(args, 1, AppPaths.dataDir().toString()));

        check("模型文件存在", Files.isRegularFile(modelPath), modelPath.toString());
        check("MNIST 数据存在", MnistLoader.isDataAvailable(dataPath), dataPath.toString());

        Network network = Network.load(modelPath);
        check("模型结构 784-64-32-10", Arrays.equals(network.sizes(), new int[]{784, 64, 32, 10}),
                Arrays.toString(network.sizes()));
        check("参数量 52650", network.parameterCount() == 52_650, Long.toString(network.parameterCount()));

        double[] zero = new double[784];
        double[] probs = network.forward(zero);
        check("空输入概率和为 1", Math.abs(Arrays.stream(probs).sum() - 1) < 1e-9, sum(probs));
        check("输出 10 个概率", probs.length == 10, Integer.toString(probs.length));

        Dataset test = MnistLoader.loadTest(dataPath);
        int limit = 1000;
        int correct = 0;
        for (int i = 0; i < limit; i++) {
            int predicted = argmax(network.forward(test.image(i)));
            if (predicted == test.label(i)) correct++;
        }
        double accuracy = correct / (double) limit;
        check("测试集前 1000 张准确率 > 96%", accuracy > 0.96,
                String.format(Locale.ROOT, "%.2f%%", accuracy * 100));

        // 序列化往返
        Path tmp = Files.createTempFile("digitlab-model", ".bin");
        try {
            network.save(tmp);
            Network loaded = Network.load(tmp);
            check("模型保存/加载结构一致", Arrays.equals(network.sizes(), loaded.sizes()), "");
            boolean same = true;
            for (int i = 0; i < 100; i++) {
                double[] a = network.forward(test.image(i));
                double[] b = loaded.forward(test.image(i));
                for (int d = 0; d < 10; d++) {
                    if (Math.abs(a[d] - b[d]) > 1e-9) {
                        same = false;
                        break;
                    }
                }
            }
            check("模型保存/加载预测一致", same, "");
        } finally {
            Files.deleteIfExists(tmp);
        }

        // 预处理：空白画板应得到全零输入
        int[] white = new int[280 * 280];
        Arrays.fill(white, 0xFFFFFFFF);
        double[] empty = Preprocessor.fromArgb(280, 280, white);
        check("空白画板预处理为全零", Arrays.stream(empty).allMatch(v -> v == 0), "");

        // 预处理后仍能识别一张 MNIST 样本
        Dataset small = test.slice(0, 20);
        int processedCorrect = 0;
        for (int i = 0; i < small.size(); i++) {
            var image = Preprocessor.toImage(small.image(i));
            double[] input = Preprocessor.fromImage(image);
            if (argmax(network.forward(input)) == small.label(i)) processedCorrect++;
        }
        check("预处理往返后 20 张中至少 15 张正确", processedCorrect >= 15, processedCorrect + "/20");

        // 训练器可以在一个 batch 上更新参数且不报错
        Network tiny = new Network(7L, 784, 16, 10);
        Trainer trainer = new Trainer(tiny, TrainConfig.defaults().withBatchSize(16).withEpochs(1));
        var result = trainer.trainEpoch(MnistLoader.loadTrain(dataPath).subset(160, 3), Trainer.BatchListener.NONE);
        check("一次小训练 loss 有限", Double.isFinite(result.loss()) && result.batches() == 10,
                "loss=" + result.loss() + ", batches=" + result.batches());

        System.out.println("\n自测完成（GUI 需另行人工/截图验证）。");
    }

    private static String argsOr(String[] args, int index, String fallback) {
        return args.length > index ? args[index] : fallback;
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    private static String sum(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return String.format(Locale.ROOT, "%.12f", sum);
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + name + (detail.isBlank() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            System.out.println("  [FAIL] " + name + (detail.isBlank() ? "" : "  (" + detail + ")"));
        }
    }
}
