package digitai.ui;

import digitai.data.AppPaths;
import digitai.data.Dataset;
import digitai.data.MnistLoader;
import digitai.nn.Network;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 离屏截图工具：不依赖屏幕截图，把 MainFrame 渲染到 BufferedImage 并写 PNG。
 *
 * <p>用法（在 digit-ai 目录下）：</p>
 * <pre>
 * javac -encoding UTF-8 -d out/classes $(find src/main/java -name '*.java')
 * java -cp out/classes digitai.ui.ScreenshotHarness [输出目录]
 * </pre>
 *
 * <p>输出：识别页（空 / 有样本 / 权重热图）、训练页、教学页，以及 1120×740 最小尺寸版本。</p>
 */
public final class ScreenshotHarness {

    private ScreenshotHarness() {
    }

    public static void main(String[] args) throws Exception {
        Theme.install();
        Path outDir = Path.of(args.length > 0 ? args[0] : "build/ui-shots");
        Files.createDirectories(outDir);

        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainFrame());
        MainFrame frame = holder[0];

        // 同步加载模型并注入状态，避免截图时异步加载尚未完成
        Path modelFile = AppPaths.modelFile();
        if (Files.isRegularFile(modelFile)) {
            Network network = Network.load(modelFile);
            SwingUtilities.invokeAndWait(() -> frame.state().setNetwork(network,
                    AppState.ModelState.LOADED, "模型已加载 · " + modelFile.getFileName()));
        }

        Dataset test = null;
        if (AppPaths.hasData()) {
            try {
                test = MnistLoader.loadTest(AppPaths.dataDir());
            } catch (Exception ex) {
                System.err.println("测试集加载失败: " + ex.getMessage());
            }
        }

        render(frame, 0, 1360, 860, outDir.resolve("01-recognize-empty-1360x860.png"));

        final Dataset loadedTest = test;
        if (loadedTest != null) {
            SwingUtilities.invokeAndWait(() -> frame.state().setTestDataForTest(loadedTest));
        }

        if (test != null && test.size() > 0 && frame.state().hasNetwork()) {
            Dataset data = test;
            SwingUtilities.invokeAndWait(() -> frame.recognition().canvas().loadSample(data.image(0).clone()));
            Thread.sleep(1100); // 等防抖 + 脉冲动画结束
            render(frame, 0, 1360, 860, outDir.resolve("02-recognize-sample-1360x860.png"));

            SwingUtilities.invokeAndWait(() -> {
                frame.recognition().networkView().selectTopNeuron(0);
                frame.recognition().networkView().repaint();
            });
            Thread.sleep(120);
            render(frame, 0, 1360, 860, outDir.resolve("03-recognize-inspector-1360x860.png"));

            SwingUtilities.invokeAndWait(() -> frame.recognition().networkView().selectForDemo(-1, -1));
            render(frame, 0, 1120, 740, outDir.resolve("04-recognize-min-1120x740.png"));
        }

        render(frame, 1, 1360, 860, outDir.resolve("05-train-1360x860.png"));
        SwingUtilities.invokeAndWait(() -> frame.trainingPage().seedDemoVisualsForTest());
        Thread.sleep(200);
        render(frame, 1, 1360, 860, outDir.resolve("05b-train-demo-1360x860.png"));
        render(frame, 1, 1120, 740, outDir.resolve("08-train-min-1120x740.png"));

        render(frame, 2, 1360, 860, outDir.resolve("06-teach-1360x860.png"));
        render(frame, 2, 1120, 740, outDir.resolve("07-teach-min-1120x740.png"));

        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println("截图已输出到: " + outDir.toAbsolutePath());
        // AWT 的事件线程不是守护线程，离屏渲染完成后必须显式退出，避免自动化挂起
        System.exit(0);
    }

    private static void render(MainFrame frame, int page, int w, int h, Path file) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(w, h);
            if (!frame.isDisplayable()) {
                frame.addNotify();
            }
            Insets insets = frame.getInsets();
            frame.setSize(w + insets.left + insets.right, h + insets.top + insets.bottom);
            frame.showPage(page);
            frame.validate();
            frame.doLayout();
        });
        // 让一次事件循环处理布局与重绘请求
        Thread.sleep(180);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = image.createGraphics();
            Theme.quality(g);
            g.setColor(Theme.BG);
            g.fillRect(0, 0, w, h);
            JComponent root = frame.getRootPane();
            if (root.getWidth() != w || root.getHeight() != h) {
                System.out.println("  [warn] root size " + root.getSize() + " != " + w + "x" + h);
            }
            root.paint(g);
            g.dispose();
        });
        ImageIO.write(image, "png", file.toFile());
        System.out.println("已输出 " + file + " (" + w + "×" + h + ")");
    }
}
