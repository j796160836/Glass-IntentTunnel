package com.masterbaron.intenttunnel.router;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import ktlab.lib.connection.ConnectionCallback;
import ktlab.lib.connection.ConnectionCommand;
import ktlab.lib.connection.PendingData;
import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ServerBluetoothConnection;

/**
 * Created by Van Etten on 12/6/13.
 */
public abstract class BluetoothService implements ConnectionCallback, Handler.Callback {
    private static final String ENCODER_KEY_PREFIX = "IntentTunnel[byte]";
    private static final String ENCODER_KEY_LIST_STRING = "IntentTunnel.StringList";
    private static final String ENCODER_KEY_LIST_INTEGER = "IntentTunnel.IntegerList";

    protected static byte BLUETOOTH_COMMAND_BROADCAST_INTENT = 100;
    protected static byte BLUETOOTH_COMMAND_STARTSERVICE_INTENT = 101;

    protected static int failedRetries = 0;
    protected Handler mHandler;
    protected BluetoothConnection mBTConnection;
    protected RouterService mRouterService;

    private String mStatus = "Stopped";
    private boolean isEnabled = true;
    private boolean isRunning = false;
    private boolean isConnected = false;
    private long lastActivity = 0;
    private long lastFailTime = 0;
    private int mMessageId = 0;

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

    public long getLastActivity() {
        return lastActivity;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public BluetoothService(RouterService routerService) {
        Log.d(getTag(), "created()");
        this.mRouterService = routerService;

        mStatus = "Starting";

        // setup handler and messenger
        mHandler = new Handler(this);
    }

    public boolean isBluetoothEnabled() {
        // if no bluetooth or off, then just stop since we cant do anything
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter == null || !defaultAdapter.isEnabled()) {
            Log.d(getTag(), "bluetooth adapter disabled");
            return false;
        }
        return true;
    }

    public void stop() {
        Log.d(getTag(), "stopped()");
        mStatus = "Stopping";
        isEnabled = false;
        stopConnection();
        mStatus = "Stopped";
    }

