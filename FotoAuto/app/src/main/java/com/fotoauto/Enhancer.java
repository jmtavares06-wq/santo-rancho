package com.fotoauto;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.InputStream;
import java.io.OutputStream;

/** Correcao automatica: rotacao EXIF, balanco de branco, contraste, brilho e saturacao. */
public final class Enhancer {
    public static final String FOLDER = Environment.DIRECTORY_PICTURES + "/FotoAuto";
    private static final int MAX_SIDE = 4096;

    private Enhancer() {}

    public static Uri process(ContentResolver cr, Uri src, String name) throws Exception {
        Bitmap bmp = decode(cr, src);
        if (bmp == null) return null;
        bmp = rotate(bmp, readOrientation(cr, src));
        enhance(bmp);
        Uri out = save(cr, bmp, name);
        bmp.recycle();
        return out;
    }

    private static Bitmap decode(ContentResolver cr, Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inMutable = true;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = cr.openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, d); }
    }

    private static int readOrientation(ContentResolver cr, Uri uri) {
        try (InputStream in = cr.openInputStream(uri)) {
            switch (new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) { return 0; }
    }

    private static Bitmap rotate(Bitmap b, int deg) {
        if (deg == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(deg);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r.isMutable() ? r : r.copy(Bitmap.Config.ARGB_8888, true);
    }

    /** Aplica a correcao diretamente nos pixels (linha a linha para economizar memoria). */
    static void enhance(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] row = new int[w];
        int[][] hist = new int[3][256];
        long lumSum = 0;
        int step = Math.max(1, h / 512);
        long count = 0;
        for (int y = 0; y < h; y += step) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x += step) {
                int p = row[x];
                int r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255;
                hist[0][r]++; hist[1][g]++; hist[2][bl]++;
                lumSum += (r * 299 + g * 587 + bl * 114) / 1000;
                count++;
            }
        }
        if (count == 0) return;

        // Niveis por canal (0,5% / 99,5%) = contraste + balanco de branco automatico.
        int[][] lut = new int[3][256];
        for (int c = 0; c < 3; c++) {
            int lo = percentile(hist[c], count, 0.005), hi = percentile(hist[c], count, 0.995);
            if (hi - lo < 32) { lo = 0; hi = 255; } // imagem muito uniforme: nao forca
            // limita a forca para nao exagerar
            lo = Math.min(lo, 60); hi = Math.max(hi, 195);
            for (int i = 0; i < 256; i++) {
                float v = (i - lo) / (float) (hi - lo);
                lut[c][i] = clamp(Math.round(Math.max(0f, Math.min(1f, v)) * 255f));
            }
        }

        // Gama para levar o brilho medio perto de ~0.47.
        float mean = Math.max(0.02f, Math.min(0.98f, lumSum / (float) count / 255f));
        float gamma = (float) (Math.log(0.47) / Math.log(mean));
        gamma = Math.max(0.6f, Math.min(1.5f, gamma));
        int[] gLut = new int[256];
        for (int i = 0; i < 256; i++) gLut[i] = clamp(Math.round((float) Math.pow(i / 255f, gamma) * 255f));

        final float sat = 1.12f;
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                int r = gLut[lut[0][(p >> 16) & 255]];
                int g = gLut[lut[1][(p >> 8) & 255]];
                int bl = gLut[lut[2][p & 255]];
                float l = r * 0.299f + g * 0.587f + bl * 0.114f;
                r = clamp(Math.round(l + (r - l) * sat));
                g = clamp(Math.round(l + (g - l) * sat));
                bl = clamp(Math.round(l + (bl - l) * sat));
                row[x] = (p & 0xFF000000) | (r << 16) | (g << 8) | bl;
            }
            b.setPixels(row, 0, w, 0, y, w, 1);
        }
    }

    private static int percentile(int[] hist, long total, double q) {
        long target = (long) (total * q), acc = 0;
        for (int i = 0; i < 256; i++) { acc += hist[i]; if (acc > target) return i; }
        return 255;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static Uri save(ContentResolver cr, Bitmap b, String name) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IllegalStateException("Falha ao criar arquivo");
        try (OutputStream os = cr.openOutputStream(uri)) {
            if (os == null || !b.compress(Bitmap.CompressFormat.JPEG, 95, os))
                throw new IllegalStateException("Falha ao salvar JPEG");
        } catch (Exception e) {
            cr.delete(uri, null, null);
            throw e;
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return uri;
    }
}
