package digitai.tools;

import digitai.data.AppPaths;
import digitai.nn.Network;
import digitai.ui.AppState;
import digitai.ui.DrawingCanvas;
import digitai.ui.RecognitionPanel;
import digitai.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 无显示器的 GUI 交互冒烟测试：真实地向 DrawingCanvas 派发鼠标事件画一个“3”，
 * 等待防抖与前向传播，检查预处理向量和识别结果，并输出离屏截图。
 */
public final class UiSmokeTest {

    private UiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Theme.install();
        Network network = Network.load(args.length > 0 ? Path.of(args[0]) : AppPaths.modelFile());
        AppState state = new AppState();
        state.setNetwork(network, AppState.ModelState.LOADED, "测试模型");

        RecognitionPanel[] panelHolder = new RecognitionPanel[1];
        JFrame[] frameHolder = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            RecognitionPanel panel = new RecognitionPanel(state);
            JFrame frame = new JFrame("DigitLab UI smoke test");
            frame.setContentPane(panel);
            frame.setSize(1360, 860);
            frame.addNotify();
            frame.validate();
            frame.doLayout();
            panel.setSize(frame.getContentPane().getWidth(), frame.getContentPane().getHeight());
            panel.doLayout();
            frameHolder[0] = frame;
            panelHolder[0] = panel;
        });
        JFrame frame = frameHolder[0];
        RecognitionPanel panel = panelHolder[0];
        DrawingCanvas canvas = panel.canvas();
        Thread.sleep(300);
        System.out.println("canvas=" + canvas.getWidth() + "×" + canvas.getHeight());

        drawDigitThree(canvas);
        Thread.sleep(900); // 防抖 150ms + 动画/前向

        double[] vector = panel.canvas().currentVector();
        if (vector == null) {
            throw new AssertionError("画完数字后 currentVector() 仍为 null");
        }
        double[] probs = network.forward(vector);
        int predicted = argmax(probs);
        System.out.printf(Locale.ROOT, "识别结果: %d  置信度 %.2f%%%n", predicted, probs[predicted] * 100);
        System.out.println("画板非空: " + !canvas.isEmpty());
        System.out.println("预处理非零像素: " + countInk(vector));

        Path out = Path.of("build", "ui-smoke.png");
        Files.createDirectories(out.getParent());
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
                ImageIO.write(image, "png", out.toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("截图: " + out.toAbsolutePath());

        SwingUtilities.invokeAndWait(panel::clearCanvas);
        Thread.sleep(250);
        System.out.println("清空后画板为空: " + canvas.isEmpty());

        boolean ok = predicted == 3 && canvas.isEmpty();
        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println(ok ? "UI 冒烟测试通过" : "UI 冒烟测试失败（预测不是 3，见截图）");
        System.exit(ok ? 0 : 1);
    }

    private static void drawDigitThree(DrawingCanvas canvas) throws Exception {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        float[][] first = {
                {0.30f, 0.26f}, {0.42f, 0.21f}, {0.56f, 0.21f}, {0.64f, 0.27f},
                {0.62f, 0.36f}, {0.50f, 0.44f}, {0.38f, 0.51f}
        };
        float[][] second = {
                {0.66f, 0.48f}, {0.64f, 0.58f}, {0.56f, 0.69f}, {0.44f, 0.75f},
                {0.32f, 0.70f}, {0.26f, 0.61f}
        };
        SwingUtilities.invokeAndWait(() -> {
            stroke(canvas, w, h, first);
            stroke(canvas, w, h, second);
        });
    }

    private static void stroke(DrawingCanvas canvas, int w, int h, float[][] points) {
        long when = System.currentTimeMillis();
        MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, when,
                InputEvent.BUTTON1_DOWN_MASK, (int) (points[0][0] * w), (int) (points[0][1] * h),
                1, false, MouseEvent.BUTTON1);
        canvas.dispatchEvent(press);
        for (int i = 1; i < points.length; i++) {
            MouseEvent drag = new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, when + i,
                    InputEvent.BUTTON1_DOWN_MASK, (int) (points[i][0] * w), (int) (points[i][1] * h),
                    1, false, MouseEvent.NOBUTTON);
            canvas.dispatchEvent(drag);
        }
        MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, when + points.length,
                0, (int) (points[points.length - 1][0] * w), (int) (points[points.length - 1][1] * h),
                1, false, MouseEvent.BUTTON1);
        canvas.dispatchEvent(release);
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    private static int countInk(double[] vector) {
        int count = 0;
        for (double v : vector) {
            if (v > 0.05) count++;
        }
        return count;
    }
}
