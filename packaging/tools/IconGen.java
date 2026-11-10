import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成安装包用图标：红色圆角底 + 白色主球 + 蓝色副球，输出多尺寸 .ico。
 * 纯 JDK 实现（Java2D + ImageIO），不依赖任何图像库。
 *
 * <p>用法：{@code java IconGen out.ico [preview.png] [png:48:res/mipmap-hdpi/ic_launcher.png ...]}
 */
public class IconGen {

    private static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "app.ico";
        BufferedImage base = draw(256);

        // 额外输出：png:<尺寸>:<路径>（安卓 mipmap 用），其余参数视为 PNG 预览路径
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("png:")) {
                String[] seg = a.split(":", 3);
                int size = Integer.parseInt(seg[1]);
                java.io.File f = new java.io.File(seg[2]);
                if (f.getParentFile() != null) {
                    f.getParentFile().mkdirs();
                }
                ImageIO.write(resize(base, size), "png", f);
                System.out.println("PNG 已生成：" + f + "（" + size + "x" + size + "）");
            } else if (i == 1) {
                ImageIO.write(base, "png", new java.io.File(a));
            }
        }

        List<byte[]> frames = new ArrayList<byte[]>();
        for (int s : SIZES) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(resize(base, s), "png", bos);
            frames.add(bos.toByteArray());
        }

        DataOutputStream o = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(out)));
        try {
            writeShort(o, 0);              // reserved
            writeShort(o, 1);              // type = icon
            writeShort(o, SIZES.length);   // count

            int offset = 6 + 16 * SIZES.length;
            for (int i = 0; i < SIZES.length; i++) {
                int s = SIZES[i];
                o.writeByte(s == 256 ? 0 : s);   // 0 表示 256
                o.writeByte(s == 256 ? 0 : s);
                o.writeByte(0);                  // 调色板数
                o.writeByte(0);                  // reserved
                writeShort(o, 1);                // planes
                writeShort(o, 32);               // bpp
                writeInt(o, frames.get(i).length);
                writeInt(o, offset);
                offset += frames.get(i).length;
            }
            for (byte[] f : frames) {
                o.write(f);
            }
        } finally {
            o.close();
        }
        System.out.println("图标已生成：" + out + "（" + SIZES.length + " 个尺寸）");
    }

    /** 画 256x256 母版 */
    private static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 圆角底板
        g.setPaint(new GradientPaint(0, 0, new Color(0xE8554E), size, size, new Color(0xC0332C)));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.23, size * 0.23));

        // 主球（白）
        double r1 = size * 0.265;
        double cx1 = size * 0.415;
        double cy1 = size * 0.395;
        g.setColor(new Color(0, 0, 0, 45));
        g.fill(new Ellipse2D.Double(cx1 - r1, cy1 - r1 + size * 0.025, r1 * 2, r1 * 2));
        g.setColor(new Color(0xFDFDFD));
        g.fill(new Ellipse2D.Double(cx1 - r1, cy1 - r1, r1 * 2, r1 * 2));

        // 主球上的小高光
        g.setColor(new Color(255, 255, 255, 235));
        double hr = r1 * 0.30;
        g.fill(new Ellipse2D.Double(cx1 - r1 * 0.48 - hr, cy1 - r1 * 0.46 - hr, hr * 2, hr * 2));

        // 副球（蓝）
        double r2 = size * 0.175;
        double cx2 = size * 0.715;
        double cy2 = size * 0.715;
        g.setColor(new Color(0, 0, 0, 55));
        g.fill(new Ellipse2D.Double(cx2 - r2, cy2 - r2 + size * 0.02, r2 * 2, r2 * 2));
        g.setColor(new Color(0x2F6FD6));
        g.fill(new Ellipse2D.Double(cx2 - r2, cy2 - r2, r2 * 2, r2 * 2));
        g.setColor(new Color(255, 255, 255, 215));
        g.setStroke(new BasicStroke((float) (size * 0.012), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1f, new float[]{(float) (size * 0.04), (float) (size * 0.028)}, 0f));
        g.draw(new Ellipse2D.Double(cx2 - r2 * 0.82, cy2 - r2 * 0.82, r2 * 1.64, r2 * 1.64));

        g.dispose();
        return img;
    }

    private static BufferedImage resize(BufferedImage src, int size) {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return dst;
    }

    private static void writeShort(DataOutputStream o, int v) throws java.io.IOException {
        o.writeByte(v & 0xFF);
        o.writeByte((v >>> 8) & 0xFF);
    }

    private static void writeInt(DataOutputStream o, int v) throws java.io.IOException {
        o.writeByte(v & 0xFF);
        o.writeByte((v >>> 8) & 0xFF);
        o.writeByte((v >>> 16) & 0xFF);
        o.writeByte((v >>> 24) & 0xFF);
    }
}
