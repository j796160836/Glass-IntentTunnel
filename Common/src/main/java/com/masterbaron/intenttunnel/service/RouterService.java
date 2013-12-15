package com.masterbaron.intenttunnel.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
public class RouterService extends Service {
    private static final String TAG = RouterService.class.getSimpleName();

    protected static final int ROUTER_MESSAGE_FORWARD_INTENT = 1000;
    protected static final int ROUTER_MESSAGE_CLIENT_FAILED = 1001;

    private final AtomicInteger binds = new AtomicInteger(0);
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final Queue<Message> mPendingMessages = new LinkedList<Message>();
    private Messenger mService = null;
    private ServiceConnection mConnection = null;

    @Override
    public void onCreate() {
        super.onCreate();

        // the ServerService should also always be running
        startService(new Intent(this, ServerService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // "prevent" this service from stopping!
    }

    @Override
    public IBinder onBind(Intent intent) {
        // track binds except ones coming from ClientService and ServiceService
        if ( !BluetoothService.class.getName().equals(intent.getAction())) {
            int i = binds.incrementAndGet();
            Log.d(TAG, i + " binds so far onBind()");
        }
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // track binds except ones coming from ClientService and ServiceService
        if ( !BluetoothService.class.getName().equals(intent.getAction())) {
            int i = binds.decrementAndGet();
            Log.d(TAG, i + " binds left onUnbind()");

            if ( binds.get() == 0 && mPendingMessages.size() == 0 ) {
                // no one left bound to the router and we don't have any pending messages
                unBindClientService();
            }
        }

        return super.onUnbind(intent);
    }

    /**
     * bind to the client if we haven't already
     * once bound, start sending messages to the ClientService
     */
    private void bindClientService() {
        if( mConnection == null ) {
            // setup the ClientService bind/unbind callback
            mConnection = new ServiceConnection() {
                public void onServiceConnected(ComponentName className, IBinder service) {
                    Log.d(TAG, "onClientServiceConnected()");
                    mService = new Messenger(service);

                    // not that we are bound, start sending pending messages
                    for ( Message msg : mPendingMessages) {
                        try {
                            Log.e(TAG, "sending queued message: " + msg.what);
                            Message routeMessage = Message.obtain();
                            routeMessage.copyFrom(msg);
                            mService.send(routeMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to route message", e);
                        }
                    }
                    mPendingMessages.clear();

                    if ( binds.get() == 0 ) {
                        // All packets are sent, and no one bound to the router
                        unBindClientService();
                    }
                }

                public void onServiceDisconnected(ComponentName className) {
                    Log.e(TAG, "onClientServiceDisconnected()");
                    mService = null;
                }
            };

            // bind the ClientService
            Log.d(TAG, "binding client service");
            Intent in = new Intent();
            in.setClass(RouterService.this, ClientService.class);
            bindService(in, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    /**
     * unbind to the ClientService if we are connected
     */
    private void unBindClientService() {
        if( mConnection != null ) {
            unbindService(mConnection);
            mConnection = null;
        }
    }

    /**
     * Handle all internal and external messages
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ROUTER_MESSAGE_CLIENT_FAILED) {
                //  restart the ClientService
                unBindClientService();
            } else if (msg.what == ROUTER_MESSAGE_FORWARD_INTENT) {
                // make sure the ClientService is started
                bindClientService();

                if (mService == null) {
                    // we are not connected to the ClientService yet, lets queue this message
                    Message routeMessage = Message.obtain();
                    routeMessage.copyFrom(msg);
                    Log.e(TAG, "service not ready, queued message: " + routeMessage.what);
                    mPendingMessages.add(routeMessage);
                } else {
                    // ready to send the message to the ClientService
                    try {
                        Log.e(TAG, "send message: " + msg.what);
                        Message routeMessage = Message.obtain();
                        routeMessage.copyFrom(msg);
                        mService.send(routeMessage);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to route message", e);
                    }
                }
            }
        }
    }
}
