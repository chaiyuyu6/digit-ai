package digitai;

import digitai.data.AppPaths;
import digitai.tools.EvaluateCli;
import digitai.tools.MnistDownloader;
import digitai.tools.PredictCli;
import digitai.tools.TrainCli;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * DigitLab 入口。
 *
 * <p>不带参数（或带 GUI 参数）启动图形界面；带 train/eval/predict/download 子命令时运行命令行工具。</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // 在 Windows 控制台中尽量以 UTF-8 输出中文
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            // 保持默认输出即可
        }

        if (args.length == 0) {
            launchGui();
            return;
        }
        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "gui" -> launchGui();
            case "train" -> exit(TrainCli.run(rest, System.out, System.err));
            case "eval" -> exit(EvaluateCli.run(rest, System.out, System.err));
            case "predict" -> exit(PredictCli.run(rest, System.out, System.err));
            case "download" -> {
                try {
                    MnistDownloader.ensureData(AppPaths.dataDir(), System.out);
                } catch (Exception e) {
                    System.err.println("下载失败: " + e.getMessage());
                    exit(1);
                }
            }
            case "-h", "--help", "help" -> printUsage(System.out);
            case "-v", "--version", "version" -> {
                System.out.println("DigitLab 1.0 · 纯 Java 手写数字识别教学实验室");
                System.out.println("Java: " + System.getProperty("java.version"));
                System.out.println("数据目录: " + AppPaths.dataDir().toAbsolutePath());
                System.out.println("模型文件: " + AppPaths.modelFile().toAbsolutePath());
            }
            default -> {
                if (command.startsWith("-")) {
                    launchGui();
                } else {
                    System.err.println("未知命令: " + command);
                    printUsage(System.err);
                    exit(2);
                }
            }
        }
    }

    private static void launchGui() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("当前环境没有图形界面（headless），无法启动 GUI。");
            System.err.println("请在有桌面的环境运行，或使用命令行: train / eval / predict");
            exit(1);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                new digitai.ui.MainFrame().setVisible(true);
            } catch (Throwable t) {
                t.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "启动界面失败：\n" + t.getMessage(),
                        "DigitLab", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void printUsage(PrintStream out) {
        out.println("""
                DigitLab · 纯 Java 手写数字识别教学实验室

                用法:
                  java -jar digit-ai.jar                 启动图形界面
                  java -jar digit-ai.jar train [选项]    训练模型
                  java -jar digit-ai.jar eval [选项]     在 MNIST 测试集上评估
                  java -jar digit-ai.jar predict <图片>  识别白底黑字数字图片
                  java -jar digit-ai.jar download        下载 MNIST 数据
                  java -jar digit-ai.jar --version       显示版本与路径

                训练选项示例:
                  --epochs 10 --hidden 64,32 --lr 0.001 --batch 64
                  --optimizer adam|sgd --data <目录> --out <模型文件>

                更多说明见 README.md。
                """);
    }

    private static void exit(int code) {
        if (code != 0) {
            System.exit(code);
        }
    }
}
