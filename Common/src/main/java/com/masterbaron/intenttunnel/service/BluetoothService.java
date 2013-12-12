package com.masterbaron.intenttunnel.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import ktlab.lib.connection.ConnectionCallback;
import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.PendingData;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ServerBluetoothConnection;

/**
 * Created by Van Etten on 12/6/13.
 */
public abstract class BluetoothService extends Service implements ConnectionCallback, Handler.Callback {
    private static String SERVICE_PREFERENCES = "ServerPreferences";
    private static String SERVICE_PREFERENCE_START_ON_BOOT = "StartOnBoot";

    protected static byte COMMAND_PASS_INTENT = 100;

    protected Handler mHandler;
    protected BluetoothConnection mBTConnection;

    private Messenger mRouterService;
    private Messenger mMessenger;
    private String mStatus = "Stopped";
    private boolean isRunning = true;
    private boolean isConnected = false;
    private long lastActivity = 0;
    private boolean bound = false;
    private int mMessageId = 0;
    private Map<Integer, String> mReplyMap = new HashMap<Integer, String>();

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
        if (defaultAdapter == null || !defaultAdapter.isEnabled()) {
            stopSelf();
            return;
        }

        SharedPreferences config = getSharedPreferences(SERVICE_PREFERENCES, MODE_PRIVATE);
        if (!config.getBoolean(SERVICE_PREFERENCE_START_ON_BOOT, false)) {
            stopSelf();
            return;
        }

        mStatus = "Starting";
        super.onCreate();

        startConnection();
        mMessenger = new Messenger(new Handler(this));
        mHandler = new Handler(this);

        Intent in = new Intent(RouterService.class.getName());
        in.setClassName("com.masterbaron.intenttunnel", "com.masterbaron.intenttunnel.service.RouterService");
        bindService(in, mRouterConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        Log.d(getTag(), "onDestroy()");
        mStatus = "Stopping";

        unbindService(mRouterConnection);
        stopConnection();

        isRunning = false;
        mStatus = "Stopped";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        bound = true;
        Log.d(getTag(), "onBind()");
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        bound = false;
        Log.d(getTag(), "onUnbind()");
        return super.onUnbind(intent);
    }

    protected void stopConnection() {
        mStatus = "Disconnecting";
        if (mBTConnection != null) {
            mBTConnection.stopConnection();
        }
        mMessageId = 0;
        isConnected = false;
        mStatus = "Disconnected";
    }

    protected void startConnection() {
        mStatus = "Connecting";
        mMessageId = 0;
        mBTConnection = createNewBTConnection();
        if (mBTConnection != null) {
            if (mBTConnection instanceof ServerBluetoothConnection) {
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
    public void onConnectionFailed(Queue<PendingData> left) {
        Log.d(getTag(), "onConnectionFailed()");
        mStatus = "Connection Failed";
        stopConnection();
    }

    @Override
    public void onConnectionLost(Queue<PendingData> left) {
        Log.d(getTag(), "onConnectionLost()");
        mStatus = "Connection Lost";
        stopConnection();
    }

    @Override
    public void onDataSendComplete(int id) {
        Log.d(getTag(), "onDataSendComplete(" + id + ")");
        mStatus = "Sent Data";
        trackAcitivty();

        String result = mReplyMap.remove(id);
        if (result != null) {
            notifyResult(result, -1);
        }
    }

    @Override
    public void onCommandReceived(ConnectionCommand command) {
        Log.d(getTag(), "onCommandReceived(" + command.type + ")");
        mStatus = "Received Data";
        trackAcitivty();
    }

    public boolean isBound() {
        return bound;
    }

    private void notifyResult(String uri, int result) {
        try {
            Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            intent.putExtra(Intent.EXTRA_INTENT + ":RESULT", result);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(getTag(), "Invalid Intent.EXTRA_INTENT + \":RESULT\" provided", e);
        }
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public static void setServiceOnBoot(Context context, boolean onBoot) {
        SharedPreferences.Editor edit = context.getSharedPreferences(SERVICE_PREFERENCES, MODE_PRIVATE).edit();
        edit.putBoolean(SERVICE_PREFERENCE_START_ON_BOOT, onBoot);
        edit.commit();
    }

    public static void stopServices(Context context) {
        context.stopService(new Intent(context, ClientService.class));
        context.stopService(new Intent(context, ServerService.class));
    }

    public static void startServices(Context context) {
        context.startService(new Intent(context, ServerService.class));
    }

    public static boolean areServicesRunning() {
        return ClientService.isServiceRunning() || ServerService.isServiceRunning();
    }

    public boolean handleMessage(Message msg) {
        if (mRouterService == null) {
            Log.d(getTag(), "waiting for routerservice to bind");
            // wait for the router service to bind
            Message resend = msg.getTarget().obtainMessage();
            resend.copyFrom(msg);
            msg.getTarget().sendMessageDelayed(msg, 100);
        } else {
            Log.d(getTag(), "act on what=" + msg.what);
            if (msg.what == RouterService.INCOMING_MSG_INTENT_FORWARD) {
                try {
                    mMessageId++;
                    Intent intent = (Intent) msg.obj;
                    String sendUri = intent.toUri(Intent.URI_INTENT_SCHEME);
                    mBTConnection.sendData(COMMAND_PASS_INTENT, sendUri.getBytes(), mMessageId);

                    String ifSentUri = intent.getStringExtra(Intent.EXTRA_INTENT + ":SENT");
                    if (ifSentUri != null) {
                        mReplyMap.put(mMessageId, ifSentUri);
                    }
                } catch (Exception e) {
                    Log.e(getTag(), "failed to process INCOMING_MSG_INTENT_FORWARD", e);
                }
                return true;
            }
        }
        return false;
    }

    private ServiceConnection mRouterConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(getTag(), "onRouterServiceConnected()");
            mRouterService = new Messenger(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(getTag(), "onRouterServiceDisconnected()");
            mRouterService = null;
        }
    };
}
