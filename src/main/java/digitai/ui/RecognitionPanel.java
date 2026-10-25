package digitai.ui;

import digitai.data.Dataset;
import digitai.data.Preprocessor;
import digitai.nn.ForwardTrace;
import digitai.nn.Network;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 识别页：手写画板 + 实时识别结果 + 神经网络可视化，三栏布局。
 *
 * <p>画板变化后 150ms 防抖触发一次前向；空白 / 无模型时展示引导文案。
 * 所有绘制与识别都在 EDT（单次前向为毫秒级），MNIST 数据集懒加载在后台。</p>
 */
public final class RecognitionPanel extends JPanel {

    private static final int RECOGNIZE_DELAY_MS = 150;

    private final AppState state;
    private Runnable onGoTrain = () -> {
    };

    private final DrawingCanvas canvas;
    private final NetworkView networkView;
    private final ResultHero hero;
    private final ProbBars bars;
    private final JLabel footerLabel;
    private final JPanel footerCards = new JPanel(new CardLayout());
    private final JLabel footerLowHint;
    private final JLabel brushValue;
    private final InputPreview preview;
    private final JButton sampleButton;
    private final JButton undoButton;
    private final JButton clearButton;

    private final Timer debounce;
    private final Random random = new Random();

    public RecognitionPanel(AppState state) {
        this.state = state;
        setOpaque(false);
        setBorder(new EmptyBorder(Theme.PAGE_PAD, Theme.PAGE_PAD, Theme.PAGE_PAD, Theme.PAGE_PAD));
        setLayout(new BorderLayout());

        canvas = new DrawingCanvas(c -> onCanvasChanged());
        networkView = new NetworkView();
        hero = new ResultHero();
        bars = new ProbBars();
        preview = new InputPreview();

        debounce = new Timer(RECOGNIZE_DELAY_MS, e -> recognizeNow());
        debounce.setRepeats(false);

        // ---------------- 左栏：画板 + 预处理预览 ----------------
        Widgets.Card canvasCard = new Widgets.Card("手写画板", "鼠标拖动书写 · 松开自动识别");
        JPanel brushRow = new JPanel(new BorderLayout(8, 0));
        brushRow.setOpaque(false);
        brushRow.setBorder(new EmptyBorder(0, 0, 10, 0));
        brushRow.add(Widgets.label("笔刷", Theme.F_SMALL, Theme.TEXT_DIM), BorderLayout.WEST);
        JSlider brush = new JSlider(8, 28, 18);
        brush.setOpaque(false);
        brush.setFocusable(false);
        brush.setUI(new Theme.SliderUI(brush));
        brush.setPreferredSize(new Dimension(90, 28));
        brushValue = Widgets.label("18 px", Theme.F_MONO_SMALL, Theme.ACCENT);
        brushValue.setPreferredSize(new Dimension(42, 28));
        brushValue.setHorizontalAlignment(SwingConstants.RIGHT);
        brush.addChangeListener(e -> {
            canvas.setBrushSize(brush.getValue());
            brushValue.setText(brush.getValue() + " px");
        });
        brushRow.add(brush, BorderLayout.CENTER);
        brushRow.add(brushValue, BorderLayout.EAST);
        canvasCard.body().add(brushRow, BorderLayout.NORTH);
        canvasCard.body().add(new SquareHolder(canvas), BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 3, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(10, 0, 0, 0));
        undoButton = new Widgets.PillButton("撤销", Widgets.ButtonKind.GHOST);
        clearButton = new Widgets.PillButton("清空", Widgets.ButtonKind.GHOST);
        sampleButton = new Widgets.PillButton("随机样本", Widgets.ButtonKind.VIOLET);
        actions.add(undoButton);
        actions.add(clearButton);
        actions.add(sampleButton);
        canvasCard.body().add(actions, BorderLayout.SOUTH);
        undoButton.addActionListener(e -> undoCanvas());
        clearButton.addActionListener(e -> clearCanvas());
        sampleButton.addActionListener(e -> randomSample());

        Widgets.Card previewCard = new Widgets.Card("网络看到的输入", null);
        previewCard.body().add(preview, BorderLayout.WEST);
        JPanel previewText = Widgets.column(3);
        previewText.setBorder(new EmptyBorder(2, 12, 2, 0));
        previewText.add(Widgets.label("28×28 · 784 维", Theme.F_MONO_SMALL, Theme.ACCENT));
        previewText.add(Widgets.vgap(4));
        previewText.add(Widgets.label("去边框 · 等比缩放到 20×20", Theme.F_TINY, Theme.TEXT_DIM));
        previewText.add(Widgets.vgap(2));
        previewText.add(Widgets.label("按重心平移到画布中央", Theme.F_TINY, Theme.TEXT_DIM));
        previewText.add(Widgets.vgap(2));
        previewText.add(Widgets.label("与 MNIST 预处理一致", Theme.F_TINY, Theme.TEXT_FAINT));
        previewCard.body().add(previewText, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout(0, Theme.COL_GAP));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(334, 600));
        left.add(canvasCard, BorderLayout.CENTER);
        left.add(previewCard, BorderLayout.SOUTH);

