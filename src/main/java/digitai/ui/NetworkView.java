package digitai.ui;

import digitai.data.Preprocessor;
import digitai.nn.ForwardTrace;
import digitai.nn.Network;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 神经网络可视化：输入 28×28 缩略图 → 隐藏层点阵 → 输出层，边权采样 + 前向脉冲动画。
 *
 * <p>教学重点交互：点击隐藏层 / 输出层神经元，浮层显示该神经元的权重热图与激活值。
 * 点击空白处取消。所有图形均为自绘，无图片资源。</p>
 */
public final class NetworkView extends JComponent {

    /** 采样连接线的总预算（避免糊成一片）。 */
    private static final int EDGE_BUDGET = 500;
    private static final long ANIM_NANOS = 680_000_000L;

    private Network network;
    private ForwardTrace trace;
    private final List<Edge> edges = new ArrayList<>();
    private final List<Group> hiddenGroups = new ArrayList<>();
    private final Point2D.Float[] outPos = new Point2D.Float[10];
    private final Rectangle inputRect = new Rectangle();
    private float outCx;
    private float outSpacing = 24;
    private float outCy;

    private boolean layoutValid;
    private int layoutW = -1;
    private int layoutH = -1;

    // 交互
    private int hoverGroup = -1;
    private int hoverIndex = -1;
    private int selGroup = -1;
    private int selIndex = -1;
    private final Rectangle inspectorRect = new Rectangle();
    private final Rectangle closeRect = new Rectangle();
    private boolean closeHover;

    // 动画
    private Timer animTimer;
    private long animStart;
    private boolean animating;
    private int[] pathNode;

