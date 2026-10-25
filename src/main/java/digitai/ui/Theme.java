package digitai.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * DigitLab 统一视觉主题。
 *
 * <p>深色卡片式设计，所有颜色 / 字体 / 圆角 / 间距集中在这里，避免样式散落。
 * 只使用 JDK 自带字体（优先 Microsoft YaHei UI，回退 SansSerif / Monospaced）。</p>
 */
public final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------------
    // 色板（GitHub Dark 风格，与需求约定一致）
    // ------------------------------------------------------------------
    public static final Color BG = new Color(0x0D1117);          // 窗口背景
    public static final Color BG_DEEP = new Color(0x080B10);     // 状态栏等更深层
    public static final Color CARD = new Color(0x161B22);        // 卡片
    public static final Color CARD_HI = new Color(0x1C232D);     // 卡片高亮 / 输入控件
    public static final Color CARD_INNER = new Color(0x0F141B);  // 卡片内凹区域（图表底、进度槽）
    public static final Color BORDER = new Color(0x30363D);      // 边框
    public static final Color BORDER_SOFT = new Color(0x21262D); // 浅边框 / 网格
    public static final Color TEXT = new Color(0xE6EDF3);        // 主文字
    public static final Color TEXT_DIM = new Color(0x8B949E);    // 次文字
    public static final Color TEXT_FAINT = new Color(0x6E7681);  // 更弱的注释
    public static final Color ACCENT = new Color(0x22D3EE);      // 强调青
    public static final Color ACCENT_DEEP = new Color(0x0E7490); // 青色暗部
    public static final Color VIOLET = new Color(0xA78BFA);      // 强调紫
    public static final Color VIOLET_DEEP = new Color(0x5B21B6);
    public static final Color SUCCESS = new Color(0x3FB950);     // 成功绿
    public static final Color WARNING = new Color(0xD29922);     // 警告黄
    public static final Color DANGER = new Color(0xF85149);      // 错误红
    public static final Color INK = new Color(0x111827);         // 画布墨色
    public static final Color CANVAS_WHITE = new Color(0xFFFFFF);

    // ------------------------------------------------------------------
    // 字体与尺寸
    // ------------------------------------------------------------------
    /** 界面主字体族：优先微软雅黑 UI，保证中文排版。 */
    public static final String FONT_FAMILY;
    /** 等宽字体族：数字 / 代码 / 指标数值。 */
    public static final String MONO_FAMILY;

    public static final Font F_BODY;
    public static final Font F_SMALL;
    public static final Font F_TINY;
    public static final Font F_BOLD;
    public static final Font F_H1;
    public static final Font F_H2;
    public static final Font F_BRAND;
    public static final Font F_BRAND_SUB;
    public static final Font F_HUGE;
    public static final Font F_MONO;
    public static final Font F_MONO_SMALL;
    public static final Font F_MONO_BOLD;

    /** 卡片圆角。 */
    public static final int RADIUS = 12;
    /** 列间距。 */
    public static final int COL_GAP = 14;
    /** 页面外边距。 */
    public static final int PAGE_PAD = 16;
    /** 卡片内边距。 */
    public static final int CARD_PAD_X = 18;
    public static final int CARD_PAD_Y = 16;

    static {
        // 文字抗锯齿（必须在工具包初始化前设置）。Theme 是 UI 的第一入口。
        if (System.getProperty("awt.useSystemAAFontSettings") == null) {
            System.setProperty("awt.useSystemAAFontSettings", "on");
        }
        if (System.getProperty("swing.aatext") == null) {
            System.setProperty("swing.aatext", "true");
        }
        FONT_FAMILY = firstAvailable("Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                "Noto Sans CJK SC", "Source Han Sans SC", "SansSerif");
        MONO_FAMILY = firstAvailable("Cascadia Mono", "Consolas", "JetBrains Mono",
                "DejaVu Sans Mono", "Courier New", "Monospaced");
        F_BODY = ui(Font.PLAIN, 13f);
        F_SMALL = ui(Font.PLAIN, 11.5f);
        F_TINY = ui(Font.PLAIN, 10.5f);
        F_BOLD = ui(Font.BOLD, 13f);
        F_H1 = ui(Font.BOLD, 17f);
        F_H2 = ui(Font.BOLD, 14.5f);
        F_BRAND = ui(Font.BOLD, 21f);
        F_BRAND_SUB = ui(Font.PLAIN, 11.5f);
        F_HUGE = ui(Font.BOLD, 108f);
        F_MONO = mono(Font.PLAIN, 12f);
        F_MONO_SMALL = mono(Font.PLAIN, 11f);
        F_MONO_BOLD = mono(Font.BOLD, 13f);
    }

    /** 按顺序挑第一个系统里存在的字体族。 */
    private static String firstAvailable(String... candidates) {
        String fallback = candidates[candidates.length - 1];
        try {
            Set<String> families = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String c : candidates) {
                if (families.contains(c)) {
                    return c;
                }
            }
        } catch (Throwable ignored) {
            // 极端环境下拿不到字体列表时直接回退
        }
        return fallback;
    }

    public static Font ui(int style, float size) {
        return new Font(FONT_FAMILY, style, 12).deriveFont(style, size);
    }

    public static Font mono(int style, float size) {
        return new Font(MONO_FAMILY, style, 12).deriveFont(style, size);
    }

    // ------------------------------------------------------------------
    // 全局安装：UIManager 深色默认值 + 滚动条尺寸
    // ------------------------------------------------------------------
    private static boolean installed;

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        UIManager.put("Label.font", F_BODY);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Panel.font", F_BODY);
        UIManager.put("Panel.background", BG);
        UIManager.put("Button.font", F_BODY);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("ToolTip.font", F_SMALL);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.background", CARD_HI);
        UIManager.put("ToolTip.border", compound(new Color(0x3A424C)));

        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", F_BODY);
        UIManager.put("OptionPane.buttonFont", F_BODY);
        UIManager.put("OptionPane.foreground", TEXT);

        UIManager.put("PopupMenu.background", CARD_HI);
        UIManager.put("PopupMenu.foreground", TEXT);
        UIManager.put("PopupMenu.border", compound(BORDER));
        UIManager.put("MenuItem.background", CARD_HI);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.font", F_BODY);
        UIManager.put("MenuItem.selectionBackground", new Color(0x263140));
        UIManager.put("MenuItem.selectionForeground", TEXT);

        UIManager.put("ScrollPane.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.background", BG);

        UIManager.put("ProgressBar.background", CARD_INNER);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.font", F_SMALL);
        UIManager.put("ProgressBar.border", BorderFactory.createEmptyBorder());

        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("Separator.background", BORDER);

        UIManager.put("TextField.background", CARD_INNER);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", ACCENT_DEEP);
        UIManager.put("TextField.selectionForeground", Color.WHITE);
        UIManager.put("TextField.font", F_BODY);

        UIManager.put("CheckBox.font", F_BODY);
        UIManager.put("CheckBox.background", CARD);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("RadioButton.font", F_BODY);
        UIManager.put("RadioButton.background", CARD);
        UIManager.put("RadioButton.foreground", TEXT);

        UIManager.put("FileChooser.background", CARD);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("FileChooser.listFont", F_BODY);
    }

    private static Border compound(Color line) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(line),
                new EmptyBorder(5, 8, 5, 8));
    }

    // ------------------------------------------------------------------
    // 绘图工具
    // ------------------------------------------------------------------

    /** 打开高质量抗锯齿，返回同一个 g 方便链式调用。 */
    public static Graphics2D quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    public static Graphics2D quality(Graphics g) {
        return quality((Graphics2D) g.create());
    }

    public static void fillRound(Graphics2D g, int x, int y, int w, int h, int r, Color c) {
        g.setColor(c);
        g.fillRoundRect(x, y, w, h, r, r);
    }

    public static void drawRound(Graphics2D g, int x, int y, int w, int h, int r, Color c) {
        g.setColor(c);
        g.drawRoundRect(x, y, w - 1, h - 1, r, r);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a, 0, 255));
    }

    /** 线性插值两个颜色，t=0 返回 a，t=1 返回 b。 */
    public static Color mix(Color a, Color b, double t) {
        double tt = clamp(t, 0, 1);
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * tt);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * tt);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * tt);
        return new Color(r, g, bl);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * 权重热图颜色映射：正权重 → 青色，负权重 → 紫色，0 → 接近底色。
     * 用幂函数拉开弱权重的区分度，避免大片死黑。
     */
    public static Color weightColor(double value, double maxAbs) {
        double t = maxAbs <= 1e-9 ? 0 : clamp(Math.abs(value) / maxAbs, 0, 1);
        t = Math.pow(t, 0.65);
        if (value >= 0) {
            return mix(new Color(0x10222A), ACCENT, t);
        }
        return mix(new Color(0x1B1730), VIOLET, t);
    }

    /** 激活值 → 亮度色：激活越大越接近青色并发亮。 */
    public static Color activationColor(double activation, double maxActivation) {
        double t = maxActivation <= 1e-9 ? 0 : clamp(activation / maxActivation, 0, 1);
        t = Math.pow(t, 0.75);
        return mix(new Color(0x2B313A), ACCENT, t);
    }

    /** 带千分位的整数。 */
    public static String fmtInt(long n) {
        return String.format("%,d", n);
    }

    /** 百分比，保留 digits 位小数。 */
    public static String pct(double p, int digits) {
        return String.format("%." + digits + "f%%", p * 100.0);
    }

    /** 概率百分比：极小概率显示为 &lt;0.1%，避免一整列 0.0% 的观感。 */
    public static String pctProb(double p) {
        if (p <= 0) {
            return "0.0%";
        }
        if (p < 0.001) {
            return "<0.1%";
        }
        return pct(p, 1);
    }

    /** 毫秒格式化。 */
    public static String fmtMillis(double ms) {
        if (ms < 1) {
            return String.format("%.2f ms", ms);
        }
        if (ms < 100) {
            return String.format("%.1f ms", ms);
        }
        return String.format("%.0f ms", ms);
    }

    /** 时长格式化（训练耗时）。 */
    public static String fmtDuration(long millis) {
        long s = millis / 1000;
        if (s < 60) {
            return s + " s";
        }
        long m = s / 60;
        long rem = s % 60;
        return m + " 分 " + rem + " 秒";
    }

    /** 在 x 处左对齐绘制文本。 */
    public static void text(Graphics2D g, String s, int x, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    /** 在 rightX 处右对齐绘制文本。 */
    public static void textRight(Graphics2D g, String s, int rightX, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, rightX - g.getFontMetrics(f).stringWidth(s), y);
    }

    /** 以 cx 为中心水平居中绘制文本。 */
    public static void textCenter(Graphics2D g, String s, int cx, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics(f).stringWidth(s) / 2, y);
    }

    /** 圆角裁切，用于卡片内图表区域。 */
    public static Shape roundClip(int x, int y, int w, int h, int r) {
        return new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, r, r);
    }

    /** 给卡片画一层极淡的投影，增强层次。 */
    public static void cardShadow(Graphics2D g, int x, int y, int w, int h, int r) {
        for (int i = 3; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 14));
            g.fillRoundRect(x - i, y - i + 2, w + i * 2, h + i * 2, r + i, r + i);
        }
    }

    /** 常用卡片内边距。 */
    public static Border cardPadding() {
        return new EmptyBorder(CARD_PAD_Y, CARD_PAD_X, CARD_PAD_Y, CARD_PAD_X);
    }

    // ------------------------------------------------------------------
    // 自定义滚动条
    // ------------------------------------------------------------------
    /** 细长的深色滚动条。 */
    public static final class ScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0x3A424C);
            trackColor = BG;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        private JButton invisibleButton() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            b.setBorder(BorderFactory.createEmptyBorder());
            return b;
        }

        @Override
        public void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // 轨道留空，让卡片背景透出来
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) {
                return;
            }
            Graphics2D g2 = quality(g);
            g2.setColor(isThumbRollover() ? new Color(0x59636E) : new Color(0x3A424C));
            int inset = 2;
            int arc = 6;
            g2.fillRoundRect(r.x + inset, r.y + 1, Math.max(4, r.width - inset * 2),
                    Math.max(4, r.height - 2), arc, arc);
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(10, 10);
        }
    }

    /** 配置好的深色滚动面板（透明、无边框、窄滚动条）。 */
    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBar().setUI(new ScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ScrollBarUI());
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getHorizontalScrollBar().setUnitIncrement(18);
        return sp;
    }

    // ------------------------------------------------------------------
    // 自定义滑杆（细轨道 + 圆形滑块 + 青色填充）
    // ------------------------------------------------------------------
    /** 深色滑杆 UI。 */
    public static final class SliderUI extends BasicSliderUI {
        public SliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = quality(g);
            Rectangle t = trackRect;
            int h = 5;
            int y = t.y + t.height / 2 - h / 2;
            fillRound(g2, t.x, y, t.width, h, h, CARD_INNER);
            int thumbCenter = thumbRect.x + thumbRect.width / 2;
            int fill = clamp(thumbCenter - t.x, 0, t.width);
            if (fill > 0) {
                GradientPaint gp = new GradientPaint(t.x, y, ACCENT_DEEP, t.x + t.width, y, ACCENT);
                g2.setPaint(gp);
                g2.fillRoundRect(t.x, y, fill, h, h, h);
            }
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = quality(g);
            int cx = thumbRect.x + thumbRect.width / 2;
            int cy = thumbRect.y + thumbRect.height / 2;
            int r = 7;
            g2.setColor(alpha(ACCENT, 45));
            g2.fillOval(cx - r - 3, cy - r - 3, (r + 3) * 2, (r + 3) * 2);
            g2.setColor(CARD_HI);
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(alpha(ACCENT, 210));
            g2.fillOval(cx - 2, cy - 2, 4, 4);
            g2.dispose();
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(16, 16);
        }
    }

    // ------------------------------------------------------------------
    // 窗口图标（纯代码绘制，无外部图片）
    // ------------------------------------------------------------------

    /** 生成窗口图标：深色圆角底 + 青紫渐变神经网络徽标。 */
    public static java.util.List<Image> appIcons() {
        java.util.List<Image> list = new java.util.ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128}) {
            list.add(appIcon(size));
        }
        return list;
    }

    public static Image appIcon(int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(img.createGraphics());
        int r = Math.max(4, size / 5);
        g.setPaint(new GradientPaint(0, 0, new Color(0x0E7490), size, size, new Color(0x5B21B6)));
        g.fillRoundRect(0, 0, size, size, r, r);
        // 神经网络：2 输入 → 2 隐藏 → 1 输出
        int[][] xs = {
                {size * 24 / 100, size * 24 / 100},
                {size * 52 / 100, size * 52 / 100},
                {size * 78 / 100, size * 78 / 100}
        };
        int[] ys = {size * 34 / 100, size * 66 / 100};
        g.setStroke(new BasicStroke(Math.max(1f, size / 24f)));
        g.setColor(new Color(255, 255, 255, 105));
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                g.drawLine(xs[0][i], ys[i], xs[1][j], ys[j]);
                g.drawLine(xs[1][i], ys[i], xs[2][0], size / 2);
            }
        }
        int dot = Math.max(2, size / 9);
        g.setColor(ACCENT);
        for (int i = 0; i < 2; i++) {
            g.fillOval(xs[0][i] - dot / 2, ys[i] - dot / 2, dot, dot);
        }
        g.setColor(new Color(0xB9F6FF));
        for (int i = 0; i < 2; i++) {
            g.fillOval(xs[1][i] - dot / 2, ys[i] - dot / 2, dot, dot);
        }
        g.setColor(Color.WHITE);
        int od = Math.max(3, size / 7);
        g.fillOval(xs[2][0] - od / 2, size / 2 - od / 2, od, od);
        g.dispose();
        return img;
    }
}
