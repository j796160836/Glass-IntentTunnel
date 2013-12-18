package com.masterbaron.intenttunnel.router;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Van Etten on 12/9/13.
 */
public class RouterService extends Service implements Handler.Callback {
    private static final String TAG = RouterService.class.getSimpleName();

    protected static final int ROUTER_MESSAGE_BROADCAST_INTENT = 1000;
    protected static final int ROUTER_MESSAGE_STARTSERVICE_INTENT = 1001;

    private static RouterService service;

    private final AtomicInteger binds = new AtomicInteger(0);
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final Queue<Message> mPendingMessages = new LinkedList<Message>();
    protected Handler mHandler;
    private ClientService mClientService;
    private ServerService mServerService;

    public static boolean isServicesRunning() {
        return service != null;
    }

    public static String getClientStatus() {
        RouterService routerService = service;
        return routerService != null?routerService.mClientService.getStatus():"Stopped";
    }

    public static String getServerStatus() {
        RouterService routerService = service;
        return routerService != null?routerService.mServerService.getStatus():"Stopped";
    }

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

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        mClientService.stop();
        mServerService.stop();
        service = null;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // "prevent" this service from stopping!
    }

    @Override
    public IBinder onBind(Intent intent) {
        int i = binds.incrementAndGet();
        Log.d(TAG, i + " binds so far onBind()");
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        int i = binds.decrementAndGet();
        Log.d(TAG, i + " binds left onUnbind()");

        return super.onUnbind(intent);
    }

    protected boolean isBound() {
        return binds.get() > 0;
    }

    private void sendQueuedMessages() {
        // not that we are bound, start sending pending messages
        for (Message msg : mPendingMessages) {
            Log.e(TAG, "sending queued message: " + msg.what);
            sentToService(msg);
        }
        mPendingMessages.clear();
    }

    protected void sentToService(Message msg) {
        try {
            Message routeMessage = Message.obtain();
            routeMessage.copyFrom(msg);
            if (mServerService.isConnected()) {
                mServerService.send(routeMessage);
            } else {
                boolean queueMessage = false;;
                if (!mClientService.isRunning()) {
                    if (System.currentTimeMillis() - mClientService.getLastFailTime() > 5000) {
                        mClientService.startConnection();
                    } else {
                        queueMessage = true;
                    }
                }
                if ( queueMessage ) {
                    mHandler.sendMessageDelayed(routeMessage, 1000);
                } else {
                    mClientService.send(routeMessage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to route message", e);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == ROUTER_MESSAGE_BROADCAST_INTENT || msg.what == ROUTER_MESSAGE_STARTSERVICE_INTENT) {
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
