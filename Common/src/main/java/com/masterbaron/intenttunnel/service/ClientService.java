package com.masterbaron.intenttunnel.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.masterbaron.intenttunnel.R;

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

        // keep myself alive until I finish my work.
        Intent in = new Intent();
        in.setClass(this, ClientService.class);
        startService(in);
    }

    @Override
    public void onDestroy() {
        mBTConnection = null;
        super.onDestroy();
    }

    @Override
    public void onConnectionFailed(Queue<PendingData> left) {
        // we are going to need a new ClientService
        tellRouterToRestartClient();

        // Have we failed enough?
        if (failedRetries > 5) {
            Log.d(getTag(), "Too many failed tries.  Clearing messages.");
            failedRetries = 0;
            left.clear();
        }
        super.onConnectionFailed(left);
        mBTConnection = null;
        stopSelf();
    }

    @Override
    public void onConnectionLost(Queue<PendingData> left) {
        tellRouterToRestartClient();
        super.onConnectionLost(left);
        mBTConnection = null;
        stopSelf();
    }

    private void tellRouterToRestartClient() {
        try {
            Message msg = Message.obtain(null, RouterService.ROUTER_MESSAGE_CLIENT_FAILED);
            mRouterService.send(msg);
        } catch (RemoteException e) {
            Log.d(getTag(), "tellRouterToRestartClient", e);
        }
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

        // only return a connection if we found a device
        if (mDevice != null) {
            Log.d(getTag(), "Binding to: " + mDevice.getName());
            return new ClientBluetoothConnection(UUID.fromString(getString(R.string.bluetooth_uuid)), this, true, mDevice);
        }
        return null;
    }

    @Override
    public void onConnectComplete() {
        super.onConnectComplete();

        // start the process of checking for inactivity
        mHandler.sendEmptyMessageDelayed(MESSAGE_CHECK_TIMEOUT, CONNECTION_TIMEOUT);
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MESSAGE_CHECK_TIMEOUT) { // inactivity checking
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

    public static String getServiceStatus() {
        ClientService cs = ClientService.service;
        if (cs != null) {
            return cs.getStatus();
        }
        return "Never Started";
    }

    // Look for a connected device with glass in the name, or the one and only bound device
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
