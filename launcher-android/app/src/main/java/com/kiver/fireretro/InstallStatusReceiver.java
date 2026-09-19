package com.kiver.fireretro;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public final class InstallStatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) { Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT); if (confirmation != null) { confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(confirmation); } return; }
        String message = status == PackageInstaller.STATUS_SUCCESS ? "Aplicativo instalado" : "Instalação não concluída";
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
