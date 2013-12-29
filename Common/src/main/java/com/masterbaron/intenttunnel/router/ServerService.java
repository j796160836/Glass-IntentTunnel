package com.masterbaron.intenttunnel.router;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Message;
import android.util.Log;

import com.masterbaron.intenttunnel.R;

import java.net.URISyntaxException;
import java.util.Queue;
import java.util.UUID;

import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.PendingData;
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
        return new ServerBluetoothConnection(UUID.fromString(mRouterService.getString(R.string.bluetooth_server_uuid)), this, true);
    }

    @Override
    public void onConnectionLost(Queue<PendingData> left) {
        super.onConnectionLost(left);

        if (isEnabled()) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_RECONNECT, 250);
        }
    }

    @Override
    public void onConnectionFailed(Queue<PendingData> left) {
        super.onConnectionFailed(left);

        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.isEnabled() && isEnabled()) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_RECONNECT, 10000);
        }
    }

    @Override
    public void onCommandReceived(ConnectionCommand command) {
        super.onCommandReceived(command);
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
