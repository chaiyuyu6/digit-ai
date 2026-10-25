package digitai.ui;

import digitai.data.AppPaths;
import digitai.nn.Network;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * DigitLab 主窗口：品牌栏 + 自定义标签页 + 识别 / 训练 / 教学三个页面 + 状态栏。
 *
 * <p>构造时在后台加载预训练模型（缺失时优雅降级），并预热 MNIST 测试集。
 * 所有 Swing 组件都只在 EDT 创建。</p>
 */
public final class MainFrame extends JFrame {

    public static final String TITLE = "DigitLab · 数字实验室";

    private final AppState state = new AppState();
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pageHost = new JPanel(pageLayout);
    private final List<Widgets.TabButton> tabButtons = new ArrayList<>();
    private final BrandBar brandBar = new BrandBar();
    private final TabBar tabBar = new TabBar();
    private final StatusBar statusBar = new StatusBar();

    private RecognitionPanel recognition;
    private TrainPanel training;
    private TeachingPanel teaching;
    private int currentPage;

    public MainFrame() {
        Theme.install();
        setTitle(TITLE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 740));
        setSize(1360, 860);
        setLocationRelativeTo(null);
        setIconImages(Theme.appIcons());
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        buildPages();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(brandBar, BorderLayout.NORTH);
        header.add(tabBar, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        add(pageHost, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        state.addNetworkListener(this::onNetworkChanged);
        state.addStatusListener(this::refreshStatus);
        installShortcuts();
        refreshStatus();
        loadModelAsync();
        warmUpData();
        showPage(0);
    }

    // ==================================================================
    // 页面装配
    // ==================================================================

    private void buildPages() {
        pageHost.setOpaque(false);
        recognition = new RecognitionPanel(state);
        recognition.setOnGoTrain(() -> showPage(1));
        training = new TrainPanel(state, () -> showPage(0));
        teaching = new TeachingPanel();
        pageHost.add(recognition, "recognize");
        pageHost.add(training, "train");
        pageHost.add(teaching, "teach");
    }

    /** 切换页面（0 识别 / 1 训练 / 2 教学）。 */
    public void showPage(int index) {
        if (index < 0 || index >= 3) {
            return;
        }
        currentPage = index;
        String[] names = {"recognize", "train", "teach"};
        pageLayout.show(pageHost, names[index]);
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).putClientProperty("active", i == index);
            tabButtons.get(i).repaint();
        }
        if (index == 1) {
            training.onShown();
        }
    }

    // ==================================================================
    // 模型加载 / 数据预热
    // ==================================================================

    private void loadModelAsync() {
        Path file = AppPaths.modelFile();
        if (!AppPaths.hasModel()) {
            state.setNetwork(null, AppState.ModelState.MISSING, "未找到模型文件");
            state.setStatus("未找到模型：可到「训练」页训练后保存，或放入 " + file);
            return;
        }
        state.setStatus("正在加载模型…");
        new SwingWorker<Network, Void>() {
            @Override
            protected Network doInBackground() throws Exception {
                return Network.load(file);
            }

            @Override
            protected void done() {
                try {
                    Network network = get();
                    state.setNetwork(network, AppState.ModelState.LOADED,
                            "模型已加载 · " + file.getFileName());
                    state.setStatus("模型已加载，开始书写吧");
                } catch (Exception ex) {
                    String msg = rootMessage(ex);
                    state.setNetwork(null, AppState.ModelState.ERROR, "模型加载失败");
                    state.setStatus("模型加载失败：" + msg);
                    Widgets.error(MainFrame.this, "模型加载失败",
                            "无法读取模型文件：\n" + file + "\n\n" + msg
                                    + "\n\n可以到「训练」页重新训练并保存模型。");
                }
            }
        }.execute();
    }

    private void warmUpData() {
        Timer timer = new Timer(900, e -> {
            if (AppPaths.hasData() && state.network() != null) {
                state.prefetchTestData();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && (t.getMessage() == null || t.getMessage().isBlank())) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    // ==================================================================
    // 状态刷新
    // ==================================================================

    private void onNetworkChanged() {
        refreshStatus();
    }

    private void refreshStatus() {
        brandBar.update(state);
        statusBar.update(state);
    }

    // ==================================================================
    // 快捷键
    // ==================================================================

    private void installShortcuts() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();
        bind(im, am, KeyStroke.getKeyStroke('C'), "clear-canvas",
                () -> recognition.clearCanvas());
        bind(im, am, KeyStroke.getKeyStroke('R'), "random-sample",
                () -> recognition.randomSample());
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
                "undo-stroke", () -> recognition.undoCanvas());
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK),
                "page-1", () -> showPage(0));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK),
                "page-2", () -> showPage(1));
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK),
                "page-3", () -> showPage(2));
    }

    private void bind(InputMap im, ActionMap am, KeyStroke key, String name, Runnable action) {
        im.put(key, name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ==================================================================
    // 供离屏截图工具使用的包内入口
    // ==================================================================

    AppState state() {
        return state;
    }

    RecognitionPanel recognition() {
        return recognition;
    }

    TrainPanel trainingPage() {
        return training;
    }

    TeachingPanel teachingPage() {
        return teaching;
    }

    int currentPage() {
        return currentPage;
    }

    // ==================================================================
    // 品牌栏
    // ==================================================================

    private static final class BrandBar extends JComponent {
        private String modelText = "模型未加载";
        private Color modelColor = Theme.WARNING;
        private String dataText = "MNIST 数据检查中";
        private Color dataColor = Theme.TEXT_FAINT;
        private final Image logo = Theme.appIcon(38);

        BrandBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(1360, 70));
            setMinimumSize(new Dimension(0, 70));
        }

        void update(AppState state) {
            switch (state.modelState()) {
                case LOADED -> {
                    modelText = "模型已加载";
                    modelColor = Theme.SUCCESS;
                }
                case TRAINED -> {
                    modelText = "新模型已启用";
                    modelColor = Theme.SUCCESS;
                }
                case ERROR -> {
                    modelText = "模型加载失败";
                    modelColor = Theme.DANGER;
                }
                default -> {
                    modelText = "模型未加载";
                    modelColor = Theme.WARNING;
                }
            }
            if (AppPaths.hasData()) {
                dataText = "MNIST 60,000 / 10,000";
                dataColor = Theme.SUCCESS;
            } else {
                dataText = "MNIST 数据集缺失";
                dataColor = Theme.DANGER;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(0x0A0E14), 0, h, Theme.BG));
            g2.fillRect(0, 0, w, h);

            // Logo + 标题
            int x = 22;
            int logoY = (h - 38) / 2;
            if (logo != null) {
                g2.drawImage(logo, x, logoY, 38, 38, null);
            }
            int tx = x + 38 + 14;
            int baseline = (h / 2) - 2;
            g2.setFont(Theme.F_BRAND);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString("DigitLab", tx, baseline);
            int w1 = fm.stringWidth("DigitLab");
            g2.setFont(Theme.ui(Font.PLAIN, 16f));
            FontMetrics fm2 = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(" · 数字实验室", tx + w1, baseline);

            g2.setFont(Theme.F_SMALL);
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString("纯 Java 手写数字识别 · 可解释的教学演示",
                    tx + 1, baseline + 20);

            // 右侧状态徽标（从右往左画）
            int right = w - 22;
            int cy = h / 2;
            right = drawPill(g2, right, cy, "v1.0 · JDK 25", Theme.TEXT_FAINT, Theme.TEXT_DIM);
            right = drawPill(g2, right, cy, modelText, modelColor, Theme.TEXT_DIM);
            drawPill(g2, right, cy, dataText, dataColor, Theme.TEXT_DIM);
            g2.dispose();
        }

        /** 画一个胶囊徽标，返回它的左边界（供继续往左排）。 */
        private int drawPill(Graphics2D g2, int right, int cy, String text, Color dot, Color textColor) {
            g2.setFont(Theme.F_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int pw = fm.stringWidth(text) + 30;
            int ph = 24;
            int x = right - pw;
            int y = cy - ph / 2;
            g2.setColor(new Color(0x151B23));
            g2.fillRoundRect(x, y, pw, ph, ph, ph);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(x, y, pw - 1, ph - 1, ph, ph);
            g2.setColor(Theme.alpha(dot, 60));
            g2.fillOval(x + 9, cy - 5, 10, 10);
            g2.setColor(dot);
            g2.fillOval(x + 11, cy - 3, 6, 6);
            g2.setColor(textColor);
            g2.drawString(text, x + 21, cy - fm.getHeight() / 2 + fm.getAscent());
            return x - 8;
        }
    }

    // ==================================================================
    // 自定义标签栏
    // ==================================================================

    private final class TabBar extends JPanel {
        TabBar() {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT, 6, 3));
            setBorder(new EmptyBorder(0, 16, 0, 16));
            setPreferredSize(new Dimension(1360, 52));
            addTab("识别", "实时识别");
            addTab("训练", "训练网络");
            addTab("教学", "原理课堂");
        }

        private void addTab(String caption, String sub) {
            int index = tabButtons.size();
            Widgets.TabButton button = new Widgets.TabButton(caption, sub);
            button.addActionListener(e -> showPage(index));
            tabButtons.add(button);
            add(button);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, Theme.BG, 0, h, new Color(0x0B1017)));
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawLine(0, h - 1, w, h - 1);
            g2.dispose();
        }
    }

    // ==================================================================
    // 底部状态栏
    // ==================================================================

    private static final class StatusBar extends JComponent {
        private String dataText = "MNIST —";
        private Color dataColor = Theme.TEXT_FAINT;
        private String modelText = "模型 —";
        private Color modelColor = Theme.TEXT_FAINT;
        private String paramText = "参数 —";
        private String statusText = "就绪";
        private String inferenceText = "等待输入";

        StatusBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(1360, 30));
            setMinimumSize(new Dimension(0, 30));
        }

        void update(AppState state) {
            dataText = AppPaths.hasData() ? "MNIST 60,000 / 10,000" : "MNIST 数据集缺失";
            dataColor = AppPaths.hasData() ? Theme.SUCCESS : Theme.DANGER;
            switch (state.modelState()) {
                case LOADED -> {
                    modelText = "模型已加载";
                    modelColor = Theme.SUCCESS;
                }
                case TRAINED -> {
                    modelText = "新模型已启用";
                    modelColor = Theme.SUCCESS;
                }
                case ERROR -> {
                    modelText = "模型加载失败";
                    modelColor = Theme.DANGER;
                }
                default -> {
                    modelText = "模型未加载";
                    modelColor = Theme.WARNING;
                }
            }
            long params = state.parameterCount();
            paramText = params > 0 ? "参数 " + Theme.fmtInt(params) : "参数 —";
            statusText = state.status();
            if (state.lastInferenceMillis() >= 0) {
                inferenceText = "最近预测 " + state.lastPredicted() + " · "
                        + Theme.fmtMillis(state.lastInferenceMillis());
            } else {
                inferenceText = "等待输入";
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.BG_DEEP);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawLine(0, 0, w, 0);

            g2.setFont(Theme.F_TINY);
            FontMetrics fm = g2.getFontMetrics();
            int cy = h / 2;
            int ty = cy - fm.getHeight() / 2 + fm.getAscent();
            int x = 18;

            x = drawDot(g2, x, cy, dataColor);
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(dataText, x, ty);
            x += fm.stringWidth(dataText) + 14;

            x = drawSep(g2, x, h);
            x = drawDot(g2, x, cy, modelColor);
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(modelText, x, ty);
            x += fm.stringWidth(modelText) + 14;

            x = drawSep(g2, x, h);
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(paramText, x, ty);
            x += fm.stringWidth(paramText) + 14;

            x = drawSep(g2, x, h);
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString(statusText, x, ty);

            // 右侧推理耗时
            g2.setColor(Theme.TEXT_FAINT);
            int rw = fm.stringWidth(inferenceText);
            g2.drawString(inferenceText, w - 18 - rw, ty);
            g2.setColor(Theme.alpha(Theme.ACCENT, 150));
            g2.fillOval(w - 18 - rw - 12, cy - 2, 4, 4);
            g2.dispose();
        }

        private int drawDot(Graphics2D g2, int x, int cy, Color color) {
            g2.setColor(Theme.alpha(color, 60));
            g2.fillOval(x, cy - 4, 8, 8);
            g2.setColor(color);
            g2.fillOval(x + 2, cy - 2, 4, 4);
            return x + 13;
        }

        private int drawSep(Graphics2D g2, int x, int h) {
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawLine(x + 2, h / 2 - 5, x + 2, h / 2 + 5);
            return x + 13;
        }
    }
}
