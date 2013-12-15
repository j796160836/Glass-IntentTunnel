package com.masterbaron.intenttunnel.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
    protected static byte BLUETOOTH_COMMAND_PASS_INTENT = 100;

    protected static int failedRetries = 0;

    protected Handler mHandler;
    protected BluetoothConnection mBTConnection;
    protected Messenger mRouterService;

    private Messenger mMessenger;
    private String mStatus = "Stopped";
    private boolean isRunning = true;
    private boolean isConnected = false;
    private long lastActivity = 0;
    private boolean bound = false;
    private int mMessageId = 0;

    /**
     * stop the ServerService
     */
    public static void stopServices(Context context) {
        context.stopService(new Intent(context, ServerService.class));
    }

    /**
     * start the ServerService
     */
    public static void startServices(Context context) {
        context.startService(new Intent(context, ServerService.class));
    }

    /**
     * is the ServerService running
     */
    public static boolean areServicesRunning() {
        return ServerService.isServiceRunning();
    }

    abstract protected BluetoothConnection createNewBTConnection();

    protected String getTag() {
        // logs to belong to whoever implements this abstract class.
        return ((Object) this).getClass().getSimpleName();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public String getStatus() {
        return mStatus;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isBound() {
        return bound;
    }

    public long getLastActivity() {
        return lastActivity;
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

        // if no bluetooth or off, then just stop since we cant do anything
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter == null || !defaultAdapter.isEnabled()) {
            Log.d(getTag(), "bluetooth adapter disabled");
            stopSelf();
            return;
        }

        mStatus = "Starting";
        super.onCreate();

        // setup handler and messenger
        mHandler = new Handler(this);
        mMessenger = new Messenger(mHandler);

        // bind back to the router
        Intent in = new Intent(BluetoothService.class.getName());
        in.setClass(this, RouterService.class);
        bindService(in, mRouterConnection, Context.BIND_AUTO_CREATE);

        startConnection();
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

    /**
     * take and messages we haven't sent over bluetooth and send them back to the router.
     */
    private void backToRouter(Queue<PendingData> left) {
        Log.e(getTag(), "reroute: " + left );
        if (left != null) {
            for (PendingData data : left) {
                if (data.getCommand().type == RouterService.ROUTER_MESSAGE_FORWARD_INTENT) {
                    try {
                        byte[] bytes = data.getCommand().option;
                        Intent intent = Intent.parseUri(new String(bytes), Intent.URI_INTENT_SCHEME);
                        Message msg = Message.obtain(null, RouterService.ROUTER_MESSAGE_FORWARD_INTENT, intent);
                        mRouterService.send(msg);
                    } catch (Exception e) {
                        Log.e(getTag(), "failed to resend intent", e);
                    }
                }
            }
        }
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
            // if this is a server, then we will be waiting for a connection
            if (mBTConnection instanceof ServerBluetoothConnection) {
                mStatus = "Waiting for connection";
            }

            mBTConnection.startConnection();
        } else {
            mStatus = "BT device failure";
            stopSelf();
        }
    }

    @Override
    public void onConnectComplete() {
        Log.d(getTag(), "onConnectComplete()");
        mStatus = "Connected";
        isConnected = true;

        // once connected reset failure counter
        failedRetries = 0;
        trackBluetoothActivity();
    }

    /**
     * called when we detect bluetooth activity.
     * used to determine idle timeout
     */
    private void trackBluetoothActivity() {
        lastActivity = System.currentTimeMillis();
    }

    @Override
    public void onConnectionFailed(Queue<PendingData> left) {
        Log.d(getTag(), "onConnectionFailed()");
        mStatus = "Connection Failed";

        // count failures so we can give up at some point
        failedRetries++;

        stopConnection();

        // when there is a failure, we need to send all of the pending messages back to the router.
        backToRouter(left);
    }

    @Override
    public void onConnectionLost(Queue<PendingData> left) {
        Log.d(getTag(), "onConnectionLost()");
        mStatus = "Connection Lost";

        // count failures so we can give up at some point
        failedRetries++;

        stopConnection();

        // when there is a failure, we need to send all of the pending messages back to the router.
        backToRouter(left);
    }

    @Override
    public void onDataSendComplete(int id) {
        Log.d(getTag(), "onDataSendComplete(" + id + ")");
        mStatus = "Sent Data";
        trackBluetoothActivity();
    }

    @Override
    public void onCommandReceived(ConnectionCommand command) {
        Log.d(getTag(), "onCommandReceived(" + command.type + ")");
        mStatus = "Received Data";
        trackBluetoothActivity();
    }

    /**
     * Handle all internal and external messages
     */
    public boolean handleMessage(Message msg) {
        if (mRouterService == null) {
            Log.d(getTag(), "waiting for routerservice to bind");
            // wait for the router service to bind
            Message resend = msg.getTarget().obtainMessage();
            resend.copyFrom(msg);
            msg.getTarget().sendMessageDelayed(msg, 100);
        } else {
            Log.d(getTag(), "act on what=" + msg.what);
            if (msg.what == RouterService.ROUTER_MESSAGE_FORWARD_INTENT) {
                try {
                    mMessageId++;
                    Intent intent = (Intent) msg.obj;
                    String sendUri = intent.toUri(Intent.URI_INTENT_SCHEME);
                    mBTConnection.sendData(BLUETOOTH_COMMAND_PASS_INTENT, sendUri.getBytes(), mMessageId);
                } catch (Exception e) {
                    Log.e(getTag(), "failed to process ROUTER_MESSAGE_FORWARD_INTENT", e);
                }
                return true;
            }
        }
        return false;
    }

    // track router binding.
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
