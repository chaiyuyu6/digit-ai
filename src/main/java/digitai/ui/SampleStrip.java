package digitai.ui;

import digitai.data.Dataset;
import digitai.data.Preprocessor;
import digitai.nn.Network;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 实时测试样本条：10 张 MNIST 测试图片的缩略图 + 预测 / 真值。
 *
 * <p>预测正确显示绿色，错误显示红色；训练 / 评估后由训练页刷新。</p>
 */
final class SampleStrip extends JComponent {

    private Dataset data;
    private int[] indices;
    private Network network;
    private int[] predictions;

    SampleStrip() {
        setOpaque(false);
        setPreferredSize(new Dimension(600, 82));
    }

    void setSource(Dataset data, int[] indices) {
        this.data = data;
        this.indices = indices;
        this.predictions = null;
        repaint();
    }

    /** 用当前网络刷新预测结果。 */
    void refresh(Network network) {
        this.network = network;
        if (data == null || indices == null || network == null) {
            predictions = null;
            repaint();
            return;
        }
        predictions = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            try {
                predictions[i] = argmax(network.forward(data.image(indices[i])));
            } catch (RuntimeException ex) {
                predictions[i] = -1;
            }
        }
        repaint();
    }

    void clear() {
        data = null;
        indices = null;
        predictions = null;
        network = null;
        repaint();
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
        int count = 10;
        int gap = Math.max(6, w / 90);
        int tileW = (w - gap * (count - 1)) / count;
        tileW = Math.min(tileW, 64);
        int imgSize = Math.min(tileW - 4, h - 36);
        int totalW = count * tileW + gap * (count - 1);
        int startX = Math.max(0, (w - totalW) / 2);

        for (int i = 0; i < count; i++) {
            int x = startX + i * (tileW + gap);
            int y = 2;
            int imgH = Math.max(28, imgSize);
            int boxY = y + (h - imgH - 28) / 2;
            int boxX = x + (tileW - imgSize) / 2;

            boolean hasSample = data != null && indices != null && i < indices.length;
            if (hasSample) {
                BufferedImage img = Preprocessor.toImage(data.image(indices[i]));
                int actual = data.label(indices[i]);
                int pred = predictions != null && i < predictions.length ? predictions[i] : -1;
                Color border = pred < 0 ? Theme.BORDER : pred == actual ? Theme.SUCCESS : Theme.DANGER;
                g2.setColor(Theme.CARD_INNER);
                g2.fillRoundRect(boxX - 3, boxY - 3, imgSize + 6, imgSize + 6, 8, 8);
                Shape old = g2.getClip();
                g2.clip(Theme.roundClip(boxX, boxY, imgSize, imgSize, 6));
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(img, boxX, boxY, imgSize, imgSize, null);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setClip(old);
                g2.setColor(border);
                g2.drawRoundRect(boxX - 3, boxY - 3, imgSize + 5, imgSize + 5, 8, 8);
                g2.setFont(Theme.F_TINY);
                FontMetrics fm = g2.getFontMetrics();
                String p = pred < 0 ? "预测 —" : "预测 " + pred;
                String a = "真值 " + actual;
                int ty = boxY + imgSize + 15;
                g2.setColor(pred < 0 ? Theme.TEXT_FAINT : pred == actual ? Theme.SUCCESS : Theme.DANGER);
                g2.drawString(p, x + (tileW - fm.stringWidth(p)) / 2, ty);
                g2.setColor(Theme.TEXT_FAINT);
                g2.drawString(a, x + (tileW - fm.stringWidth(a)) / 2, ty + 13);
            } else {
                // 占位
                g2.setColor(Theme.CARD_INNER);
                g2.fillRoundRect(boxX - 3, boxY - 3, imgSize + 6, imgSize + 6, 8, 8);
                g2.setColor(Theme.alpha(Theme.BORDER, 180));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[]{3f, 4f}, 0f));
                g2.drawRoundRect(boxX - 3, boxY - 3, imgSize + 5, imgSize + 5, 8, 8);
                g2.setFont(Theme.F_TINY);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.TEXT_FAINT);
                String dash = "—";
                g2.drawString(dash, boxX + (imgSize - fm.stringWidth(dash)) / 2,
                        boxY + imgSize / 2 + 4);
                String t = "待评估";
                g2.drawString(t, x + (tileW - fm.stringWidth(t)) / 2, boxY + imgSize + 15);
            }
        }
        g2.dispose();
    }
}
