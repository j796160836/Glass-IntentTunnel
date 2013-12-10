package com.masterbaron.intenttunnel.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.masterbaron.intenttunnel.R;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ClientBluetoothConnection;

/**
 * Created by Van Etten on 12/2/13.
 */
public class ClientService extends BluetoothService {
    private static final int MESSAGE_CHECK_TIMEOUT = 2300;
    private static final int INCOMING_MSG_INTENT_FORWARD = 0;

    private static long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    private static ClientService service;

    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private BluetoothDevice mDevice;
    private Queue<String> mSendMap = new LinkedList<String>();
    private Map<Integer, String> mReplyMap = new HashMap<Integer, String>();
    private int mMessageId = 0;

    private AtomicInteger binds = new AtomicInteger(0);

    @Override
    public void onCreate() {
        service = this;
        super.onCreate();

        startService(new Intent(this, ClientService.class));
    }

    /*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mMessageId++;
        String sendUri = intent.getStringExtra(Intent.EXTRA_INTENT);
        if (sendUri != null) {
            mBTConnection.sendData(COMMAND_PASS_INTENT, sendUri.getBytes(), mMessageId);
        }
        String ifSentUri = intent.getStringExtra(Intent.EXTRA_INTENT + ":CONFIRM");
        if (ifSentUri != null) {
            mReplyMap.put(mMessageId, ifSentUri);
        }

        return super.onStartCommand(intent, flags, startId);
    }
    */

    protected void stopConnection() {
        for (String uri : mReplyMap.values()) {
            notifyResult(uri, -1);
        }

        super.stopConnection();
        mReplyMap.clear();
        mMessageId = 0;
        stopSelf();
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

    protected void startConnection() {
        mReplyMap.clear();
        mMessageId = 0;
        super.startConnection();
    }

    @Override
    public void onDataSendComplete(int id) {
        super.onDataSendComplete(id);
        String result = mReplyMap.remove(id);
        if (result != null) {
            notifyResult(result, -1);
        }
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
                        if (binds.get() == 0) {
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

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INCOMING_MSG_INTENT_FORWARD:
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
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        binds.incrementAndGet();
        Log.d(getTag(), "onBind() binds=" + binds.get());
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        binds.decrementAndGet();
        Log.d(getTag(), "onUnbind() binds left=" + binds.get());
        return super.onUnbind(intent);
    }

    public static boolean isServiceRunning() {
        ClientService cs = ClientService.service;
        return (cs != null && cs.isRunning());
    }

    public static String getServiceStatus() {
        ClientService cs = ClientService.service;
        if ( cs != null ) {
            return cs.getStatus();
        }
        return "Never Started";
    }

    public BluetoothDevice getBTDevice() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.getBondedDevices().size() > 0) {
            Set<BluetoothDevice> bondedDevices = defaultAdapter.getBondedDevices();
            if (bondedDevices != null ) {
                if( bondedDevices.size() == 1 ) {
                    BluetoothDevice device = bondedDevices.iterator().next();
                    Log.d(getTag(), "BT Device: " + device.getName());
                    return device;
                } else if( bondedDevices.size() > 0 ) {
                    for (BluetoothDevice device : bondedDevices) {
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            if( device.getName().toLowerCase().contains("glass")) {
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
