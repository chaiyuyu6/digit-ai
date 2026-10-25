package digitai.ui;

import digitai.nn.ForwardTrace;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 识别结果的「英雄区」：超大王牌数字 + 置信度 + Top-3。
 *
 * <p>自定义绘制以获得 110pt 级别的数字排版；新预测出现时做一次轻微弹出动画，
 * 置信度低时数字转为警告色。</p>
 */
final class ResultHero extends JComponent {

    private ForwardTrace trace;
    private boolean noModel;
    private int lastDigit = -1;
    private double popScale = 1.0;
    private Timer popTimer;

    ResultHero() {
        setOpaque(false);
        setPreferredSize(new Dimension(280, 240));
    }

    void setTrace(ForwardTrace trace) {
        this.trace = trace;
        this.noModel = false;
        if (trace == null) {
            lastDigit = -1;
        } else if (trace.predicted() != lastDigit) {
            lastDigit = trace.predicted();
            playPop();
        }
        repaint();
    }

    void setNoModel(boolean noModel) {
        this.noModel = noModel;
        if (noModel) {
            this.trace = null;
            lastDigit = -1;
        }
        repaint();
    }

    private void playPop() {
        if (popTimer != null) {
            popTimer.stop();
        }
        long start = System.nanoTime();
        popTimer = new Timer(16, e -> {
            double p = (System.nanoTime() - start) / 220_000_000.0;
            if (p >= 1) {
                popScale = 1.0;
                popTimer.stop();
            } else {
                double ease = 1 - Math.pow(1 - p, 3);
                popScale = 1.14 - 0.14 * ease;
            }
            repaint();
        });
        popTimer.setCoalesce(true);
        popScale = 1.14;
        popTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.quality(g);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }
        int cx = w / 2;

        if (trace == null) {
            paintPlaceholder(g2, w, h, cx);
            g2.dispose();
            return;
        }

        int digit = trace.predicted();
        double conf = trace.confidence();
        Color main = conf >= 0.75 ? Theme.TEXT : conf >= 0.45
                ? Theme.mix(Theme.TEXT, Theme.WARNING, 0.45) : Theme.WARNING;

        // 主数字字号：随可用高度收缩，最大值 110pt
        int fontSize = (int) Math.min(110, Math.max(46, (h - 88) * 0.92));
        Font huge = Theme.ui(Font.BOLD, fontSize);
        FontMetrics fm = g2.getFontMetrics(huge);
        int baseline = (int) (h * 0.56);

