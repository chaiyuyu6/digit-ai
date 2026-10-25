package digitai.tools;

import digitai.data.AppPaths;
import digitai.nn.Network;
import digitai.ui.AppState;
import digitai.ui.Theme;
import digitai.ui.TrainPanel;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 训练页交互冒烟测试：真实点击「开始训练」，等待后台训练产生 batch 进度，截图；
 * 再点击「停止」，确认状态回到空闲。不会跑完整个训练，也不会覆盖预训练模型。
 */
public final class TrainingUiSmokeTest {

    private TrainingUiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Theme.install();
        Network network = Network.load(AppPaths.modelFile());
        AppState state = new AppState();
        state.setNetwork(network, AppState.ModelState.LOADED, "测试模型");

        JFrame[] frameHolder = new JFrame[1];
        TrainPanel[] panelHolder = new TrainPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            TrainPanel panel = new TrainPanel(state, () -> {
            });
            JFrame frame = new JFrame("DigitLab training smoke test");
            frame.setContentPane(panel);
            frame.setSize(1360, 860);
            frame.addNotify();
            frame.validate();
            frame.doLayout();
            panel.setSize(frame.getContentPane().getWidth(), frame.getContentPane().getHeight());
            panel.doLayout();
            panel.onShown();
            frameHolder[0] = frame;
            panelHolder[0] = panel;
        });
        JFrame frame = frameHolder[0];
        TrainPanel panel = panelHolder[0];
        Thread.sleep(400);

        JButton start = findButton(panel, "开始训练");
        if (start == null) {
            throw new AssertionError("找不到「开始训练」按钮");
        }
        SwingUtilities.invokeAndWait(start::doClick);

        String runningPhase = waitForPhase(panel, "训练中", 20_000);
        System.out.println("训练启动阶段文案: " + runningPhase);
        if (runningPhase == null) {
            throw new AssertionError("点击开始后 20 秒内没有进入「训练中」");
        }
        Thread.sleep(1200);
        System.out.println("训练中 state.isTraining=" + state.isTraining()
                + " 训练集已加载=" + state.hasTrainData());
        capture(frame, Path.of("build", "ui-train-running.png"));

        JButton stop = findButton(panel, "停止");
        if (stop == null) {
            throw new AssertionError("找不到「停止」按钮");
        }
        SwingUtilities.invokeAndWait(stop::doClick);
        boolean stopped = waitForTrainingState(state, false, 15_000);
        System.out.println("停止后 state.isTraining=false: " + stopped
                + "，阶段文案=" + latestPhase(panel));
        capture(frame, Path.of("build", "ui-train-stopped.png"));

        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println("训练页截图已写入 build/ui-train-running.png 与 build/ui-train-stopped.png");
        System.exit(stopped ? 0 : 1);
    }

    private static boolean waitForTrainingState(AppState state, boolean target, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (state.isTraining() == target) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static String waitForPhase(TrainPanel panel, String keyword, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final String[] hit = new String[1];
            SwingUtilities.invokeAndWait(() -> {
                for (JLabel label : labels(panel)) {
                    String text = label.getText();
                    if (text != null && text.contains(keyword)) {
                        hit[0] = text;
                        return;
                    }
                }
            });
            if (hit[0] != null) {
                return hit[0];
            }
            Thread.sleep(100);
        }
        return null;
    }

    private static String latestPhase(TrainPanel panel) throws Exception {
        final String[] phase = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            for (JLabel label : labels(panel)) {
                String text = label.getText();
                if (text != null && (text.contains("停止") || text.contains("训练")
                        || text.contains("加载") || text.contains("取消"))) {
                    phase[0] = text;
                }
            }
        });
        return phase[0];
    }

    private static List<JLabel> labels(Container root) {
        List<JLabel> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(Container root, List<JLabel> out) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel label) {
                out.add(label);
            }
            if (c instanceof Container container) {
                collect(container, out);
            }
        }
    }

    private static void capture(JFrame frame, Path file) throws Exception {
        Files.createDirectories(file.getParent());
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(frame.getRootPane().getWidth(),
                    frame.getRootPane().getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            Theme.quality(g);
            g.setColor(Theme.BG);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            frame.getRootPane().paint(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", file.toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton button && button.getText() != null && button.getText().contains(text)) {
                return button;
            }
            if (c instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
