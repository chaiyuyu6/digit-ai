package digitai.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 教学页：原理课堂。
 *
 * <p>把「28×28 像素 → 784 维输入 → 全连接 + ReLU → Softmax → 交叉熵 →
 * 反向传播 → 泛化」这条链路拆成 8 个可读的小节。整页是一个自绘的滚动文档：
 * 所有卡片、公式、示意图、文字排版都用 Java2D 手工绘制，以精确控制中文换行与
 * 行高，并与识别页 / 训练页共用 {@link Theme} 的色板与卡片质感。</p>
 *
 * <p>页面宽度自适应但上限 {@link #PAGE_MAX}，窄窗口下文字重新折行、内容不会被裁掉；
 * 底部「快速测验」支持点击作答。所有绘制都在 EDT。</p>
 */
public final class TeachingPanel extends JPanel {

    /** 正文内容最大宽度。 */
    private static final int PAGE_MAX = 1120;
    private static final int CONTENT_TOP = 2;
    private static final int CONTENT_BOTTOM = 26;
    private static final int SECTION_GAP = 14;
    private static final int SEC_PAD_X = 18;
    private static final int SEC_TOP = 14;
    private static final int SEC_BOTTOM = 17;
    private static final int BLOCK_GAP = 11;

    // ------------------------------------------------------------------
    // 字体 / 度量（统一入口，方便整体调字号）
    // ------------------------------------------------------------------
    private static final BufferedImage METRICS_IMG =
            new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    private static final Graphics2D METRICS = METRICS_IMG.createGraphics();

    private static final Font F_BODY = Theme.ui(Font.PLAIN, 13.5f);
    private static final Font F_BODY_B = Theme.ui(Font.BOLD, 13.5f);
    private static final Font F_SMALL = Theme.ui(Font.PLAIN, 12f);
    private static final Font F_SMALL_B = Theme.ui(Font.BOLD, 12f);
    private static final Font F_TINY = Theme.ui(Font.PLAIN, 11f);
    private static final Font F_TINY_B = Theme.ui(Font.BOLD, 11f);
    private static final Font F_TITLE = Theme.ui(Font.BOLD, 15.5f);
    private static final Font F_SUB = Theme.ui(Font.PLAIN, 12f);
    private static final Font F_HERO = Theme.ui(Font.BOLD, 23f);
    private static final Font F_HERO_SUB = Theme.ui(Font.PLAIN, 13.5f);
    private static final Font F_FORMULA = Theme.ui(Font.PLAIN, 15.5f);
    private static final Font F_BIG = Theme.ui(Font.BOLD, 32f);
    private static final Font F_NUM = Theme.mono(Font.BOLD, 12f);
    private static final Font F_MONO = Theme.mono(Font.PLAIN, 12f);
    private static final Font F_MONO_S = Theme.mono(Font.PLAIN, 11f);
    private static final Font F_MONO_B = Theme.mono(Font.BOLD, 12f);
    private static final Font F_MONO_BIG = Theme.mono(Font.BOLD, 17f);
    private static final Font F_MONO_STAT = Theme.mono(Font.BOLD, 19f);

    static {
        METRICS.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        METRICS.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static final Color CARD_TOP = new Color(0x171E26);
    private static final Color CARD_BOTTOM = new Color(0x141A21);

    // ------------------------------------------------------------------
    // 构造：整页就是一个透明的滚动区域
    // ------------------------------------------------------------------
    public TeachingPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(Theme.PAGE_PAD, Theme.PAGE_PAD, Theme.PAGE_PAD, Theme.PAGE_PAD));
        JScrollPane scroll = Theme.scroll(new DocView());
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getVerticalScrollBar().setBlockIncrement(240);
        add(scroll, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------
    // 通用绘制小工具
    // ------------------------------------------------------------------
    private static FontMetrics fm(Font f) {
        return METRICS.getFontMetrics(f);
    }

    /** 在给定高度里垂直居中一行文字的基线。 */
    private static int centerBaseline(int top, int height, Font f) {
        FontMetrics m = fm(f);
        return top + (height - m.getHeight()) / 2 + m.getAscent();
    }

    private static void paintCardBg(Graphics2D g2, int x, int y, int w, int h) {
        Theme.cardShadow(g2, x, y, w, h, Theme.RADIUS);
        g2.setPaint(new GradientPaint(0, y, CARD_TOP, 0, y + h, CARD_BOTTOM));
        g2.fillRoundRect(x, y, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(x, y, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
    }

    private static void paintInner(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(Theme.CARD_INNER);
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(Theme.BORDER_SOFT);
        g2.drawRoundRect(x, y, w - 1, h - 1, 10, 10);
    }

    private static void dashedRect(Graphics2D g2, float x, float y, float w, float h, Color c) {
        Stroke old = g2.getStroke();
        g2.setColor(c);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{4f, 4f}, 0f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 4, 4));
        g2.setStroke(old);
    }

    private static int chipWidth(String text) {
        return fm(F_TINY).stringWidth(text) + 27;
    }

    private static void paintChip(Graphics2D g2, int x, int y, String text, Color dot) {
        int w = chipWidth(text);
        int h = 24;
        g2.setColor(Theme.CARD_HI);
        g2.fillRoundRect(x, y, w, h, h, h);
        g2.setColor(Theme.BORDER_SOFT);
        g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
        g2.setColor(Theme.alpha(dot, 58));
        g2.fillOval(x + 8, y + h / 2 - 5, 10, 10);
        g2.setColor(dot);
        g2.fillOval(x + 10, y + h / 2 - 3, 6, 6);
        g2.setFont(F_TINY);
        g2.setColor(Theme.TEXT_DIM);
        g2.drawString(text, x + 20, centerBaseline(y, h, F_TINY));
    }

    private static void paintRich(Graphics2D g2, Rich rich, int x, int y, int width) {
        int cy = y;
        for (Rich.Line line : rich.lines(Math.max(40, width))) {
            int baseline = cy + line.ascent;
            for (Rich.Run run : line.runs) {
                g2.setFont(run.font);
                g2.setColor(run.color);
                g2.drawString(run.text, x + run.x, baseline);
            }
            cy += line.height;
        }
    }

    // ==================================================================
    // 富文本：按宽度自动换行的轻量排版
    // ==================================================================
    private static final class Rich {

        private static final int LEADING = 6;

        private static final class Seg {
            final String text;
            final Font font;
            final Color color;

            Seg(String text, Font font, Color color) {
                this.text = text;
                this.font = font;
                this.color = color;
            }
        }

        static final class Run {
            String text;
            final Font font;
            final Color color;
            final int x;

            Run(String text, Font font, Color color, int x) {
                this.text = text;
                this.font = font;
                this.color = color;
                this.x = x;
            }
        }

        static final class Line {
            final List<Run> runs;
            final int width;
            final int height;
            final int ascent;

            Line(List<Run> runs, int width, int height, int ascent) {
                this.runs = runs;
                this.width = width;
                this.height = height;
                this.ascent = ascent;
            }
        }

        private final List<Seg> segs = new ArrayList<>();
        private int cacheWidth = -1;
        private List<Line> cache;

        // ---- 常用样式 ----
        Rich txt(String s) {
            return add(s, F_BODY, Theme.TEXT);
        }

        Rich bold(String s) {
            return add(s, F_BODY_B, Theme.TEXT);
        }

        Rich accent(String s) {
            return add(s, F_BODY_B, Theme.ACCENT);
        }

        Rich violet(String s) {
            return add(s, F_BODY_B, Theme.VIOLET);
        }

        Rich warn(String s) {
            return add(s, F_BODY_B, Theme.WARNING);
        }

        Rich ok(String s) {
            return add(s, F_SMALL_B, Theme.SUCCESS);
        }

        Rich bad(String s) {
            return add(s, F_SMALL_B, Theme.DANGER);
        }

        Rich dim(String s) {
            return add(s, F_SMALL, Theme.TEXT_DIM);
        }

        Rich tiny(String s) {
            return add(s, F_TINY, Theme.TEXT_DIM);
        }

        Rich mono(String s) {
            return add(s, F_MONO, Theme.ACCENT);
        }

        Rich monoDim(String s) {
            return add(s, F_MONO_S, Theme.TEXT_FAINT);
        }

        Rich f(String s) {
            return add(s, F_FORMULA, Theme.TEXT);
        }

        Rich fa(String s) {
            return add(s, F_FORMULA, Theme.ACCENT);
        }

        Rich fv(String s) {
            return add(s, F_FORMULA, Theme.VIOLET);
        }

        Rich add(String s, Font font, Color color) {
            if (s != null && !s.isEmpty()) {
                segs.add(new Seg(s, font, color));
                cache = null;
                cacheWidth = -1;
            }
            return this;
        }

        int height(int width) {
            int h = 0;
            for (Line line : lines(Math.max(40, width))) {
                h += line.height;
            }
            return h;
        }

        /** 不换行时的整行宽度（公式居中用）。 */
        int singleWidth() {
            int w = 0;
            for (Seg seg : segs) {
                w += fm(seg.font).stringWidth(seg.text);
            }
            return w;
        }

        List<Line> lines(int maxWidth) {
            int mw = Math.max(40, maxWidth);
            if (mw == cacheWidth && cache != null) {
                return cache;
            }
            cacheWidth = mw;
            List<Line> out = new ArrayList<>();
            LineBuilder b = new LineBuilder();
            for (Seg seg : segs) {
                StringBuilder word = new StringBuilder();
                for (int i = 0; i < seg.text.length(); ) {
                    int cp = seg.text.codePointAt(i);
                    int n = Character.charCount(cp);
                    if (Character.isWhitespace(cp)) {
                        if (word.length() > 0) {
                            b.put(word.toString(), seg.font, seg.color, out, mw);
                            word.setLength(0);
                        }
                        b.put(" ", seg.font, seg.color, out, mw);
                    } else if (isCjk(cp)) {
                        if (word.length() > 0) {
                            b.put(word.toString(), seg.font, seg.color, out, mw);
                            word.setLength(0);
                        }
                        b.put(new String(Character.toChars(cp)), seg.font, seg.color, out, mw);
                    } else {
                        word.appendCodePoint(cp);
                    }
                    i += n;
                }
                if (word.length() > 0) {
                    b.put(word.toString(), seg.font, seg.color, out, mw);
                }
            }
            if (b.has()) {
                out.add(b.build());
            }
            cache = out;
            return out;
        }

        private static final class LineBuilder {
            private final List<Run> runs = new ArrayList<>();
            private int x;
            private int ascent;
            private int descent;

            boolean has() {
                return !runs.isEmpty();
            }

            void put(String unit, Font font, Color color, List<Line> out, int maxWidth) {
                if (unit.isEmpty()) {
                    return;
                }
                boolean space = unit.equals(" ");
                FontMetrics m = fm(font);
                int w = m.stringWidth(unit);
                if (x > 0 && x + w > maxWidth && !isNoStart(unit)) {
                    out.add(build());
                    reset();
                }
                if (x == 0 && space) {
                    return;
                }
                if (!runs.isEmpty()) {
                    Run last = runs.get(runs.size() - 1);
                    if (last.font.equals(font) && last.color.equals(color)) {
                        last.text += unit;
                        x += w;
                        ascent = Math.max(ascent, m.getAscent());
                        descent = Math.max(descent, m.getDescent());
                        return;
                    }
                }
                runs.add(new Run(unit, font, color, x));
                x += w;
                ascent = Math.max(ascent, m.getAscent());
                descent = Math.max(descent, m.getDescent());
            }

            Line build() {
                return new Line(new ArrayList<>(runs), x, ascent + descent + LEADING, ascent);
            }

            void reset() {
                runs.clear();
                x = 0;
                ascent = 0;
                descent = 0;
            }
        }

        private static boolean isCjk(int cp) {
            return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                    || (cp >= 0x3000 && cp <= 0x303F)
                    || (cp >= 0xFF00 && cp <= 0xFFEF);
        }

        private static boolean isNoStart(String unit) {
            if (unit.length() != 1) {
                return false;
            }
            return "，。、；：！？）》」』】…·%,.;:!?)]}".indexOf(unit.charAt(0)) >= 0;
        }
    }

    // ==================================================================
    // 内容块：每个块自己知道「在给定宽度下多高」以及怎么画
    // ==================================================================
    private interface Block {
        int height(int width);

        void paint(Graphics2D g2, int x, int y, int width);
    }

    private static final class ParaBlock implements Block {
        private final Rich rich;

        ParaBlock(Rich rich) {
            this.rich = rich;
        }

        @Override
        public int height(int width) {
            return rich.height(width);
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            paintRich(g2, rich, x, y, width);
        }
    }

    private static final class BulletBlock implements Block {
        private final List<Rich> items = new ArrayList<>();
        private final Color dot;

        BulletBlock(Rich... items) {
            this(Theme.ACCENT, items);
        }

        BulletBlock(Color dot, Rich... items) {
            this.dot = dot;
            for (Rich r : items) {
                this.items.add(r);
            }
        }

        @Override
        public int height(int width) {
            int h = 0;
            for (Rich r : items) {
                h += r.height(width - 18) + 7;
            }
            return Math.max(0, h - 7);
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            int cy = y;
            for (Rich r : items) {
                int h = r.height(width - 18);
                g2.setColor(Theme.alpha(dot, 55));
                g2.fillOval(x + 1, cy + 9, 8, 8);
                g2.setColor(dot);
                g2.fillOval(x + 3, cy + 11, 4, 4);
                paintRich(g2, r, x + 18, cy, width - 18);
                cy += h + 7;
            }
        }
    }

    private static final class FormulaBlock implements Block {
        private final List<Rich> lines = new ArrayList<>();
        private final String note;

        FormulaBlock(String note, Rich... lines) {
            this.note = note;
            for (Rich r : lines) {
                this.lines.add(r);
            }
        }

        @Override
        public int height(int width) {
            return 14 + lines.size() * 26 + (note == null ? 0 : 21) + 11;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            int h = height(width);
            paintInner(g2, x, y, width, h);
            int cx = x + width / 2;
            int cy = y + 14;
            for (Rich line : lines) {
                int baseline = cy + 18;
                int lx = cx - line.singleWidth() / 2;
                for (Rich.Seg seg : line.segs) {
                    g2.setFont(seg.font);
                    g2.setColor(seg.color);
                    g2.drawString(seg.text, lx, baseline);
                    lx += fm(seg.font).stringWidth(seg.text);
                }
                cy += 26;
            }
            if (note != null) {
                Theme.textCenter(g2, note, cx, cy + 14, F_TINY, Theme.TEXT_DIM);
            }
        }
    }

    private static final class HighlightBlock implements Block {
        private final Color accent;
        private final String title;
        private final Rich body;

        HighlightBlock(Color accent, String title, Rich body) {
            this.accent = accent;
            this.title = title;
            this.body = body;
        }

        @Override
        public int height(int width) {
            int inner = width - 30;
            return 12 + (title == null ? 0 : 18) + body.height(inner) + 12;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            int h = height(width);
            int inner = width - 30;
            g2.setColor(Theme.alpha(accent, 15));
            g2.fillRoundRect(x, y, width, h, 9, 9);
            g2.setColor(Theme.alpha(accent, 72));
            g2.drawRoundRect(x, y, width - 1, h - 1, 9, 9);
            g2.setColor(accent);
            g2.fillRoundRect(x + 1, y + 7, 3, h - 14, 2, 2);
            int ty = y + 12;
            if (title != null) {
                Theme.text(g2, title, x + 14, ty + 12, F_TINY_B, accent);
                ty += 18;
            }
            paintRich(g2, body, x + 14, ty, inner);
        }
    }

    private static final class Tile {
        final String caption;
        final String value;
        final String sub;
        final Color color;

        Tile(String caption, String value, String sub, Color color) {
            this.caption = caption;
            this.value = value;
            this.sub = sub;
            this.color = color;
        }
    }

    private static final class TilesBlock implements Block {
        private final List<Tile> tiles = new ArrayList<>();

        TilesBlock(Tile... tiles) {
            for (Tile t : tiles) {
                this.tiles.add(t);
            }
        }

        @Override
        public int height(int width) {
            return 68;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            int n = tiles.size();
            int gap = 10;
            int tw = (width - (n - 1) * gap) / n;
            for (int i = 0; i < n; i++) {
                Tile t = tiles.get(i);
                int tx = x + i * (tw + gap);
                g2.setColor(Theme.CARD_INNER);
                g2.fillRoundRect(tx, y, tw, 68, 9, 9);
                g2.setColor(Theme.BORDER_SOFT);
                g2.drawRoundRect(tx, y, tw - 1, 67, 9, 9);
                g2.setColor(Theme.alpha(t.color, 160));
                g2.fillOval(tx + tw - 15, y + 11, 5, 5);
                Theme.text(g2, t.caption, tx + 12, y + 20, F_TINY, Theme.TEXT_DIM);
                Theme.text(g2, t.value, tx + 12, y + 46, F_MONO_STAT, t.color);
                if (t.sub != null) {
                    Theme.text(g2, t.sub, tx + 12, y + 60, F_TINY, Theme.TEXT_FAINT);
                }
            }
        }
    }

    private static final class Step {
        final String title;
        final Rich detail;
        final String time;

        Step(String title, Rich detail, String time) {
            this.title = title;
            this.detail = detail;
            this.time = time;
        }
    }

    private static final class StepsBlock implements Block {
        private final List<Step> steps = new ArrayList<>();
        private final Color accent;

        StepsBlock(Color accent, Step... steps) {
            this.accent = accent;
            for (Step s : steps) {
                this.steps.add(s);
            }
        }

        @Override
        public int height(int width) {
            int h = 0;
            for (Step s : steps) {
                h += 24 + s.detail.height(width - 36) + 12;
            }
            return h;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            int cy = y;
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                int dh = s.detail.height(width - 36);
                if (i < steps.size() - 1) {
                    g2.setColor(Theme.BORDER_SOFT);
                    g2.drawLine(x + 11, cy + 24, x + 11, cy + 24 + dh + 12);
                }
                g2.setColor(Theme.alpha(accent, 30));
                g2.fillOval(x, cy, 23, 23);
                g2.setColor(Theme.alpha(accent, 120));
                g2.drawOval(x, cy, 22, 22);
                Theme.textCenter(g2, String.valueOf(i + 1), x + 11, cy + 16, F_MONO_B, accent);
                Theme.text(g2, s.title, x + 34, cy + 16, F_BODY_B, Theme.TEXT);
                if (s.time != null) {
                    Theme.textRight(g2, s.time, x + width, cy + 16, F_TINY, Theme.TEXT_FAINT);
                }
                paintRich(g2, s.detail, x + 34, cy + 23, width - 36);
                cy += 24 + dh + 12;
            }
        }
    }

    // ------------------------------------------------------------------
    // 图 1：28×28 像素与预处理
    // ------------------------------------------------------------------
    private static final class PixelBlock implements Block {
        private static final int H = 184;
        private static final int GRID = 140;
        private static final int CELL = GRID / 28;
        private static final double[] PIX = rasterizeDigit();

        @Override
        public int height(int width) {
            return H;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            paintInner(g2, x, y, width, H);

            // 左：白底画板上的笔迹（偏小、偏左）
            int cs = 92;
            int cx = x + 18;
            int cy = y + 16;
            g2.setColor(Theme.CANVAS_WHITE);
            g2.fillRoundRect(cx, cy, cs, cs, 8, 8);
            Graphics2D clip = (Graphics2D) g2.create();
            clip.clip(Theme.roundClip(cx + 1, cy + 1, cs - 2, cs - 2, 7));
            drawDigit(clip, cx, cy, cs, 0.58, 0.20, 0.16, Theme.INK, 3f);
            clip.dispose();
            drawDigitBox(g2, cx, cy, cs, 0.58, 0.20, 0.16, Theme.alpha(Theme.ACCENT, 190));
            Theme.textCenter(g2, "画板上的笔迹", cx + cs / 2, cy + cs + 20, F_TINY, Theme.TEXT_DIM);
            Theme.textCenter(g2, "偏小、偏左也没关系", cx + cs / 2, cy + cs + 34, F_TINY, Theme.TEXT_FAINT);

            // 中间：箭头
            int ax1 = cx + cs + 12;
            int ax2 = ax1 + 46;
            int ay = y + 66;
            g2.setColor(Theme.alpha(Theme.ACCENT, 170));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ax1, ay, ax2, ay);
            g2.drawLine(ax2, ay, ax2 - 7, ay - 4);
            g2.drawLine(ax2, ay, ax2 - 7, ay + 4);
            Theme.textCenter(g2, "预处理", (ax1 + ax2) / 2, ay - 9, F_TINY, Theme.ACCENT);

            // 右：28×28 网格
            int gx = x + 196;
            int gy = y + 16;
            for (int r = 0; r < 28; r++) {
                for (int c = 0; c < 28; c++) {
                    double v = PIX[r * 28 + c];
                    Color cell = Theme.mix(new Color(0x0A0F15), new Color(0xD9F4FF),
                            Math.pow(Theme.clamp(v, 0, 1), 0.82));
                    g2.setColor(cell);
                    g2.fill(new Rectangle2D.Float(gx + c * CELL + 0.3f, gy + r * CELL + 0.3f,
                            CELL - 0.6f, CELL - 0.6f));
                }
            }
            g2.setColor(Theme.alpha(Theme.ACCENT, 110));
            g2.drawRoundRect(gx - 1, gy - 1, GRID + 1, GRID + 1, 4, 4);
            Theme.textCenter(g2, "28 × 28 = 784 个灰度值", gx + GRID / 2, gy + GRID + 21,
                    F_TINY, Theme.TEXT_DIM);

            // 最右：解释与展开后的向量
            int rx = x + 356;
            int rw = width - 356 - 18;
            Theme.text(g2, "网络真正看到的输入", rx, y + 36, F_SMALL_B, Theme.ACCENT);
            Theme.text(g2, "784 维向量 a[0]，每个值都在 0 到 1 之间", rx, y + 62, F_BODY, Theme.TEXT);
            Theme.text(g2, "按行展开：像素 → 一列数字", rx, y + 86, F_SMALL, Theme.TEXT_DIM);
            int chipW = Math.min(430, rw);
            g2.setColor(Theme.CARD_HI);
            g2.fillRoundRect(rx, y + 98, chipW, 30, 7, 7);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(rx, y + 98, chipW - 1, 29, 7, 7);
            String vec = "0.00  0.00  0.03  0.91  …  0.00";
            Theme.text(g2, vec, rx + 12, y + 118, F_MONO_S, Theme.TEXT_DIM);
            Theme.text(g2, "0 = 纯黑，1 = 最亮；预处理后的缩略图就是它。",
                    rx, y + 154, F_TINY, Theme.TEXT_FAINT);
            Theme.text(g2, "识别页里的「网络看到的输入」缩略图 = 这张网格", rx, y + 172,
                    F_TINY, Theme.alpha(Theme.ACCENT, 220));
        }

        private static void drawDigit(Graphics2D g2, float x, float y, float side,
                                      double scale, double offX, double offY, Color color, float stroke) {
            Graphics2D gg = (Graphics2D) g2.create();
            Theme.quality(gg);
            gg.translate(x + offX * side, y + offY * side);
            double k = side / 28.0 * scale;
            gg.scale(k, k);
            gg.setColor(color);
            gg.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double p = new Path2D.Double();
            digitPath(p);
            gg.draw(p);
            gg.dispose();
        }

        private static void drawDigitBox(Graphics2D g2, float x, float y, float side,
                                         double scale, double offX, double offY, Color color) {
            double k = side / 28.0 * scale;
            float pad = 2f;
            float bx = (float) (x + offX * side + 5.6 * k - pad);
            float by = (float) (y + offY * side + 4.8 * k - pad);
            float bw = (float) (16.4 * k + pad * 2);
            float bh = (float) (18.9 * k + pad * 2);
            dashedRect(g2, bx, by, bw, bh, color);
        }

        private static void digitPath(Path2D.Double p) {
            p.moveTo(6.4, 8.2);
            p.quadTo(13.8, 5.1, 21.4, 7.3);
            p.lineTo(11.2, 23.2);
        }

        private static double[] rasterizeDigit() {
            // 先按 4 倍分辨率绘制，再取平均降采样，得到接近真实灰度的抗锯齿像素
            int scale = 4;
            int hi = 28 * scale;
            BufferedImage img = new BufferedImage(hi, hi, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Theme.quality(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, hi, hi);
            g.scale(scale, scale);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(3.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double p = new Path2D.Double();
            digitPath(p);
            g.draw(p);
            g.dispose();
            double[] v = new double[28 * 28];
            double max = 0;
            for (int y = 0; y < 28; y++) {
                for (int x = 0; x < 28; x++) {
                    double ink = 0;
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) {
                            int rgb = img.getRGB(x * scale + sx, y * scale + sy);
                            double lum = (0.299 * ((rgb >> 16) & 255)
                                    + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255)) / 255.0;
                            ink += 1.0 - lum;
                        }
                    }
                    ink /= scale * scale;
                    if (ink < 0.06) {
                        ink = 0;
                    }
                    v[y * 28 + x] = ink;
                    max = Math.max(max, ink);
                }
            }
            if (max > 1e-9) {
                for (int i = 0; i < v.length; i++) {
                    v[i] /= max;
                }
            }
            return v;
        }
    }

    // ------------------------------------------------------------------
    // 图 2：784-64-32-10 全连接结构
    // ------------------------------------------------------------------
    private static final class MlpBlock implements Block {
        private static final int H = 252;

        @Override
        public int height(int width) {
            return H;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            paintInner(g2, x, y, width, H);
            Theme.text(g2, "784 → 64 → 32 → 10 · 52,650 个可学习参数", x + 18, y + 21,
                    F_SMALL, Theme.TEXT_DIM);

            int midY = y + 108;
            int inSide = 78;
            int inX = x + 54;
            int inY = midY - inSide / 2;
            int h1cx = x + (int) (width * 0.34);
            int h2cx = x + (int) (width * 0.51);
            int ocx = x + (int) (width * 0.68);
            Random rnd = new Random(20240607L);

            // 连线（先画线，再画点，避免点被线糊住）
            g2.setStroke(new BasicStroke(0.7f));
            for (int j = 0; j < 64; j++) {
                int jx = Math.round(h1cx - 45.5f + (j % 8) * 13);
                int jy = Math.round(midY - 45.5f + (j / 8) * 13);
                for (int k = 0; k < 2; k++) {
                    int iy = inY + rnd.nextInt(inSide - 8) + 4;
                    g2.setColor(Theme.alpha(Theme.mix(Theme.ACCENT, Theme.VIOLET, 0.55), 30));
                    g2.drawLine(inX + inSide, iy, jx, jy);
                }
            }
            for (int j = 0; j < 32; j++) {
                int jx = Math.round(h2cx - 45.5f + (j % 8) * 13);
                int jy = Math.round(midY - 19.5f + (j / 8) * 13);
                for (int k = 0; k < 3; k++) {
                    int si = rnd.nextInt(64);
                    int sx = Math.round(h1cx - 45.5f + (si % 8) * 13);
                    int sy = Math.round(midY - 45.5f + (si / 8) * 13);
                    g2.setColor(Theme.alpha(Theme.VIOLET, 32));
                    g2.drawLine(sx, sy, jx, jy);
                }
            }
            for (int o = 0; o < 10; o++) {
                int oy = Math.round(midY - 76.5f + o * 17);
                for (int k = 0; k < 8; k++) {
                    int si = rnd.nextInt(32);
                    int sx = Math.round(h2cx - 45.5f + (si % 8) * 13);
                    int sy = Math.round(midY - 19.5f + (si / 8) * 13);
                    g2.setColor(Theme.alpha(Theme.mix(Theme.VIOLET, Theme.ACCENT, 0.45), 36));
                    g2.drawLine(sx, sy, ocx, oy);
                }
            }

            // 输入缩略图
            g2.setColor(Theme.CARD_HI);
            g2.fillRoundRect(inX - 3, inY - 3, inSide + 6, inSide + 6, 8, 8);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(inX - 3, inY - 3, inSide + 5, inSide + 5, 8, 8);
            float cell = inSide / 28f;
            for (int r = 0; r < 28; r++) {
                for (int c = 0; c < 28; c++) {
                    double v = PixelBlock.PIX[r * 28 + c];
                    g2.setColor(Theme.mix(new Color(0x0A0F15), new Color(0xD9F4FF),
                            Math.pow(v, 0.82)));
                    g2.fill(new Rectangle2D.Float(inX + c * cell, inY + r * cell, cell + 0.2f, cell + 0.2f));
                }
            }
            g2.setColor(Theme.alpha(Theme.ACCENT, 130));
            g2.drawRoundRect(inX - 3, inY - 3, inSide + 5, inSide + 5, 8, 8);

            // 隐藏层节点（激活值决定亮度，故意做成稀疏）
            for (int j = 0; j < 64; j++) {
                int jx = Math.round(h1cx - 45.5f + (j % 8) * 13);
                int jy = Math.round(midY - 45.5f + (j / 8) * 13);
                g2.setColor(Theme.activationColor(pseudoActivation(j), 1.1));
                g2.fillOval(jx - 3, jy - 3, 7, 7);
                g2.setColor(Theme.alpha(Theme.VIOLET, 110));
                g2.drawOval(jx - 3, jy - 3, 6, 6);
            }
            for (int j = 0; j < 32; j++) {
                int jx = Math.round(h2cx - 45.5f + (j % 8) * 13);
                int jy = Math.round(midY - 19.5f + (j / 8) * 13);
                g2.setColor(Theme.activationColor(pseudoActivation(j + 17), 1.1));
                g2.fillOval(jx - 3, jy - 3, 7, 7);
                g2.setColor(Theme.alpha(Theme.VIOLET, 110));
                g2.drawOval(jx - 3, jy - 3, 6, 6);
            }

            // 输出节点 + 数字标签
            for (int o = 0; o < 10; o++) {
                int oy = Math.round(midY - 76.5f + o * 17);
                boolean top = o == 3;
                g2.setColor(Theme.alpha(Theme.ACCENT, top ? 55 : 26));
                g2.fillOval(ocx - 7, oy - 7, 14, 14);
                g2.setColor(top ? Theme.ACCENT : Theme.activationColor(0.35, 1.1));
                g2.fillOval(ocx - 4, oy - 4, 9, 9);
                Theme.text(g2, String.valueOf(o), ocx + 12, oy + 4, F_MONO_S,
                        top ? Theme.ACCENT : Theme.TEXT_FAINT);
            }

            // 右侧说明
            int px = x + width - 250;
            g2.setColor(Theme.CARD_HI);
            g2.fillRoundRect(px, y + 44, 232, 96, 8, 8);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(px, y + 44, 231, 95, 8, 8);
            Theme.text(g2, "全连接层", px + 12, y + 66, F_SMALL_B, Theme.ACCENT);
            Theme.text(g2, "每个点都连接上一层所有节点", px + 12, y + 88, F_TINY, Theme.TEXT_DIM);
            Theme.text(g2, "权重 = 网络自己学到的特征组合", px + 12, y + 106, F_TINY, Theme.TEXT_DIM);
            Theme.text(g2, "激活亮的节点，表示它被这次输入触发", px + 12, y + 124, F_TINY, Theme.TEXT_FAINT);

            // 层标题
            int labelY = y + H - 24;
            Theme.textCenter(g2, "输入层 784", inX + inSide / 2, labelY, F_SMALL_B, Theme.ACCENT);
            Theme.textCenter(g2, "28×28 灰度", inX + inSide / 2, labelY + 15, F_TINY, Theme.TEXT_FAINT);
            Theme.textCenter(g2, "隐藏层 64", h1cx, labelY, F_SMALL_B, Theme.VIOLET);
            Theme.textCenter(g2, "ReLU", h1cx, labelY + 15, F_TINY, Theme.TEXT_FAINT);
            Theme.textCenter(g2, "隐藏层 32", h2cx, labelY, F_SMALL_B, Theme.VIOLET);
            Theme.textCenter(g2, "ReLU", h2cx, labelY + 15, F_TINY, Theme.TEXT_FAINT);
            Theme.textCenter(g2, "输出层 10", ocx + 6, labelY, F_SMALL_B, Theme.ACCENT);
            Theme.textCenter(g2, "Softmax", ocx + 6, labelY + 15, F_TINY, Theme.TEXT_FAINT);
        }

        private static double pseudoActivation(int i) {
            double v = Math.abs(Math.sin(i * 1.7 + 0.4));
            if (v < 0.24) {
                return 0.05;
            }
            return 0.14 + 0.86 * Math.pow(v, 1.7);
        }
    }

    // ------------------------------------------------------------------
    // 图 3：ReLU 折线
    // ------------------------------------------------------------------
    private static final class ReluBlock implements Block {
        private static final int H = 184;
        private final List<Rich> notes = new ArrayList<>();

        ReluBlock() {
            notes.add(new Rich().bold("负输入 → 0：").txt("节点「熄灭」，可视化里就是暗点。"));
            notes.add(new Rich().bold("正输入 → 原样通过：").txt("没有上限，也不像 Sigmoid 那样饱和。"));
            notes.add(new Rich().bold("导数只有 0 或 1：").txt("反向传播时算起来非常便宜。"));
            notes.add(new Rich().bold("稀疏激活：").txt("大量 0 让每层只有少数节点在「发言」。"));
        }

        @Override
        public int height(int width) {
            return H;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            paintInner(g2, x, y, width, H);
            Theme.text(g2, "ReLU(z) = max(0, z)", x + 30, y + 25, F_MONO_B, Theme.ACCENT);

            int px = x + 28;
            int pw = (int) (width * 0.42);
            int plotTop = y + 40;
            int plotBottom = y + 148;
            int left = px + 10;
            int right = px + pw - 10;
            int ox = (left + right) / 2;

            g2.setColor(Theme.BORDER_SOFT);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{3f, 4f}, 0f));
            for (int gv = 1; gv <= 2; gv++) {
                int gy = plotBottom - (int) (gv / 2.2 * (plotBottom - plotTop));
                g2.drawLine(left, gy, right, gy);
            }
            g2.setColor(Theme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(left, plotBottom, right, plotBottom);
            g2.drawLine(ox, plotTop, ox, plotBottom);

            // 负半轴：被截断
            g2.setColor(Theme.alpha(Theme.DANGER, 150));
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(left, plotBottom, ox, plotBottom);
            // 正半轴：y = z
            g2.setColor(Theme.alpha(Theme.ACCENT, 60));
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ox, plotBottom, right, plotTop + 8);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ox, plotBottom, right, plotTop + 8);

            // 刻度
            for (int t = -2; t <= 2; t++) {
                int tx = ox + (int) (t / 2.2 * ((right - left) / 2.0));
                Theme.textCenter(g2, String.valueOf(t), tx, plotBottom + 16, F_TINY, Theme.TEXT_FAINT);
            }
            Theme.text(g2, "z", right + 4, plotBottom + 16, F_TINY, Theme.TEXT_FAINT);
            Theme.text(g2, "ReLU(z)", left, plotTop + 8, F_TINY, Theme.TEXT_FAINT);
            Theme.textCenter(g2, "截断为 0", (left + ox) / 2, plotBottom - 12, F_TINY, Theme.DANGER);
            Theme.text(g2, "原样通过", ox + 22, plotTop + 28, F_TINY, Theme.ACCENT);

            // 右侧要点
            int nx = x + (int) (width * 0.51);
            int nw = width - (nx - x) - 18;
            int cy = y + 36;
            for (Rich note : notes) {
                int h = note.height(nw - 18);
                g2.setColor(Theme.alpha(Theme.VIOLET, 60));
                g2.fillOval(nx + 1, cy + 9, 8, 8);
                g2.setColor(Theme.VIOLET);
                g2.fillOval(nx + 3, cy + 11, 4, 4);
                paintRich(g2, note, nx + 18, cy, nw - 18);
                cy += h + 10;
            }
        }
    }

    // ------------------------------------------------------------------
    // 图 4：Softmax 的 10 个概率
    // ------------------------------------------------------------------
    private static final class ProbBlock implements Block {
        private static final int H = 192;
        private static final int TOP = 3;
        private static final double[] P = {
                0.004, 0.001, 0.008, 0.912, 0.003, 0.012, 0.000, 0.002, 0.041, 0.017
        };

        @Override
        public int height(int width) {
            return H;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            paintInner(g2, x, y, width, H);
            int lx = x + 18;
            int lw = (int) (width * 0.36);
            Theme.text(g2, "一次预测的 10 个概率", lx, y + 25, F_SMALL, Theme.TEXT_DIM);

            int rowTop = y + 38;
            int rowH = 13;
            for (int d = 0; d < 10; d++) {
                int ry = rowTop + d * rowH;
                boolean top = d == TOP;
                Theme.text(g2, String.valueOf(d), lx, ry + 10, F_MONO_S,
                        top ? Theme.ACCENT : Theme.TEXT_DIM);
                int bx = lx + 18;
                int bw = lw - 62;
                g2.setColor(Theme.CARD_HI);
                g2.fillRoundRect(bx, ry + 2, bw, 8, 4, 4);
                int fill = Math.max(3, (int) Math.round(bw * P[d]));
                g2.setColor(top ? Theme.ACCENT : Theme.mix(Theme.VIOLET, Theme.BORDER, 0.25));
                g2.fillRoundRect(bx, ry + 2, fill, 8, 4, 4);
                Theme.textRight(g2, String.format("%.1f%%", P[d] * 100), lx + lw - 4,
                        ry + 10, F_MONO_S, top ? Theme.ACCENT : Theme.TEXT_FAINT);
            }

            int rx = lx + lw + 30;
            int rw = width - (rx - x) - 18;
            Theme.text(g2, "3", rx, y + 76, F_BIG, Theme.ACCENT);
            int dx = rx + 38;
            Theme.text(g2, "预测结果", dx, y + 40, F_TINY, Theme.TEXT_DIM);
            Theme.text(g2, "置信度 91.2%", dx, y + 62, F_BODY_B, Theme.TEXT);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawLine(rx, y + 88, rx + rw, y + 88);
            Theme.text(g2, "次高：8，仅 4.1%，差距很大", rx, y + 110, F_SMALL, Theme.TEXT_DIM);
            Theme.text(g2, "10 个概率之和 = 1.000", rx, y + 132, F_SMALL, Theme.TEXT_DIM);
            Theme.text(g2, "真实标签为 3 时：L = −log(0.912) ≈ 0.092", rx, y + 154,
                    F_SMALL, Theme.TEXT_DIM);
            Theme.text(g2, "低置信度 = 前两名接近，通常意味着笔画有歧义。", rx, y + 176,
                    F_TINY, Theme.TEXT_FAINT);
        }
    }

    // ------------------------------------------------------------------
    // 图 5：交叉熵 -log(p) 曲线 + 数值例子
    // ------------------------------------------------------------------
    private static final class LossBlock implements Block {
        private static final int H = 196;

        @Override
        public int height(int width) {
            return H;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            paintInner(g2, x, y, width, H);
            Theme.text(g2, "L = −log(p) 的形状", x + 30, y + 25, F_SMALL, Theme.TEXT_DIM);

            int px = x + 30;
            int pw = (int) (width * 0.34);
            int left = px + 12;
            int right = px + pw;
            int top = y + 44;
            int bottom = y + 156;

            g2.setColor(Theme.BORDER_SOFT);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[]{3f, 4f}, 0f));
            for (int l = 1; l <= 4; l++) {
                int gy = bottom - (int) (l / 4.8 * (bottom - top));
                g2.drawLine(left, gy, right, gy);
            }
            g2.setColor(Theme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(left, bottom, right, bottom);
            g2.drawLine(left, top, left, bottom);

            Path2D.Double curve = new Path2D.Double();
            for (int i = 0; i <= 220; i++) {
                double p = 0.010 + 0.990 * i / 220.0;
                double loss = -Math.log(p);
                double cx = left + p * (right - left);
                double cy = bottom - Theme.clamp(loss, 0, 4.8) / 4.8 * (bottom - top);
                if (i == 0) {
                    curve.moveTo(cx, cy);
                } else {
                    curve.lineTo(cx, cy);
                }
            }
            g2.setColor(Theme.alpha(Theme.WARNING, 45));
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curve);
            g2.setColor(Theme.WARNING);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curve);

            double[][] pts = {{0.99, 0.010}, {0.50, 0.693}, {0.01, 4.605}};
            String[] labels = {"0.99", "0.50", "0.01"};
            Color[] colors = {Theme.SUCCESS, Theme.WARNING, Theme.DANGER};
            for (int i = 0; i < pts.length; i++) {
                double p = pts[i][0];
                double loss = pts[i][1];
                int cx = (int) (left + p * (right - left));
                int cy = (int) (bottom - Theme.clamp(loss, 0, 4.8) / 4.8 * (bottom - top));
                g2.setColor(Theme.alpha(colors[i], 45));
                g2.fillOval(cx - 7, cy - 7, 14, 14);
                g2.setColor(colors[i]);
                g2.fillOval(cx - 3, cy - 3, 7, 7);
                int lx = Math.min(cx + 7, right - 26);
                int ly = Math.max(cy - 7, top + 10);
                Theme.text(g2, labels[i], lx, ly, F_MONO_S, colors[i]);
            }
            Theme.textCenter(g2, "p[真实标签]", (left + right) / 2, bottom + 17, F_TINY, Theme.TEXT_FAINT);
            Theme.text(g2, "L", left - 8, top + 4, F_TINY, Theme.TEXT_FAINT);

            // 右侧数值表
            int rx = x + (int) (width * 0.40);
            int rw = width - (rx - x) - 18;
            Theme.text(g2, "p[真实标签]", rx, y + 34, F_TINY, Theme.TEXT_FAINT);
            Theme.text(g2, "损失 L", rx + (int) (rw * 0.27), y + 34, F_TINY, Theme.TEXT_FAINT);
            Theme.text(g2, "直观含义", rx + (int) (rw * 0.48), y + 34, F_TINY, Theme.TEXT_FAINT);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawLine(rx, y + 40, rx + rw, y + 40);

            String[] ps = {"0.99", "0.50", "0.01"};
            String[] ls = {"0.010", "0.693", "4.605"};
            String[] hints = {"几乎全对，惩罚很小", "一半把握，中等惩罚", "自信地答错，重罚"};
            Color[] cs = {Theme.SUCCESS, Theme.WARNING, Theme.DANGER};
            for (int i = 0; i < 3; i++) {
                int ry = y + 64 + i * 40;
                Theme.text(g2, ps[i], rx, ry, F_MONO_B, Theme.TEXT);
                Theme.text(g2, ls[i], rx + (int) (rw * 0.27), ry, F_MONO_B, cs[i]);
                Theme.text(g2, hints[i], rx + (int) (rw * 0.48), ry, F_SMALL, Theme.TEXT_DIM);
                g2.setColor(Theme.BORDER_SOFT);
                g2.drawLine(rx, ry + 13, rx + rw, ry + 13);
            }
            Theme.text(g2, "−log 在 p → 0 时趋于无穷：越确信错误，罚得越狠。",
                    rx, y + 178, F_TINY, Theme.TEXT_FAINT);
        }
    }

    // ==================================================================
    // 互动小测
    // ==================================================================
    private static final class QuizBlock implements Block {
        private static final class Question {
            final Rich prompt;
            final String[] options;
            final int correct;
            final Rich hint;
            final Rich okExplain;
            final Rich badExplain;

            Question(Rich prompt, String[] options, int correct, String explanation) {
                this.prompt = prompt;
                this.options = options;
                this.correct = correct;
                this.hint = new Rich().dim("点击选项，立即看到解析  →");
                this.okExplain = new Rich().ok("√ 正确。").dim(explanation);
                this.badExplain = new Rich().bad("× 再想想。").dim(explanation);
            }

            int explainHeight(int width) {
                return Math.max(hint.height(width),
                        Math.max(okExplain.height(width), badExplain.height(width)));
            }
        }

        private static final class Hit {
            final int x;
            final int y;
            final int w;
            final int h;
            final int q;
            final int opt;

            Hit(int x, int y, int w, int h, int q, int opt) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.q = q;
                this.opt = opt;
            }

            boolean contains(int px, int py) {
                return px >= x && px < x + w && py >= y && py < y + h;
            }
        }

        private final List<Question> questions = new ArrayList<>();
        private final int[] chosen;
        private final List<Hit> hits = new ArrayList<>();

        QuizBlock() {
            questions.add(new Question(
                    new Rich().fa("Q1  ").bold("隐藏层使用 ReLU 后，可视化里很多节点是暗的，主要原因是？"),
                    new String[]{"这些节点还没训练好", "负的输入被 ReLU 截断为 0", "权重矩阵太小", "网络层数太多"},
                    1,
                    "ReLU 把负的 z 截成 0，未激活的神经元在可视化里就是暗的，这正是稀疏激活的直观体现。"));
            questions.add(new Question(
                    new Rich().fa("Q2  ").bold("模型给正确类别 0.5 的概率，它的交叉熵损失约为？"),
                    new String[]{"0.010", "0.693", "1.000", "4.605"},
                    1,
                    "L = −log(0.5) ≈ 0.693。作为对比：p=0.99 时约 0.010，p=0.01 时约 4.605。"));
            questions.add(new Question(
                    new Rich().fa("Q3  ").bold("Softmax 计算前减去最大值，主要是为了？"),
                    new String[]{"让概率之和等于 1", "防止 exp 溢出、保持数值稳定", "提高训练准确率", "让梯度变成 0"},
                    1,
                    "exp 在 z 很大时会溢出；上下同时除以 exp(max z) 不改变 Softmax 结果，是最常见的数值稳定技巧。"));
            chosen = new int[questions.size()];
            for (int i = 0; i < chosen.length; i++) {
                chosen[i] = -1;
            }
        }

        private int perQuestionHeight(Question q, int width) {
            return q.prompt.height(width) + 8 + 2 * 30 + 8 + 6 + q.explainHeight(width) + 14;
        }

        @Override
        public int height(int width) {
            int h = 24;
            for (Question q : questions) {
                h += perQuestionHeight(q, width);
            }
            return h;
        }

        @Override
        public void paint(Graphics2D g2, int x, int y, int width) {
            hits.clear();

            int answered = 0;
            int correct = 0;
            for (int i = 0; i < questions.size(); i++) {
                if (chosen[i] >= 0) {
                    answered++;
                    if (chosen[i] == questions.get(i).correct) {
                        correct++;
                    }
                }
            }
            String score = "已答 " + answered + " / " + questions.size()
                    + " · 答对 " + correct + " / " + questions.size();
            Theme.textRight(g2, score, x + width, y + 12, F_SMALL,
                    answered == questions.size() && correct == questions.size()
                            ? Theme.SUCCESS : Theme.TEXT_DIM);

            int qy = y + 24;
            for (int qi = 0; qi < questions.size(); qi++) {
                Question q = questions.get(qi);
                int qh = q.prompt.height(width);
                paintRich(g2, q.prompt, x, qy, width);
                int optTop = qy + qh + 8;
                int ow = (width - 10) / 2;
                for (int oi = 0; oi < 4; oi++) {
                    int ox = x + (oi % 2) * (ow + 10);
                    int oy = optTop + (oi / 2) * 38;
                    paintOption(g2, q, qi, oi, ox, oy, ow);
                    hits.add(new Hit(ox, oy, ow, 30, qi, oi));
                }
                int expTop = optTop + 2 * 30 + 8 + 6;
                Rich exp;
                if (chosen[qi] < 0) {
                    exp = q.hint;
                } else if (chosen[qi] == q.correct) {
                    exp = q.okExplain;
                } else {
                    exp = q.badExplain;
                }
                paintRich(g2, exp, x, expTop, width);
                qy += perQuestionHeight(q, width);
            }
        }

        private void paintOption(Graphics2D g2, Question q, int qi, int oi,
                                 int x, int y, int width) {
            boolean answered = chosen[qi] >= 0;
            Color fill = Theme.CARD_INNER;
            Color border = Theme.BORDER_SOFT;
            Color letterColor = Theme.TEXT_FAINT;
            Color textColor = Theme.TEXT_DIM;
            if (answered) {
                if (oi == q.correct) {
                    fill = Theme.alpha(Theme.SUCCESS, 24);
                    border = Theme.alpha(Theme.SUCCESS, 150);
                    letterColor = Theme.SUCCESS;
                    textColor = Theme.SUCCESS;
                } else if (oi == chosen[qi]) {
                    fill = Theme.alpha(Theme.DANGER, 24);
                    border = Theme.alpha(Theme.DANGER, 155);
                    letterColor = Theme.DANGER;
                    textColor = Theme.DANGER;
                } else {
                    textColor = Theme.TEXT_FAINT;
                }
            }
            g2.setColor(fill);
            g2.fillRoundRect(x, y, width, 30, 8, 8);
            g2.setColor(border);
            g2.drawRoundRect(x, y, width - 1, 29, 8, 8);
            String letter = "ABCD".substring(oi, oi + 1);
            Theme.text(g2, letter, x + 10, centerBaseline(y, 30, F_MONO_S), F_MONO_S, letterColor);
            Theme.text(g2, q.options[oi], x + 27, centerBaseline(y, 30, F_SMALL), F_SMALL, textColor);
            if (answered && oi == q.correct) {
                Theme.textRight(g2, "√", x + width - 10, centerBaseline(y, 30, F_SMALL), F_SMALL, Theme.SUCCESS);
            } else if (answered && oi == chosen[qi]) {
                Theme.textRight(g2, "×", x + width - 10, centerBaseline(y, 30, F_SMALL), F_SMALL, Theme.DANGER);
            }
        }

        void clearHits() {
            hits.clear();
        }

        boolean click(int px, int py) {
            for (Hit hit : hits) {
                if (hit.contains(px, py)) {
                    chosen[hit.q] = hit.opt;
                    return true;
                }
            }
            return false;
        }

        boolean hover(int px, int py) {
            for (Hit hit : hits) {
                if (hit.contains(px, py)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ==================================================================
    // 小节卡片
    // ==================================================================
    private static final class Section {
        private final String no;
        private final String title;
        private final String sub;
        private final Color accent;
        private final List<Block> blocks = new ArrayList<>();

        Section(String no, String title, String sub, Color accent) {
            this.no = no;
            this.title = title;
            this.sub = sub;
            this.accent = accent;
        }

        void add(Block block) {
            blocks.add(block);
        }

        private int bodyTop() {
            return SEC_TOP + 17 + (sub == null ? 0 : 21) + 11;
        }

        int height(int width) {
            int cw = width - 2 * SEC_PAD_X;
            int h = bodyTop();
            for (Block b : blocks) {
                h += b.height(cw) + BLOCK_GAP;
            }
            if (!blocks.isEmpty()) {
                h -= BLOCK_GAP;
            }
            return h + SEC_BOTTOM;
        }

        void paint(Graphics2D g2, int x, int y, int width) {
            int h = height(width);
            paintCardBg(g2, x, y, width, h);
            int numX = x + SEC_PAD_X + 11;
            int titleBase = y + SEC_TOP + 13;
            g2.setColor(accent);
            g2.fillRoundRect(x + SEC_PAD_X, y + SEC_TOP, 3, 16, 2, 2);
            Theme.text(g2, no, numX, titleBase, F_NUM, accent);
            int numW = fm(F_NUM).stringWidth(no);
            Theme.text(g2, title, numX + numW + 10, titleBase, F_TITLE, Theme.TEXT);
            if (sub != null) {
                Theme.text(g2, sub, numX, titleBase + 20, F_SUB, Theme.TEXT_DIM);
            }
            int cw = width - 2 * SEC_PAD_X;
            int cy = y + bodyTop();
            for (Block b : blocks) {
                b.paint(g2, x + SEC_PAD_X, cy, cw);
                cy += b.height(cw) + BLOCK_GAP;
            }
        }
    }

    // ==================================================================
    // 滚动文档视图
    // ==================================================================
    private static final class DocView extends JComponent implements Scrollable {

        private static final int HERO_H = 150;
        private static final String FOOTER =
                "对应源码：data/Preprocessor.java · nn/Network.java · nn/Trainer.java　|　完整推导见 docs/ARCHITECTURE.md";

        private final QuizBlock quiz = new QuizBlock();
        private final List<Section> sections = buildSections(quiz);
        private final Rectangle2D.Float topLink = new Rectangle2D.Float();
        private int layoutWidth = -1;
        private int contentWidth = PAGE_MAX;
        private int totalHeight = 800;
        private int footerY;
        private int[] sectionY = new int[0];

        DocView() {
            setOpaque(true);
            setBackground(Theme.BG);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (topLink.contains(e.getX(), e.getY())) {
                        scrollToTop();
                        return;
                    }
                    if (quiz.click(e.getX(), e.getY())) {
                        repaint();
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean hand = quiz.hover(e.getX(), e.getY())
                            || topLink.contains(e.getX(), e.getY());
                    setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
        }

        private void scrollToTop() {
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (sp != null) {
                sp.getVerticalScrollBar().setValue(0);
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }

        private void reflow(int width) {
            int cw = Math.min(PAGE_MAX, Math.max(240, width));
            if (width == layoutWidth) {
                return;
            }
            layoutWidth = width;
            contentWidth = cw;
            int y = CONTENT_TOP + HERO_H + SECTION_GAP;
            sectionY = new int[sections.size()];
            for (int i = 0; i < sections.size(); i++) {
                sectionY[i] = y;
                y += sections.get(i).height(cw) + SECTION_GAP;
            }
            footerY = y + 2;
            int linkW = 118;
            int linkH = 24;
            topLink.setRect((width - linkW) / 2.0, footerY + 26, linkW, linkH);
            totalHeight = footerY + 26 + linkH + CONTENT_BOTTOM;
        }

        @Override
        public Dimension getPreferredSize() {
            int w = getWidth() > 0 ? getWidth() : PAGE_MAX;
            reflow(w);
            return new Dimension(PAGE_MAX, totalHeight);
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            int oldWidth = getWidth();
            super.setBounds(x, y, width, height);
            if (oldWidth != width) {
                revalidate();
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            if (w <= 0) {
                return;
            }
            Graphics2D g2 = Theme.quality(g);
            g2.setColor(Theme.BG);
            g2.fillRect(0, 0, w, getHeight());
            reflow(w);
            int x = (w - contentWidth) / 2;
            paintHero(g2, x, CONTENT_TOP, contentWidth);
            quiz.clearHits();
            for (int i = 0; i < sections.size(); i++) {
                sections.get(i).paint(g2, x, sectionY[i], contentWidth);
            }
            Theme.textCenter(g2, FOOTER, x + contentWidth / 2, footerY + 12,
                    F_TINY, Theme.TEXT_FAINT);
            int lx = (int) topLink.x;
            int ly = (int) topLink.y;
            int lw = (int) topLink.width;
            int lh = (int) topLink.height;
            g2.setColor(Theme.CARD_HI);
            g2.fillRoundRect(lx, ly, lw, lh, lh, lh);
            g2.setColor(Theme.BORDER_SOFT);
            g2.drawRoundRect(lx, ly, lw - 1, lh - 1, lh, lh);
            Theme.textCenter(g2, "↑ 回到顶部", lx + lw / 2, centerBaseline(ly, lh, F_TINY),
                    F_TINY, Theme.TEXT_DIM);
            g2.dispose();
        }

        private void paintHero(Graphics2D g2, int x, int y, int w) {
            paintCardBg(g2, x, y, w, HERO_H);
            Graphics2D hi = (Graphics2D) g2.create();
            hi.clip(Theme.roundClip(x + 1, y + 1, w - 2, HERO_H - 2, Theme.RADIUS - 1));
            hi.setPaint(new GradientPaint(x, y, Theme.alpha(Theme.ACCENT, 26),
                    x + w * 0.55f, y + 46, Theme.alpha(Theme.VIOLET, 10)));
            hi.fillRect(x, y, w, 46);
            hi.dispose();

            Theme.text(g2, "PRINCIPLES · 8 SECTIONS", x + 22, y + 30, F_MONO_S, Theme.ACCENT);
            Theme.text(g2, "原理课堂", x + 22, y + 66, F_HERO, Theme.TEXT);
            Theme.text(g2, "从 28×28 像素到 0–9 的概率，把这个网络拆开看。", x + 22, y + 92,
                    F_HERO_SUB, Theme.TEXT_DIM);

            String[] chips = {"784 维输入", "52,650 个参数", "97.54% 测试准确率", "纯 Java · 无框架"};
            Color[] dots = {Theme.ACCENT, Theme.VIOLET, Theme.SUCCESS, Theme.WARNING};
            int cx = x + 22;
            for (int i = 0; i < chips.length; i++) {
                paintChip(g2, cx, y + 108, chips[i], dots[i]);
                cx += chipWidth(chips[i]) + 8;
            }

            Theme.textRight(g2, "784  →  64  →  32  →  10", x + w - 24, y + 62,
                    F_MONO_BIG, Theme.ACCENT);
            Theme.textRight(g2, "输入 · 隐藏层 ×2 · 输出", x + w - 24, y + 82,
                    F_TINY, Theme.TEXT_FAINT);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(PAGE_MAX, 680);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 22;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(120, visibleRect.height - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ==================================================================
    // 页面内容
    // ==================================================================
    private static List<Section> buildSections(QuizBlock quiz) {
        List<Section> list = new ArrayList<>();

        // ---------------- 01 从像素到 784 维输入 ----------------
        Section s1 = new Section("01", "从像素到 784 维输入",
                "网络不认识「数字」，只认识 0 到 1 之间的数", Theme.ACCENT);
        s1.add(new ParaBlock(new Rich()
                .txt("一张 MNIST 图片是 28×28 的灰度图，共 ").accent("784 个像素")
                .txt("。每个像素的亮度被归一化到 0–1，按行展开成一列，就是网络第一层的输入 a[0]。")));
        s1.add(new PixelBlock());
        s1.add(new BulletBlock(
                new Rich().bold("去边框：").txt("只保留有笔画的包围盒，消除画布大小和位置的影响。"),
                new Rich().bold("等比缩放：").txt("最长边缩到 20 像素，保持长宽比，不把数字拉扁。"),
                new Rich().bold("重心居中：").txt("把墨水重心移到 (14, 14)，而不是简单按外框居中。"),
                new Rich().bold("亮度归一化：").txt("最浓的像素为 1，减少笔刷粗细与颜色差异。")));
        s1.add(new HighlightBlock(Theme.ACCENT, "记住这一点", new Rich()
                .txt("识别页右侧最左边的 28×28 缩略图，就是网络真正收到的 784 个数——不是你的画板，而是预处理之后的结果。")));
        list.add(s1);

        // ---------------- 02 全连接层与 ReLU ----------------
        Section s2 = new Section("02", "全连接层与 ReLU",
                "每一层只做两件事：加权求和，再把负数砍掉", Theme.ACCENT);
        s2.add(new ParaBlock(new Rich()
                .txt("第 l 层的每个神经元都和上一层 ").accent("全部")
                .txt("节点相连，所以叫「全连接」。计算只有两步：")));
        s2.add(new FormulaBlock("W 是权重矩阵，b 是偏置向量；ReLU 对每个元素独立作用。",
                new Rich().f("z[l] = W[l] · a[l−1] + b[l]"),
                new Rich().f("a[l] = ReLU(").fa("z[l]").f(") = max(0, z[l])")));
        s2.add(new MlpBlock());
        s2.add(new ParaBlock(new Rich()
                .txt("本项目的结构是 784 → 64 → 32 → 10，共 ").accent("52,650")
                .txt(" 个参数：").mono("784×64+64 + 64×32+32 + 32×10+10。")));
        s2.add(new ReluBlock());
        s2.add(new HighlightBlock(Theme.VIOLET, "为什么可视化里有那么多暗节点", new Rich()
                .txt("ReLU 把所有负的 z 截成 0，只有一部分神经元真正被激活——这叫").accent("稀疏激活")
                .txt("。越深的层，越像是在组合前一层的简单特征。")));
        list.add(s2);

        // ---------------- 03 Softmax 输出概率 ----------------
        Section s3 = new Section("03", "Softmax 输出概率",
                "10 个分数 → 一组和为 1 的概率", Theme.VIOLET);
        s3.add(new ParaBlock(new Rich()
                .txt("输出层的 10 个原始分数 z 还不是概率。Softmax 用指数函数把它们的差距放大，再归一化：")));
        s3.add(new FormulaBlock("减去 max z 只是数值技巧：exp 可能溢出，上下同除一个常数后概率完全不变。",
                new Rich().f("p[i] = exp(z[i] − max z) / Σ exp(z[j] − max z)")));
        s3.add(new ProbBlock());
        s3.add(new BulletBlock(
                new Rich().bold("概率和为 1：").txt("10 个数相加一定等于 1，可以直接读成「模型的押注比例」。"),
                new Rich().bold("置信度：").txt("就是最大的那个概率，91% 表示模型把大部分把握放在 3 上。"),
                new Rich().bold("低置信度：").txt("前两名往往很接近，通常意味着笔画模糊、风格陌生，或这个字本来就容易混。")));
        s3.add(new HighlightBlock(Theme.WARNING, "概率不是事实", new Rich()
                .txt("Softmax 只负责归一化，不会改变谁大谁小。低概率不等于「网络没看到」，只代表它在这份训练数据上不够确定。")));
        list.add(s3);

        // ---------------- 04 交叉熵损失 ----------------
        Section s4 = new Section("04", "交叉熵损失",
                "只关心正确类别的概率", Theme.WARNING);
        s4.add(new ParaBlock(new Rich()
                .txt("训练需要一把尺子衡量「错得多离谱」。交叉熵只盯住 ").accent("正确类别")
                .txt(" 的概率：正确类概率越低，惩罚越大。")));
        s4.add(new FormulaBlock("一个 batch 内取平均；p 越接近 1，损失越接近 0。",
                new Rich().f("L = −log(p[真实标签])")));
        s4.add(new LossBlock());
        s4.add(new ParaBlock(new Rich()
                .txt("训练做的事就是让 L 变小：提高正确类的概率。注意优化的是概率分布，不是准确率——")
                .accent("准确率不可导，交叉熵可导").txt("。")));
        s4.add(new HighlightBlock(Theme.WARNING, "为什么分类更常用交叉熵", new Rich()
                .txt("错得越离谱，梯度越大，参数修正得越快；均方误差在概率接近 0 时反而学得很慢。")));
        list.add(s4);

        // ---------------- 05 反向传播与梯度下降 ----------------
        Section s5 = new Section("05", "反向传播与梯度下降",
                "误差怎么一层层传回去", Theme.VIOLET);
        s5.add(new ParaBlock(new Rich()
                .txt("知道损失还不够，还要知道每个参数该往哪调。梯度描述「参数增大一点，损失会升还是降」，链式法则把输出层的误差逐层乘回去：")));
        s5.add(new FormulaBlock("^T 表示转置，× 表示逐元素相乘。得到梯度后：θ = θ − lr · g。",
                new Rich().f("δ[L] = ").fa("p − onehot(y)"),
                new Rich().f("dW[l] = δ[l] · a[l−1]^T        db[l] = δ[l]"),
                new Rich().f("δ[l−1] = (W[l]^T · δ[l]) × ReLU'(z[l−1])")));
        s5.add(new HighlightBlock(Theme.ACCENT, "p − onehot 是怎么来的", new Rich()
                .txt("Softmax 的导数与交叉熵的 −1/p 正好抵消，输出层误差就简化成「预测概率 − 真实标签」。这是整个反向传播最漂亮的一步。")));
        s5.add(new ParaBlock(new Rich()
                .txt("拿到梯度后，参数沿着反方向走一小步。本项目默认 ").accent("Adam")
                .txt("：同时维护一阶动量（惯性）和二阶动量（按参数自适应步长），对学习率更不敏感；")
                .accent("SGD").txt(" 只沿原始梯度走，曲线更抖。")));
        s5.add(new BulletBlock(
                new Rich().bold("学习率太大：").txt("一步跨过谷底，loss 上下震荡，甚至越来越大。"),
                new Rich().bold("学习率太小：").txt("走得稳，但几十个 epoch 也到不了低点。"),
                new Rich().bold("常见做法：").txt("Adam + 1e-3 起步；在训练页把 lr 调到 1.0，看曲线怎么炸。")));
        list.add(s5);

        // ---------------- 06 数据、泛化与错误 ----------------
        Section s6 = new Section("06", "数据、泛化与错误",
                "训练集是课本，测试集是考试", Theme.SUCCESS);
        s6.add(new TilesBlock(
                new Tile("训练集", "60,000", "MNIST train", Theme.ACCENT),
                new Tile("测试集", "10,000", "MNIST test", Theme.VIOLET),
                new Tile("预训练准确率", "97.54%", "9,754 / 10,000", Theme.SUCCESS),
                new Tile("参数量", "52,650", "784-64-32-10", Theme.WARNING)));
        s6.add(new ParaBlock(new Rich()
                .txt("模型从没在测试集上更新过参数，所以测试准确率才代表").accent("泛化能力")
                .txt("。训练准确率很高、测试明显更低，说明模型在背答案（过拟合）；两者都低，则是欠拟合。")));
        s6.add(new ParaBlock(new Rich()
                .txt("十类里 8 最难（").mono("95.48%").txt("），1 最好（").mono("99.38%")
                .txt("）——数字 8 的写法差异确实更大。")));
        s6.add(new BulletBlock(
                new Rich().bold("手写风格差异：").txt("连笔、倾斜、粗细、比例都可能偏离 MNIST。"),
                new Rich().bold("预处理偏差：").txt("裁切、缩放、居中任何一步做错，输入分布就变了。"),
                new Rich().bold("模型容量：").txt("64/32 只有两层，换成 128/64 通常再高零点几个百分点。"),
                new Rich().bold("标注噪声：").txt("MNIST 里也有少量标错、或人眼都难辨的样本。")));
        s6.add(new HighlightBlock(Theme.SUCCESS, "错误样本是最好的课堂材料", new Rich()
                .txt("观察「7 被认成 2」还是「4 被认成 9」，往往能看出模型到底在看哪些笔画。别急着把它当 bug 修掉，先问：如果是人会怎么判？")));
        list.add(s6);

        // ---------------- 07 课堂演示流程 ----------------
        Section s7 = new Section("07", "课堂演示流程",
                "5 个可以当场操作的实验", Theme.ACCENT);
        s7.add(new ParaBlock(new Rich()
                .txt("照着下面 5 步走，大约 20 分钟可以完整演示一次「从输入到训练」的闭环，每一步都对应界面里真实存在的操作。")));
        s7.add(new StepsBlock(Theme.ACCENT,
                new Step("写一个数字，先别急着看答案",
                        new Rich().txt("在识别页写 0–9，观察中间 10 条概率条怎么变；再写一个容易混的（如 4 / 9），比较前两名的差距。"),
                        "3 分钟"),
                new Step("点开一个隐藏层神经元",
                        new Rich().txt("点击右侧 64 / 32 层里任意节点，查看它和上一层的权重；第一层节点的权重正好是一张 28×28 热图。问学生：它偏爱竖线、圆弧还是交叉？"),
                        "4 分钟"),
                new Step("训练 1–2 个 epoch",
                        new Rich().txt("到训练页用默认参数开始训练，盯着 loss / accuracy 曲线；停止后回到识别页，对比同一笔迹的概率变化。"),
                        "5 分钟"),
                new Step("换结构、换优化器",
                        new Rich().txt("分别用 64,32 + Adam 与 128,64 + SGD 训练同样 epoch，比较曲线抖动、最终准确率和耗时。"),
                        "5 分钟"),
                new Step("故意写歪、写小、写细",
                        new Rich().txt("观察预处理预览和置信度如何变化；把数字写到画布边缘，讨论「重心居中」为什么能救回来，以及哪种风格仍然会失败。"),
                        "3 分钟")));
        s7.add(new HighlightBlock(Theme.VIOLET, "演示节奏建议", new Rich()
                .txt("先让模型出错，再解释为什么——错误比正确更能抓住注意力。每讲完一个概念，就切回识别页做一个 10 秒的小实验。")));
        list.add(s7);

        // ---------------- 08 快速测验 ----------------
        Section s8 = new Section("08", "快速测验",
                "3 道单选题 · 点击选项立即判分", Theme.VIOLET);
        s8.add(quiz);
        list.add(s8);

        return list;
    }
}
