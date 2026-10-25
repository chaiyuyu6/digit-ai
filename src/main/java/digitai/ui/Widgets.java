package digitai.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * DigitLab 自绘控件集合。
 *
 * <p>拒绝默认 Swing 灰色外观：卡片、按钮、下拉选择、步进器、滑杆、指标块、进度条、徽标
 * 全部使用 {@link Theme} 中的色板手绘，保证深色主题的一致性。</p>
 */
public final class Widgets {

    private Widgets() {
    }

    // ==================================================================
    // 布局小工具
    // ==================================================================

    public static JPanel column(int gap) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.putClientProperty("gap", gap);
        return p;
    }

    public static JPanel row(int gap) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        return p;
    }

    /** BoxLayout 纵向排列时让组件占满宽度且高度固定为首选高度。 */
    public static <T extends JComponent> T fullWidth(T c) {
        int h = c.getPreferredSize().height;
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    public static <T extends JComponent> T fullWidth(T c, int height) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    public static JComponent vgap(int h) {
        return (JComponent) Box.createVerticalStrut(h);
    }

    public static JComponent hgap(int w) {
        return (JComponent) Box.createHorizontalStrut(w);
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    public static JLabel title(String text) {
        return label(text, Theme.F_H2, Theme.TEXT);
    }

    public static JLabel hint(String text) {
        return label(text, Theme.F_SMALL, Theme.TEXT_DIM);
    }

    /** 标签在左、控件在右的一行（用于超参数表单）。 */
    public static JPanel formRow(String labelText, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel l = label(labelText, Theme.F_BODY, Theme.TEXT_DIM);
        row.add(l, BorderLayout.WEST);
        control.setAlignmentX(Component.RIGHT_ALIGNMENT);
        row.add(control, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(control.getPreferredSize().height, 30)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /** 若干行纵向堆叠，统一间距。 */
    public static JPanel rows(int gap, JComponent... rows) {
        JPanel p = column(gap);
        p.setOpaque(false);
        for (JComponent r : rows) {
            p.add(r);
            p.add(vgap(gap));
        }
        return p;
    }

    /** 1px 分隔线。 */
    public static JComponent divider() {
        return new JComponent() {
            {
                setOpaque(false);
                setPreferredSize(new Dimension(10, 1));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER_SOFT);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
    }

    // ==================================================================
    // 卡片
    // ==================================================================

    /**
     * 深色圆角卡片：可选标题 + 副标题提示，内容区域用 {@link #body()} 填充。
     */
    public static class Card extends JPanel {
        private final JPanel body = new JPanel(new BorderLayout());
        private final Color accent;

        public Card(String titleText, String hintText) {
            this(titleText, hintText, Theme.ACCENT);
        }

        public Card(String titleText, String hintText, Color accent) {
            this.accent = accent;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(14, Theme.CARD_PAD_X, 16, Theme.CARD_PAD_X));
            body.setOpaque(false);
            if (titleText != null) {
                JPanel header = new JPanel(new BorderLayout(9, 0));
                header.setOpaque(false);
                header.add(new AccentBar(accent), BorderLayout.WEST);
                JPanel titles = column(2);
                titles.add(label(titleText, Theme.F_H2, Theme.TEXT));
                if (hintText != null && !hintText.isEmpty()) {
                    titles.add(label(hintText, Theme.F_SMALL, Theme.TEXT_DIM));
                }
                header.add(titles, BorderLayout.CENTER);
                add(header, BorderLayout.NORTH);
                body.setBorder(new EmptyBorder(12, 0, 0, 0));
            }
            add(body, BorderLayout.CENTER);
        }

        public JPanel body() {
            return body;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            Theme.cardShadow(g2, 0, 0, w, h, Theme.RADIUS);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x171E26), 0, h, new Color(0x141A21)));
            g2.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g2.dispose();
        }
    }

    /** 卡片标题左侧的强调竖条。 */
    private static final class AccentBar extends JComponent {
        private final Color color;

        AccentBar(Color color) {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(3, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            g2.setColor(color);
            g2.fillRoundRect(0, 3, 3, Math.max(6, getHeight() - 6), 2, 2);
            g2.dispose();
        }
    }

    // ==================================================================
    // 按钮
    // ==================================================================

    /** 按钮风格。 */
    public enum ButtonKind {
        /** 青色实心主按钮。 */
        PRIMARY,
        /** 深色描边次按钮。 */
        GHOST,
        /** 红色危险操作。 */
        DANGER,
        /** 透明文字按钮。 */
        SUBTLE,
        /** 紫色实心。 */
        VIOLET
    }

    /** 圆角按钮（全部自绘，带 hover / pressed / disabled 状态）。 */
    public static class PillButton extends JButton {
        private final ButtonKind kind;

        public PillButton(String text, ButtonKind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(kind == ButtonKind.PRIMARY ? Theme.F_BOLD : Theme.F_BODY);
            setBorder(new EmptyBorder(7, 13, 7, 13));
            addChangeListener(e -> repaint());
        }

        public ButtonKind kind() {
            return kind;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(d.height, 32);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            boolean en = isEnabled();
            boolean hover = en && getModel().isRollover();
            boolean down = en && getModel().isPressed() && getModel().isArmed();
            int r = 9;
            Color textColor;

            switch (kind) {
                case PRIMARY -> {
                    Color fill = en ? (down ? new Color(0x0891B2) : hover ? new Color(0x67E8F9) : Theme.ACCENT)
                            : new Color(0x232A33);
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, w, h, r, r);
                    if (en) {
                        g2.setColor(Theme.alpha(Color.WHITE, hover ? 90 : 45));
                        g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
                    }
                    textColor = en ? new Color(0x041218) : Theme.TEXT_FAINT;
                }
                case VIOLET -> {
                    Color fill = en ? (down ? new Color(0x8B5CF6) : hover ? new Color(0xC4B5FD) : Theme.VIOLET)
                            : new Color(0x232A33);
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, w, h, r, r);
                    textColor = en ? new Color(0x120B26) : Theme.TEXT_FAINT;
                }
                case DANGER -> {
                    Color base = en ? Theme.DANGER : new Color(0x5B3233);
                    Color fill = Theme.alpha(base, down ? 52 : hover ? 34 : 18);
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, w, h, r, r);
                    g2.setColor(Theme.alpha(base, en ? 150 : 70));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
                    textColor = en ? Theme.DANGER : Theme.TEXT_FAINT;
                }
                case SUBTLE -> {
                    if (hover || down) {
                        g2.setColor(down ? new Color(0x222B36) : new Color(0x1B232D));
                        g2.fillRoundRect(0, 0, w, h, r, r);
                    }
                    textColor = en ? (hover ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_FAINT;
                }
                default -> { // GHOST
                    Color fill = en ? (down ? new Color(0x1A222C) : hover ? new Color(0x222B36) : Theme.CARD_HI)
                            : new Color(0x171D24);
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, w, h, r, r);
                    g2.setColor(en ? (hover ? new Color(0x4A5561) : Theme.BORDER) : new Color(0x252C35));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
                    textColor = en ? (hover ? Theme.TEXT : new Color(0xC9D3DC)) : Theme.TEXT_FAINT;
                }
            }

            Font f = getFont();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(textColor);
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    // ==================================================================
    // 下拉选择（自绘，不用 JComboBox）
    // ==================================================================

    /** 深色下拉选择器。点击展开 JPopupMenu，选中后回调。 */
    public static final class Select<T> extends JComponent {
        private final List<T> items = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private int selectedIndex;
        private Consumer<T> onChange;
        private boolean hover;
        private JPopupMenu openMenu;

        public Select(T[] values, Function<T, String> labeler, T initial) {
            for (T v : values) {
                items.add(v);
                labels.add(labeler.apply(v));
            }
            int idx = items.indexOf(initial);
            selectedIndex = Math.max(0, idx);
            setOpaque(false);
            setFocusable(false);
            setFont(Theme.F_BODY);
            setPreferredSize(new Dimension(150, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) {
                        showPopup();
                    }
                }
            });
        }

        public T value() {
            return items.isEmpty() ? null : items.get(selectedIndex);
        }

        public int selectedIndex() {
            return selectedIndex;
        }

        public void setOnChange(Consumer<T> listener) {
            this.onChange = listener;
        }

        public void setSelectedIndexSilently(int i) {
            if (i >= 0 && i < items.size()) {
                selectedIndex = i;
                repaint();
            }
        }

        private void showPopup() {
            if (openMenu != null && openMenu.isVisible()) {
                openMenu.setVisible(false);
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            menu.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER),
                    new EmptyBorder(4, 4, 4, 4)));
            for (int i = 0; i < labels.size(); i++) {
                final int idx = i;
                JMenuItem mi = new JMenuItem(labels.get(i));
                mi.setFont(Theme.F_BODY);
                mi.setForeground(i == selectedIndex ? Theme.ACCENT : Theme.TEXT);
                mi.setBackground(Theme.CARD_HI);
                mi.setOpaque(true);
                mi.setBorder(new EmptyBorder(7, 10, 7, 10));
                mi.addActionListener(e -> {
                    selectedIndex = idx;
                    repaint();
                    if (onChange != null) {
                        onChange.accept(items.get(idx));
                    }
                });
                menu.add(mi);
            }
            int width = Math.max(getWidth(), 120);
            menu.setPopupSize(new Dimension(width, labels.size() * 33 + 10));
            openMenu = menu;
            menu.show(this, 0, getHeight() + 3);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            boolean en = isEnabled();
            g2.setColor(en ? (hover ? new Color(0x222B36) : Theme.CARD_INNER) : new Color(0x141A21));
            g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(en ? (hover ? new Color(0x4A5561) : Theme.BORDER) : new Color(0x252C35));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            g2.setFont(Theme.F_BODY);
            FontMetrics fm = g2.getFontMetrics();
            String text = labels.isEmpty() ? "" : labels.get(selectedIndex);
            g2.setColor(en ? Theme.TEXT : Theme.TEXT_FAINT);
            g2.drawString(text, 10, (h - fm.getHeight()) / 2 + fm.getAscent());

            // 右侧箭头
            int ax = w - 16;
            int ay = h / 2 - 1;
            g2.setColor(en ? Theme.TEXT_DIM : new Color(0x4A5561));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ax - 4, ay - 2, ax, ay + 2);
            g2.drawLine(ax, ay + 2, ax + 4, ay - 2);
            g2.dispose();
        }
    }

    // ==================================================================
    // 数字步进器
    // ==================================================================

    /** 数字步进器：[−] 值 [+]，数值范围与步长可配置。 */
    public static final class Stepper extends JPanel {
        private final int min;
        private final int max;
        private final int step;
        private final JLabel valueLabel;
        private final PillButton minus;
        private final PillButton plus;
        private int value;
        private Consumer<Integer> onChange;

        public Stepper(int min, int max, int step, int initial) {
            this.min = min;
            this.max = max;
            this.step = step;
            this.value = Theme.clamp(initial, min, max);
            setOpaque(false);
            setLayout(new BorderLayout(6, 0));
            valueLabel = label(String.valueOf(value), Theme.F_MONO_BOLD, Theme.ACCENT);
            valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
            valueLabel.setPreferredSize(new Dimension(38, 30));
            minus = mini("−");
            plus = mini("+");
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            buttons.setOpaque(false);
            buttons.add(minus);
            buttons.add(plus);
            add(valueLabel, BorderLayout.WEST);
            add(buttons, BorderLayout.EAST);
            setPreferredSize(new Dimension(118, 30));
            minus.addActionListener(e -> setValue(value - step));
            plus.addActionListener(e -> setValue(value + step));
        }

        private PillButton mini(String text) {
            PillButton b = new PillButton(text, ButtonKind.GHOST) {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(28, 28);
                }
            };
            b.setFont(Theme.ui(Font.BOLD, 14f));
            b.setBorder(new EmptyBorder(0, 0, 0, 0));
            return b;
        }

        public int value() {
            return value;
        }

        public void setValue(int v) {
            int nv = Theme.clamp(v, min, max);
            if (nv == value) {
                return;
            }
            value = nv;
            valueLabel.setText(String.valueOf(value));
            if (onChange != null) {
                onChange.accept(value);
            }
        }

        public void setOnChange(Consumer<Integer> listener) {
            this.onChange = listener;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            minus.setEnabled(enabled);
            plus.setEnabled(enabled);
            valueLabel.setForeground(enabled ? Theme.ACCENT : Theme.TEXT_FAINT);
        }
    }

    // ==================================================================
    // 对数滑杆（学习率）
    // ==================================================================

    /** 按对数刻度映射 [min,max] 的滑杆，右侧显示当前值。 */
    public static final class LogSlider extends JPanel {
        private final double min;
        private final double max;
        private final JSlider slider;
        private final JLabel readout;
        private Consumer<Double> onChange;
        private double value;

        public LogSlider(double min, double max, double initial) {
            this.min = min;
            this.max = max;
            this.value = initial;
            setOpaque(false);
            setLayout(new BorderLayout(8, 0));
            slider = new JSlider(0, 1000, pos(initial));
            slider.setOpaque(false);
            slider.setFocusable(false);
            slider.setUI(new Theme.SliderUI(slider));
            slider.setPreferredSize(new Dimension(96, 30));
            readout = label(fmt(initial), Theme.F_MONO, Theme.ACCENT);
            readout.setHorizontalAlignment(SwingConstants.RIGHT);
            readout.setPreferredSize(new Dimension(54, 30));
            add(slider, BorderLayout.CENTER);
            add(readout, BorderLayout.EAST);
            setPreferredSize(new Dimension(150, 30));
            slider.addChangeListener(e -> {
                double v = valueAt(slider.getValue());
                if (Math.abs(v - value) < 1e-12) {
                    return;
                }
                value = v;
                readout.setText(fmt(v));
                if (onChange != null) {
                    onChange.accept(v);
                }
            });
        }

        private int pos(double v) {
            double t = Math.log(v / min) / Math.log(max / min);
            return (int) Math.round(Theme.clamp(t, 0, 1) * 1000);
        }

        private double valueAt(int p) {
            double t = p / 1000.0;
            double v = min * Math.pow(max / min, t);
            // 保留 4 位有效数字，显示更干净
            double mag = Math.pow(10, Math.floor(Math.log10(v)));
            return Math.round(v / mag * 100) / 100.0 * mag;
        }

        private String fmt(double v) {
            return String.format("%.4f", v);
        }

        public double value() {
            return value;
        }

        public void setOnChange(Consumer<Double> listener) {
            this.onChange = listener;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            slider.setEnabled(enabled);
            readout.setForeground(enabled ? Theme.ACCENT : Theme.TEXT_FAINT);
        }
    }

    // ==================================================================
    // 指标块 / 进度条 / 徽标
    // ==================================================================

    /** 训练页指标小卡片：上方小标签，下方数值。 */
    public static class MetricTile extends JComponent {
        private final String caption;
        private final Color accent;
        private String value = "—";

        public MetricTile(String caption, Color accent) {
            this.caption = caption;
            this.accent = accent;
            setOpaque(false);
            setPreferredSize(new Dimension(100, 62));
        }

        public void setValue(String v) {
            if (!String.valueOf(v).equals(value)) {
                value = v;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.CARD_INNER);
            g2.fillRoundRect(0, 0, w, h, 9, 9);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 9, 9);
            g2.setFont(Theme.F_SMALL);
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(caption, 11, 20);
            // 值使用界面字体，保证「分 / 秒」等中文正常显示
            g2.setFont(Theme.ui(Font.BOLD, 15.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(accent);
            g2.drawString(value, 11, h - 12);
            // 右上角强调点
            g2.setColor(Theme.alpha(accent, 150));
            g2.fillOval(w - 16, 12, 4, 4);
            g2.dispose();
        }
    }

    /** 细进度条（自绘渐变 + 微光）。 */
    public static class SlimProgress extends JComponent {
        private double fraction;
        private Color accent = Theme.ACCENT;

        public SlimProgress() {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 7));
        }

        public void setFraction(double f) {
            double nf = Theme.clamp(f, 0, 1);
            if (Math.abs(nf - fraction) > 1e-5) {
                fraction = nf;
                repaint();
            }
        }

        public void setAccent(Color c) {
            accent = c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.CARD_INNER);
            g2.fillRoundRect(0, 0, w, h, h, h);
            int fill = (int) Math.round(w * fraction);
            if (fill > 0) {
                GradientPaint gp = new GradientPaint(0, 0, Theme.mix(accent, Theme.CARD_INNER, 0.35),
                        Math.max(fill, 8), 0, accent);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, Math.max(fill, h), h, h, h);
                g2.setColor(Theme.alpha(accent, 70));
                g2.fillOval(Math.max(2, fill - 5), 1, 5, h - 2);
            }
            g2.dispose();
        }
    }

    /** 状态徽标（带彩色圆点的小胶囊），用于品牌栏。 */
    public static class StatusPill extends JComponent {
        private String text = "";
        private Color color = Theme.TEXT_DIM;

        public StatusPill(String text, Color color) {
            this.text = text;
            this.color = color;
            setOpaque(false);
            setFont(Theme.F_SMALL);
            setPreferredSize(new Dimension(120, 24));
        }

        public void set(String text, Color color) {
            this.text = text;
            this.color = color;
            setPreferredSize(new Dimension(
                    getFontMetrics(Theme.F_SMALL).stringWidth(text) + 34, 24));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.CARD_HI);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.setColor(color);
            g2.fillOval(11, h / 2 - 3, 6, 6);
            g2.setColor(Theme.alpha(color, 60));
            g2.fillOval(9, h / 2 - 5, 10, 10);
            g2.setFont(Theme.F_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(text, 23, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** 简单标签页按钮（品牌栏下方）。 */
    public static class TabButton extends JButton {
        private final String caption;
        private final String sub;

        public TabButton(String caption, String sub) {
            this.caption = caption;
            this.sub = sub;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(132, 46));
            addChangeListener(e -> repaint());
        }

        public String caption() {
            return caption;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.quality(g);
            int w = getWidth();
            int h = getHeight();
            boolean active = Boolean.TRUE.equals(getClientProperty("active"));
            boolean hover = getModel().isRollover();
            if (active) {
                g2.setColor(new Color(0x1B232D));
                g2.fillRoundRect(0, 0, w, h - 5, 10, 10);
                g2.setColor(Theme.alpha(Theme.ACCENT, 70));
                g2.drawRoundRect(0, 0, w - 1, h - 6, 10, 10);
            } else if (hover) {
                g2.setColor(new Color(0x161D25));
                g2.fillRoundRect(0, 0, w, h - 5, 10, 10);
            }
            g2.setFont(Theme.F_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(active ? Theme.TEXT : Theme.TEXT_DIM);
            g2.drawString(caption, 16, h / 2 - 3);
            g2.setFont(Theme.F_TINY);
            FontMetrics sfm = g2.getFontMetrics();
            g2.setColor(active ? Theme.alpha(Theme.ACCENT, 220) : Theme.TEXT_FAINT);
            g2.drawString(sub, 16, h / 2 + sfm.getAscent() + 3);
            if (active) {
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(14, h - 4, w - 28, 3, 2, 2);
                g2.setColor(Theme.alpha(Theme.ACCENT, 50));
                g2.fillRoundRect(8, h - 4, w - 16, 3, 2, 2);
            }
            g2.dispose();
        }
    }

    // ==================================================================
    // 提示 / 对话框
    // ==================================================================

    /** 深色提示对话框（JDK 自带 JOptionPane，样式由 Theme 统一）。 */
    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        int r = JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return r == JOptionPane.YES_OPTION;
    }
}
