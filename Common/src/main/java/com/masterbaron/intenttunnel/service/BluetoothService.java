package com.masterbaron.intenttunnel.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.util.Set;
import java.util.UUID;

import ktlab.lib.connection.ConnectionCallback;
import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ServerBluetoothConnection;

/**
 * Created by Van Etten on 12/6/13.
 */
 public abstract class BluetoothService extends Service  implements ConnectionCallback, Handler.Callback {
    private static String SERVICE_PREFERENCES = "ServerPreferences";
    private static String SERVICE_PREFERENCE_START_ON_BOOT = "StartOnBoot";

    protected static byte COMMAND_PASS_INTENT = 100;

    protected BluetoothConnection mBTConnection;
    private String mStatus = "Stopped";
    private boolean isRunning = true;
    private boolean isConnected = false;

    private long lastActivity = 0;

    protected Handler mHandler;

    abstract protected BluetoothConnection createNewBTConnection();

    public boolean isRunning() {
        return isRunning;
    }
    public String getStatus() {
        return mStatus;
    }
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected String getTag() {
        return ((Object) this).getClass().getSimpleName();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.d(getTag(), "onCreate()");

        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter == null || !defaultAdapter.isEnabled())
        {
            stopSelf();
            return;
        }

        SharedPreferences config = getSharedPreferences(SERVICE_PREFERENCES, MODE_PRIVATE);
        if ( !config.getBoolean(SERVICE_PREFERENCE_START_ON_BOOT, false)) {
            stopSelf();
            return;
        }

        mStatus = "Starting";
        super.onCreate();

        startConnection();
        mHandler = new Handler(this);
    }

    @Override
    public void onDestroy() {
        Log.d(getTag(), "onDestroy()");

        mStatus = "Stopping";
        stopConnection();
        super.onDestroy();
        mStatus = "Stopped";
        isRunning = false;
    }

    protected void stopConnection() {
        mStatus = "Disconnecting";
        if( mBTConnection != null ) {
            mBTConnection.stopConnection();
        }
        isConnected = false;
        mStatus = "Disconnected";
    }

    protected void startConnection() {
        mStatus = "Connecting";
        mBTConnection = createNewBTConnection();
        if( mBTConnection != null ) {
            if ( mBTConnection instanceof ServerBluetoothConnection ) {
                mStatus = "Waiting for connection";
            }

            mBTConnection.startConnection();
        } else {
            mStatus = "BT device failure";
        }
    }

    @Override
    public void onConnectComplete() {
        Log.d(getTag(), "onConnectComplete()");
        mStatus = "Connected";
        isConnected = true;
        trackAcitivty();
    }

    private void trackAcitivty() {
        lastActivity = System.currentTimeMillis();
    }

    @Override
    public void onConnectionFailed() {
        Log.d(getTag(), "onConnectionFailed()");
        mStatus = "Connection Failed";
        stopConnection();
    }

    @Override
    public void onConnectionLost() {
        Log.d(getTag(), "onConnectionLost()");
        mStatus = "Connection Lost";
        stopConnection();
    }

    @Override
    public void onDataSendComplete(int id) {
        Log.d(getTag(), "onDataSendComplete(" + id + ")");
        mStatus = "Sent Data";
        trackAcitivty();
    }

    @Override
    public void onCommandReceived(ConnectionCommand command) {
        Log.d(getTag(), "onCommandReceived(" + command.type + ")");
        mStatus = "Received Data";
        trackAcitivty();
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.w(getTag(), "unhandled message:  " + msg.what);
        return false;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public static void setServiceOnBoot( Context context, boolean onBoot ) {
        SharedPreferences.Editor edit = context.getSharedPreferences(SERVICE_PREFERENCES, MODE_PRIVATE).edit();
        edit.putBoolean(SERVICE_PREFERENCE_START_ON_BOOT, onBoot);
        edit.commit();
    }

    public static void stopServices( Context context ) {
        context.stopService(new Intent(context, ClientService.class));
        context.stopService(new Intent(context, ServerService.class));
    }

    public static void startServices( Context context ) {
        context.startService(new Intent(context, ServerService.class));
    }

    public static boolean areServicesRunning() {
        return ClientService.isServiceRunning() || ServerService.isServiceRunning();
    }
}
