package digitai.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 自绘训练曲线图：深色内嵌绘图区 + 网格 + 坐标标签 + 图例 + 最新点提示。
 *
 * <p>支持多条曲线（损失 / 训练准确率 / 测试准确率），百分比模式会把 Y 轴固定为 0–100%。</p>
 */
final class CurveChart extends JComponent {

    private final String title;
    private final boolean percentAxis;
    private final String emptyText;
    private final List<Series> series = new ArrayList<>();

    private double xMin = 0;
    private double xMax = 8;
    private double yMin = 0;
    private double yMax = 1;
    private boolean autoYMax;

    CurveChart(String title, boolean percentAxis, String emptyText) {
        this.title = title;
        this.percentAxis = percentAxis;
        this.emptyText = emptyText;
        this.autoYMax = !percentAxis;
        setOpaque(false);
        setPreferredSize(new Dimension(400, 150));
    }

    void addSeries(String name, Color color) {
        series.add(new Series(name, color));
    }

    void addPoint(int seriesIndex, double x, double y) {
        if (seriesIndex < 0 || seriesIndex >= series.size() || Double.isNaN(y) || Double.isInfinite(y)) {
            return;
        }
        Series s = series.get(seriesIndex);
        s.points.add(new double[]{x, y});
        if (s.points.size() > 900) {
            s.points.remove(0);
        }
        repaint();
    }

    void clearData() {
        for (Series s : series) {
            s.points.clear();
        }
        repaint();
    }

    void setXRange(double min, double max) {
        this.xMin = min;
        this.xMax = Math.max(min + 1e-9, max);
        repaint();
    }

    void setFixedYRange(double min, double max) {
        this.autoYMax = false;
        this.yMin = min;
        this.yMax = max;
        repaint();
    }

    /** 恢复 Y 轴自适应（损失曲线用）。 */
    void setAutoY() {
        this.autoYMax = true;
        repaint();
    }