    /**
     * take and messages we haven't sent over bluetooth and send them back to the router.
     */
    private void backToRouter(Queue<PendingData> left) {
        Log.e(getTag(), "reroute: " + left);
        if (left != null) {
            for (PendingData data : left) {
                if (data.getCommand().type == BLUETOOTH_COMMAND_BROADCAST_INTENT) {
                    try {
                        byte[] bytes = data.getCommand().option;
                        Intent intent = Intent.parseUri(new String(bytes), Intent.URI_INTENT_SCHEME);
                        Message msg = Message.obtain(null, RouterService.ROUTER_MESSAGE_BROADCAST_INTENT, intent);
                        mRouterService.sentToService(msg);
                        Log.e(getTag(), "rerouted " + msg);
                    } catch (Exception e) {
                        Log.e(getTag(), "failed to resend intent", e);
                    }
                } else if (data.getCommand().type == BLUETOOTH_COMMAND_STARTSERVICE_INTENT) {
                    try {
                        byte[] bytes = data.getCommand().option;
                        Intent intent = Intent.parseUri(new String(bytes), Intent.URI_INTENT_SCHEME);
                        Message msg = Message.obtain(null, RouterService.ROUTER_MESSAGE_STARTSERVICE_INTENT, intent);
                        mRouterService.sentToService(msg);
                        Log.e(getTag(), "rerouted " + msg);
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
            mBTConnection = null;
        }
        mMessageId = 0;
        isRunning = false;
        isConnected = false;
        mStatus = "Disconnected";
    }

    protected boolean startConnection() {
        mStatus = "Connecting";
        mMessageId = 0;
        if (isBluetoothEnabled()) {
            mBTConnection = createNewBTConnection();
        }
        if (mBTConnection != null) {
            // if this is a server, then we will be waiting for a connection
            if (mBTConnection instanceof ServerBluetoothConnection) {
                mStatus = "Waiting for connection";
            }

            isRunning = true;
            mBTConnection.startConnection();
            return true;
        } else {
            mStatus = "BT device failure";
        }
        return false;
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
        lastFailTime = System.currentTimeMillis();

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

        if (command.type == BLUETOOTH_COMMAND_BROADCAST_INTENT) {
            broadcast(command.option);
        } else if (command.type == BLUETOOTH_COMMAND_STARTSERVICE_INTENT) {
            startService(command.option);
        }

        mStatus = "Received Data";
        trackBluetoothActivity();
    }

    /**
     * Handle all internal and external messages
     */
    public boolean handleMessage(Message msg) {
        Log.d(getTag(), "act on what=" + msg.what);
        if (msg.what == RouterService.ROUTER_MESSAGE_BROADCAST_INTENT) {
            try {
                mMessageId++;
                Intent intent = (Intent) msg.obj;
                String sendUri = encodeIntent(intent);
                Log.e(getTag(), "sending intent: " + intent);
                Log.e(getTag(), "       of size: " + sendUri.length());

                mBTConnection.sendData(BLUETOOTH_COMMAND_BROADCAST_INTENT, sendUri.getBytes(), mMessageId);
            } catch (Exception e) {
                Log.e(getTag(), "failed to process ROUTER_MESSAGE_FORWARD_INTENT", e);
            }
            return true;
        } else if (msg.what == RouterService.ROUTER_MESSAGE_STARTSERVICE_INTENT) {
            try {
                mMessageId++;
                Intent intent = (Intent) msg.obj;
                String sendUri = encodeIntent(intent);
                mBTConnection.sendData(BLUETOOTH_COMMAND_STARTSERVICE_INTENT, sendUri.getBytes(), mMessageId);
            } catch (Exception e) {
                Log.e(getTag(), "failed to process ROUTER_MESSAGE_FORWARD_INTENT", e);
            }
            return true;
        }
        return false;
    }

    public void send(Message msg) {
        mHandler.sendMessage(msg);
    }

    public long getLastFailTime() {
        return lastFailTime;
    }

    protected void broadcast(byte[] option) {
        try {
            String uri = new String(option);
            Intent intent = decodeIntent(uri);
            Log.d(getTag(), "Broadcasting Intent: " + intent);
            mRouterService.sendBroadcast(intent);
        } catch (URISyntaxException e) {
            Log.e(getTag(), "Invalid URI", e);
        }
    }

    protected void startService(byte[] option) {
        try {
            String uri = new String(option);
            Intent intent = decodeIntent(uri);
            Log.d(getTag(), "startService Intent: " + intent);
            mRouterService.startService(intent);
        } catch (URISyntaxException e) {
            Log.e(getTag(), "Invalid URI", e);
        }
    }

    protected String encodeIntent( Intent intent ) {
        Bundle extras = intent.getExtras();
        Set<String> keys = extras.keySet();
        for ( String key : keys ) {
            Object value = extras.get(key);
            if ( value instanceof byte[] ) {
                String encoded = Base64.encodeToString((byte[]) value, 0);
                intent.putExtra(ENCODER_KEY_PREFIX + key, encoded);
                intent.removeExtra(key);
            }
            else if ( value instanceof List) {
                boolean stringList = (extras.getStringArrayList(key) != null);
                boolean intList = (extras.getIntegerArrayList(key) != null);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream out = new ObjectOutputStream(baos);
                    out.writeObject((List) value);
                    out.close();
                    baos.close();

                    String encoded = Base64.encodeToString(baos.toByteArray(), 0);
                    intent.removeExtra(key);

                    if( stringList ) {
                        intent.putExtra(ENCODER_KEY_LIST_STRING + key, encoded);
                    } if( intList ) {
                        intent.putExtra(ENCODER_KEY_LIST_INTEGER + key, encoded);
                    }
                } catch (IOException e) {
                    Log.e(getTag(), "Invalid URI", e);
                }
            }
        }

        return intent.toUri(Intent.URI_INTENT_SCHEME);
    }

    protected Intent decodeIntent(String uri) throws URISyntaxException {
        Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);

        Bundle extras = intent.getExtras();
        Set<String> keys = extras.keySet();
        for ( String key : keys ) {
            if ( key.startsWith(ENCODER_KEY_PREFIX)) {
                String newKey = key.substring(ENCODER_KEY_PREFIX.length());
                intent.putExtra(newKey, Base64.decode(extras.getString(key), 0));
                intent.removeExtra(key);
            }
            else if ( key.startsWith(ENCODER_KEY_LIST_STRING) || key.startsWith(ENCODER_KEY_LIST_INTEGER)) {
                try {
                    String newKey;
                    if ( key.startsWith(ENCODER_KEY_LIST_STRING) ) {
                        newKey = key.substring(ENCODER_KEY_LIST_STRING.length());
                    } else if ( key.startsWith(ENCODER_KEY_LIST_INTEGER) ) {
                        newKey = key.substring(ENCODER_KEY_LIST_INTEGER.length());
                    } else {
                        throw new IOException("Invalid URI");
                    }

                    ByteArrayInputStream bais = new ByteArrayInputStream( Base64.decode(extras.getString(key), 0));
                    ObjectInputStream in = new ObjectInputStream(bais);
                    ArrayList<?> list = (ArrayList<?>) in.readObject();
                    intent.removeExtra(key);
                    if ( key.startsWith(ENCODER_KEY_LIST_STRING) ) {
                        intent.putExtra(newKey, (ArrayList<String>) list);
                    } else if ( key.startsWith(ENCODER_KEY_LIST_INTEGER) ) {
                        intent.putExtra(newKey, (ArrayList<Integer>) list);
                    }
                } catch (IOException e) {
                    Log.e(getTag(), "Invalid URI", e);
                } catch (ClassNotFoundException e) {
                    Log.e(getTag(), "Invalid URI", e);
                }
            }
            else if ( key.startsWith(ENCODER_KEY_LIST_INTEGER)) {
                String newKey = key.substring(ENCODER_KEY_LIST_INTEGER.length());
                byte[] list = Base64.decode(extras.getString(key), 0);
                intent.removeExtra(key);
            }
        }
        return intent;
    }
}