    public NetworkView() {
        setOpaque(false);
        setPreferredSize(new Dimension(600, 560));
        setToolTipText("点击隐藏层或输出层神经元查看权重热图");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                boolean changed = hoverGroup != -1 || closeHover;
                hoverGroup = -1;
                hoverIndex = -1;
                closeHover = false;
                if (changed) {
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutValid = false;
                repaint();
            }
        });
    }

    // ==================================================================
    // 对外接口
    // ==================================================================

    public void setNetwork(Network network) {
        this.network = network;
        this.trace = null;
        this.selGroup = -1;
        this.selIndex = -1;
        this.hoverGroup = -1;
        this.hoverIndex = -1;
        this.pathNode = null;
        stopAnimation();
        sampleEdges();
        buildGroups();
        layoutValid = false;
        repaint();
    }

    public void setTrace(ForwardTrace trace) {
        this.trace = trace;
        if (trace != null) {
            updatePath();
            startAnimation();
        } else {
            pathNode = null;
            stopAnimation();
        }
        repaint();
    }

    /** 仅供离屏截图 / 自测：直接选中某个神经元。group < 0 表示取消选择。 */
    void selectForDemo(int denseLayer, int index) {
        this.selGroup = denseLayer;
        this.selIndex = index;
        repaint();
    }

    /** 仅供离屏截图 / 自测：选中该层激活值最大的神经元。 */
    void selectTopNeuron(int denseLayer) {
        if (trace == null || network == null) {
            return;
        }
        if (denseLayer < 0 || denseLayer >= network.denseLayerCount()) {
            return;
        }
        double[] acts = trace.activations(denseLayer);
        int best = 0;
        for (int i = 1; i < acts.length; i++) {
            if (acts[i] > acts[best]) {
                best = i;
            }
        }
        selectForDemo(denseLayer, best);
    }

    @Override
    public void removeNotify() {
        stopAnimation();
        super.removeNotify();
    }

    // ==================================================================
    // 边采样
    // ==================================================================

    private void sampleEdges() {
        edges.clear();
        if (network == null) {
            return;
        }
        int[] sizes = network.sizes();
        int dense = network.denseLayerCount();
        int[] budget;
        if (dense <= 1) {
            budget = new int[]{EDGE_BUDGET};
        } else if (dense == 2) {
            budget = new int[]{320, 180};
        } else {
            // 越多层，越靠后的层越稀疏，突出「最强连接」
            budget = new int[dense];
            int per = Math.max(60, EDGE_BUDGET / dense);
            Arrays.fill(budget, per);
        }
        for (int layer = 0; layer < dense; layer++) {
            double[][] weights = network.layerWeights(layer);
            int in = sizes[layer];
            int out = sizes[layer + 1];
            int total = in * out;
            double[] abs = new double[total];
            Integer[] order = new Integer[total];
            for (int o = 0; o < out; o++) {
                for (int i = 0; i < in; i++) {
                    int k = o * in + i;
                    abs[k] = Math.abs(weights[o][i]);
                    order[k] = k;
                }
            }
            int take = Math.min(budget[layer], total);
            Arrays.sort(order, Comparator.comparingDouble((Integer k) -> -abs[k]));
            double maxAbs = abs[order[0]];
            for (int n = 0; n < take; n++) {
                int k = order[n];
                int o = k / in;
                int i = k % in;
                edges.add(new Edge(layer, i, o, weights[o][i], maxAbs));
            }
        }
    }

    private void buildGroups() {
        hiddenGroups.clear();
        if (network == null) {
            return;
        }
        int dense = network.denseLayerCount();
        int[] sizes = network.sizes();
        for (int l = 0; l < dense - 1; l++) {
            int size = sizes[l + 1];
            int cols;
            if (size <= 64) {
                cols = Math.min(8, size);
            } else {
                cols = 16;
            }
            int rows = (size + cols - 1) / cols;
            hiddenGroups.add(new Group(l, size, cols, rows));
        }
        for (int i = 0; i < 10 && i < outPos.length; i++) {
            if (outPos[i] == null) {
                outPos[i] = new Point2D.Float();
            }
        }
    }

    // ==================================================================
    // 布局
    // ==================================================================

    private void ensureLayout() {
        int w = getWidth();
        int h = getHeight();
        if (layoutValid && w == layoutW && h == layoutH) {
            return;
        }
        layoutW = w;
        layoutH = h;
        layoutValid = true;
        if (network == null || w <= 0 || h <= 0) {
            return;
        }
        int top = 46;
        int bottom = Math.max(top + 40, h - 46);
        int avail = bottom - top;
        int cy = top + avail / 2;
        int dense = network.denseLayerCount();
        int hiddenCount = Math.max(1, dense - 1);

        // 输入缩略图
        int side = (int) Theme.clamp(Math.min(w * 0.19, avail * 0.42), 54, 108);
        float inCx = Math.max(12, w * 0.045f) + side / 2f;
        inputRect.setRect(Math.round(inCx - side / 2f), cy - side / 2, side, side);

        // 输出列位置
        outCx = Math.round(w * 0.86f);
        outSpacing = (float) Theme.clamp(avail * 0.088, 17, 31);
        outCy = cy;

        // 隐藏层列位置
        float xStart = inputRect.x + inputRect.width + w * 0.025f;
        float xEnd = outCx - w * 0.035f;
        float gap = (xEnd - xStart) / (hiddenCount + 1);
        float spacing = (float) Math.min(Theme.clamp(avail * 0.036, 7, 17), gap / 8.3f);
        spacing = Math.max(5.5f, spacing);
        for (int i = 0; i < hiddenGroups.size(); i++) {
            Group grp = hiddenGroups.get(i);
            grp.cx = xStart + gap * (i + 1);
            grp.cy = cy;
            grp.spacing = spacing;
            grp.place();
        }

        // 输出节点
        for (int i = 0; i < 10; i++) {
            outPos[i].setLocation(outCx, outCy + (i - 4.5f) * outSpacing);
        }
    }

    // ==================================================================
    // 绘制
    // ==================================================================

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.quality(g);
        int w = getWidth();
        int h = getHeight();
        if (w < 40 || h < 40) {
            g2.dispose();
            return;
        }
        ensureLayout();
        if (network == null) {
            paintEmpty(g2, w, h);
            g2.dispose();
            return;
        }
        double progress = animating
                ? Theme.clamp((System.nanoTime() - animStart) / (double) ANIM_NANOS, 0, 1)
                : 1.0;

        paintGuides(g2, w, h);
        paintEdges(g2, progress);
        paintInput(g2, progress);
        for (Group grp : hiddenGroups) {
            paintHiddenGroup(g2, grp, progress);
        }
        paintOutput(g2, progress);
        paintLayerLabels(g2, w);
        paintPulses(g2, progress);
        paintFooter(g2, w, h);
        if (selGroup >= 0) {
            paintInspector(g2, w, h);
        }
        g2.dispose();
    }

    private void paintGuides(Graphics2D g2, int w, int h) {
        int top = 40;
        int bottom = Math.max(top + 30, h - 42);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{3f, 6f}, 0f));
        g2.setColor(Theme.alpha(Theme.BORDER, 70));
        if (!hiddenGroups.isEmpty()) {
            for (Group grp : hiddenGroups) {
                int x = Math.round(grp.cx);
                g2.drawLine(x, top, x, bottom);
            }
        }
        g2.drawLine(Math.round(outCx), top, Math.round(outCx), bottom);
        // 输入 → 第一层之间的流动感引导线
        int inRight = inputRect.x + inputRect.width;
        g2.drawLine(inRight + 8, top, inRight + 8, bottom);
    }

    private void paintEdges(Graphics2D g2, double progress) {
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingDouble(e -> e.w / Math.max(1e-9, e.maxAbs)));
        for (Edge e : sorted) {
            Point2D.Float src = edgeSource(e);
            Point2D.Float dst = edgeTarget(e);
            if (src == null || dst == null) {
                continue;
            }
            double t = Theme.clamp(Math.abs(e.w) / Math.max(1e-9, e.maxAbs), 0, 1);
            boolean onPath = isOnPath(e);
            Color color = e.w >= 0 ? Theme.ACCENT : Theme.VIOLET;
            int alpha = (int) (10 + 45 * t);
            float width = (float) (0.4 + 1.15 * t);
            if (trace == null) {
                // 没有输入时只展示结构，降低存在感
                alpha = (int) (alpha * 0.42);
                width *= 0.8f;
            }
            if (onPath) {
                alpha = animating ? 130 : 80;
                width += 0.5f;
            }
            g2.setColor(Theme.alpha(color, alpha));
            g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curve(src, dst));
        }
    }

    /** 三次贝塞尔连接线，横向控制点让连线呈优雅弧线。 */
    private Shape curve(Point2D.Float src, Point2D.Float dst) {
        float dx = dst.x - src.x;
        return new CubicCurve2D.Float(src.x, src.y,
                src.x + dx * 0.45f, src.y,
                src.x + dx * 0.55f, dst.y,
                dst.x, dst.y);
    }

    private Point2D.Float pointOnCurve(Point2D.Float src, Point2D.Float dst, float t) {
        float dx = dst.x - src.x;
        float x0 = src.x;
        float x1 = src.x + dx * 0.45f;
        float x2 = src.x + dx * 0.55f;
        float x3 = dst.x;
        float y0 = src.y;
        float y1 = src.y;
        float y2 = dst.y;
        float y3 = dst.y;
        float u = 1 - t;
        float x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
        float y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3;
        return new Point2D.Float(x, y);
    }

    private Point2D.Float edgeSource(Edge e) {
        if (e.layer == 0) {
            int row = e.from / 28;
            int col = e.from % 28;
            return new Point2D.Float(
                    inputRect.x + inputRect.width + 1 + col * 0.42f,
                    inputRect.y + (row + 0.5f) / 28f * inputRect.height);
        }
        Group src = hiddenGroups.get(e.layer - 1);
        return src.pos[e.from];
    }

    private Point2D.Float edgeTarget(Edge e) {
        if (e.layer == network.denseLayerCount() - 1) {
            return outPos[e.to];
        }
        return hiddenGroups.get(e.layer).pos[e.to];
    }

    private void paintInput(Graphics2D g2, double progress) {
        int x = inputRect.x;
        int y = inputRect.y;
        int s = inputRect.width;
        int arc = 10;
        g2.setColor(Theme.CARD_INNER);
        g2.fillRoundRect(x, y, s, s, arc, arc);
        BufferedImage img = null;
        if (trace != null) {
            try {
                img = Preprocessor.toImage(trace.input());
            } catch (RuntimeException ignored) {
                img = null;
            }
        }
        Shape oldClip = g2.getClip();
        g2.clip(Theme.roundClip(x + 1, y + 1, s - 2, s - 2, arc - 2));
        if (img != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(img, x + 1, y + 1, s - 2, s - 2, null);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        } else {
            // 等待输入：简洁的虚线占位，不画 784 个点
            g2.setColor(Theme.alpha(Theme.BORDER, 170));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{4f, 5f}, 0f));
            g2.drawRoundRect(x + 9, y + 9, s - 19, s - 19, 8, 8);
            g2.setFont(Theme.F_TINY);
            g2.setColor(Theme.TEXT_FAINT);
            String hint = "等待输入";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, x + (s - fm.stringWidth(hint)) / 2, y + s / 2 + 4);
        }
        g2.setClip(oldClip);
        g2.setColor(Theme.alpha(Theme.ACCENT, trace != null ? 120 : 60));
        g2.drawRoundRect(x, y, s - 1, s - 1, arc, arc);
    }

    private void paintHiddenGroup(Graphics2D g2, Group grp, double progress) {
        double[] acts = trace != null ? trace.activations(grp.denseLayer) : null;
        double maxAct = 0;
        if (acts != null) {
            for (double a : acts) {
                maxAct = Math.max(maxAct, a);
            }
        }
        double revealAll = animating ? smoothstep(0.02 + 0.25 * grp.denseLayer,
                0.30 + 0.25 * grp.denseLayer, progress) : 1.0;

        for (int i = 0; i < grp.size; i++) {
            Point2D.Float p = grp.pos[i];
            if (p == null) {
                continue;
            }
            double a = acts == null ? 0 : acts[i];
            double norm = maxAct <= 1e-9 ? 0 : a / maxAct;
            // 节点错峰点亮，形成「逐层传播」的观感
            double stagger = Theme.clamp((revealAll - (i % 8) * 0.012) / 0.92, 0, 1);
            norm *= stagger;

            float radius = (float) (2.0 + 2.0 * norm);
            Color base = new Color(0x2A323C);
            Color color = norm > 0.02 ? Theme.mix(base, Theme.ACCENT, Math.pow(norm, 0.7)) : base;
            g2.setColor(color);
            g2.fill(circle(p, radius));

            if (norm > 0.72) {
                g2.setColor(Theme.alpha(Theme.ACCENT, 55));
                g2.fill(circle(p, radius + 3));
            }
            if (isSelected(grp.denseLayer, i)) {
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(circle(p, radius + 4));
            } else if (isHovered(grp.denseLayer, i)) {
                g2.setColor(Theme.alpha(Theme.ACCENT, 160));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(circle(p, radius + 3.5f));
            }
        }
    }

    private void paintOutput(Graphics2D g2, double progress) {
        double[] probs = trace != null ? trace.activations(trace.layerCount() - 1) : null;
        double reveal = animating ? smoothstep(0.52, 0.84, progress) : 1.0;
        int predicted = trace != null ? trace.predicted() : -1;
        for (int i = 0; i < 10; i++) {
            Point2D.Float p = outPos[i];
            double prob = probs == null ? 0 : probs[i];
            double norm = prob;
            float radius = (float) (4.2 + 3.6 * Math.pow(Theme.clamp(norm, 0, 1), 0.7));
            if (norm < 0.001) {
                radius = 3.6f;
            }
            boolean isPred = i == predicted;
            Color color = probs == null ? new Color(0x2A323C)
                    : Theme.mix(new Color(0x2A323C), Theme.ACCENT, Math.pow(norm, 0.55) * reveal);
            if (isPred && probs != null) {
                color = Theme.ACCENT;
            }
            g2.setColor(color);
            g2.fill(circle(p, isPred ? radius + 1 : radius));
            if (isPred && probs != null) {
                g2.setColor(Theme.alpha(Theme.ACCENT, 45));
                g2.fill(circle(p, radius + 7));
                g2.setColor(Theme.alpha(Theme.ACCENT, 130));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(circle(p, radius + 3.5f));
            }
            if (isSelected(network.denseLayerCount() - 1, i)) {
                g2.setColor(Theme.VIOLET);
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(circle(p, radius + 5));
            } else if (isHovered(network.denseLayerCount() - 1, i)) {
                g2.setColor(Theme.alpha(Theme.TEXT, 170));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(circle(p, radius + 4));
            }

            // 数字与概率
            boolean highlight = isPred || isSelected(network.denseLayerCount() - 1, i);
            g2.setFont(highlight ? Theme.mono(Font.BOLD, 12.5f) : Theme.mono(Font.PLAIN, 12f));
            FontMetrics fm = g2.getFontMetrics();
            int lx = (int) (p.x + radius + 10);
            int ly = (int) (p.y + fm.getAscent() / 2 - 1);
            g2.setColor(highlight ? Theme.ACCENT : Theme.TEXT_DIM);
            g2.drawString(Integer.toString(i), lx, ly);
            String pct = probs == null ? "—"
                    : prob >= 0.995 ? "100%" : Theme.pctProb(prob);
            g2.setFont(Theme.mono(Font.PLAIN, 10.5f));
            FontMetrics pfm = g2.getFontMetrics();
            g2.setColor(highlight ? Theme.alpha(Theme.ACCENT, 210) : Theme.TEXT_FAINT);
            g2.drawString(pct, lx + 14, (int) (p.y + pfm.getAscent() / 2 - 1));
        }
    }

    private void paintLayerLabels(Graphics2D g2, int w) {
        int y1 = 15;
        int y2 = 29;
        // 输入
        int inCx = inputRect.x + inputRect.width / 2;
        g2.setFont(Theme.ui(Font.BOLD, 10.5f));
        FontMetrics bfm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_DIM);
        g2.drawString("输入 784", inCx - bfm.stringWidth("输入 784") / 2, y1);
        g2.setFont(Theme.F_TINY);
        FontMetrics sfm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString("28×28 灰度", inCx - sfm.stringWidth("28×28 灰度") / 2, y2);

        // 隐藏层
        for (Group grp : hiddenGroups) {
            String line1 = w < 520 ? grp.size + " · ReLU" : "隐藏层 " + grp.size;
            String line2 = w < 520 ? "" : "ReLU · 点阵";
            paintColumnLabel(g2, grp.cx, line1, line2, Theme.VIOLET);
        }
        // 输出
        String out1 = "输出 10";
        String out2 = "Softmax";
        paintColumnLabel(g2, outCx, out1, out2, Theme.ACCENT);
    }

    private void paintColumnLabel(Graphics2D g2, float cx, String line1, String line2, Color accent) {
        g2.setFont(Theme.ui(Font.BOLD, 10.5f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(accent);
        g2.drawString(line1, (int) (cx - fm.stringWidth(line1) / 2f), 15);
        if (line2 != null && !line2.isEmpty()) {
            g2.setFont(Theme.F_TINY);
            FontMetrics sfm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString(line2, (int) (cx - sfm.stringWidth(line2) / 2f), 29);
        }
    }

    private void paintPulses(Graphics2D g2, double progress) {
        if (trace == null || !animating) {
            return;
        }
        int dense = network.denseLayerCount();
        for (int layer = 0; layer < dense; layer++) {
            double start = 0.03 + 0.26 * layer;
            double phase = (progress - start) / 0.26;
            if (phase < 0 || phase > 1) {
                continue;
            }
            int target = pathNode[layer];
            for (Edge e : edges) {
                if (e.layer != layer) {
                    continue;
                }
                if (e.to != target) {
                    continue;
                }
                boolean sourceOk = layer == 0 || pathNode[layer - 1] == e.from;
                if (!sourceOk) {
                    continue;
                }
                Point2D.Float src = edgeSource(e);
                Point2D.Float dst = edgeTarget(e);
                if (src == null || dst == null) {
                    continue;
                }
                for (int trail = 0; trail < 3; trail++) {
                    float t = (float) Theme.clamp(phase - trail * 0.06, 0, 1);
                    if (t <= 0) {
                        continue;
                    }
                    Point2D.Float pt = pointOnCurve(src, dst, t);
                    float radius = trail == 0 ? 3.4f : 2.6f - trail * 0.6f;
                    int alpha = trail == 0 ? 235 : trail == 1 ? 120 : 60;
                    g2.setColor(Theme.alpha(Theme.ACCENT, alpha));
                    g2.fill(circle(pt, radius));
                    if (trail == 0) {
                        g2.setColor(Theme.alpha(Theme.ACCENT, 45));
                        g2.fill(circle(pt, radius + 4));
                    }
                }
            }
        }
    }

    private void paintFooter(Graphics2D g2, int w, int h) {
        g2.setFont(Theme.F_TINY);
        FontMetrics fm = g2.getFontMetrics();
        String line1 = "线宽 = |权重|　亮度 = 激活值";
        String line2 = "点击神经元查看权重热图 · 点击空白取消";
        if (w >= 520) {
            int total = fm.stringWidth(line1) + 18 + fm.stringWidth(line2);
            int x = Math.max(10, (w - total) / 2);
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString(line1, x, h - 22);
            g2.drawString(line2, x + fm.stringWidth(line1) + 18, h - 22);
        } else {
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString("点击神经元查看权重热图", Math.max(8, (w - fm.stringWidth("点击神经元查看权重热图")) / 2), h - 24);
            g2.drawString("线宽 = |权重| · 亮度 = 激活值",
                    Math.max(8, (w - fm.stringWidth("线宽 = |权重| · 亮度 = 激活值")) / 2), h - 10);
        }
    }

    // ==================================================================
    // 权重热图浮层
    // ==================================================================

    private void paintInspector(Graphics2D g2, int w, int h) {
        boolean isOutput = selGroup == network.denseLayerCount() - 1;
        double[][] weights = network.layerWeights(selGroup);
        if (selIndex >= weights.length) {
            return;
        }
        double[] row = weights[selIndex];
        int prev = network.sizes()[selGroup];
        int cols;
        int rows;
        if (prev == 784) {
            cols = 28;
            rows = 28;
        } else if (prev <= 64) {
            cols = 8;
            rows = (prev + 7) / 8;
        } else {
            cols = 16;
            rows = (prev + 15) / 16;
        }
        double maxAbs = 1e-9;
        for (double v : row) {
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }

        int iw = (int) Theme.clamp(w * 0.42, 176, 224);
        int heat = iw - 30;
        int cell = Math.max(4, heat / cols);
        int gridW = cols * cell;
        int gridH = rows * cell;
        int ih = 34 + gridH + 72;
        int ix = w - iw - 8;
        int iy = 32;
        inspectorRect.setBounds(ix, iy, iw, ih);

        // 浮层背景
        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 16));
            g2.fillRoundRect(ix - i, iy - i + 3, iw + i * 2, ih + i * 2, 14 + i, 14 + i);
        }
        g2.setColor(new Color(27, 34, 44, 248));
        g2.fillRoundRect(ix, iy, iw, ih, 13, 13);
        g2.setColor(Theme.alpha(Theme.ACCENT, 110));
        g2.drawRoundRect(ix, iy, iw - 1, ih - 1, 13, 13);

        // 标题
        g2.setFont(Theme.ui(Font.BOLD, 11.5f));
        g2.setColor(Theme.TEXT);
        String title = isOutput
                ? "输出神经元 " + selIndex
                : "隐藏层 " + (selGroup + 1) + " · #" + selIndex;
        g2.drawString(title, ix + 14, iy + 22);

        // 关闭按钮
        closeRect.setBounds(ix + iw - 28, iy + 8, 20, 20);
        g2.setColor(closeHover ? Theme.DANGER : Theme.TEXT_FAINT);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = closeRect.x + 10;
        int cy = closeRect.y + 10;
        g2.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
        g2.drawLine(cx + 4, cy - 4, cx - 4, cy + 4);

        // 热图
        int hx = ix + 15;
        int hy = iy + 32;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= row.length) {
                    continue;
                }
                g2.setColor(Theme.weightColor(row[idx], maxAbs));
                g2.fillRect(hx + c * cell, hy + r * cell, cell, cell);
            }
        }
        g2.setColor(Theme.BORDER);
        g2.drawRect(hx, hy, gridW, gridH);

        // 图例渐变
        int ly = hy + gridH + 12;
        for (int i = 0; i < gridW; i++) {
            double v = (i / (double) Math.max(1, gridW - 1)) * 2 - 1;
            g2.setColor(Theme.weightColor(v * maxAbs, maxAbs));
            g2.drawLine(hx + i, ly, hx + i, ly + 5);
        }
        g2.setFont(Theme.F_TINY);
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString("−", hx, ly + 16);
        g2.drawString("+", hx + gridW - 5, ly + 16);

        // 激活值与偏置
        double activation = Double.NaN;
        if (trace != null) {
            double[] acts = trace.activations(selGroup);
            if (selIndex < acts.length) {
                activation = acts[selIndex];
            }
        }
        double bias = network.layerBiases(selGroup)[selIndex];
        g2.setFont(Theme.ui(Font.PLAIN, 11f));
        int ty = ly + 34;
        g2.setColor(Theme.ACCENT);
        g2.drawString("激活 " + (Double.isNaN(activation) ? "—"
                : String.format("%.3f", activation)), hx, ty);
        g2.setColor(Theme.TEXT_DIM);
        g2.drawString("偏置 " + String.format("%+.3f", bias), hx + gridW / 2, ty);
        g2.setFont(Theme.F_TINY);
        g2.setColor(Theme.TEXT_FAINT);
        String note = prev == 784 ? "28×28 输入感受野"
                : isOutput ? "上一层权重摘要" : "上一层连接权重摘要";
        g2.drawString(note, hx, ty + 17);
    }

    // ==================================================================
    // 空状态
    // ==================================================================

    private void paintEmpty(Graphics2D g2, int w, int h) {
        int pad = 18;
        g2.setColor(Theme.CARD_INNER);
        g2.fillRoundRect(pad, pad, w - pad * 2, h - pad * 2, 12, 12);
        g2.setColor(Theme.alpha(Theme.BORDER, 160));
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{4f, 6f}, 0f));
        g2.drawRoundRect(pad, pad, w - pad * 2 - 1, h - pad * 2 - 1, 12, 12);

        int cx = w / 2;
        int cy = h / 2;
        // 三列示意点
        for (int col = 0; col < 3; col++) {
            int bx = cx - 80 + col * 80;
            int count = col == 1 ? 6 : 3;
            for (int i = 0; i < count; i++) {
                g2.setColor(Theme.alpha(col == 2 ? Theme.ACCENT : Theme.TEXT_FAINT, 70));
                g2.fillOval(bx - 3, cy - 40 + i * 16, 6, 6);
            }
        }
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(Theme.alpha(Theme.TEXT_FAINT, 50));
        g2.drawLine(cx - 80, cy - 16, cx - 3, cy - 32);
        g2.drawLine(cx - 80, cy - 16, cx - 3, cy - 16);
        g2.drawLine(cx - 80, cy - 16, cx - 3, cy);
        g2.drawLine(cx + 3, cy - 24, cx + 77, cy - 16);
        g2.drawLine(cx + 3, cy - 8, cx + 77, cy - 16);

        g2.setFont(Theme.ui(Font.BOLD, 14f));
        String title = "尚未加载模型";
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_DIM);
        g2.drawString(title, cx - fm.stringWidth(title) / 2, cy + 92);
        g2.setFont(Theme.F_SMALL);
        String sub = "训练页完成训练后，这里会展示每一层神经元与权重连接";
        FontMetrics sfm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString(sub, cx - sfm.stringWidth(sub) / 2, cy + 114);
    }

    // ==================================================================
    // 交互
    // ==================================================================

    private void updateHover(int x, int y) {
        int prevGroup = hoverGroup;
        int prevIndex = hoverIndex;
        boolean prevClose = closeHover;
        hoverGroup = -1;
        hoverIndex = -1;
        closeHover = false;

        if (selGroup >= 0) {
            if (closeRect.contains(x, y)) {
                closeHover = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (prevClose != closeHover || prevGroup != -1) {
                    repaint();
                }
                return;
            }
            if (inspectorRect.contains(x, y)) {
                setCursor(Cursor.getDefaultCursor());
                if (prevGroup != -1 || prevClose) {
                    repaint();
                }
                return;
            }
        }
        int[] hit = nodeAt(x, y);
        if (hit != null) {
            hoverGroup = hit[0];
            hoverIndex = hit[1];
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
        if (prevGroup != hoverGroup || prevIndex != hoverIndex || prevClose != closeHover) {
            repaint();
        }
    }

    private void handleClick(int x, int y) {
        if (network == null) {
            return;
        }
        if (selGroup >= 0 && closeRect.contains(x, y)) {
            clearSelection();
            return;
        }
        if (selGroup >= 0 && inspectorRect.contains(x, y)) {
            return;
        }
        int[] hit = nodeAt(x, y);
        if (hit != null) {
            selGroup = hit[0];
            selIndex = hit[1];
            repaint();
        } else {
            clearSelection();
        }
    }

    private void clearSelection() {
        selGroup = -1;
        selIndex = -1;
        closeHover = false;
        repaint();
    }

    /**
     * 命中测试。
     *
     * @return {denseLayer, nodeIndex}；未命中返回 null
     */
    private int[] nodeAt(int x, int y) {
        if (network == null) {
            return null;
        }
        ensureLayout();
        for (Group grp : hiddenGroups) {
            for (int i = 0; i < grp.size; i++) {
                Point2D.Float p = grp.pos[i];
                if (p != null && p.distance(x, y) <= 8) {
                    return new int[]{grp.denseLayer, i};
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            Point2D.Float p = outPos[i];
            if (p.distance(x, y) <= 9) {
                return new int[]{network.denseLayerCount() - 1, i};
            }
        }
        return null;
    }

    private boolean isHovered(int denseLayer, int index) {
        return hoverGroup == denseLayer && hoverIndex == index;
    }

    private boolean isSelected(int denseLayer, int index) {
        return selGroup == denseLayer && selIndex == index;
    }

    // ==================================================================
    // 动画
    // ==================================================================

    private void startAnimation() {
        stopAnimation();
        animStart = System.nanoTime();
        animating = true;
        animTimer = new Timer(30, e -> {
            if (!animating) {
                return;
            }
            double p = (System.nanoTime() - animStart) / (double) ANIM_NANOS;
            if (p >= 1) {
                animating = false;
                animTimer.stop();
            }
            repaint();
        });
        animTimer.setCoalesce(true);
        animTimer.start();
    }

    private void stopAnimation() {
        animating = false;
        if (animTimer != null) {
            animTimer.stop();
            animTimer = null;
        }
    }

    private void updatePath() {
        int dense = network == null ? 0 : network.denseLayerCount();
        if (dense == 0) {
            pathNode = null;
            return;
        }
        pathNode = new int[dense];
        for (int l = 0; l < dense - 1; l++) {
            double[] acts = trace.activations(l);
            int best = 0;
            for (int i = 1; i < acts.length; i++) {
                if (acts[i] > acts[best]) {
                    best = i;
                }
            }
            pathNode[l] = best;
        }
        pathNode[dense - 1] = trace.predicted();
    }

    private boolean isOnPath(Edge e) {
        if (pathNode == null || e.layer >= pathNode.length) {
            return false;
        }
        if (e.to != pathNode[e.layer]) {
            return false;
        }
        return e.layer == 0 || e.from == pathNode[e.layer - 1];
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Theme.clamp((x - edge0) / Math.max(1e-9, edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Shape circle(Point2D.Float p, float r) {
        return new Ellipse2D.Float(p.x - r, p.y - r, r * 2, r * 2);
    }

    // ==================================================================
    // 内部数据结构
    // ==================================================================

    /** 一条采样后的连接线。 */
    private static final class Edge {
        final int layer;
        final int from;
        final int to;
        final double w;
        final double maxAbs;

        Edge(int layer, int from, int to, double w, double maxAbs) {
            this.layer = layer;
            this.from = from;
            this.to = to;
            this.w = w;
            this.maxAbs = Math.max(1e-9, maxAbs);
        }
    }

    /** 一层隐藏神经元的点阵布局。 */
    private static final class Group {
        final int denseLayer;
        final int size;
        final int cols;
        final int rows;
        final Point2D.Float[] pos;
        float cx;
        float cy;
        float spacing = 12;

        Group(int denseLayer, int size, int cols, int rows) {
            this.denseLayer = denseLayer;
            this.size = size;
            this.cols = cols;
            this.rows = rows;
            this.pos = new Point2D.Float[size];
            for (int i = 0; i < size; i++) {
                pos[i] = new Point2D.Float();
            }
        }

        void place() {
            for (int i = 0; i < size; i++) {
                int row = i / cols;
                int col = i % cols;
                int len = Math.min(cols, size - row * cols);
                float offset = (cols - len) * spacing / 2f;
                pos[i].x = cx + (col - (cols - 1) / 2f) * spacing + offset;
                pos[i].y = cy + (row - (rows - 1) / 2f) * spacing;
            }
        }
    }
}