    private boolean hasData() {
        for (Series s : series) {
            if (!s.points.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.quality(g);
        int w = getWidth();
        int h = getHeight();
        if (w < 60 || h < 40) {
            g2.dispose();
            return;
        }
        if (autoYMax) {
            double max = 0.02;
            for (Series s : series) {
                for (double[] p : s.points) {
                    max = Math.max(max, p[1]);
                }
            }
            yMax = max * 1.18;
            yMin = 0;
        }
        int left = 44;
        int right = 12;
        int top = 24;
        int bottom = 18;
        int plotW = Math.max(20, w - left - right);
        int plotH = Math.max(20, h - top - bottom);
        int px = left;
        int py = top;

        // 标题
        g2.setFont(Theme.ui(Font.BOLD, 11f));
        g2.setColor(Theme.TEXT_DIM);
        g2.drawString(title, 2, 14);

        // 图例（右侧，带最新值）
        g2.setFont(Theme.F_TINY);
        FontMetrics fm = g2.getFontMetrics();
        int lx = w - 4;
        for (int i = series.size() - 1; i >= 0; i--) {
            Series s = series.get(i);
            String latest = s.points.isEmpty() ? "—"
                    : percentAxis ? Theme.pct(s.points.get(s.points.size() - 1)[1], 1)
                    : String.format("%.3f", s.points.get(s.points.size() - 1)[1]);
            String text = s.name + " " + latest;
            int tw = fm.stringWidth(text);
            lx -= tw;
            g2.setColor(Theme.TEXT_DIM);
            g2.drawString(text, lx, 14);
            g2.setColor(s.color);
            g2.fillOval(lx - 10, 6, 5, 5);
            lx -= 20;
        }

        // 绘图区背景
        Shape clip = Theme.roundClip(px, py, plotW, plotH, 7);
        g2.setColor(Theme.CARD_INNER);
        g2.fillRoundRect(px, py, plotW, plotH, 7, 7);

        // 网格 + Y 轴标签（高度不足时自动减少网格线，避免标签重叠）
        int rows = Math.max(2, Math.min(4, plotH / 28));
        g2.setFont(Theme.F_TINY);
        FontMetrics afm = g2.getFontMetrics();
        for (int r = 0; r <= rows; r++) {
            double v = yMin + (yMax - yMin) * r / rows;
            int y = py + plotH - (int) Math.round((v - yMin) / (yMax - yMin) * plotH);
            g2.setColor(r == 0 ? Theme.alpha(Theme.BORDER, 160) : Theme.alpha(Theme.BORDER_SOFT, 170));
            g2.drawLine(px + 1, y, px + plotW - 1, y);
            String label = percentAxis ? String.format("%.0f%%", v * 100)
                    : String.format("%.2f", v);
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString(label, px - afm.stringWidth(label) - 6, y + afm.getAscent() / 2 - 1);
        }

        // X 轴刻度：epoch 范围较小时使用整数刻度
        int xTicks;
        if (xMax <= 12 && Math.abs(xMax - Math.round(xMax)) < 1e-6) {
            xTicks = Math.max(1, (int) Math.round(xMax - xMin));
        } else {
            xTicks = Math.max(2, Math.min(6, (int) Math.round(xMax - xMin)));
        }
        for (int t = 0; t <= xTicks; t++) {
            double v = xMin + (xMax - xMin) * t / xTicks;
            int x = px + (int) Math.round((v - xMin) / (xMax - xMin) * plotW);
            g2.setColor(Theme.alpha(Theme.BORDER_SOFT, 120));
            g2.drawLine(x, py + 1, x, py + plotH - 1);
            String label = String.format("%.0f", v);
            g2.setColor(Theme.TEXT_FAINT);
            if (t == 0) {
                g2.drawString(label, x + 2, py + plotH + 13);
            } else if (t == xTicks) {
                g2.drawString(label, x - afm.stringWidth(label) - 2, py + plotH + 13);
            } else {
                g2.drawString(label, x - afm.stringWidth(label) / 2, py + plotH + 13);
            }
        }
        if (!hasData()) {
            g2.setFont(Theme.F_SMALL);
            FontMetrics efm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_FAINT);
            g2.drawString(emptyText, px + (plotW - efm.stringWidth(emptyText)) / 2,
                    py + plotH / 2 + 4);
            g2.drawRoundRect(px, py, plotW - 1, plotH - 1, 7, 7);
            g2.dispose();
            return;
        }

        // 曲线裁剪
        Shape oldClip = g2.getClip();
        g2.clip(clip);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Series s : series) {
            if (s.points.isEmpty()) {
                continue;
            }
            g2.setColor(s.color);
            double prevX = 0;
            double prevY = 0;
            boolean first = true;
            for (double[] p : s.points) {
                int x = px + (int) Math.round((p[0] - xMin) / (xMax - xMin) * plotW);
                int y = py + plotH - (int) Math.round((p[1] - yMin) / (yMax - yMin) * plotH);
                if (!first) {
                    g2.drawLine((int) Math.round(prevX), (int) Math.round(prevY), x, y);
                }
                prevX = x;
                prevY = y;
                first = false;
            }
            // 最新点
            double[] last = s.points.get(s.points.size() - 1);
            int lxp = px + (int) Math.round((last[0] - xMin) / (xMax - xMin) * plotW);
            int lyp = py + plotH - (int) Math.round((last[1] - yMin) / (yMax - yMin) * plotH);
            g2.setColor(Theme.alpha(s.color, 60));
            g2.fillOval(lxp - 5, lyp - 5, 10, 10);
            g2.setColor(s.color);
            g2.fillOval(lxp - 3, lyp - 3, 6, 6);
        }
        g2.setClip(oldClip);
        g2.setColor(Theme.alpha(Theme.BORDER, 140));
        g2.drawRoundRect(px, py, plotW - 1, plotH - 1, 7, 7);
        g2.dispose();
    }

    /** 一条曲线。 */
    private static final class Series {
        final String name;
        final Color color;
        final List<double[]> points = new ArrayList<>();

        Series(String name, Color color) {
            this.name = name;
            this.color = color;
        }
    }
}
