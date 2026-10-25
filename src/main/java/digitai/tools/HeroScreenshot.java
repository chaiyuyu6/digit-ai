package digitai.tools;

import digitai.ui.DrawingCanvas;
import digitai.ui.MainFrame;
import digitai.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 生成 README 用主截图：真实主窗口 + 鼠标画出的数字。 */
public final class HeroScreenshot {

    private HeroScreenshot() {
    }

    public static void main(String[] args) throws Exception {
        Theme.install();
        Path out = Path.of("docs", "screenshots", "recognition.png");
        if (args.length > 0) {
            out = Path.of(args[0]);
        }
        final Path output = out;
        Files.createDirectories(output.getParent());

        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setSize(1360, 860);
            frame.addNotify();
            frame.validate();
            frame.doLayout();
            holder[0] = frame;
        });
        MainFrame frame = holder[0];
        Thread.sleep(1800); // 等异步模型加载

        DrawingCanvas canvas = find(frame, DrawingCanvas.class);
        if (canvas == null) {
            throw new AssertionError("找不到 DrawingCanvas");
        }
        System.out.println("canvas=" + canvas.getWidth() + "×" + canvas.getHeight());

        drawThree(canvas);
        Thread.sleep(1200); // 防抖 + 脉冲动画

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
                ImageIO.write(image, "png", output.toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("已输出 " + output.toAbsolutePath());
        SwingUtilities.invokeAndWait(frame::dispose);
        System.exit(0);
    }

    private static void drawThree(DrawingCanvas canvas) throws Exception {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        float[][] first = {
                {0.31f, 0.27f}, {0.43f, 0.22f}, {0.56f, 0.22f}, {0.64f, 0.28f},
                {0.63f, 0.37f}, {0.51f, 0.45f}, {0.39f, 0.52f}
        };
        float[][] second = {
                {0.66f, 0.49f}, {0.64f, 0.60f}, {0.56f, 0.70f}, {0.44f, 0.76f},
                {0.32f, 0.71f}, {0.27f, 0.62f}
        };
        SwingUtilities.invokeAndWait(() -> {
            stroke(canvas, w, h, first);
            stroke(canvas, w, h, second);
        });
    }

    private static void stroke(DrawingCanvas canvas, int w, int h, float[][] points) {
        long when = System.currentTimeMillis();
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, when,
                InputEvent.BUTTON1_DOWN_MASK, (int) (points[0][0] * w), (int) (points[0][1] * h),
                1, false, MouseEvent.BUTTON1));
        for (int i = 1; i < points.length; i++) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, when + i,
                    InputEvent.BUTTON1_DOWN_MASK, (int) (points[i][0] * w), (int) (points[i][1] * h),
                    1, false, MouseEvent.NOBUTTON));
        }
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, when + points.length,
                0, (int) (points[points.length - 1][0] * w), (int) (points[points.length - 1][1] * h),
                1, false, MouseEvent.BUTTON1));
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container container) {
                T found = find(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