        // 背景柔光（高置信度时用青色，低置信度跟随数字颜色）
        Color glow = conf >= 0.75 ? Theme.ACCENT : main;
        int radius = (int) Math.min(w * 0.46, h * 0.52);
        if (radius > 4) {
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(cx, baseline - fm.getAscent() * 0.35f), radius,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{Theme.alpha(glow, 36), Theme.alpha(glow, 10),
                            Theme.alpha(glow, 0)}));
            g2.fillRect(0, 0, w, (int) (h * 0.78));
        }

        // 超大王牌数字（弹出缩放）
        Graphics2D digitG = (Graphics2D) g2.create();
        digitG.translate(cx, baseline);
        digitG.scale(popScale, popScale);
        String text = Integer.toString(digit);
        int tw = digitG.getFontMetrics(huge).stringWidth(text);
        digitG.setFont(huge);
        // 细微的字形描边光晕
        digitG.setColor(Theme.alpha(main, 28));
        for (int ox = -2; ox <= 2; ox += 2) {
            for (int oy = -2; oy <= 2; oy += 2) {
                digitG.drawString(text, -tw / 2 + ox, oy);
            }
        }
        digitG.setColor(main);
        digitG.drawString(text, -tw / 2, 0);
        digitG.dispose();

        // 置信度胶囊
        Color pillColor = conf >= 0.75 ? Theme.ACCENT : conf >= 0.45 ? Theme.WARNING : Theme.DANGER;
        String confText = "置信度 " + Theme.pct(conf, 1);
        g2.setFont(Theme.ui(Font.BOLD, 12.5f));
        FontMetrics cfm = g2.getFontMetrics();
        int pw = cfm.stringWidth(confText) + 26;
        int ph = 26;
        int px = cx - pw / 2;
        int py = (int) (h * 0.66);
        g2.setColor(Theme.alpha(pillColor, 30));
        g2.fillRoundRect(px, py, pw, ph, ph, ph);
        g2.setColor(Theme.alpha(pillColor, 110));
        g2.drawRoundRect(px, py, pw - 1, ph - 1, ph, ph);
        g2.setColor(pillColor);
        g2.drawString(confText, px + 13, py + (ph - cfm.getHeight()) / 2 + cfm.getAscent());

        // Top-3（窄栏时自动退化为只显示 Top-1）
        int[] order = topThree(trace);
        double[] probs = trace.activations(trace.layerCount() - 1);
        g2.setFont(Theme.F_SMALL);
        FontMetrics tfm = g2.getFontMetrics();
        String head = "Top-3  ";
        String[] parts = new String[3];
        for (int i = 0; i < 3; i++) {
            int d = order[i];
            parts[i] = d + " " + Theme.pctProb(probs[d]);
        }
        String dot = " · ";
        String[] texts = {head, parts[0], dot, parts[1], dot, parts[2]};
        Color[] colors = {Theme.TEXT_FAINT, Theme.ACCENT, Theme.TEXT_FAINT,
                Theme.TEXT_DIM, Theme.TEXT_FAINT, Theme.TEXT_DIM};
        int total = 0;
        for (String t : texts) {
            total += tfm.stringWidth(t);
        }
        if (total > w - 16) {
            texts = new String[]{head, parts[0]};
            colors = new Color[]{Theme.TEXT_FAINT, Theme.ACCENT};
            total = tfm.stringWidth(texts[0]) + tfm.stringWidth(texts[1]);
        }
        int x = cx - total / 2;
        int y = h - 10;
        for (int i = 0; i < texts.length; i++) {
            g2.setColor(colors[i]);
            g2.drawString(texts[i], x, y);
            x += tfm.stringWidth(texts[i]);
        }
        g2.dispose();
    }

    private void paintPlaceholder(Graphics2D g2, int w, int h, int cx) {
        int fontSize = (int) Math.min(96, Math.max(44, (h - 100) * 0.8));
        Font huge = Theme.ui(Font.BOLD, fontSize);
        g2.setFont(huge);
        FontMetrics fm = g2.getFontMetrics();
        String mark = "?";
        int baseline = (int) (h * 0.47);
        g2.setColor(new Color(0x2A323C));
        g2.drawString(mark, cx - fm.stringWidth(mark) / 2, baseline);

        String title = noModel ? "尚未加载模型" : "请在左侧写一个数字";
        String sub = noModel ? "训练并保存模型后即可识别" : "松开鼠标即自动识别";
        g2.setFont(Theme.ui(Font.BOLD, 13f));
        FontMetrics tfm = g2.getFontMetrics();
        g2.setColor(noModel ? Theme.WARNING : Theme.TEXT_DIM);
        g2.drawString(title, cx - tfm.stringWidth(title) / 2, (int) (h * 0.64));
        g2.setFont(Theme.F_SMALL);
        FontMetrics sfm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString(sub, cx - sfm.stringWidth(sub) / 2, (int) (h * 0.64) + 22);
    }

    private static int[] topThree(ForwardTrace trace) {
        double[] p = trace.activations(trace.layerCount() - 1);
        int[] idx = {-1, -1, -1};
        for (int t = 0; t < 3; t++) {
            int best = -1;
            for (int j = 0; j < p.length; j++) {
                boolean used = false;
                for (int k = 0; k < t; k++) {
                    if (idx[k] == j) {
                        used = true;
                        break;
                    }
                }
                if (!used && (best < 0 || p[j] > p[best])) {
                    best = j;
                }
            }
            idx[t] = best;
        }
        return idx;
    }

    /** 供外部做命中测试（点击「去训练」按钮区域时用不到，保留扩展点）。 */
    Shape heroShape() {
        return new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12);
    }
}
