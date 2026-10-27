package learn.gjx.tuku1.api.aliyunai;

/** View expansion is an editing instruction, not a promise of pixel-exact enlargement. */
public final class OutpaintingSize {
    private OutpaintingSize() { }
    public static int[] calculate(int width, int height, double x, double y) {
        if (width < 512 || height < 512 || width > 4096 || height > 4096
                || !Double.isFinite(x) || !Double.isFinite(y) || x < 1 || y < 1 || x > 3 || y > 3 || (x == 1 && y == 1))
            throw new IllegalArgumentException("Invalid expansion dimensions");
        double w = width * x, h = height * y;
        if (w / h < 0.25 || w / h > 4) throw new IllegalArgumentException("Unsupported aspect ratio");
        double factor = Math.min(1, Math.sqrt(1_048_576d / (w * h)));
        factor = Math.min(factor, 2048d / Math.max(w, h));
        int outW = (int) (w * factor / 16) * 16;
        int outH = (int) (h * factor / 16) * 16;
        if (outW < 512 || outH < 512) throw new IllegalArgumentException("Unsupported output dimensions");
        return new int[]{outW, outH};
    }
}
