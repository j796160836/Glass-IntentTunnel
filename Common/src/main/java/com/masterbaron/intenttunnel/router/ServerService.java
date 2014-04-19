package com.masterbaron.intenttunnel.router;

import android.bluetooth.BluetoothAdapter;
import android.os.Message;

import com.masterbaron.intenttunnel.common.R;

import java.util.Queue;
import java.util.UUID;

import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ServerBluetoothConnection;

/**
 * Created by Van Etten on 12/2/13.
 */
public class ServerService extends BluetoothService {
    private static int MESSAGE_RECONNECT = 3200;

    public static ServerService service;

    public ServerService(RouterService routerService) {
        super(routerService);
    }

    @Override
    protected BluetoothConnection createNewBTConnection() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();

        if (defaultAdapter != null) {
            return new ServerBluetoothConnection(getUUIDFromAddress(defaultAdapter.getAddress()), this, false);
        }
        return null;
    }

    @Override
    public void onConnectionLost() {
        boolean enabled = isEnabled();
        super.onConnectionLost();
        if (enabled) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_RECONNECT, 2000);
        }
    }

    @Override
    public void onConnectionFailed() {
        boolean enabled = isEnabled();
        super.onConnectionFailed();

        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.isEnabled() && enabled) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_RECONNECT, 10000);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MESSAGE_RECONNECT) {
            startConnection();
            return true;
        }

        return super.handleMessage(msg);
    }

    public static boolean isServiceRunning() {
        ServerService ss = ServerService.service;
        return (ss != null && ss.isRunning());
    }

    public static String getServiceStatus() {
        ServerService ss = ServerService.service;
        if (ss != null) {
            return ss.getStatus();
        }
        return "Never Started";
    }
}
