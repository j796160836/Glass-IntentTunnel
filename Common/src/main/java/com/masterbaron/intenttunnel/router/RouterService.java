package com.masterbaron.intenttunnel.router;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by Van Etten on 12/9/13.
 */
public class RouterService extends Service implements Handler.Callback {
    private static final String TAG = RouterService.class.getSimpleName();

    protected static final int ROUTER_MESSAGE_BROADCAST_INTENT = 1000;
    protected static final int ROUTER_MESSAGE_STARTSERVICE_INTENT = 1001;
    protected static final int ROUTER_MESSAGE_STARTACTIVITY_INTENT = 1002;

    protected static final int ROUTER_MESSAGE_SEND_QUEUED_MESSAGES = 1100;

    private static Boolean isGlass = null;
    private static RouterService service;

    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final LinkedList<Packet> mPackets = new LinkedList<Packet>();

    protected Handler mHandler;
    private ClientService mClientService;
    private ServerService mServerService;
    private long lastClientError;

    /**
     * Check if the service is still active
     *
     * @return
     */
    public static boolean isServicesRunning() {
        return service != null;
    }

    /**
     * Get the current status of the client bluetooth connection
     *
     * @return
     */
    public static String getClientStatus() {
        RouterService routerService = service;
        return routerService != null ? routerService.mClientService.getStatus() : "Stopped";
    }

    /**
     * Get the current status of the server bluetooth connection
     *
     * @return
     */
    public static String getServerStatus() {
        RouterService routerService = service;
        return routerService != null ? routerService.mServerService.getStatus() : "Stopped";
    }

    /**
     * Setup work for the start of the router service
     */
    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        service = this;

        getPreferences(this).registerOnSharedPreferenceChangeListener(mPreferenceHandler);

        startService(new Intent(this, RouterService.class));

        // setup handler and messenger
        mHandler = new Handler(this);

        mClientService = new ClientService(this);
        mServerService = new ServerService(this);

        mServerService.startConnection();
    }

    /**
     * cleanup the router service
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        getPreferences(this).unregisterOnSharedPreferenceChangeListener(mPreferenceHandler);
        mClientService.stop();
        mServerService.stop();
        service = null;
        super.onDestroy();
    }

    /**
     * Process incoming intents to the service
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                Log.d("onStartCommand", "intent=" + intent.toUri(0));
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    mServerService.stopConnection();
                    mServerService.startConnection();
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    mServerService.stopConnection();

                }
            }
        }
        return START_STICKY; // "prevent" this service from stopping!
    }

    /**
     * Return the binder for the router
     *
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    /**
     * Route a message to a bluetooth service.
     * If the bluetooth server is connected, send to that service
     * otherwise, send to the client bluetooth to be sent
     */
    protected void sentToService() {
        try {
            expirePackets();
            if ( mPackets.size() > 0 && isBluetoothEnabled() ) {
                if (mServerService.isConnected()) {
                    if ( !mServerService.isSending() ) {
                        Log.d(TAG, "sentToService: serviceService running");
                        mServerService.sendIntent(mPackets.poll());
                    }
                } else {
                    if (!mClientService.isRunning()) {
                        if ( lastClientError + 5000 < System.currentTimeMillis() ) {
                            Log.d(TAG, "sentToService: clientService not running");
                            mClientService.startConnection();
                        } else {
                            Log.d(TAG, "sentToService: clientService error delay");
                            mHandler.sendEmptyMessageDelayed(ROUTER_MESSAGE_SEND_QUEUED_MESSAGES, 1000);
                        }
                    } else if ( !mServerService.isSending() ) {
                        Log.d(TAG, "sentToService: sending to clientService");
                        mClientService.sendIntent(mPackets.poll());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to route message", e);
        }
    }

    private void expirePackets() {
        // now expire old ones
        Iterator<Packet> iterator = mPackets.iterator();
        while ( iterator.hasNext() ) {
            if ( iterator.next().isExpired() ) {
                iterator.remove();
            }
        }

        // cap size of queue
        while ( mPackets.size() > 100 ) {
            mPackets.removeFirst();
        }
    }

    /**
     * Hande messages coming from a 3rd party that has bound to the router.
     * @param msg
     * @return
     */
    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == ROUTER_MESSAGE_SEND_QUEUED_MESSAGES) {
            sentToService();
            return true;
        }
        return false;
    }

    private void processQueue() {
        mHandler.sendEmptyMessage(ROUTER_MESSAGE_SEND_QUEUED_MESSAGES);
    }

    protected void onIntentSendComplete(BluetoothService bluetoothService, Packet packet) {
        processQueue();
    }

    protected void onIntentSendFail(BluetoothService bluetoothService, Packet packet) {
        if ( !bluetoothService.isBTServer() ) {
            lastClientError = System.currentTimeMillis();
            if ( !mServerService.isConnected() ) {
                mServerService.onConnectionLost();
            }
        }
        if( packet != null ) {
            mPackets.addFirst(packet);
        }
        processQueue();
    }

    protected void onConnectComplete(BluetoothService bluetoothService) {
        processQueue();
    }

    /**
     * Handle all internal and external messages
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "send to service message: " + msg.what);
            if (msg.what == ROUTER_MESSAGE_BROADCAST_INTENT || msg.what == ROUTER_MESSAGE_STARTSERVICE_INTENT
                    || msg.what == ROUTER_MESSAGE_STARTACTIVITY_INTENT) {
                if ( msg.obj instanceof  Intent ) {
                    expirePackets();

                    Intent intent = (Intent) msg.obj;
                    Log.d(TAG, "send message: " + intent.toUri(0));
                    mPackets.add( new Packet(msg.what, intent) );
                    processQueue();
                }
            }
        }
    }

    public boolean isBluetoothEnabled() {
        // if no bluetooth or off, then just stop since we cant do anything
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();

        if (defaultAdapter == null || !defaultAdapter.isEnabled()) {
            Log.d(TAG, "bluetooth adapter disabled");
            return false;
        }
        return true;
    }

    public static boolean isGlass() {
        if( isGlass == null ) {
            try {
                RouterService.class.getClassLoader().loadClass("com.google.android.glass.timeline.LiveCard");
                isGlass = true;
            } catch (Exception e) {
                isGlass = false;
            }
        }

        return isGlass;
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences("Router", Context.MODE_PRIVATE);
    }

    public static void setDeviceAddress(Context context, String address) {
        SharedPreferences.Editor edit = getPreferences(context).edit();
        edit.putString("bt.device.address", address);
        edit.commit();
    }
    public static String getDeviceAddress(Context context) {
        return getPreferences(context).getString("bt.device.address", null);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceHandler = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if( "bt.device.address".equals(key)) {
                mClientService.onConnectionLost();
            }
        }
    };
}
