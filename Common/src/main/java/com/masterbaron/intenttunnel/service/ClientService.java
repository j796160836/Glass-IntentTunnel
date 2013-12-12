package com.masterbaron.intenttunnel.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.masterbaron.intenttunnel.R;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import ktlab.lib.connection.PendingData;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ClientBluetoothConnection;

/**
 * Created by Van Etten on 12/2/13.
 */
public class ClientService extends BluetoothService {
    private static final int MESSAGE_CHECK_TIMEOUT = 2300;

    private static long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    private static ClientService service;

    private BluetoothDevice mDevice;

    @Override
    public void onCreate() {
        super.onCreate();

        Intent in = new Intent(RouterService.class.getName());
        in.setClassName("com.masterbaron.intenttunnel", "com.masterbaron.intenttunnel.service.ClientService");
        startService(in);
    }

    @Override
    public void onConnectionFailed(Queue<PendingData> left) {
        super.onConnectionFailed(left);

        if ( left != null ) {
            for ( PendingData data : left ) {
                if ( data.getCommand().type == COMMAND_PASS_INTENT ) {
                    try {
                        byte[] bytes = data.getCommand().option;
                        Intent intent = Intent.parseUri(new String(bytes), Intent.URI_INTENT_SCHEME);
                        RouterService.resendIntent(intent);
                    } catch (Exception e) {
                        Log.e(getTag(), "failed to resend intent", e);
                    }
                }
            }
        }
    }

    @Override
    public void onConnectionLost(Queue<PendingData> left) {
        super.onConnectionLost(left);
    }

    protected void startConnection() {
        super.startConnection();
    }

    @Override
    public void onDataSendComplete(int id) {
        super.onDataSendComplete(id);
    }

    @Override
    protected BluetoothConnection createNewBTConnection() {
        mDevice = getBTDevice();

        if (mDevice != null) {
            Log.d(getTag(), "Binding to: " + mDevice.getName());
            return new ClientBluetoothConnection(UUID.fromString(getString(R.string.client_uuid)), this, true, mDevice);
        }

        stopSelf();
        return null;
    }

    @Override
    public void onConnectComplete() {
        super.onConnectComplete();

        mHandler.sendEmptyMessageDelayed(MESSAGE_CHECK_TIMEOUT, CONNECTION_TIMEOUT);
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MESSAGE_CHECK_TIMEOUT) {
            if (isConnected()) {
                if (!mBTConnection.isSending() && !mBTConnection.hasPending()) {
                    if (getLastActivity() + CONNECTION_TIMEOUT < System.currentTimeMillis()) {
                        if (!isBound()) {
                            Log.d(getTag(), "MESSAGE_CHECK_TIMEOUT.  No binds: stopSelf()");
                            stopSelf();
                        }
                    }
                }

                mHandler.sendEmptyMessageDelayed(MESSAGE_CHECK_TIMEOUT, CONNECTION_TIMEOUT);
            }
            return true;
        }
        return super.handleMessage(msg);
    }

    public static boolean isServiceRunning() {
        ClientService cs = ClientService.service;
        return (cs != null && cs.isRunning());
    }

    public static String getServiceStatus() {
        ClientService cs = ClientService.service;
        if (cs != null) {
            return cs.getStatus();
        }
        return "Never Started";
    }

    public BluetoothDevice getBTDevice() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.getBondedDevices().size() > 0) {
            Set<BluetoothDevice> bondedDevices = defaultAdapter.getBondedDevices();
            if (bondedDevices != null) {
                if (bondedDevices.size() == 1) {
                    BluetoothDevice device = bondedDevices.iterator().next();
                    Log.d(getTag(), "BT Device: " + device.getName());
                    return device;
                } else if (bondedDevices.size() > 0) {
                    for (BluetoothDevice device : bondedDevices) {
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            if (device.getName().toLowerCase().contains("glass")) {
                                Log.d(getTag(), "BT Device: " + device.getName());
                                return device;
                            }
                        }
                    }
                }
            }
        }
        Log.d(getTag(), "NO BT Device found");
        return null;
    }
}
