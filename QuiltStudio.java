/*
 * Quilt Studio
 * Design a patchwork block, lay it out as a whole quilt, and get a cutting
 * list and fabric shopping list worked out for you.
 *
 * Copyright Mario Gianota 2026. All rights reserved.
 *
 * Run with:  java QuiltStudio.java
 */

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class QuiltStudio extends JFrame {

    static final String APP = "Quilt Studio";
    static final String VERSION = "1.0";
    static final String EMAIL = "mariogianota@protonmail.com";

    // =====================================================================
    //  Fabrics
    // =====================================================================

    enum Print {
        PLAIN("Plain"), DOTS("Polka dot"), STRIPE("Stripe"), GINGHAM("Gingham"),
        CROSSHATCH("Crosshatch"), SPECKLE("Speckle"), FLORAL("Ditsy floral");
        final String label;
        Print(String l) { label = l; }
        public String toString() { return label; }
    }

    static class Fabric {
        String name;
        Color color;
        Print print;
        private Paint cached;

        Fabric(String n, int rgb, Print p) { this(n, new Color(rgb), p); }
        Fabric(String n, Color c, Print p) { name = n; color = c; print = p; }

        Fabric copy() { return new Fabric(name, color, print); }
        void changed() { cached = null; }

        /** Paint in "inch space": one pattern repeat is one inch square. */
        Paint paint() {
            if (cached == null) cached = makePaint();
            return cached;
        }

        private Paint makePaint() {
            if (print == Print.PLAIN) return color;
            final int T = 48;
            BufferedImage img = new BufferedImage(T, T, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillRect(0, 0, T, T);
            Color k = contrast(color);
            g.setColor(k);
            switch (print) {
                case DOTS -> {
                    g.fill(new Ellipse2D.Double(6, 6, 11, 11));
                    g.fill(new Ellipse2D.Double(30, 30, 11, 11));
                }
                case STRIPE -> {
                    g.fillRect(0, 0, 8, T);
                    g.fillRect(24, 0, 8, T);
                }
                case GINGHAM -> {
                    g.setColor(new Color(k.getRed(), k.getGreen(), k.getBlue(), 120));
                    g.fillRect(0, 0, 12, T);
                    g.fillRect(24, 0, 12, T);
                    g.fillRect(0, 0, T, 12);
                    g.fillRect(0, 24, T, 12);
                }
                case CROSSHATCH -> {
                    g.setStroke(new BasicStroke(2f));
                    for (int i = -T; i <= T; i += 16) {
                        g.drawLine(i, 0, i + T, T);
                        g.drawLine(i + T, 0, i, T);
                    }
                }
                case SPECKLE -> {
                    Random r = new Random(color.getRGB());
                    for (int i = 0; i < 40; i++) g.fillRect(r.nextInt(T - 2), r.nextInt(T - 2), 2, 2);
                }
                case FLORAL -> {
                    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                    Color accent = Color.getHSBColor((hsb[0] + 0.15f) % 1f, Math.max(hsb[1], 0.55f), 0.85f);
                    flower(g, 14, 14, k, accent);
                    flower(g, 36, 34, k, accent);
                    g.setColor(mix(k, color, 0.4));
                    g.fillOval(36, 8, 4, 4);
                    g.fillOval(10, 36, 4, 4);
                }
                default -> { }
            }
            g.dispose();
            return new TexturePaint(img, new Rectangle2D.Double(0, 0, 1.0, 1.0));
        }

        private static void flower(Graphics2D g, double cx, double cy, Color petal, Color centre) {
            g.setColor(petal);
            for (int i = 0; i < 5; i++) {
                double a = i * Math.PI * 2 / 5;
                g.fill(new Ellipse2D.Double(cx + Math.cos(a) * 4 - 3.5, cy + Math.sin(a) * 4 - 3.5, 7, 7));
            }
            g.setColor(centre);
            g.fill(new Ellipse2D.Double(cx - 2.5, cy - 2.5, 5, 5));
        }

        String hex() { return String.format("%06X", color.getRGB() & 0xFFFFFF); }
        public String toString() { return name; }
    }

    static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    static Color contrast(Color c) {
        return luminance(c) > 0.55 ? mix(c, Color.BLACK, 0.32) : mix(c, Color.WHITE, 0.5);
    }

    // Fabric collections ---------------------------------------------------

    static final String[] COLLECTIONS = {
            "Country Cottage", "Seaside", "Autumn Harvest", "Christmas", "Amish Brights", "Scrappy Pastels"
    };

    static List<Fabric> collection(String name) {
        List<Fabric> l = new ArrayList<>();
        switch (name) {
            case "Seaside" -> {
                l.add(new Fabric("White sand", 0xF7F3EA, Print.SPECKLE));
                l.add(new Fabric("Sea blue stripe", 0x2F7FA8, Print.STRIPE));
                l.add(new Fabric("Aqua dot", 0x7FCBC4, Print.DOTS));
                l.add(new Fabric("Deep navy", 0x1D2B4F, Print.PLAIN));
                l.add(new Fabric("Coral floral", 0xE8735A, Print.FLORAL));
                l.add(new Fabric("Driftwood", 0xB9A889, Print.CROSSHATCH));
            }
            case "Autumn Harvest" -> {
                l.add(new Fabric("Oatmeal", 0xE9DFC9, Print.SPECKLE));
                l.add(new Fabric("Pumpkin dot", 0xD9692B, Print.DOTS));
                l.add(new Fabric("Cranberry floral", 0x8E2433, Print.FLORAL));
                l.add(new Fabric("Olive gingham", 0x6B6B2E, Print.GINGHAM));
                l.add(new Fabric("Chocolate", 0x4A2C20, Print.PLAIN));
                l.add(new Fabric("Gold stripe", 0xD8A630, Print.STRIPE));
            }
            case "Christmas" -> {
                l.add(new Fabric("Snow", 0xFBFAF6, Print.SPECKLE));
                l.add(new Fabric("Holly red gingham", 0xB3202C, Print.GINGHAM));
                l.add(new Fabric("Pine floral", 0x1F5C3A, Print.FLORAL));
                l.add(new Fabric("Gold", 0xCFA845, Print.DOTS));
                l.add(new Fabric("Cranberry stripe", 0x7E1622, Print.STRIPE));
                l.add(new Fabric("Midnight", 0x1B2340, Print.PLAIN));
            }
            case "Amish Brights" -> {
                l.add(new Fabric("Black", 0x161616, Print.PLAIN));
                l.add(new Fabric("Plum", 0x6E2A7A, Print.PLAIN));
                l.add(new Fabric("Teal", 0x1F8A8A, Print.PLAIN));
                l.add(new Fabric("Cherry", 0xC4243A, Print.PLAIN));
                l.add(new Fabric("Royal blue", 0x2A4CB0, Print.PLAIN));
                l.add(new Fabric("Gold", 0xE0A21E, Print.PLAIN));
            }
            case "Scrappy Pastels" -> {
                l.add(new Fabric("Ivory", 0xFAF5E8, Print.SPECKLE));
                l.add(new Fabric("Blush dot", 0xF2B8C2, Print.DOTS));
                l.add(new Fabric("Mint gingham", 0xB8E0C8, Print.GINGHAM));
                l.add(new Fabric("Lilac floral", 0xC9B8E6, Print.FLORAL));
                l.add(new Fabric("Butter stripe", 0xF5E3A0, Print.STRIPE));
                l.add(new Fabric("Sky", 0xAFCFEA, Print.CROSSHATCH));
            }
            default -> { // Country Cottage
                l.add(new Fabric("Cream", 0xF4ECD8, Print.SPECKLE));
                l.add(new Fabric("Rose dot", 0xC9566B, Print.DOTS));
                l.add(new Fabric("Sage gingham", 0x8FA77A, Print.GINGHAM));
                l.add(new Fabric("Navy floral", 0x2E3F66, Print.FLORAL));
                l.add(new Fabric("Mustard", 0xD9A441, Print.PLAIN));
                l.add(new Fabric("Brown stripe", 0x7A5232, Print.STRIPE));
            }
        }
        return l;
    }

    // =====================================================================
    //  Patches and blocks
    // =====================================================================

    enum Shape {
        SOLID("Plain square"), HST("Half-square triangle"), QST("Quarter-square triangle"), RECT("Two rectangles");
        final String label;
        Shape(String l) { label = l; }
    }

    /** One patch in the block grid. f[] holds fabric numbers for each piece. */
    static class Cell {
        Shape shape = Shape.SOLID;
        int rot;              // HST: corner of piece 0 (0=TL,1=TR,2=BR,3=BL). RECT: edge of piece 0 (0=top,1=right,2=bottom,3=left)
        int[] f = new int[4]; // QST: 0=top,1=right,2=bottom,3=left

        Cell copy() {
            Cell c = new Cell();
            c.shape = shape; c.rot = rot; c.f = f.clone();
            return c;
        }

        int pieces() {
            return switch (shape) { case SOLID -> 1; case HST, RECT -> 2; case QST -> 4; };
        }

        void rotateCW() {
            switch (shape) {
                case HST, RECT -> rot = (rot + 1) % 4;
                case QST -> { int[] o = f.clone(); for (int i = 0; i < 4; i++) f[i] = o[(i + 3) % 4]; }
                default -> { }
            }
        }

        Cell mirrorLR() {
            Cell c = copy();
            switch (shape) {
                case HST -> c.rot = new int[]{1, 0, 3, 2}[rot];
                case RECT -> c.rot = new int[]{0, 3, 2, 1}[rot];
                case QST -> { c.f[1] = f[3]; c.f[3] = f[1]; }
                default -> { }
            }
            return c;
        }

        Cell mirrorTB() {
            Cell c = copy();
            switch (shape) {
                case HST -> c.rot = new int[]{3, 2, 1, 0}[rot];
                case RECT -> c.rot = new int[]{2, 1, 0, 3}[rot];
                case QST -> { c.f[0] = f[2]; c.f[2] = f[0]; }
                default -> { }
            }
            return c;
        }

        /** Which piece is under fractional position (fx, fy) inside the patch? */
        int pieceAt(double fx, double fy) {
            switch (shape) {
                case HST: {
                    boolean a = switch (rot) {
                        case 0 -> fx + fy < 1;
                        case 1 -> fy < fx;
                        case 2 -> fx + fy > 1;
                        default -> fy > fx;
                    };
                    return a ? 0 : 1;
                }
                case RECT: {
                    boolean a = switch (rot) {
                        case 0 -> fy < 0.5;
                        case 1 -> fx > 0.5;
                        case 2 -> fy > 0.5;
                        default -> fx < 0.5;
                    };
                    return a ? 0 : 1;
                }
                case QST:
                    if (fy < fx && fy < 1 - fx) return 0;
                    if (fx > fy && fx > 1 - fy) return 1;
                    if (fy > fx && fy > 1 - fx) return 2;
                    return 3;
                default:
                    return 0;
            }
        }

        /** Outline of piece i for a patch at (x, y) with side s. */
        Shape2 piece(int i, double x, double y, double s) {
            double[][] p = {{x, y}, {x + s, y}, {x + s, y + s}, {x, y + s}};
            Path2D path = new Path2D.Double();
            switch (shape) {
                case SOLID -> path.append(new Rectangle2D.Double(x, y, s, s), false);
                case HST -> {
                    int[] idx = i == 0
                            ? new int[]{(rot + 3) % 4, rot, (rot + 1) % 4}
                            : new int[]{(rot + 1) % 4, (rot + 2) % 4, (rot + 3) % 4};
                    poly(path, p[idx[0]], p[idx[1]], p[idx[2]]);
                }
                case QST -> poly(path, p[i], p[(i + 1) % 4], new double[]{x + s / 2, y + s / 2});
                case RECT -> {
                    int edge = i == 0 ? rot : (rot + 2) % 4;
                    Rectangle2D r = switch (edge) {
                        case 0 -> new Rectangle2D.Double(x, y, s, s / 2);
                        case 1 -> new Rectangle2D.Double(x + s / 2, y, s / 2, s);
                        case 2 -> new Rectangle2D.Double(x, y + s / 2, s, s / 2);
                        default -> new Rectangle2D.Double(x, y, s / 2, s);
                    };
                    path.append(r, false);
                }
            }
            return new Shape2(path);
        }

        private static void poly(Path2D path, double[]... pts) {
            path.moveTo(pts[0][0], pts[0][1]);
            for (int k = 1; k < pts.length; k++) path.lineTo(pts[k][0], pts[k][1]);
            path.closePath();
        }
    }

    /** Thin wrapper so the geometry type is distinct from the Shape enum. */
    record Shape2(Path2D path) { }

    static class Block {
        int n;          // patches across (and down)
        double size;    // finished size in inches
        Cell[][] cells;

        Block(int n, double size) {
            this.n = n; this.size = size;
            cells = new Cell[n][n];
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) cells[r][c] = new Cell();
        }

        Block copy() {
            Block b = new Block(n, size);
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) b.cells[r][c] = cells[r][c].copy();
            return b;
        }

        Block resized(int nn) {
            Block b = new Block(nn, size);
            for (int r = 0; r < nn; r++)
                for (int c = 0; c < nn; c++)
                    b.cells[r][c] = (r < n && c < n) ? cells[r][c].copy() : new Cell();
            return b;
        }

        double patch() { return size / n; }
    }

    static Cell solid(int a) { Cell c = new Cell(); Arrays.fill(c.f, a); return c; }
    static Cell hst(int rot, int a, int b) { Cell c = solid(a); c.shape = Shape.HST; c.rot = rot; c.f[1] = b; return c; }
    static Cell rect(int rot, int a, int b) { Cell c = solid(a); c.shape = Shape.RECT; c.rot = rot; c.f[1] = b; return c; }
    static Cell qst(int t, int r, int b, int l) { Cell c = new Cell(); c.shape = Shape.QST; c.f = new int[]{t, r, b, l}; return c; }

    /** Place a patch and its three quarter-turn copies around the block centre. */
    static void rot4(Block bl, int r, int c, Cell cell) {
        for (int k = 0; k < 4; k++) {
            bl.cells[r][c] = cell.copy();
            int nr = c, nc = bl.n - 1 - r;
            r = nr; c = nc;
            cell = cell.copy();
            cell.rotateCW();
        }
    }

    static final String[] CLASSICS = {
            "Nine Patch", "Shoo Fly", "Churn Dash", "Friendship Star", "Ohio Star", "Sawtooth Star",
            "Pinwheel", "Square in a Square", "Hourglass", "Rail Fence", "Flying Geese"
    };

    static Block classic(String name, double size) {
        Block b;
        switch (name) {
            case "Nine Patch" -> {
                b = new Block(3, size);
                for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) b.cells[r][c] = solid((r + c) % 2 == 0 ? 1 : 0);
            }
            case "Shoo Fly" -> {
                b = new Block(3, size);
                rot4(b, 0, 0, hst(0, 0, 1));
                rot4(b, 0, 1, solid(0));
                b.cells[1][1] = solid(2);
            }
            case "Churn Dash" -> {
                b = new Block(3, size);
                rot4(b, 0, 0, hst(0, 0, 3));
                rot4(b, 0, 1, rect(0, 0, 3));
                b.cells[1][1] = solid(0);
            }
            case "Friendship Star" -> {
                b = new Block(3, size);
                rot4(b, 0, 0, solid(0));
                rot4(b, 0, 1, hst(3, 1, 0));
                b.cells[1][1] = solid(1);
            }
            case "Ohio Star" -> {
                b = new Block(3, size);
                rot4(b, 0, 0, solid(0));
                rot4(b, 0, 1, qst(0, 1, 0, 1));
                b.cells[1][1] = solid(2);
            }
            case "Sawtooth Star" -> {
                b = new Block(4, size);
                rot4(b, 0, 0, solid(0));
                rot4(b, 0, 1, hst(0, 1, 0));
                rot4(b, 0, 2, hst(1, 1, 0));
                rot4(b, 1, 1, solid(3));
            }
            case "Pinwheel" -> {
                b = new Block(2, size);
                rot4(b, 0, 0, hst(1, 1, 0));
            }
            case "Square in a Square" -> {
                b = new Block(2, size);
                rot4(b, 0, 0, hst(0, 0, 2));
            }
            case "Hourglass" -> {
                b = new Block(2, size);
                rot4(b, 0, 0, qst(0, 3, 0, 3));
            }
            case "Rail Fence" -> {
                b = new Block(2, size);
                rot4(b, 0, 0, rect(0, 1, 2));
            }
            case "Flying Geese" -> {
                b = new Block(4, size);
                int[] goose = {1, 3, 4, 2};
                for (int r = 0; r < 4; r++)
                    for (int half = 0; half < 2; half++) {
                        b.cells[r][half * 2] = hst(0, 0, goose[r]);
                        b.cells[r][half * 2 + 1] = hst(1, 0, goose[r]);
                    }
            }
            default -> b = new Block(3, size);
        }
        return b;
    }

    // =====================================================================
    //  Quilt layout
    // =====================================================================

    enum Layout {
        REPEAT("Straight repeat"), ROTATE("Turn every other block"), FOURWAY("Four-way turn"),
        MIRROR("Mirror (kaleidoscope)"), ALTERNATE("Alternate with plain blocks");
        final String label;
        Layout(String l) { label = l; }
        public String toString() { return label; }
    }

    static class QuiltSettings {
        int rows = 5, cols = 4;
        Layout layout = Layout.REPEAT;
        double sash = 2, border = 4;
        int sashFab = 0, cornerFab = 4, borderFab = 3, bindFab = 1, plainFab = 0;

        QuiltSettings copy() {
            QuiltSettings q = new QuiltSettings();
            q.rows = rows; q.cols = cols; q.layout = layout; q.sash = sash; q.border = border;
            q.sashFab = sashFab; q.cornerFab = cornerFab; q.borderFab = borderFab; q.bindFab = bindFab; q.plainFab = plainFab;
            return q;
        }

        double centreW(Block b) { return cols * b.size + (cols - 1) * sash; }
        double centreH(Block b) { return rows * b.size + (rows - 1) * sash; }
        double width(Block b) { return centreW(b) + 2 * border; }
        double height(Block b) { return centreH(b) + 2 * border; }
    }

    static final String[][] SIZES = {
            {"Cot / baby", "36", "48"}, {"Lap / throw", "50", "65"}, {"Single bed", "66", "90"},
            {"Double bed", "84", "92"}, {"King bed", "104", "100"}, {"Wall hanging", "36", "36"}
    };

    // =====================================================================
    //  Drawing (everything is drawn in inches; callers scale the Graphics)
    // =====================================================================

    static final class Render {
        static Fabric fab(List<Fabric> fabs, int i) {
            return fabs.get(Math.floorMod(i, fabs.size()));
        }

        static void fill(Graphics2D g, java.awt.Shape s, Paint p, double px) {
            g.setPaint(p);
            g.fill(s);
            g.setStroke(new BasicStroke((float) (px * 0.9)));
            g.draw(s); // closes hairline gaps between neighbouring pieces
        }

        static void drawCell(Graphics2D g, Cell c, double x, double y, double s, List<Fabric> fabs, boolean seams, double px) {
            int n = c.pieces();
            for (int i = 0; i < n; i++) fill(g, c.piece(i, x, y, s).path(), fab(fabs, c.f[i]).paint(), px);
            if (seams) {
                g.setPaint(new Color(0, 0, 0, 70));
                g.setStroke(new BasicStroke((float) px));
                for (int i = 0; i < n; i++) g.draw(c.piece(i, x, y, s).path());
            }
        }

        static void drawBlock(Graphics2D g, Block b, double x, double y, double size, List<Fabric> fabs, boolean seams, double px) {
            double cs = size / b.n;
            for (int r = 0; r < b.n; r++)
                for (int c = 0; c < b.n; c++)
                    drawCell(g, b.cells[r][c], x + c * cs, y + r * cs, cs, fabs, seams, px);
        }

        static void drawQuilt(Graphics2D g, Block b, QuiltSettings q, List<Fabric> fabs, boolean seams, double px, boolean binding) {
            double B = b.size, s = q.sash, bw = q.border;
            double cw = q.centreW(b), ch = q.centreH(b), W = cw + 2 * bw, H = ch + 2 * bw;
            Color seam = new Color(0, 0, 0, 70);

            if (bw > 0) fill(g, new Rectangle2D.Double(0, 0, W, H), fab(fabs, q.borderFab).paint(), px);
            if (s > 0) {
                fill(g, new Rectangle2D.Double(bw, bw, cw, ch), fab(fabs, q.sashFab).paint(), px);
                if (q.cornerFab >= 0)
                    for (int i = 0; i < q.rows - 1; i++)
                        for (int j = 0; j < q.cols - 1; j++)
                            fill(g, new Rectangle2D.Double(bw + (j + 1) * B + j * s, bw + (i + 1) * B + i * s, s, s),
                                    fab(fabs, q.cornerFab).paint(), px);
            }
            for (int r = 0; r < q.rows; r++) {
                for (int c = 0; c < q.cols; c++) {
                    double x = bw + c * (B + s), y = bw + r * (B + s);
                    if (q.layout == Layout.ALTERNATE && (r + c) % 2 == 1) {
                        Rectangle2D plain = new Rectangle2D.Double(x, y, B, B);
                        fill(g, plain, fab(fabs, q.plainFab).paint(), px);
                        if (seams) { g.setPaint(seam); g.setStroke(new BasicStroke((float) px)); g.draw(plain); }
                        continue;
                    }
                    int turns = 0; double sx = 1, sy = 1;
                    switch (q.layout) {
                        case ROTATE -> turns = (r + c) % 2;
                        case FOURWAY -> turns = new int[]{0, 1, 3, 2}[(r % 2) * 2 + (c % 2)];
                        case MIRROR -> { sx = c % 2 == 1 ? -1 : 1; sy = r % 2 == 1 ? -1 : 1; }
                        default -> { }
                    }
                    AffineTransform old = g.getTransform();
                    g.translate(x + B / 2, y + B / 2);
                    g.rotate(turns * Math.PI / 2);
                    g.scale(sx, sy);
                    g.translate(-B / 2, -B / 2);
                    drawBlock(g, b, 0, 0, B, fabs, seams, px);
                    g.setTransform(old);
                }
            }
            if (seams && (s > 0 || bw > 0)) {
                g.setPaint(seam);
                g.setStroke(new BasicStroke((float) px));
                if (bw > 0) g.draw(new Rectangle2D.Double(bw, bw, cw, ch));
                if (s > 0) {
                    for (int r = 0; r < q.rows; r++)
                        for (int c = 0; c < q.cols; c++)
                            g.draw(new Rectangle2D.Double(bw + c * (B + s), bw + r * (B + s), B, B));
                    if (q.cornerFab >= 0)
                        for (int i = 0; i < q.rows - 1; i++)
                            for (int j = 0; j < q.cols - 1; j++)
                                g.draw(new Rectangle2D.Double(bw + (j + 1) * B + j * s, bw + (i + 1) * B + i * s, s, s));
                }
            }
            if (binding) {
                double t = 0.375;
                Area band = new Area(new Rectangle2D.Double(0, 0, W, H));
                band.subtract(new Area(new Rectangle2D.Double(t, t, W - 2 * t, H - 2 * t)));
                fill(g, band, fab(fabs, q.bindFab).paint(), px);
                g.setPaint(new Color(0, 0, 0, 90));
                g.setStroke(new BasicStroke((float) px));
                g.draw(new Rectangle2D.Double(t, t, W - 2 * t, H - 2 * t));
            }
        }

        static BufferedImage quiltImage(Block b, QuiltSettings q, List<Fabric> fabs, boolean seams, double ppi) {
            int w = (int) Math.ceil(q.width(b) * ppi), h = (int) Math.ceil(q.height(b) * ppi);
            BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            hints(g);
            g.scale(ppi, ppi);
            drawQuilt(g, b, q, fabs, seams, 1 / ppi, true);
            g.dispose();
            return img;
        }

        static BufferedImage blockImage(Block b, List<Fabric> fabs, boolean seams, double ppi) {
            int w = (int) Math.ceil(b.size * ppi);
            BufferedImage img = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            hints(g);
            g.scale(ppi, ppi);
            drawBlock(g, b, 0, 0, b.size, fabs, seams, 1 / ppi);
            g.dispose();
            return img;
        }

        static void hints(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    // =====================================================================
    //  Cutting list and fabric requirements
    // =====================================================================

    static final class Cutting {
        static final class Cut {
            final int fab; final double h, l; int count; final String what;
            Cut(int fab, double h, double l, int count, String what) { this.fab = fab; this.h = h; this.l = l; this.count = count; this.what = what; }
        }

        /** Round a cut size up to the next 1/8 inch. */
        static double up8(double x) { return Math.ceil(x * 8 - 1e-9) / 8.0; }

        static String inches(double x) {
            long e = Math.round(x * 8);
            long whole = e / 8, rem = e % 8;
            String frac = switch ((int) rem) {
                case 1 -> "1/8"; case 2 -> "1/4"; case 3 -> "3/8"; case 4 -> "1/2";
                case 5 -> "5/8"; case 6 -> "3/4"; case 7 -> "7/8"; default -> "";
            };
            if (whole == 0 && !frac.isEmpty()) return frac + "\"";
            return whole + (frac.isEmpty() ? "" : " " + frac) + "\"";
        }

        static String yards(double y) {
            long e = Math.round(y * 8);
            long whole = e / 8, rem = e % 8;
            String frac = switch ((int) rem) {
                case 1 -> "1/8"; case 2 -> "1/4"; case 3 -> "3/8"; case 4 -> "1/2";
                case 5 -> "5/8"; case 6 -> "3/4"; case 7 -> "7/8"; default -> "";
            };
            String s = whole == 0 ? frac : whole + (frac.isEmpty() ? "" : " " + frac);
            return s + " yd";
        }

        static String cm(double in) { return Math.round(in * 2.54) + " cm"; }

        static String report(Block b, QuiltSettings q, List<Fabric> fabs, double wof) {
            List<Cut> cuts = new ArrayList<>();
            double B = b.size, f = b.patch(), s = q.sash, bw = q.border;
            int total = q.rows * q.cols;
            int plain = q.layout == Layout.ALTERNATE ? total / 2 : 0;
            int pieced = total - plain;
            double cw = q.centreW(b), ch = q.centreH(b), W = cw + 2 * bw, H = ch + 2 * bw;
            int nf = fabs.size();

            java.util.function.IntUnaryOperator norm = i -> Math.floorMod(i, nf);

            // --- pieces from the blocks
            Map<Long, Integer> hstPairs = new TreeMap<>();
            int[] qstTri = new int[nf];
            for (Cell[] row : b.cells) {
                for (Cell c : row) {
                    int a = norm.applyAsInt(c.f[0]), bb = norm.applyAsInt(c.f[1]);
                    switch (c.shape) {
                        case SOLID -> add(cuts, a, f + 0.5, f + 0.5, pieced, "squares");
                        case HST -> {
                            if (a == bb) add(cuts, a, f + 0.5, f + 0.5, pieced, "squares");
                            else hstPairs.merge((long) Math.min(a, bb) * 1000 + Math.max(a, bb), 1, Integer::sum);
                        }
                        case RECT -> {
                            if (a == bb) add(cuts, a, f + 0.5, f + 0.5, pieced, "squares");
                            else {
                                add(cuts, a, f / 2 + 0.5, f + 0.5, pieced, "rectangles");
                                add(cuts, bb, f / 2 + 0.5, f + 0.5, pieced, "rectangles");
                            }
                        }
                        case QST -> {
                            boolean same = true;
                            for (int k = 1; k < 4; k++) if (norm.applyAsInt(c.f[k]) != a) same = false;
                            if (same) add(cuts, a, f + 0.5, f + 0.5, pieced, "squares");
                            else for (int k = 0; k < 4; k++) qstTri[norm.applyAsInt(c.f[k])]++;
                        }
                    }
                }
            }
            for (Map.Entry<Long, Integer> e : hstPairs.entrySet()) {
                int a = (int) (e.getKey() / 1000), bb = (int) (e.getKey() % 1000);
                int sq = (int) Math.ceil(e.getValue() * (double) pieced / 2.0);
                add(cuts, a, f + 0.875, f + 0.875, sq, "squares for half-square triangles (pair with " + fabs.get(bb).name + ")");
                add(cuts, bb, f + 0.875, f + 0.875, sq, "squares for half-square triangles (pair with " + fabs.get(a).name + ")");
            }
            for (int i = 0; i < nf; i++)
                if (qstTri[i] > 0)
                    add(cuts, i, f + 1.25, f + 1.25, (int) Math.ceil(qstTri[i] * (double) pieced / 4.0),
                            "squares, cut twice on the diagonal (quarter-square triangles)");

            // --- the rest of the quilt top
            if (plain > 0) add(cuts, norm.applyAsInt(q.plainFab), B + 0.5, B + 0.5, plain, "plain alternate blocks");
            if (s > 0) {
                int sf = norm.applyAsInt(q.sashFab);
                if (q.cols > 1) add(cuts, sf, s + 0.5, B + 0.5, q.rows * (q.cols - 1), "sashing strips");
                if (q.rows > 1) {
                    if (q.cornerFab >= 0) {
                        add(cuts, sf, s + 0.5, B + 0.5, (q.rows - 1) * q.cols, "sashing strips");
                        if (q.cols > 1)
                            add(cuts, norm.applyAsInt(q.cornerFab), s + 0.5, s + 0.5, (q.rows - 1) * (q.cols - 1), "cornerstones");
                    } else {
                        add(cuts, sf, s + 0.5, cw + 0.5, q.rows - 1, "long sashing strips");
                    }
                }
            }
            if (bw > 0) {
                int bf = norm.applyAsInt(q.borderFab);
                add(cuts, bf, bw + 0.5, ch + 0.5, 2, "side borders");
                add(cuts, bf, bw + 0.5, W + 0.5, 2, "top and bottom borders");
            }
            double bindLen = 2 * (W + H) + 12;
            int bindStrips = (int) Math.ceil(bindLen / wof);
            int bindFab = norm.applyAsInt(q.bindFab);

            // --- group by fabric and work out yardage
            StringBuilder sb = new StringBuilder();
            sb.append("QUILT STUDIO — CUTTING LIST & FABRIC REQUIREMENTS\n");
            sb.append("==================================================\n\n");
            sb.append(String.format("Finished quilt:  %s × %s  (%s × %s)%n", inches(W), inches(H), cm(W), cm(H)));
            sb.append(String.format("Layout:          %d × %d blocks, %s%n", q.cols, q.rows, q.layout.label.toLowerCase()));
            sb.append(String.format("Block:           %s finished, %d × %d patches of %s%n", inches(B), b.n, b.n, inches(f)));
            sb.append(String.format("Pieced blocks:   %d%s%n", pieced, plain > 0 ? "   (plus " + plain + " plain)" : ""));
            if (s > 0) sb.append(String.format("Sashing:         %s%s%n", inches(s), q.cornerFab >= 0 ? " with cornerstones" : ""));
            if (bw > 0) sb.append(String.format("Border:          %s%n", inches(bw)));
            sb.append(String.format("Fabric width:    %s usable (WOF = width of fabric)%n%n", inches(wof)));
            sb.append("All sizes include 1/4\" seam allowances. Half-square triangles use the\n");
            sb.append("two-at-a-time method (finished size + 7/8\"); quarter-square triangles\n");
            sb.append("use finished size + 1 1/4\". Shopping amounts include about 10% extra.\n");
            if (Math.abs(f * 8 - Math.round(f * 8)) > 1e-6)
                sb.append("\nNOTE: the patch size (" + String.format("%.3f", f) + "\") isn't a neat ruler size.\n"
                        + "Try a block size that divides evenly by " + b.n + ".\n");
            sb.append("\n");

            double totalYds = 0;
            for (int fi = 0; fi < nf; fi++) {
                List<Cut> mine = new ArrayList<>();
                for (Cut c : cuts) if (c.fab == fi && c.count > 0) mine.add(c);
                boolean binds = fi == bindFab;
                if (mine.isEmpty() && !binds) continue;
                Fabric fab = fabs.get(fi);
                double length = 0;
                StringBuilder lines = new StringBuilder();
                for (Cut c : mine) {
                    double h = up8(c.h), l = up8(c.l);
                    boolean square = Math.abs(h - l) < 1e-9;
                    String piece = square ? inches(h) : inches(h) + " × " + inches(l);
                    int strips;
                    if (l <= wof) {
                        int per = Math.max(1, (int) Math.floor(wof / l));
                        strips = (int) Math.ceil(c.count / (double) per);
                        lines.append(String.format("  • Cut %d strip%s %s × WOF; cut into %d %s %s%n",
                                strips, strips == 1 ? "" : "s", inches(h), c.count, piece, c.what));
                    } else {
                        strips = (int) Math.ceil(c.count * l / wof);
                        lines.append(String.format("  • Cut %d strip%s %s × WOF; join end to end and cut %d %s long for %s%n",
                                strips, strips == 1 ? "" : "s", inches(h), c.count, inches(l), c.what));
                    }
                    length += strips * h;
                }
                if (binds) {
                    lines.append(String.format("  • Binding: cut %d strip%s 2 1/2\" × WOF, join end to end (about %s), press in half%n",
                            bindStrips, bindStrips == 1 ? "" : "s", inches(bindLen)));
                    length += bindStrips * 2.5;
                }
                double yd = Math.max(0.25, Math.ceil(length * 1.1 / 36.0 * 8 - 1e-9) / 8.0);
                double m = Math.max(0.3, Math.ceil(length * 1.1 * 0.0254 * 10 - 1e-9) / 10.0);
                totalYds += yd;
                sb.append(String.format("%s   (%s, #%s)%n", fab.name.toUpperCase(), fab.print.label, fab.hex()));
                sb.append(String.format("  Buy %s  /  %.1f m%n", yards(yd), m));
                sb.append(lines).append("\n");
            }

            // backing & batting
            double bW = W + 8, bH = H + 8;
            double backLen;
            String how;
            if (bW <= wof) { backLen = bH; how = "one width"; }
            else {
                int pv = (int) Math.ceil(bW / (wof - 1)), ph = (int) Math.ceil(bH / (wof - 1));
                double vert = pv * bH, horiz = ph * bW;
                if (vert <= horiz) { backLen = vert; how = pv + " widths seamed side by side"; }
                else { backLen = horiz; how = ph + " widths seamed across"; }
            }
            double backYd = Math.ceil(backLen / 36.0 * 8 - 1e-9) / 8.0;
            sb.append("BACKING\n");
            sb.append(String.format("  Buy %s  /  %.1f m   (%s; cut to %s × %s)%n%n",
                    yards(backYd), Math.ceil(backLen * 0.0254 * 10 - 1e-9) / 10.0, how, inches(bW), inches(bH)));
            sb.append("BATTING (WADDING)\n");
            sb.append(String.format("  One piece at least %s × %s  (%s × %s)%n%n", inches(bW), inches(bH), cm(bW), cm(bH)));
            sb.append(String.format("Quilt top fabric in total: about %s%n", yards(totalYds)));
            return sb.toString();
        }

        static void add(List<Cut> cuts, int fab, double h, double l, int count, String what) {
            if (count <= 0) return;
            for (Cut c : cuts)
                if (c.fab == fab && Math.abs(c.h - h) < 1e-9 && Math.abs(c.l - l) < 1e-9 && c.what.equals(what)) {
                    c.count += count;
                    return;
                }
            cuts.add(new Cut(fab, h, l, count, what));
        }
    }

    // =====================================================================
    //  Application state
    // =====================================================================

    enum Tool {
        PAINT("Paint", "Click or drag over patches to colour them with the chosen fabric"),
        SOLID("Square", "Turn a patch into a plain square"),
        HST("Half-square", "Make a half-square triangle — click again to turn it"),
        QST("Quarter-square", "Make a quarter-square triangle (four triangles)"),
        RECT("Rectangles", "Split a patch into two rectangles — click again to turn it"),
        ROTATE("Turn", "Turn a patch a quarter turn clockwise"),
        PICK("Pick", "Pick up the fabric from a patch");
        final String label, tip;
        Tool(String l, String t) { label = l; tip = t; }
    }

    enum Symmetry {
        NONE("No symmetry"), LR("Mirror left ↔ right"), FOUR("Mirror four ways"), ROT4("Turn four ways");
        final String label;
        Symmetry(String l) { label = l; }
        public String toString() { return label; }
    }

    final List<Fabric> fabrics = new ArrayList<>(collection("Country Cottage"));
    Block block = classic("Sawtooth Star", 12);
    final QuiltSettings quilt = new QuiltSettings();
    int current = 1;
    Tool tool = Tool.PAINT;
    Symmetry symmetry = Symmetry.NONE;
    boolean seams = true;
    double wof = 42;
    File file;
    boolean dirty;
    boolean syncing;

    final Deque<Block> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    DefaultListModel<Fabric> paletteModel = new DefaultListModel<>();
    JList<Fabric> paletteList;
    BlockEditor editor;
    QuiltView quiltView;
    RepeatPreview preview;
    JTextArea cuttingText;
    JLabel status, blockInfo;
    JSpinner gridSpin, sizeSpin, rowsSpin, colsSpin, sashSpin, borderSpin, wofSpin;
    JComboBox<Layout> layoutBox;
    JComboBox<Symmetry> symBox;
    JCheckBox seamBox;
    final List<FabricCombo> fabricCombos = new ArrayList<>();
    final Map<Tool, JToggleButton> toolButtons = new EnumMap<>(Tool.class);

    // =====================================================================
    //  Construction
    // =====================================================================

    QuiltStudio() {
        super(APP);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { quit(); }
        });

        editor = new BlockEditor();
        quiltView = new QuiltView();
        preview = new RepeatPreview();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("  Design the Block  ", blockTab());
        tabs.addTab("  Lay Out the Quilt  ", quiltTab());
        tabs.addTab("  Cutting & Shopping List  ", cuttingTab());
        tabs.addChangeListener(e -> refreshAll());

        status = new JLabel(" ");
        status.setBorder(new EmptyBorder(4, 10, 4, 10));

        JPanel root = new JPanel(new BorderLayout());
        root.add(palettePanel(), BorderLayout.WEST);
        root.add(tabs, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        setJMenuBar(menus());

        reloadPalette();
        syncControls();
        refreshAll();
        dirty = false;
        updateTitle();

        setSize(1280, 820);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------------
    //  Palette (left side)
    // ---------------------------------------------------------------------

    JComponent palettePanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new EmptyBorder(8, 8, 8, 4));
        p.setPreferredSize(new Dimension(255, 100));

        JLabel title = new JLabel("Fabrics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        p.add(title, BorderLayout.NORTH);

        paletteList = new JList<>(paletteModel);
        paletteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paletteList.setCellRenderer(new SwatchRenderer());
        paletteList.addListSelectionListener(e -> {
            if (!syncing && paletteList.getSelectedIndex() >= 0) {
                current = paletteList.getSelectedIndex();
                setStatus("Painting with " + fabrics.get(current).name);
            }
        });
        paletteList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editFabric(paletteList.locationToIndex(e.getPoint()));
            }
        });
        p.add(new JScrollPane(paletteList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
        for (JButton b : new JButton[]{
                button("Add", "Add a new fabric", this::addFabric),
                button("Edit", "Change the selected fabric", () -> editFabric(current)),
                button("Remove", "Remove the selected fabric", this::removeFabric)}) {
            b.setMargin(new Insets(2, 2, 2, 2));
            row.add(b);
        }
        buttons.add(row);

        JComboBox<String> coll = prompted("Choose…", COLLECTIONS);
        coll.setToolTipText("Swap in a whole fabric collection — your design recolours instantly");
        coll.addActionListener(e -> {
            if (coll.getSelectedIndex() <= 0) return;
            String name = (String) coll.getSelectedItem();
            fabrics.clear();
            fabrics.addAll(collection(name));
            changed(true);
            reloadPalette();
            coll.setSelectedIndex(0);
            setStatus("Swapped in the " + name + " collection");
        });
        JPanel cl = new JPanel(new BorderLayout(4, 0));
        cl.add(new JLabel("Collection:"), BorderLayout.WEST);
        cl.add(coll, BorderLayout.CENTER);
        buttons.add(cl);
        buttons.add(button("Shuffle colours", "Swap the fabrics around at random for a fresh look", this::shuffleFabrics));

        JLabel hint = new JLabel("<html><small>Double-click a fabric to change its colour or print.</small></html>");
        hint.setForeground(Color.GRAY);
        buttons.add(hint);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    class SwatchRenderer extends JPanel implements ListCellRenderer<Fabric> {
        Fabric fab; boolean sel;
        SwatchRenderer() { setPreferredSize(new Dimension(200, 38)); setOpaque(true); }
        public Component getListCellRendererComponent(JList<? extends Fabric> l, Fabric v, int i, boolean s, boolean f) {
            fab = v; sel = s;
            setBackground(s ? l.getSelectionBackground() : l.getBackground());
            setForeground(s ? l.getSelectionForeground() : l.getForeground());
            return this;
        }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            Graphics2D sw = (Graphics2D) g.create();
            sw.translate(6, 5);
            sw.clip(new RoundRectangle2D.Double(0, 0, 44, 28, 8, 8));
            sw.scale(22, 22);
            sw.setPaint(fab.paint());
            sw.fill(new Rectangle2D.Double(0, 0, 2, 2));
            sw.dispose();
            g.setColor(new Color(0, 0, 0, 80));
            g.draw(new RoundRectangle2D.Double(6, 5, 44, 28, 8, 8));
            g.setColor(getForeground());
            g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g.drawString(fab.name, 60, 18);
            g.setFont(getFont().deriveFont(10.5f));
            g.setColor(sel ? getForeground() : Color.GRAY);
            g.drawString(fab.print.label, 60, 31);
            g.dispose();
        }
    }

    void reloadPalette() {
        syncing = true;
        paletteModel.clear();
        for (Fabric f : fabrics) paletteModel.addElement(f);
        current = Math.max(0, Math.min(current, fabrics.size() - 1));
        paletteList.setSelectedIndex(current);
        for (FabricCombo fc : fabricCombos) fc.reload();
        syncing = false;
        refreshAll();
    }

    void addFabric() {
        Fabric f = new Fabric("New fabric", Color.getHSBColor((float) Math.random(), 0.5f, 0.8f), Print.PLAIN);
        if (fabricDialog(f, "Add a fabric")) {
            fabrics.add(f);
            current = fabrics.size() - 1;
            changed(true);
            reloadPalette();
        }
    }

    void editFabric(int i) {
        if (i < 0 || i >= fabrics.size()) return;
        Fabric f = fabrics.get(i);
        Fabric trial = f.copy();
        if (fabricDialog(trial, "Change fabric")) {
            f.name = trial.name; f.color = trial.color; f.print = trial.print;
            f.changed();
            changed(true);
            reloadPalette();
        }
    }

    boolean fabricDialog(Fabric f, String title) {
        JTextField name = new JTextField(f.name, 18);
        JComboBox<Print> print = new JComboBox<>(Print.values());
        print.setSelectedItem(f.print);
        final Color[] colour = {f.color};
        JComponent swatch = new JComponent() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Render.hints(g);
                Fabric t = new Fabric("", colour[0], (Print) print.getSelectedItem());
                g.scale(30, 30);
                g.setPaint(t.paint());
                g.fill(new Rectangle2D.Double(0, 0, getWidth() / 30.0, getHeight() / 30.0));
                g.dispose();
            }
        };
        swatch.setPreferredSize(new Dimension(240, 90));
        JButton pick = new JButton("Choose colour…");
        pick.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Fabric colour", colour[0]);
            if (c != null) { colour[0] = c; swatch.repaint(); }
        });
        print.addActionListener(e -> swatch.repaint());

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; p.add(new JLabel("Name:"), g);
        g.gridx = 1; p.add(name, g);
        g.gridx = 0; g.gridy = 1; p.add(new JLabel("Print:"), g);
        g.gridx = 1; p.add(print, g);
        g.gridx = 0; g.gridy = 2; p.add(new JLabel("Colour:"), g);
        g.gridx = 1; p.add(pick, g);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 2; p.add(swatch, g);

        int r = JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        f.name = name.getText().trim().isEmpty() ? "Fabric" : name.getText().trim().replace('\t', ' ');
        f.color = colour[0];
        f.print = (Print) print.getSelectedItem();
        f.changed();
        return true;
    }

    void removeFabric() {
        if (fabrics.size() <= 2) {
            JOptionPane.showMessageDialog(this, "A quilt needs at least two fabrics.", APP, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int idx = current;
        int ok = JOptionPane.showConfirmDialog(this,
                "Remove \"" + fabrics.get(idx).name + "\"?\nPatches using it will switch to another fabric.",
                APP, JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        pushUndo();
        java.util.function.IntUnaryOperator remap = i -> {
            i = Math.floorMod(i, fabrics.size());
            return i == idx ? (idx == 0 ? 0 : idx - 1) : (i > idx ? i - 1 : i);
        };
        for (Cell[] row : block.cells) for (Cell c : row) for (int k = 0; k < 4; k++) c.f[k] = remap.applyAsInt(c.f[k]);
        quilt.sashFab = remap.applyAsInt(quilt.sashFab);
        quilt.borderFab = remap.applyAsInt(quilt.borderFab);
        quilt.bindFab = remap.applyAsInt(quilt.bindFab);
        quilt.plainFab = remap.applyAsInt(quilt.plainFab);
        if (quilt.cornerFab == idx) quilt.cornerFab = -1;
        else if (quilt.cornerFab > idx) quilt.cornerFab--;
        fabrics.remove(idx);
        changed(true);
        reloadPalette();
    }

    void shuffleFabrics() {
        Collections.shuffle(fabrics);
        changed(true);
        reloadPalette();
        setStatus("Fabrics shuffled — press Shuffle again for another look");
    }

    // ---------------------------------------------------------------------
    //  Block tab
    // ---------------------------------------------------------------------

    JComponent blockTab() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        ButtonGroup bg = new ButtonGroup();
        for (Tool t : Tool.values()) {
            JToggleButton b = new JToggleButton(t.label, new ToolIcon(t));
            b.setToolTipText(t.tip);
            b.setFocusable(false);
            b.addActionListener(e -> { tool = t; setStatus(t.tip); });
            bg.add(b);
            tb.add(b);
            toolButtons.put(t, b);
        }
        toolButtons.get(Tool.PAINT).setSelected(true);
        tb.addSeparator();
        tb.add(button("Undo", "Undo (Ctrl+Z)", this::doUndo));
        tb.add(button("Redo", "Redo (Ctrl+Y)", this::doRedo));

        JPanel side = new JPanel(new GridBagLayout());
        side.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = gbc();

        gridSpin = new JSpinner(new SpinnerNumberModel(4, 2, 8, 1));
        gridSpin.addChangeListener(e -> {
            if (syncing) return;
            pushUndo();
            block = block.resized((Integer) gridSpin.getValue());
            changed(true);
        });
        sizeSpin = new JSpinner(new SpinnerNumberModel(12.0, 3.0, 30.0, 0.5));
        sizeSpin.addChangeListener(e -> {
            if (syncing) return;
            block.size = (Double) sizeSpin.getValue();
            changed(true);
        });
        symBox = new JComboBox<>(Symmetry.values());
        symBox.setToolTipText("Copy each change you make around the block automatically");
        symBox.addActionListener(e -> symmetry = (Symmetry) symBox.getSelectedItem());

        JComboBox<String> classics = prompted("Choose a block…", CLASSICS);
        classics.addActionListener(e -> {
            if (classics.getSelectedIndex() <= 0) return;
            String name = (String) classics.getSelectedItem();
            pushUndo();
            block = classic(name, block.size);
            syncControls();
            changed(true);
            classics.setSelectedIndex(0);
            setStatus("Loaded the classic " + name + " block");
        });

        seamBox = new JCheckBox("Show seam lines", seams);
        seamBox.addActionListener(e -> { seams = seamBox.isSelected(); refreshAll(); });

        blockInfo = new JLabel(" ");
        blockInfo.setForeground(new Color(90, 90, 90));

        section(side, g, "Block");
        row(side, g, "Patches across:", gridSpin);
        row(side, g, "Finished size (in):", sizeSpin);
        full(side, g, blockInfo);
        section(side, g, "Start from a classic");
        full(side, g, classics);
        full(side, g, button("Surprise me!", "Make up a random symmetrical block", this::surprise));
        full(side, g, button("Clear block", "Fill the whole block with the chosen fabric", this::clearBlock));
        section(side, g, "Helpers");
        row(side, g, "Symmetry:", symBox);
        full(side, g, seamBox);
        section(side, g, "How it repeats");
        g.weighty = 1; g.fill = GridBagConstraints.BOTH;
        full(side, g, preview);

        JPanel p = new JPanel(new BorderLayout());
        p.add(tb, BorderLayout.NORTH);
        p.add(editor, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(side, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setPreferredSize(new Dimension(290, 100));
        p.add(sp, BorderLayout.EAST);
        return p;
    }

    void clearBlock() {
        pushUndo();
        for (int r = 0; r < block.n; r++) for (int c = 0; c < block.n; c++) block.cells[r][c] = solid(current);
        changed(true);
    }

    void surprise() {
        pushUndo();
        Random rnd = new Random();
        int n = block.n;
        int colours = Math.min(fabrics.size(), 3 + rnd.nextInt(2));
        List<Integer> pal = new ArrayList<>();
        for (int i = 0; i < fabrics.size(); i++) pal.add(i);
        Collections.shuffle(pal, rnd);
        // the lightest (or occasionally the darkest) fabric makes the best background
        Comparator<Integer> byLight = Comparator.comparingDouble(i -> luminance(fabrics.get(i).color));
        int bgFab = rnd.nextInt(5) == 0 ? Collections.min(pal, byLight) : Collections.max(pal, byLight);
        pal.remove(Integer.valueOf(bgFab));
        pal.add(0, bgFab);
        java.util.function.IntSupplier pick = () -> rnd.nextInt(10) < 4 ? bgFab : pal.get(1 + rnd.nextInt(colours - 1));
        java.util.function.Supplier<Cell> make = () -> {
            int roll = rnd.nextInt(100);
            int a = pick.getAsInt(), b = pick.getAsInt();
            if (a == b) b = (a == bgFab) ? pal.get(1) : bgFab;
            if (roll < 30) return solid(a);
            if (roll < 72) return hst(rnd.nextInt(4), a, b);
            if (roll < 88) return qst(a, b, rnd.nextBoolean() ? a : pick.getAsInt(), rnd.nextBoolean() ? b : pick.getAsInt());
            return rect(rnd.nextInt(4), a, b);
        };
        Block nb = new Block(n, block.size);
        if (rnd.nextInt(10) < 7) {
            for (int r = 0; r < (n + 1) / 2; r++)
                for (int c = 0; c < n / 2; c++) rot4(nb, r, c, make.get());
            if (n % 2 == 1) nb.cells[n / 2][n / 2] = solid(pick.getAsInt());
        } else {
            int h = (n + 1) / 2;
            for (int r = 0; r < h; r++)
                for (int c = 0; c < h; c++) {
                    Cell cell = (n % 2 == 1 && (r == n / 2 || c == n / 2)) ? solid(pick.getAsInt()) : make.get();
                    nb.cells[r][c] = cell;
                    nb.cells[r][n - 1 - c] = cell.mirrorLR();
                    nb.cells[n - 1 - r][c] = cell.mirrorTB();
                    nb.cells[n - 1 - r][n - 1 - c] = cell.mirrorLR().mirrorTB();
                }
        }
        block = nb;
        changed(true);
        setStatus("A brand-new block — press Surprise me! again for another");
    }

    // ---------------------------------------------------------------------
    //  Quilt tab
    // ---------------------------------------------------------------------

    JComponent quiltTab() {
        JPanel side = new JPanel(new GridBagLayout());
        side.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = gbc();

        layoutBox = new JComboBox<>(Layout.values());
        layoutBox.addActionListener(e -> { if (!syncing) { quilt.layout = (Layout) layoutBox.getSelectedItem(); changed(false); } });
        colsSpin = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
        colsSpin.addChangeListener(e -> { if (!syncing) { quilt.cols = (Integer) colsSpin.getValue(); changed(false); } });
        rowsSpin = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        rowsSpin.addChangeListener(e -> { if (!syncing) { quilt.rows = (Integer) rowsSpin.getValue(); changed(false); } });
        sashSpin = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 8.0, 0.5));
        sashSpin.addChangeListener(e -> { if (!syncing) { quilt.sash = (Double) sashSpin.getValue(); changed(false); } });
        borderSpin = new JSpinner(new SpinnerNumberModel(4.0, 0.0, 16.0, 0.5));
        borderSpin.addChangeListener(e -> { if (!syncing) { quilt.border = (Double) borderSpin.getValue(); changed(false); } });

        String[] sizeNames = new String[SIZES.length];
        for (int k = 0; k < SIZES.length; k++) sizeNames[k] = SIZES[k][0] + "  (about " + SIZES[k][1] + "\" × " + SIZES[k][2] + "\")";
        JComboBox<String> sizes = prompted("Choose a size…", sizeNames);
        sizes.addActionListener(e -> {
            int i = sizes.getSelectedIndex() - 1;
            if (i < 0) return;
            sizes.setSelectedIndex(0);
            double tw = Double.parseDouble(SIZES[i][1]), th = Double.parseDouble(SIZES[i][2]);
            double unit = block.size + quilt.sash;
            quilt.cols = (int) Math.max(1, Math.min(20, Math.round((tw - 2 * quilt.border + quilt.sash) / unit)));
            quilt.rows = (int) Math.max(1, Math.min(20, Math.round((th - 2 * quilt.border + quilt.sash) / unit)));
            syncControls();
            changed(false);
            setStatus(String.format("%d × %d blocks gives %s × %s", quilt.cols, quilt.rows,
                    Cutting.inches(quilt.width(block)), Cutting.inches(quilt.height(block))));
        });

        section(side, g, "Arrangement");
        row(side, g, "Layout:", layoutBox);
        row(side, g, "Blocks across:", colsSpin);
        row(side, g, "Blocks down:", rowsSpin);
        row(side, g, "Size for:", sizes);
        section(side, g, "Sashing (strips between blocks)");
        row(side, g, "Width (in):", sashSpin);
        row(side, g, "Fabric:", new FabricCombo(false, () -> quilt.sashFab, v -> quilt.sashFab = v));
        row(side, g, "Cornerstones:", new FabricCombo(true, () -> quilt.cornerFab, v -> quilt.cornerFab = v));
        section(side, g, "Border");
        row(side, g, "Width (in):", borderSpin);
        row(side, g, "Fabric:", new FabricCombo(false, () -> quilt.borderFab, v -> quilt.borderFab = v));
        section(side, g, "Finishing");
        row(side, g, "Binding:", new FabricCombo(false, () -> quilt.bindFab, v -> quilt.bindFab = v));
        row(side, g, "Plain blocks:", new FabricCombo(false, () -> quilt.plainFab, v -> quilt.plainFab = v));
        g.weighty = 1;
        full(side, g, new JLabel(" "));

        JPanel p = new JPanel(new BorderLayout());
        p.add(quiltView, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(side, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setPreferredSize(new Dimension(320, 100));
        p.add(sp, BorderLayout.EAST);
        return p;
    }

    class FabricCombo extends JComboBox<String> {
        final boolean allowNone;
        final java.util.function.IntSupplier get;
        final java.util.function.IntConsumer set;

        FabricCombo(boolean allowNone, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
            this.allowNone = allowNone; this.get = get; this.set = set;
            fabricCombos.add(this);
            reload();
            addActionListener(e -> {
                if (syncing || getSelectedIndex() < 0) return;
                set.accept(getSelectedIndex() - (allowNone ? 1 : 0));
                changed(false);
            });
        }

        void reload() {
            boolean was = syncing;
            syncing = true;
            removeAllItems();
            if (allowNone) addItem("None");
            for (Fabric f : fabrics) addItem(f.name);
            int v = get.getAsInt();
            int idx = v < 0 ? (allowNone ? 0 : 0) : Math.floorMod(v, fabrics.size()) + (allowNone ? 1 : 0);
            setSelectedIndex(Math.min(idx, getItemCount() - 1));
            syncing = was;
        }
    }

    // ---------------------------------------------------------------------
    //  Cutting tab
    // ---------------------------------------------------------------------

    JComponent cuttingTab() {
        cuttingText = new JTextArea();
        cuttingText.setEditable(false);
        cuttingText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        cuttingText.setMargin(new Insets(12, 14, 12, 14));

        wofSpin = new JSpinner(new SpinnerNumberModel(42.0, 36.0, 60.0, 1.0));
        wofSpin.setToolTipText("Usable width of your fabric once the selvedges are trimmed. Most quilting cotton is 42\".");
        wofSpin.addChangeListener(e -> { if (!syncing) { wof = (Double) wofSpin.getValue(); changed(false); } });

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel("  Usable fabric width (in): "));
        wofSpin.setMaximumSize(new Dimension(80, 30));
        tb.add(wofSpin);
        tb.addSeparator();
        tb.add(button("Copy", "Copy the list to the clipboard", () -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(cuttingText.getText()), null);
            setStatus("Cutting list copied");
        }));
        tb.add(button("Save as text…", "Save the list as a text file", this::saveCuttingText));
        tb.add(button("Print…", "Print the quilt picture and cutting list", this::print));

        JPanel p = new JPanel(new BorderLayout());
        p.add(tb, BorderLayout.NORTH);
        p.add(new JScrollPane(cuttingText), BorderLayout.CENTER);
        return p;
    }

    // ---------------------------------------------------------------------
    //  Layout helpers
    // ---------------------------------------------------------------------

    static GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 0;
        g.insets = new Insets(3, 2, 3, 2);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        return g;
    }

    static void section(JPanel p, GridBagConstraints g, String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13.5f));
        l.setBorder(new EmptyBorder(g.gridy == 0 ? 0 : 12, 0, 2, 0));
        full(p, g, l);
    }

    static void row(JPanel p, GridBagConstraints g, String label, JComponent c) {
        g.gridx = 0; g.gridwidth = 1; g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1;
        p.add(c, g);
        g.gridy++;
    }

    static void full(JPanel p, GridBagConstraints g, JComponent c) {
        g.gridx = 0; g.gridwidth = 2; g.weightx = 1;
        p.add(c, g);
        g.gridy++;
        g.gridwidth = 1;
    }

    static JComboBox<String> prompted(String prompt, String[] items) {
        JComboBox<String> c = new JComboBox<>();
        c.addItem(prompt);
        for (String i : items) c.addItem(i);
        c.setSelectedIndex(0);
        return c;
    }

    static JButton button(String text, String tip, Runnable r) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    // ---------------------------------------------------------------------
    //  State changes
    // ---------------------------------------------------------------------

    void changed(boolean blockChanged) {
        dirty = true;
        updateTitle();
        refreshAll();
    }

    void refreshAll() {
        if (editor == null) return;
        editor.repaint();
        preview.repaint();
        quiltView.repaint();
        if (blockInfo != null) {
            double f = block.patch();
            boolean neat = Math.abs(f * 8 - Math.round(f * 8)) < 1e-6;
            blockInfo.setText("<html>Each patch finishes at <b>" + (neat ? Cutting.inches(f) : String.format("%.2f\"", f)) + "</b>"
                    + (neat ? "" : "<br><font color='#B04020'>Not a neat ruler size — try a block size that divides by " + block.n + ".</font>")
                    + "</html>");
        }
        if (cuttingText != null) {
            int caret = cuttingText.getCaretPosition();
            cuttingText.setText(Cutting.report(block, quilt, fabrics, wof));
            cuttingText.setCaretPosition(Math.min(caret, cuttingText.getText().length()));
        }
    }

    void syncControls() {
        syncing = true;
        gridSpin.setValue(block.n);
        sizeSpin.setValue(block.size);
        layoutBox.setSelectedItem(quilt.layout);
        colsSpin.setValue(quilt.cols);
        rowsSpin.setValue(quilt.rows);
        sashSpin.setValue(quilt.sash);
        borderSpin.setValue(quilt.border);
        wofSpin.setValue(wof);
        for (FabricCombo fc : fabricCombos) fc.reload();
        syncing = false;
    }

    void pushUndo() {
        undo.push(block.copy());
        while (undo.size() > 200) undo.removeLast();
        redo.clear();
    }

    void doUndo() {
        if (undo.isEmpty()) { setStatus("Nothing to undo"); return; }
        redo.push(block.copy());
        block = undo.pop();
        syncControls();
        changed(true);
    }

    void doRedo() {
        if (redo.isEmpty()) { setStatus("Nothing to redo"); return; }
        undo.push(block.copy());
        block = redo.pop();
        syncControls();
        changed(true);
    }

    void setStatus(String s) { if (status != null) status.setText(s); }

    void updateTitle() {
        setTitle(APP + " — " + (file == null ? "Untitled quilt" : file.getName()) + (dirty ? " •" : ""));
    }

    // =====================================================================
    //  Block editor
    // =====================================================================

    class BlockEditor extends JPanel {
        int hoverR = -1, hoverC = -1, hoverP = -1;
        int lastR = -1, lastC = -1, lastP = -1;

        BlockEditor() {
            setBackground(new Color(0xEDE7DD));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    int[] h = hit(e.getX(), e.getY());
                    if (h == null) return;
                    pushUndo();
                    if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
                        Cell c = block.cells[h[0]][h[1]];
                        c.rotateCW();
                        propagate(h[0], h[1]);
                    } else apply(h[0], h[1], h[2]);
                    lastR = h[0]; lastC = h[1]; lastP = h[2];
                    changed(true);
                }
                public void mouseDragged(MouseEvent e) {
                    if (tool != Tool.PAINT || !SwingUtilities.isLeftMouseButton(e)) return;
                    int[] h = hit(e.getX(), e.getY());
                    hover(h);
                    if (h == null || (h[0] == lastR && h[1] == lastC && h[2] == lastP)) return;
                    apply(h[0], h[1], h[2]);
                    lastR = h[0]; lastC = h[1]; lastP = h[2];
                    changed(true);
                }
                public void mouseMoved(MouseEvent e) { hover(hit(e.getX(), e.getY())); }
                public void mouseExited(MouseEvent e) { hover(null); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        double[] frame() {
            double margin = 28;
            double avail = Math.max(50, Math.min(getWidth(), getHeight() - 30) - 2 * margin);
            return new double[]{(getWidth() - avail) / 2, margin, avail / block.size};
        }

        int[] hit(int mx, int my) {
            double[] fr = frame();
            double ix = (mx - fr[0]) / fr[2], iy = (my - fr[1]) / fr[2];
            double cs = block.patch();
            int c = (int) Math.floor(ix / cs), r = (int) Math.floor(iy / cs);
            if (r < 0 || c < 0 || r >= block.n || c >= block.n) return null;
            double fx = ix / cs - c, fy = iy / cs - r;
            return new int[]{r, c, block.cells[r][c].pieceAt(fx, fy)};
        }

        void hover(int[] h) {
            int r = h == null ? -1 : h[0], c = h == null ? -1 : h[1], p = h == null ? -1 : h[2];
            if (r == hoverR && c == hoverC && p == hoverP) return;
            hoverR = r; hoverC = c; hoverP = p;
            if (h != null) {
                Cell cell = block.cells[r][c];
                setStatus(String.format("Row %d, patch %d  ·  %s  ·  %s", r + 1, c + 1, cell.shape.label,
                        Render.fab(fabrics, cell.f[Math.min(p, cell.pieces() - 1)]).name));
            }
            repaint();
        }

        void apply(int r, int c, int p) {
            Cell cell = block.cells[r][c];
            switch (tool) {
                case PAINT -> cell.f[p] = current;
                case ROTATE -> cell.rotateCW();
                case PICK -> {
                    current = Math.floorMod(cell.f[p], fabrics.size());
                    syncing = true;
                    paletteList.setSelectedIndex(current);
                    paletteList.ensureIndexIsVisible(current);
                    syncing = false;
                    setStatus("Picked up " + fabrics.get(current).name);
                    tool = Tool.PAINT;
                    toolButtons.get(Tool.PAINT).setSelected(true);
                    return;
                }
                default -> {
                    Shape want = switch (tool) {
                        case HST -> Shape.HST; case QST -> Shape.QST; case RECT -> Shape.RECT; default -> Shape.SOLID;
                    };
                    if (cell.shape == want && (want == Shape.HST || want == Shape.RECT)) {
                        cell.rotateCW();
                    } else if (cell.shape == want) {
                        cell.f[p] = current;
                    } else {
                        int base = cell.f[0] == current ? cell.f[cell.pieces() > 1 ? 1 : 0] : cell.f[0];
                        if (base == current) base = Math.floorMod(current + 1, fabrics.size());
                        Cell nc = solid(base);
                        nc.shape = want;
                        if (want == Shape.QST) { nc.f[0] = current; nc.f[2] = current; }
                        else nc.f[0] = current;
                        block.cells[r][c] = nc;
                    }
                }
            }
            propagate(r, c);
        }

        void propagate(int r, int c) {
            int n = block.n;
            Cell src = block.cells[r][c];
            switch (symmetry) {
                case LR -> { if (c != n - 1 - c) block.cells[r][n - 1 - c] = src.mirrorLR(); }
                case FOUR -> {
                    if (c != n - 1 - c) block.cells[r][n - 1 - c] = src.mirrorLR();
                    if (r != n - 1 - r) block.cells[n - 1 - r][c] = src.mirrorTB();
                    if (c != n - 1 - c && r != n - 1 - r) block.cells[n - 1 - r][n - 1 - c] = src.mirrorLR().mirrorTB();
                }
                case ROT4 -> {
                    int rr = r, cc = c;
                    Cell cell = src;
                    for (int k = 0; k < 3; k++) {
                        int nr = cc, ncol = n - 1 - rr;
                        rr = nr; cc = ncol;
                        cell = cell.copy();
                        cell.rotateCW();
                        if (rr == r && cc == c) break;
                        block.cells[rr][cc] = cell;
                    }
                }
                default -> { }
            }
        }

        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            double[] fr = frame();
            double side = block.size * fr[2];
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new RoundRectangle2D.Double(fr[0] + 5, fr[1] + 7, side, side, 6, 6));

            Graphics2D q = (Graphics2D) g.create();
            q.translate(fr[0], fr[1]);
            q.scale(fr[2], fr[2]);
            double px = 1 / fr[2];
            Render.drawBlock(q, block, 0, 0, block.size, fabrics, seams, px);
            if (hoverR >= 0) {
                double cs = block.patch();
                Cell cell = block.cells[hoverR][hoverC];
                java.awt.Shape s = (tool == Tool.PAINT || tool == Tool.PICK)
                        ? cell.piece(Math.min(hoverP, cell.pieces() - 1), hoverC * cs, hoverR * cs, cs).path()
                        : new Rectangle2D.Double(hoverC * cs, hoverR * cs, cs, cs);
                q.setPaint(new Color(255, 255, 255, 60));
                q.fill(s);
                q.setStroke(new BasicStroke((float) (2.5 * px)));
                q.setPaint(Color.WHITE);
                q.draw(s);
                q.setStroke(new BasicStroke((float) (1.2 * px), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                        new float[]{(float) (4 * px), (float) (4 * px)}, 0));
                q.setPaint(new Color(40, 40, 40));
                q.draw(s);
            }
            q.dispose();

            g.setColor(new Color(90, 80, 70));
            g.setFont(getFont().deriveFont(13f));
            String txt = Cutting.inches(block.size) + " finished block  ·  " + block.n + " × " + block.n + " patches"
                    + (symmetry != Symmetry.NONE ? "  ·  " + symmetry.label : "");
            FontMetrics fm = g.getFontMetrics();
            g.drawString(txt, (int) (getWidth() - fm.stringWidth(txt)) / 2, (int) (fr[1] + side + 24));
            g.dispose();
        }
    }

    /** Small 3 × 3 sample of the block in the current layout. */
    class RepeatPreview extends JPanel {
        RepeatPreview() {
            setPreferredSize(new Dimension(240, 240));
            setMinimumSize(new Dimension(160, 160));
            setBackground(new Color(0xEDE7DD));
            setToolTipText("Your block repeated using the layout chosen on the quilt tab");
        }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            QuiltSettings q = quilt.copy();
            q.rows = 3; q.cols = 3; q.border = 0;
            double W = q.width(block), H = q.height(block);
            double sc = Math.min((getWidth() - 16) / W, (getHeight() - 16) / H);
            g.translate((getWidth() - W * sc) / 2, (getHeight() - H * sc) / 2);
            g.scale(sc, sc);
            Render.drawQuilt(g, block, q, fabrics, false, 1 / sc, false);
            g.dispose();
        }
    }

    /** The whole quilt. */
    class QuiltView extends JPanel {
        QuiltView() { setBackground(new Color(0xE4DDD2)); }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            double W = quilt.width(block), H = quilt.height(block);
            double m = 30;
            double sc = Math.min((getWidth() - 2 * m) / W, (getHeight() - 2 * m - 30) / H);
            if (sc <= 0) { g.dispose(); return; }
            double ox = (getWidth() - W * sc) / 2, oy = m;
            g.setColor(new Color(0, 0, 0, 45));
            g.fill(new Rectangle2D.Double(ox + 6, oy + 8, W * sc, H * sc));
            Graphics2D q = (Graphics2D) g.create();
            q.translate(ox, oy);
            q.scale(sc, sc);
            Render.drawQuilt(q, block, quilt, fabrics, seams, 1 / sc, true);
            q.dispose();
            g.setColor(new Color(80, 70, 60));
            g.setFont(getFont().deriveFont(Font.BOLD, 14f));
            String t = String.format("Finished quilt %s × %s  (%s × %s)   ·   %d blocks",
                    Cutting.inches(W), Cutting.inches(H), Cutting.cm(W), Cutting.cm(H), quilt.rows * quilt.cols);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, (int) (oy + H * sc + 26));
            g.dispose();
        }
    }

    /** Hand-drawn toolbar icons. */
    static class ToolIcon implements Icon {
        final Tool t;
        ToolIcon(Tool t) { this.t = t; }
        public int getIconWidth() { return 20; }
        public int getIconHeight() { return 20; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            g.translate(x + 2, y + 2);
            Color dark = new Color(0x2E3F66), rose = new Color(0xC9566B), cream = new Color(0xF4ECD8);
            Rectangle2D sq = new Rectangle2D.Double(0, 0, 16, 16);
            switch (t) {
                case PAINT -> {
                    g.setColor(cream); g.fill(sq);
                    g.setColor(rose); g.fill(new Ellipse2D.Double(3, 3, 10, 10));
                }
                case SOLID -> { g.setColor(rose); g.fill(sq); }
                case HST -> {
                    g.setColor(cream); g.fill(sq);
                    g.setColor(rose); g.fill(new Polygon(new int[]{0, 16, 0}, new int[]{0, 0, 16}, 3));
                }
                case QST -> {
                    g.setColor(cream); g.fill(sq);
                    g.setColor(rose);
                    g.fill(new Polygon(new int[]{0, 16, 8}, new int[]{0, 0, 8}, 3));
                    g.fill(new Polygon(new int[]{0, 16, 8}, new int[]{16, 16, 8}, 3));
                    g.setColor(dark);
                    g.fill(new Polygon(new int[]{16, 16, 8}, new int[]{0, 16, 8}, 3));
                }
                case RECT -> {
                    g.setColor(cream); g.fill(sq);
                    g.setColor(rose); g.fill(new Rectangle2D.Double(0, 0, 16, 8));
                }
                case ROTATE -> {
                    g.setColor(dark);
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Arc2D.Double(2, 2, 12, 12, 90, -270, Arc2D.OPEN));
                    g.fill(new Polygon(new int[]{8, 13, 8}, new int[]{-1, 2, 5}, 3));
                }
                case PICK -> {
                    g.setColor(dark);
                    g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(3, 13, 11, 5);
                    g.fill(new Ellipse2D.Double(9, 0, 7, 7));
                    g.setColor(rose);
                    g.fill(new Ellipse2D.Double(0, 12, 4, 4));
                }
            }
            if (t != Tool.ROTATE && t != Tool.PICK) {
                g.setColor(new Color(0, 0, 0, 110));
                g.setStroke(new BasicStroke(1f));
                g.draw(sq);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  Menus, files, printing
    // =====================================================================

    JMenuBar menus() {
        JMenuBar mb = new JMenuBar();
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu file = new JMenu("File");
        file.add(item("New quilt", KeyStroke.getKeyStroke(KeyEvent.VK_N, mod), this::newQuilt));
        file.add(item("Open…", KeyStroke.getKeyStroke(KeyEvent.VK_O, mod), this::open));
        file.add(item("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod), () -> save(false)));
        file.add(item("Save as…", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod | InputEvent.SHIFT_DOWN_MASK), () -> save(true)));
        file.addSeparator();
        file.add(item("Export quilt picture…", null, () -> exportPng(true)));
        file.add(item("Export block picture…", null, () -> exportPng(false)));
        file.add(item("Save cutting list as text…", null, this::saveCuttingText));
        file.add(item("Print…", KeyStroke.getKeyStroke(KeyEvent.VK_P, mod), this::print));
        if (!isMac()) {
            file.addSeparator();
            file.add(item("Quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, mod), this::quit));
        }

        JMenu edit = new JMenu("Edit");
        edit.add(item("Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, mod), this::doUndo));
        edit.add(item("Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, mod), this::doRedo));
        edit.addSeparator();
        edit.add(item("Surprise me!", KeyStroke.getKeyStroke(KeyEvent.VK_R, mod), this::surprise));
        edit.add(item("Shuffle fabrics", null, this::shuffleFabrics));

        JMenu help = new JMenu("Help");
        help.add(item("How to use Quilt Studio", null, this::showHelp));
        help.add(item("About Quilt Studio", null, this::showAbout));

        mb.add(file);
        mb.add(edit);
        mb.add(help);
        return mb;
    }

    static boolean isMac() { return System.getProperty("os.name", "").toLowerCase().contains("mac"); }

    static JMenuItem item(String text, KeyStroke ks, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        if (ks != null) i.setAccelerator(ks);
        i.addActionListener(e -> r.run());
        return i;
    }

    boolean confirmDiscard() {
        if (!dirty) return true;
        int r = JOptionPane.showConfirmDialog(this, "Save your quilt first?", APP, JOptionPane.YES_NO_CANCEL_OPTION);
        if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return false;
        if (r == JOptionPane.YES_OPTION) return save(false);
        return true;
    }

    void quit() {
        if (confirmDiscard()) { dispose(); System.exit(0); }
    }

    void newQuilt() {
        if (!confirmDiscard()) return;
        fabrics.clear();
        fabrics.addAll(collection("Country Cottage"));
        block = classic("Sawtooth Star", 12);
        QuiltSettings d = new QuiltSettings();
        quilt.rows = d.rows; quilt.cols = d.cols; quilt.layout = d.layout; quilt.sash = d.sash; quilt.border = d.border;
        quilt.sashFab = d.sashFab; quilt.cornerFab = d.cornerFab; quilt.borderFab = d.borderFab; quilt.bindFab = d.bindFab; quilt.plainFab = d.plainFab;
        undo.clear(); redo.clear();
        file = null;
        current = 1;
        reloadPalette();
        syncControls();
        refreshAll();
        dirty = false;
        updateTitle();
    }

    JFileChooser chooser(String ext, String desc) {
        JFileChooser fc = new JFileChooser(file != null ? file.getParentFile() : new File(System.getProperty("user.home")));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(desc, ext));
        return fc;
    }

    static File withExt(File f, String ext) {
        return f.getName().toLowerCase().endsWith("." + ext) ? f : new File(f.getParentFile(), f.getName() + "." + ext);
    }

    boolean save(boolean as) {
        File target = file;
        if (as || target == null) {
            JFileChooser fc = chooser("quilt", "Quilt Studio designs (*.quilt)");
            fc.setSelectedFile(new File(file == null ? "My quilt.quilt" : file.getName()));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            target = withExt(fc.getSelectedFile(), "quilt");
        }
        try {
            Files.writeString(target.toPath(), serialise(), StandardCharsets.UTF_8);
            file = target;
            dirty = false;
            updateTitle();
            setStatus("Saved " + target.getName());
            return true;
        } catch (IOException ex) {
            error("Couldn't save the quilt:\n" + ex.getMessage());
            return false;
        }
    }

    void open() {
        if (!confirmDiscard()) return;
        JFileChooser fc = chooser("quilt", "Quilt Studio designs (*.quilt)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            load(Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8));
            file = fc.getSelectedFile();
            undo.clear(); redo.clear();
            reloadPalette();
            syncControls();
            refreshAll();
            dirty = false;
            updateTitle();
            setStatus("Opened " + file.getName());
        } catch (Exception ex) {
            error("That file couldn't be opened as a quilt:\n" + ex.getMessage());
        }
    }

    String serialise() {
        StringBuilder sb = new StringBuilder("QuiltStudio\t1\n");
        for (Fabric f : fabrics) sb.append("fabric\t").append(f.name).append('\t').append(f.hex()).append('\t').append(f.print.name()).append('\n');
        sb.append("block\t").append(block.n).append('\t').append(block.size).append('\n');
        for (int r = 0; r < block.n; r++)
            for (int c = 0; c < block.n; c++) {
                Cell cell = block.cells[r][c];
                sb.append("cell\t").append(r).append('\t').append(c).append('\t').append(cell.shape.name()).append('\t').append(cell.rot)
                        .append('\t').append(cell.f[0]).append(',').append(cell.f[1]).append(',').append(cell.f[2]).append(',').append(cell.f[3]).append('\n');
            }
        QuiltSettings q = quilt;
        sb.append("quilt\t").append(q.rows).append('\t').append(q.cols).append('\t').append(q.layout.name()).append('\t').append(q.sash)
                .append('\t').append(q.border).append('\t').append(q.sashFab).append('\t').append(q.cornerFab).append('\t').append(q.borderFab)
                .append('\t').append(q.bindFab).append('\t').append(q.plainFab).append('\n');
        sb.append("wof\t").append(wof).append('\n');
        return sb.toString();
    }

    void load(String text) {
        String[] lines = text.split("\\R");
        if (lines.length == 0 || !lines[0].startsWith("QuiltStudio")) throw new IllegalArgumentException("Not a Quilt Studio file.");
        List<Fabric> fabs = new ArrayList<>();
        Block b = null;
        QuiltSettings q = new QuiltSettings();
        double w = 42;
        for (String line : lines) {
            String[] p = line.split("\t");
            switch (p[0]) {
                case "fabric" -> fabs.add(new Fabric(p[1], Integer.parseInt(p[2], 16), Print.valueOf(p[3])));
                case "block" -> b = new Block(Integer.parseInt(p[1]), Double.parseDouble(p[2]));
                case "cell" -> {
                    if (b == null) throw new IllegalArgumentException("Patch before block size.");
                    Cell c = new Cell();
                    c.shape = Shape.valueOf(p[3]);
                    c.rot = Integer.parseInt(p[4]);
                    String[] fs = p[5].split(",");
                    for (int k = 0; k < 4; k++) c.f[k] = Integer.parseInt(fs[k]);
                    b.cells[Integer.parseInt(p[1])][Integer.parseInt(p[2])] = c;
                }
                case "quilt" -> {
                    q.rows = Integer.parseInt(p[1]); q.cols = Integer.parseInt(p[2]); q.layout = Layout.valueOf(p[3]);
                    q.sash = Double.parseDouble(p[4]); q.border = Double.parseDouble(p[5]);
                    q.sashFab = Integer.parseInt(p[6]); q.cornerFab = Integer.parseInt(p[7]); q.borderFab = Integer.parseInt(p[8]);
                    q.bindFab = Integer.parseInt(p[9]); q.plainFab = Integer.parseInt(p[10]);
                }
                case "wof" -> w = Double.parseDouble(p[1]);
                default -> { }
            }
        }
        if (fabs.size() < 2 || b == null) throw new IllegalArgumentException("The file is incomplete.");
        fabrics.clear();
        fabrics.addAll(fabs);
        block = b;
        QuiltSettings t = quilt;
        t.rows = q.rows; t.cols = q.cols; t.layout = q.layout; t.sash = q.sash; t.border = q.border;
        t.sashFab = q.sashFab; t.cornerFab = q.cornerFab; t.borderFab = q.borderFab; t.bindFab = q.bindFab; t.plainFab = q.plainFab;
        wof = w;
        current = Math.min(1, fabrics.size() - 1);
    }

    void exportPng(boolean wholeQuilt) {
        JFileChooser fc = chooser("png", "PNG pictures (*.png)");
        String base = file == null ? "My quilt" : file.getName().replaceFirst("\\.quilt$", "");
        fc.setSelectedFile(new File(base + (wholeQuilt ? "" : " block") + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = withExt(fc.getSelectedFile(), "png");
        try {
            BufferedImage img;
            if (wholeQuilt) {
                double ppi = Math.min(30, 4000 / Math.max(quilt.width(block), quilt.height(block)));
                img = Render.quiltImage(block, quilt, fabrics, seams, ppi);
            } else img = Render.blockImage(block, fabrics, seams, Math.min(80, 2000 / block.size));
            ImageIO.write(img, "png", target);
            setStatus("Saved picture " + target.getName());
        } catch (IOException ex) {
            error("Couldn't save the picture:\n" + ex.getMessage());
        }
    }

    void saveCuttingText() {
        JFileChooser fc = chooser("txt", "Text files (*.txt)");
        String base = file == null ? "My quilt" : file.getName().replaceFirst("\\.quilt$", "");
        fc.setSelectedFile(new File(base + " cutting list.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = withExt(fc.getSelectedFile(), "txt");
        try {
            Files.writeString(target.toPath(), Cutting.report(block, quilt, fabrics, wof), StandardCharsets.UTF_8);
            setStatus("Saved " + target.getName());
        } catch (IOException ex) {
            error("Couldn't save the cutting list:\n" + ex.getMessage());
        }
    }

    void print() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(APP + " — " + (file == null ? "My quilt" : file.getName()));
        final BufferedImage img = Render.quiltImage(block, quilt, fabrics, seams,
                Math.min(20, 3000 / Math.max(quilt.width(block), quilt.height(block))));
        final String[] lines = Cutting.report(block, quilt, fabrics, wof).split("\n", -1);
        final List<int[]> pages = new ArrayList<>(); // [firstLine, lastLineExclusive] for text pages
        job.setPrintable((g0, pf, pageIndex) -> {
            Graphics2D g = (Graphics2D) g0;
            Render.hints(g);
            double x = pf.getImageableX(), y = pf.getImageableY(), w = pf.getImageableWidth(), h = pf.getImageableHeight();
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 8);
            g.setFont(mono);
            int lh = g.getFontMetrics().getHeight();
            if (pages.isEmpty()) {
                int per = Math.max(1, (int) (h / lh));
                for (int i = 0; i < lines.length; i += per) pages.add(new int[]{i, Math.min(lines.length, i + per)});
            }
            if (pageIndex == 0) {
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SERIF, Font.BOLD, 16));
                g.drawString(file == null ? "My quilt" : file.getName().replaceFirst("\\.quilt$", ""), (float) x, (float) (y + 16));
                double avW = w, avH = h - 40;
                double sc = Math.min(avW / img.getWidth(), avH / img.getHeight());
                int iw = (int) (img.getWidth() * sc), ih = (int) (img.getHeight() * sc);
                g.drawImage(img, (int) (x + (w - iw) / 2), (int) (y + 30), iw, ih, null);
                return Printable.PAGE_EXISTS;
            }
            int pi = pageIndex - 1;
            if (pi >= pages.size()) return Printable.NO_SUCH_PAGE;
            g.setColor(Color.BLACK);
            g.setFont(mono);
            int[] range = pages.get(pi);
            float ty = (float) y + lh;
            for (int i = range[0]; i < range[1]; i++) { g.drawString(lines[i], (float) x, ty); ty += lh; }
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            try { job.print(); } catch (PrinterException ex) { error("Printing failed:\n" + ex.getMessage()); }
        }
    }

    void error(String msg) { JOptionPane.showMessageDialog(this, msg, APP, JOptionPane.ERROR_MESSAGE); }

    void showHelp() {
        String html = "<html><body style='width:460px; font-family:sans-serif'>"
                + "<h2>How to use Quilt Studio</h2>"
                + "<p><b>1. Pick your fabrics.</b> The list on the left is your fabric stash. Click one to paint with it; "
                + "double-click to change its colour or print. Choose a <i>Collection</i> to swap in a whole range at once — "
                + "your design recolours straight away. <i>Shuffle colours</i> swaps them around for a fresh look.</p>"
                + "<p><b>2. Design a block.</b> Start from a classic block or press <i>Surprise me!</i>. "
                + "Then use the tools along the top:</p><ul>"
                + "<li><b>Paint</b> — click or drag to colour patches and triangles.</li>"
                + "<li><b>Square, Half-square, Quarter-square, Rectangles</b> — change the shape of a patch. "
                + "Click a half-square or rectangle again to turn it.</li>"
                + "<li><b>Turn</b> — a quarter turn clockwise. A right-click turns a patch with any tool.</li>"
                + "<li><b>Pick</b> — pick up the fabric from a patch.</li></ul>"
                + "<p>Set <i>Symmetry</i> to have every change copied around the block — "
                + "great for stars and medallions. Ctrl/⌘+Z undoes.</p>"
                + "<p><b>3. Lay out the quilt.</b> Choose how many blocks, how they're turned, and add sashing, "
                + "cornerstones, a border and binding. <i>Size for</i> picks a block count to suit a bed.</p>"
                + "<p><b>4. Cut and shop.</b> The third tab lists every piece to cut, strip by strip, and how much "
                + "of each fabric to buy in yards and metres — including backing and batting. Print it or save it as text.</p>"
                + "<p>All sizes include a 1/4\" seam allowance.</p></body></html>";
        JOptionPane.showMessageDialog(this, new JLabel(html), "How to use " + APP, JOptionPane.PLAIN_MESSAGE);
    }

    void showAbout() {
        String html = "<html><body style='width:320px; font-family:sans-serif'>"
                + "<h2>" + APP + " " + VERSION + "</h2>"
                + "<p>Design patchwork blocks, lay out whole quilts and work out the cutting and fabric.</p>"
                + "<p>Copyright Mario Gianota 2026.<br>All rights reserved.</p>"
                + "<p>Feature requests, bugs and suggestions:<br><b>" + EMAIL + "</b></p></body></html>";
        JOptionPane.showMessageDialog(this, new JLabel(html), "About " + APP, JOptionPane.PLAIN_MESSAGE);
    }

    // =====================================================================

    public static void main(String[] args) {
        if (isMac()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", APP);
        }
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            new QuiltStudio().setVisible(true);
        });
    }
}
