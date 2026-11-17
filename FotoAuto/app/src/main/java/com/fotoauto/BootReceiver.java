package com.fotoauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
                && c.getSharedPreferences(PhotoService.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(PhotoService.KEY_ENABLED, false)
                && MainActivity.hasPermissions(c)) {
            PhotoService.start(c);
        }
    }
}
