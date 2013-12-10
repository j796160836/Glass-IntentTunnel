package com.masterbaron.intenttunnel.service;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Message;
import android.util.Log;

import com.masterbaron.intenttunnel.R;

import java.net.URISyntaxException;
import java.util.UUID;

import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ServerBluetoothConnection;

/**
 * Created by Van Etten on 12/2/13.
 */
public class ServerService extends BluetoothService{
    private static int MESSAGE_RECONNECT = 3200;

    public static ServerService service;

    @Override
    public void onCreate() {
        service = this;
        super.onCreate();
    }

    @Override
    protected BluetoothConnection createNewBTConnection() {
        return new ServerBluetoothConnection(UUID.fromString(getString(R.string.server_uuid)), this, true);
    }

    @Override
    public void onConnectionLost() {
        super.onConnectionLost();
        startConnection();
    }

    @Override
    public void onConnectionFailed() {
        super.onConnectionLost();

        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if ( defaultAdapter != null && defaultAdapter.isEnabled() ) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_RECONNECT, 10000);
        }
    }

    @Override
    public void onCommandReceived(ConnectionCommand command) {
        super.onCommandReceived(command);
        if ( command.type == COMMAND_PASS_INTENT) {
            broadcast(command.option);
        }
    }

    private void broadcast(byte[] option) {
        try {
            String uri = new String(option);
            Log.d(getTag(), "Broadcasting string: " + uri);
            Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            Log.d(getTag(), "Broadcasting Intent: " + intent);
            sendBroadcast(intent);
        } catch (URISyntaxException e) {
            Log.e(getTag(), "Invalid URI", e );
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if ( msg.what == MESSAGE_RECONNECT ) {
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
        if ( ss != null ) {
            return ss.getStatus();
        }
        return "Never Started";
    }
}