        // ---------------- 中栏：识别结果 ----------------
        Widgets.Card resultCard = new Widgets.Card("识别结果", "Top-3 与全类别概率");
        JPanel resultBody = new JPanel(new BorderLayout());
        resultBody.setOpaque(false);
        resultBody.add(hero, BorderLayout.NORTH);
        resultBody.add(bars, BorderLayout.CENTER);

        footerLabel = new JLabel("", SwingConstants.CENTER);
        footerLabel.setFont(Theme.F_SMALL);
        footerLabel.setForeground(Theme.TEXT_DIM);
        JPanel footerSimple = new JPanel(new BorderLayout());
        footerSimple.setOpaque(false);
        footerSimple.setBorder(new EmptyBorder(8, 0, 0, 0));
        footerSimple.add(footerLabel, BorderLayout.CENTER);

        footerLowHint = new JLabel("", SwingConstants.CENTER);
        footerLowHint.setFont(Theme.F_SMALL);
        footerLowHint.setForeground(Theme.WARNING);
        footerLowHint.setVerticalAlignment(SwingConstants.CENTER);

        JPanel noModelPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        noModelPanel.setOpaque(false);
        noModelPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        JLabel missing = Widgets.label("尚未加载模型", Theme.F_SMALL, Theme.WARNING);
        Widgets.PillButton goTrain = new Widgets.PillButton("去训练", Widgets.ButtonKind.PRIMARY);
        goTrain.setPreferredSize(new Dimension(84, 30));
        goTrain.addActionListener(e -> onGoTrain.run());
        noModelPanel.add(missing);
        noModelPanel.add(goTrain);

        footerCards.setOpaque(false);
        footerCards.add(footerSimple, "hint");
        footerCards.add(footerLowHint, "low");
        footerCards.add(noModelPanel, "nomodel");
        resultBody.add(footerCards, BorderLayout.SOUTH);
        resultCard.body().add(resultBody, BorderLayout.CENTER);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.setPreferredSize(new Dimension(306, 600));
        middle.add(resultCard, BorderLayout.CENTER);

        // ---------------- 右栏：网络可视化 ----------------
        Widgets.Card netCard = new Widgets.Card("神经网络可视化",
                "输入 784 → 隐藏层 ReLU → 输出 Softmax");
        netCard.body().add(networkView, BorderLayout.CENTER);

