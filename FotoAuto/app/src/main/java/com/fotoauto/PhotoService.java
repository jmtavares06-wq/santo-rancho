package com.fotoauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

/** Servico em primeiro plano que observa a galeria e corrige cada foto nova. */
public class PhotoService extends Service {
    static final String PREFS = "fotoauto";
    static final String KEY_LAST_ID = "last_id";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_COUNT = "count";
    private static final String CHANNEL = "fotoauto";
    private static final String TAG = "FotoAuto";

    private HandlerThread thread;
    private Handler handler;
    private ContentObserver observer;
    private final Runnable scan = this::scanNewPhotos;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, PhotoService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Correcao automatica",
                NotificationManager.IMPORTANCE_LOW));
        Notification n = buildNotification("Aguardando novas fotos...");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }

        thread = new HandlerThread("fotoauto-worker");
        thread.start();
        handler = new Handler(thread.getLooper());

        SharedPreferences p = prefs();
        if (p.getLong(KEY_LAST_ID, -1) < 0) p.edit().putLong(KEY_LAST_ID, maxImageId()).apply();

        observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                // espera a camera terminar de gravar e agrupa varios eventos
                handler.removeCallbacks(scan);
                handler.postDelayed(scan, 1500);
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private long maxImageId() {
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, null, null,
                MediaStore.Images.Media._ID + " DESC")) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Exception e) { Log.e(TAG, "maxImageId", e); }
        return 0;
    }

    private void scanNewPhotos() {
        SharedPreferences p = prefs();
        long last = p.getLong(KEY_LAST_ID, 0);
        String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH};
        String sel = MediaStore.Images.Media._ID + ">? AND " + MediaStore.Images.Media.IS_PENDING + "=0";
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                proj, sel, new String[]{String.valueOf(last)}, MediaStore.Images.Media._ID + " ASC")) {
            if (c == null) return;
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                String path = c.getString(2);
                p.edit().putLong(KEY_LAST_ID, id).apply();
                if (path == null || !isCameraPhoto(path)) continue;
                Uri src = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                try {
                    String base = name == null ? ("foto_" + id) : name.replaceAll("\\.[^.]*$", "");
                    Enhancer.process(getContentResolver(), src, base + "_corrigida.jpg");
                    int total = p.getInt(KEY_COUNT, 0) + 1;
                    p.edit().putInt(KEY_COUNT, total).apply();
                    update("Fotos corrigidas: " + total);
                } catch (Throwable t) {
                    Log.e(TAG, "Erro ao corrigir " + src, t);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "scan", e);
        }
    }

    /** Somente fotos da camera (DCIM), nunca as que o proprio app gerou. */
    static boolean isCameraPhoto(String relPath) {
        return relPath.startsWith("DCIM/") && !relPath.contains("FotoAuto");
    }

    private void update(String text) {
        getSystemService(NotificationManager.class).notify(1, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("FotoAuto ativo")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
