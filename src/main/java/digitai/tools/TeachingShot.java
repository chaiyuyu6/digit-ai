package digitai.tools;

import digitai.ui.TeachingPanel;
import digitai.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 把教学页滚动到不同位置离屏截图，检查长页面排版是否完整。 */
public final class TeachingShot {

    private TeachingShot() {
    }

    public static void main(String[] args) throws Exception {
        Theme.install();
        int width = args.length > 0 ? Integer.parseInt(args[0]) : 1360;
        int height = args.length > 1 ? Integer.parseInt(args[1]) : 860;
        Path outDir = Path.of("build", "teach-shots");
        Files.createDirectories(outDir);

        JFrame[] frameHolder = new JFrame[1];
        TeachingPanel[] panelHolder = new TeachingPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            TeachingPanel panel = new TeachingPanel();
            JFrame frame = new JFrame("teaching shot");
            frame.setContentPane(panel);
            frame.setSize(width, height);
            frame.addNotify();
            frame.validate();
            frame.doLayout();
            panel.setSize(frame.getContentPane().getWidth(), frame.getContentPane().getHeight());
            panel.doLayout();
            frameHolder[0] = frame;
            panelHolder[0] = panel;
        });
        JFrame frame = frameHolder[0];
        TeachingPanel panel = panelHolder[0];
        Thread.sleep(300);

        JScrollPane scroll = findScroll(panel);
        if (scroll == null) {
            throw new AssertionError("找不到教学页的 JScrollPane");
        }
        JScrollBar bar = scroll.getVerticalScrollBar();
        System.out.println("viewport=" + scroll.getViewport().getExtentSize()
                + " view=" + scroll.getViewport().getView().getPreferredSize()
                + " scrollMax=" + bar.getMaximum() + " visible=" + bar.getVisibleAmount());
        int max = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
        int shots = 4;
        for (int i = 0; i < shots; i++) {
            int value = (int) Math.round(max * (i / (double) (shots - 1)));
            final int v = value;
            SwingUtilities.invokeAndWait(() -> bar.setValue(v));
            Thread.sleep(200);
            Path file = outDir.resolve(String.format("teach-%d-%dx%d.png", i, width, height));
            capture(frame, file);
            System.out.println("已输出 " + file + " scroll=" + value + "/" + max);
        }
        SwingUtilities.invokeAndWait(frame::dispose);
        System.exit(0);
    }

    private static void capture(JFrame frame, Path file) throws Exception {
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

    private static JScrollPane findScroll(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JScrollPane pane) {
                return pane;
            }
            if (c instanceof Container container) {
                JScrollPane found = findScroll(container);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
