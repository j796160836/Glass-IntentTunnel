package com.masterbaron.intenttunnel.router;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Van Etten on 12/9/13.
 */
public class RouterService extends Service implements Handler.Callback {
    private static final String TAG = RouterService.class.getSimpleName();

    protected static final int ROUTER_MESSAGE_BROADCAST_INTENT = 1000;
    protected static final int ROUTER_MESSAGE_STARTSERVICE_INTENT = 1001;
    protected static final int ROUTER_MESSAGE_EMPTY_REROUTE = 1002;

    private static RouterService service;

    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final Queue<Message> mPendingMessages = new LinkedList<Message>();

    protected Handler mHandler;
    private ClientService mClientService;
    private ServerService mServerService;

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
            Log.d("onStartCommand", "intent=" + intent.toUri(0));
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
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
     * @param msg
     */
    protected void sentToService(Message msg) {
        try {
            Message routeMessage = Message.obtain();
            routeMessage.copyFrom(msg);
            if (mServerService.isConnected()) {
                mServerService.send(routeMessage);
            } else {
                boolean queueMessage = false;
                if (!mClientService.isRunning()) {
                    if (System.currentTimeMillis() - mClientService.getLastFailTime() > 5000) {
                        mClientService.startConnection();
                    } else {
                        queueMessage = true;
                    }
                }
                if (queueMessage) {
                    mHandler.sendMessageDelayed(routeMessage, 1000);
                } else {
                    mClientService.send(routeMessage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to route message", e);
        }
    }

    /**
     * Hande messages coming from a 3rd party that has bound to the router.
     * @param msg
     * @return
     */
    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == ROUTER_MESSAGE_EMPTY_REROUTE) {
            if (!mServerService.isConnected()) {
                //Log.e(TAG, "handleMessage: restart server");
                //mServerService.stopConnection();
                //mServerService = new ServerService(this);
                //mServerService.startConnection();
            }
        } else if (msg.what == ROUTER_MESSAGE_BROADCAST_INTENT || msg.what == ROUTER_MESSAGE_STARTSERVICE_INTENT) {
            // ready to send the message to the ClientService
            Log.e(TAG, "send message: " + msg.what);
            sentToService(msg);
            return true;
        }
        return false;
    }

    /**
     * Handle all internal and external messages
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.e(TAG, "send to service message: " + msg.what);
            RouterService.this.handleMessage(msg);
        }
    }
}
