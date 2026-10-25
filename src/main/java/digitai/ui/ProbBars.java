package digitai.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * 0–9 概率条：数字标签 + 圆角进度条 + 百分比。
 *
 * <p>概率变化时用 18ms 的 Swing Timer 做缓动过渡；动画收敛后立即停止 Timer，
 * 不占用 EDT。</p>
 */
final class ProbBars extends JComponent {

    private final double[] target = new double[10];
    private final double[] shown = new double[10];
    private int predicted = -1;
    private boolean hasData;
    private Timer anim;

    ProbBars() {
        setOpaque(false);
        setPreferredSize(new Dimension(280, 300));
    }

    void setProbabilities(double[] probabilities) {
        if (probabilities == null || probabilities.length != 10) {
            clear();
            return;
        }
        System.arraycopy(probabilities, 0, target, 0, 10);
        predicted = argmax(target);
        hasData = true;
        startAnim();
    }

    void clear() {
        Arrays.fill(target, 0);
        predicted = -1;
        hasData = false;
        startAnim();
    }

    private void startAnim() {
        if (anim == null) {
            anim = new Timer(18, e -> tick());
            anim.setCoalesce(true);
        }
        if (!anim.isRunning()) {
            anim.start();
        }
    }

    private void tick() {
        double maxDelta = 0;
        for (int i = 0; i < 10; i++) {
            double delta = target[i] - shown[i];
            maxDelta = Math.max(maxDelta, Math.abs(delta));
            if (Math.abs(delta) < 0.0004) {
                shown[i] = target[i];
            } else {
                shown[i] += delta * 0.26;
            }
        }
        repaint();
        if (maxDelta < 0.0006) {
            System.arraycopy(target, 0, shown, 0, 10);
            anim.stop();
        }
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
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
        double rowH = h / 10.0;
        int trackX = 22;
        int trackW = Math.max(20, w - trackX - 58);
        int trackH = 7;

        for (int i = 0; i < 10; i++) {
            int rowY = (int) Math.round(i * rowH);
            int centerY = rowY + (int) Math.round(rowH / 2.0);
            boolean isTop = hasData && i == predicted;
            Color labelColor = !hasData ? Theme.TEXT_FAINT
                    : isTop ? Theme.ACCENT : Theme.TEXT_DIM;

            // 行分隔线（很淡）
            if (i > 0) {
                g2.setColor(Theme.alpha(Theme.BORDER_SOFT, 120));
                g2.drawLine(6, rowY, w - 6, rowY);
            }

            // 数字标签
            g2.setFont(isTop ? Theme.mono(Font.BOLD, 13f) : Theme.mono(Font.PLAIN, 12.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(labelColor);
            g2.drawString(Integer.toString(i), 6, centerY + fm.getAscent() / 2 - 1);

            // 轨道
            int ty = centerY - trackH / 2;
            g2.setColor(Theme.CARD_INNER);
            g2.fillRoundRect(trackX, ty, trackW, trackH, trackH, trackH);

            // 填充
            double v = hasData ? Theme.clamp(shown[i], 0, 1) : 0;
            int fill = (int) Math.round(trackW * v);
            if (fill > 0) {
                Color c1 = isTop ? Theme.ACCENT_DEEP : new Color(0x155E6B);
                Color c2 = isTop ? Theme.ACCENT : new Color(0x2A9DB0);
                g2.setPaint(new GradientPaint(trackX, 0, c1, trackX + Math.max(fill, 10), 0, c2));
                g2.fillRoundRect(trackX, ty, Math.max(fill, trackH), trackH, trackH, trackH);
                if (isTop) {
                    // 顶端微光
                    g2.setColor(Theme.alpha(Color.WHITE, 90));
                    g2.fillOval(trackX + fill - 4, ty + 1, 3, trackH - 2);
                }
            } else if (hasData) {
                // 极小概率也留一个 2px 的点，表示「非零」
                g2.setColor(new Color(0x24404A));
                g2.fillRoundRect(trackX, ty, 2, trackH, trackH, trackH);
            }

            // 百分比
            String pct = hasData ? Theme.pctProb(target[i]) : "—";
            g2.setFont(isTop ? Theme.mono(Font.BOLD, 12f) : Theme.mono(Font.PLAIN, 11.5f));
            FontMetrics pfm = g2.getFontMetrics();
            g2.setColor(!hasData ? Theme.TEXT_FAINT : isTop ? Theme.ACCENT : Theme.TEXT_DIM);
            g2.drawString(pct, w - 4 - pfm.stringWidth(pct), centerY + pfm.getAscent() / 2 - 1);
        }
        g2.dispose();
    }
}