        // ---------------- 三栏装配 ----------------
        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 6);
        columns.add(left, c);
        c.insets = new Insets(0, 6, 0, 6);
        columns.add(middle, c);
        c.weightx = 1;
        c.insets = new Insets(0, 6, 0, 0);
        columns.add(netCard, c);
        add(columns, BorderLayout.CENTER);

        // 初始状态
        state.addNetworkListener(this::onNetworkChanged);
        onNetworkChanged();
        updateFooterEmpty();
    }

    // ==================================================================
    // 对外接口
    // ==================================================================

    public void setOnGoTrain(Runnable onGoTrain) {
        this.onGoTrain = onGoTrain == null ? () -> {
        } : onGoTrain;
    }

    /** 供 MainFrame 绑定快捷键。 */
    public void clearCanvas() {
        debounce.stop();
        canvas.clear();
    }

    public void undoCanvas() {
        if (canvas.isSampleMode()) {
            clearCanvas();
            return;
        }
        canvas.undo();
    }

    public void randomSample() {
        if (!state.hasTestData()) {
            footerLabel.setForeground(Theme.TEXT_DIM);
            showFooter("hint", "正在加载 MNIST 测试集…");
        }
        state.withTestData(data -> {
            Dataset d = data;
            int idx = random.nextInt(d.size());
            canvas.loadSample(d.image(idx).clone());
            state.setStatus("已载入随机 MNIST 样本（真值 " + d.label(idx) + "）");
        }, err -> {
            showFooter("hint", "数据集加载失败：" + err);
            footerLabel.setForeground(Theme.DANGER);
        });
    }

    public DrawingCanvas canvas() {
        return canvas;
    }

    public NetworkView networkView() {
        return networkView;
    }

    public ResultHero hero() {
        return hero;
    }

    public ProbBars bars() {
        return bars;
    }

    // ==================================================================
    // 状态流转
    // ==================================================================

    private void onCanvasChanged() {
        if (state.network() == null) {
            updateFooterNoModel();
            return;
        }
        if (canvas.isEmpty()) {
            applyEmpty();
            return;
        }
        // 拖动期间不断重置防抖，停笔 150ms 后才真正识别
        debounce.restart();
    }

    private void recognizeNow() {
        Network net = state.network();
        if (net == null) {
            updateFooterNoModel();
            return;
        }
        double[] vector = canvas.currentVector();
        if (vector == null) {
            applyEmpty();
            return;
        }
        try {
            long t0 = System.nanoTime();
            ForwardTrace trace = net.trace(vector);
            double ms = (System.nanoTime() - t0) / 1_000_000.0;
            hero.setTrace(trace);
            bars.setProbabilities(trace.activations(trace.layerCount() - 1));
            networkView.setTrace(trace);
            preview.setVector(vector);
            state.recordInference(trace.predicted(), ms);
            updateFooterResult(trace, ms);
        } catch (RuntimeException ex) {
            footerLabel.setForeground(Theme.DANGER);
            showFooter("hint", "识别失败：" + ex.getMessage());
        }
    }

    private void applyEmpty() {
        hero.setTrace(null);
        bars.clear();
        networkView.setTrace(null);
        preview.setVector(null);
        if (state.network() != null) {
            updateFooterEmpty();
        } else {
            updateFooterNoModel();
        }
    }

    private void onNetworkChanged() {
        Network net = state.network();
        networkView.setNetwork(net);
        if (net == null) {
            hero.setNoModel(true);
            bars.clear();
            networkView.setTrace(null);
            preview.setVector(null);
            updateFooterNoModel();
            return;
        }
        hero.setNoModel(false);
        if (canvas.isEmpty()) {
            applyEmpty();
        } else {
            debounce.stop();
            recognizeNow();
        }
    }

    // ==================================================================
    // 底部提示
    // ==================================================================

    private void updateFooterEmpty() {
        showFooter("hint", "请在左侧写一个数字 · 停笔后自动识别");
        footerLabel.setForeground(Theme.TEXT_DIM);
    }

    private void updateFooterNoModel() {
        hero.setNoModel(true);
        showFooter("nomodel", "");
    }

    private void updateFooterResult(ForwardTrace trace, double ms) {
        double conf = trace.confidence();
        if (conf < 0.55) {
            showFooter("low", "置信度偏低：试试写得更居中、更大一些");
        } else {
            showFooter("hint", "预处理 + 前向耗时 " + Theme.fmtMillis(ms));
            footerLabel.setForeground(Theme.TEXT_DIM);
        }
    }

    private void showFooter(String card, String hint) {
        CardLayout cl = (CardLayout) footerCards.getLayout();
        cl.show(footerCards, card);
        if (!hint.isEmpty()) {
            footerLabel.setText(hint);
            footerLowHint.setText(hint);
        }
    }

    // ==================================================================
    // 内部组件
    // ==================================================================

    /** 让子组件保持正方形并居中（画板随窗口略缩放）。 */
    private static final class SquareHolder extends JPanel {
        private final JComponent child;

        SquareHolder(JComponent child) {
            this.child = child;
            setOpaque(false);
            setLayout(null);
            add(child);
        }

        @Override
        public void doLayout() {
            int side = Math.min(getWidth(), getHeight());
            side = Math.max(120, side);
            int x = (getWidth() - side) / 2;
            int y = (getHeight() - side) / 2;
            child.setBounds(x, y, side, side);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(320, 320);
        }
    }

    /** 28×28 网络输入的放大预览。 */
    private static final class InputPreview extends JComponent {
        private double[] vector;

        InputPreview() {
            setOpaque(false);
            setPreferredSize(new Dimension(104, 104));
        }

        void setVector(double[] vector) {
            this.vector = vector;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            int side = Math.min(w, h) - 2;
            int x = (w - side) / 2;
            int y = (h - side) / 2;
            int arc = 10;
            g2.setColor(Theme.CARD_INNER);
            g2.fillRoundRect(x, y, side, side, arc, arc);
            if (vector != null) {
                Shape old = g2.getClip();
                g2.clip(new RoundRectangle2D.Float(x + 1, y + 1, side - 2, side - 2, arc, arc));
                BufferedImage img = Preprocessor.toImage(vector);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(img, x + 1, y + 1, side - 2, side - 2, null);
                g2.setClip(old);
                g2.setColor(Theme.alpha(Theme.ACCENT, 140));
            } else {
                g2.setColor(Theme.alpha(Theme.BORDER, 200));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[]{3f, 4f}, 0f));
                g2.setFont(Theme.F_TINY);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.TEXT_FAINT);
                g2.drawString("—", x + side / 2 - fm.stringWidth("—") / 2,
                        y + side / 2 + fm.getAscent() / 2 - 1);
            }
            g2.drawRoundRect(x, y, side - 1, side - 1, arc, arc);
            g2.dispose();
        }
    }
}
