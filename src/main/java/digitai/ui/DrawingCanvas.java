package digitai.ui;

import digitai.data.Preprocessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 手写画板：白底画布 + 黑色圆头笔刷 + 多步撤销 + 随机 MNIST 样本。
 *
 * <p>内部使用 2 倍超采样位图保证高 DPI 下笔迹清晰；笔迹以归一化坐标保存，
 * 因此窗口缩放不会丢失内容。画板上叠加浅色网格与四角标记作为书写参考。</p>
 */
public final class DrawingCanvas extends JComponent {

    /** 画板变化监听：拖动过程中每个点都会触发（调用方自行做防抖）。 */
    public interface Listener {
        void canvasChanged(DrawingCanvas canvas);
    }

    private static final int SUPERSAMPLE = 2;
    private static final int MIN_SIDE = 180;

    private final List<Stroke> strokes = new ArrayList<>();
    private final Listener listener;

    private BufferedImage ink;          // 透明底墨迹层，尺寸为组件像素 × SUPERSAMPLE
    private Stroke active;

    private int brushSize = 18;

    private boolean sampleMode;
    private double[] sampleVector;      // 随机样本的原始 784 向量（直接作为网络输入）
    private BufferedImage sampleImage;  // 28×28 样本图，用于在画布上展示
    private boolean hovered;

