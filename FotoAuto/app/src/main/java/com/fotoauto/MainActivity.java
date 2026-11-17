package com.fotoauto;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView status;
    private Button toggle;

    static String[] requiredPermissions() {
        List<String> l = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(Manifest.permission.READ_MEDIA_IMAGES);
            l.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return l.toArray(new String[0]);
    }

    static boolean hasPermissions(Context c) {
        String read = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return c.checkSelfPermission(read) == PackageManager.PERMISSION_GRANTED;
    }

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("FotoAuto");
        title.setTextSize(28);
        TextView info = new TextView(this);
        info.setText("Quando ativado, toda foto nova tirada com a camera e corrigida sozinha "
                + "(rotacao, balanco de branco, contraste, brilho e cor).\n\n"
                + "A original e mantida; a versao corrigida fica em Imagens/FotoAuto.");
        info.setPadding(0, pad, 0, pad);
        status = new TextView(this);
        status.setTextSize(18);
        toggle = new Button(this);
        toggle.setOnClickListener(v -> onToggle());

        root.addView(title);
        root.addView(info);
        root.addView(status);
        root.addView(toggle);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private SharedPreferences prefs() { return getSharedPreferences(PhotoService.PREFS, MODE_PRIVATE); }

    private void onToggle() {
        boolean enabled = prefs().getBoolean(PhotoService.KEY_ENABLED, false);
        if (enabled) {
            prefs().edit().putBoolean(PhotoService.KEY_ENABLED, false).apply();
            stopService(new Intent(this, PhotoService.class));
            refresh();
        } else if (!hasPermissions(this)) {
            requestPermissions(requiredPermissions(), 1);
        } else {
            enable();
        }
    }

    private void enable() {
        prefs().edit().putBoolean(PhotoService.KEY_ENABLED, true).apply();
        PhotoService.start(this);
        refresh();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (hasPermissions(this)) enable();
        else status.setText("Permissao de fotos negada. Libere \"Permitir todas\" nas configuracoes do app.");
    }

    private void refresh() {
        SharedPreferences p = prefs();
        boolean enabled = p.getBoolean(PhotoService.KEY_ENABLED, false);
        if (enabled && hasPermissions(this)) PhotoService.start(this); // garante que esta rodando
        status.setText((enabled ? "Status: ATIVO" : "Status: desligado")
                + "\nFotos corrigidas: " + p.getInt(PhotoService.KEY_COUNT, 0));
        toggle.setText(enabled ? "Desativar" : "Ativar correcao automatica");
    }
}
