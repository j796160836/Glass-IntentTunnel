package com.masterbaron.intenttunnel.service;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Van Etten on 12/6/13.
 */
public class ServiceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
        {
            BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
            if (defaultAdapter != null && defaultAdapter.isEnabled())
            {
                context.startService(new Intent(context, ServiceReceiver.class));
            }
        }
        else if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
        {
            int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            if (newState == BluetoothAdapter.STATE_ON)
            {
                context.startService(new Intent(context, ServiceReceiver.class));
            }
            else
            {
                context.stopService(new Intent(context, ServiceReceiver.class));
            }
        }
    }
}