    public DrawingCanvas(Listener listener) {
        this.listener = listener;
        setOpaque(false);
        setPreferredSize(new Dimension(320, 320));
        setMinimumSize(new Dimension(MIN_SIDE, MIN_SIDE));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setToolTipText("按住鼠标左键书写，松开后自动识别");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || !isEnabled()) {
                    return;
                }
                requestFocusInWindow();
                if (sampleMode) {
                    // 在样本上重新书写时，清掉样本，从空白开始
                    sampleMode = false;
                    sampleVector = null;
                    sampleImage = null;
                    rebuildInk();
                }
                Point2D.Float p = toImagePoint(e.getX(), e.getY());
                active = new Stroke(brushSize);
                active.points.add(p);
                drawDot(p, brushSize);
                repaint();
                notifyChanged();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (active == null || !isEnabled()) {
                    return;
                }
                Point2D.Float p = toImagePoint(e.getX(), e.getY());
                Point2D.Float prev = active.points.get(active.points.size() - 1);
                // 归一化坐标换算回像素距离，小于 1 像素的抖动才忽略
                double dx = (p.x - prev.x) * getWidth();
                double dy = (p.y - prev.y) * getHeight();
                if (dx * dx + dy * dy < 1.0) {
                    return;
                }
                active.points.add(p);
                drawSegment(prev, p, active.thickness);
                repaint();
                notifyChanged();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (active != null) {
                    strokes.add(active);
                    active = null;
                    repaint();
                    notifyChanged();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rebuildInk();
                repaint();
            }
        });
    }

    // ==================================================================
    // 对外接口
    // ==================================================================

    public int brushSize() {
        return brushSize;
    }

    public void setBrushSize(int size) {
        this.brushSize = Theme.clamp(size, 8, 28);
    }

    /** 画板是否还是空白（无笔迹、无样本）。 */
    public boolean isEmpty() {
        return strokes.isEmpty() && active == null && !sampleMode;
    }

    /** 是否处于随机样本模式。 */
    public boolean isSampleMode() {
        return sampleMode;
    }

    public boolean hasUndo() {
        return !strokes.isEmpty() || sampleMode;
    }

    /** 当前应当输入网络的 784 向量；空白时返回 null。 */
    public double[] currentVector() {
        if (sampleMode && sampleVector != null) {
            return sampleVector.clone();
        }
        if (strokes.isEmpty() && active == null) {
            return null;
        }
        BufferedImage snap = renderSnapshot();
        if (snap == null) {
            return null;
        }
        double[] v = Preprocessor.fromImage(snap);
        if (v == null) {
            return null;
        }
        for (double d : v) {
            if (d > 1e-6) {
                return v;
            }
        }
        return null;
    }

    /** 载入一张 MNIST 样本直接展示（识别时使用原始向量，不做二次预处理）。 */
    public void loadSample(double[] vector) {
        if (vector == null || vector.length != Preprocessor.SIZE * Preprocessor.SIZE) {
            return;
        }
        sampleVector = vector.clone();
        sampleImage = Preprocessor.toImage(sampleVector);
        sampleMode = true;
        strokes.clear();
        active = null;
        rebuildInk();
        repaint();
        notifyChanged();
    }

    public void clear() {
        strokes.clear();
        active = null;
        sampleMode = false;
        sampleVector = null;
        sampleImage = null;
        rebuildInk();
        repaint();
        notifyChanged();
    }

    public void undo() {
        if (sampleMode) {
            clear();
            return;
        }
        if (strokes.isEmpty()) {
            return;
        }
        strokes.remove(strokes.size() - 1);
        rebuildInk();
        repaint();
        notifyChanged();
    }

    // ==================================================================
    // 位图绘制
    // ==================================================================

    private Point2D.Float toImagePoint(int x, int y) {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        float nx = (float) Theme.clamp(x / (double) w, 0.0, 1.0);
        float ny = (float) Theme.clamp(y / (double) h, 0.0, 1.0);
        return new Point2D.Float(nx, ny);
    }

    private void ensureInk() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (ink == null || ink.getWidth() != w * SUPERSAMPLE || ink.getHeight() != h * SUPERSAMPLE) {
            ink = new BufferedImage(w * SUPERSAMPLE, h * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private void rebuildInk() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            ink = null;
            return;
        }
        ensureInk();
        Graphics2D g = Theme.quality(ink.createGraphics());
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, ink.getWidth(), ink.getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        for (Stroke s : strokes) {
            paintStroke(g, s);
        }
        g.dispose();
    }

    private void paintStroke(Graphics2D g, Stroke s) {
        if (s.points.isEmpty()) {
            return;
        }
        int w = ink.getWidth();
        int h = ink.getHeight();
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(s.thickness * SUPERSAMPLE,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (s.points.size() == 1) {
            Point2D.Float p = s.points.get(0);
            int cx = Math.round(p.x * w);
            int cy = Math.round(p.y * h);
            int r = Math.max(1, Math.round(s.thickness * SUPERSAMPLE / 2f));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            return;
        }
        Path2D.Float path = new Path2D.Float();
        Point2D.Float first = s.points.get(0);
        path.moveTo(first.x * w, first.y * h);
        for (int i = 1; i < s.points.size(); i++) {
            Point2D.Float p = s.points.get(i);
            path.lineTo(p.x * w, p.y * h);
        }
        g.draw(path);
    }

    private void drawDot(Point2D.Float p, int thickness) {
        ensureInk();
        Graphics2D g = Theme.quality(ink.createGraphics());
        g.setColor(Theme.INK);
        int w = ink.getWidth();
        int h = ink.getHeight();
        int cx = Math.round(p.x * w);
        int cy = Math.round(p.y * h);
        int r = Math.max(1, Math.round(thickness * SUPERSAMPLE / 2f));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.dispose();
    }

    private void drawSegment(Point2D.Float a, Point2D.Float b, int thickness) {
        if (ink == null) {
            rebuildInk();
            if (ink == null) {
                return;
            }
        }
        Graphics2D g = Theme.quality(ink.createGraphics());
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(thickness * SUPERSAMPLE,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int w = ink.getWidth();
        int h = ink.getHeight();
        g.drawLine(Math.round(a.x * w), Math.round(a.y * h),
                Math.round(b.x * w), Math.round(b.y * h));
        g.dispose();
    }

    /** 生成白底黑字的逻辑尺寸快照，交给 Preprocessor。 */
    private BufferedImage renderSnapshot() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage snap = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = Theme.quality(snap.createGraphics());
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (ink != null) {
            g.drawImage(ink, 0, 0, w, h, null);
        }
        g.dispose();
        return snap;
    }

    // ==================================================================
    // 组件绘制
    // ==================================================================

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.quality(g);
        int w = getWidth();
        int h = getHeight();
        int arc = 14;

        // 投影 + 白色画布
        Theme.cardShadow(g2, 0, 0, w, h, arc);
        g2.setColor(Theme.CANVAS_WHITE);
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        Shape clip = Theme.roundClip(1, 1, w - 2, h - 2, arc);
        Shape oldClip = g2.getClip();
        g2.clip(clip);

        // 浅色网格（四等分）+ 四角标记
        g2.setColor(new Color(0xE8EEF5));
        g2.setStroke(new BasicStroke(1f));
        for (int i = 1; i <= 3; i++) {
            int x = w * i / 4;
            int y = h * i / 4;
            g2.drawLine(x, 0, x, h);
            g2.drawLine(0, y, w, y);
        }
        // 中心十字更淡
        g2.setColor(new Color(0xF1F5F9));
        g2.drawLine(w / 2, 0, w / 2, h);
        g2.drawLine(0, h / 2, w, h / 2);
        // 四角 L 形标记
        g2.setColor(new Color(0xC3CED9));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int m = 12;
        int len = 16;
        g2.drawLine(m, m, m + len, m);
        g2.drawLine(m, m, m, m + len);
        g2.drawLine(w - m, m, w - m - len, m);
        g2.drawLine(w - m, m, w - m, m + len);
        g2.drawLine(m, h - m, m + len, h - m);
        g2.drawLine(m, h - m, m, h - m - len);
        g2.drawLine(w - m, h - m, w - m - len, h - m);
        g2.drawLine(w - m, h - m, w - m, h - m - len);

        // 随机样本（最近邻放大，保留像素感；留出四周留白）
        if (sampleMode && sampleImage != null) {
            int side = Math.round(Math.min(w, h) * 0.86f);
            int x = (w - side) / 2;
            int y = (h - side) / 2;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(sampleImage, x, y, side, side, null);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // 样本标记
            String badge = "MNIST 样本";
            g2.setFont(Theme.F_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(badge) + 18;
            g2.setColor(new Color(241, 245, 249, 235));
            g2.fillRoundRect(10, 10, bw, 22, 11, 11);
            g2.setColor(new Color(0xCBD5E1));
            g2.drawRoundRect(10, 10, bw - 1, 21, 11, 11);
            g2.setColor(new Color(0x475569));
            g2.drawString(badge, 19, 10 + (22 - fm.getHeight()) / 2 + fm.getAscent());
        }

        // 墨迹层（超采样后平滑缩放）
        if (ink != null) {
            g2.drawImage(ink, 0, 0, w, h, null);
        }

        // 空白提示
        if (isEmpty()) {
            g2.setFont(Theme.ui(Font.PLAIN, 13f));
            FontMetrics fm = g2.getFontMetrics();
            String hint = "在这里写一个 0 – 9 的数字";
            int tx = (w - fm.stringWidth(hint)) / 2;
            int ty = h / 2 + fm.getAscent() / 2 - 6;
            g2.setColor(new Color(0xAEBBC8));
            g2.drawString(hint, tx, ty);
            String sub = hovered ? "按住左键拖动书写" : "鼠标拖动即可书写";
            g2.setFont(Theme.F_SMALL);
            FontMetrics fm2 = g2.getFontMetrics();
            g2.setColor(new Color(0xC4CFDA));
            g2.drawString(sub, (w - fm2.stringWidth(sub)) / 2, ty + 22);
        }

        g2.setClip(oldClip);
        g2.setColor(new Color(0x30363D));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.dispose();
    }

    /** 一条笔迹：归一化坐标点列 + 逻辑像素笔宽。 */
    private static final class Stroke {
        final List<Point2D.Float> points = new ArrayList<>();
        final int thickness;

        Stroke(int thickness) {
            this.thickness = thickness;
        }
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.canvasChanged(this);
        }
    }
}
