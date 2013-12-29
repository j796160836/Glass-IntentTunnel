package com.masterbaron.intenttunnel.router;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Van Etten on 12/6/13.
 */
public class ServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("ServiceReceiver", "action=" + intent.getAction());

        Intent passIntent = new Intent(intent);
        passIntent.setClass(context, RouterService.class);
        context.startService(passIntent);
    }
}
