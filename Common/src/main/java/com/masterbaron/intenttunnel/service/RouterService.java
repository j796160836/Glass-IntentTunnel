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
import android.os.RemoteException;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Van Etten on 12/9/13.
 */
public class RouterService extends Service {
    private static final String TAG = RouterService.class.getSimpleName();
    private static final int INCOMING_MSG_INTENT_FORWARD = 0;

    private final AtomicInteger binds = new AtomicInteger(0);
    private final Messenger mMessenger = new Messenger(new IncomingHandler());
    private final Queue<Message> mSendMap = new LinkedList<Message>();
    private Messenger mService = null;

    @Override
    public IBinder onBind(Intent intent) {
        int i = binds.getAndIncrement();
        if (i == 0) {
            // first one
            Log.d(TAG, "first onBind()");
            Intent in = new Intent();
            in.setClassName("com.masterbaron.intenttunnel", "com.masterbaron.intenttunnel.service.ClientService");
            bindService(in, mConnection, Context.BIND_AUTO_CREATE);
        } else {
            Log.d(TAG, i + " binds so far onBind()");
        }
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        int i = binds.decrementAndGet();
        if (i == 0) {
            // last one
            Log.d(TAG, "last onUnbind()");
            unbindService(mConnection);
        } else {
            Log.d(TAG, i + " binds left onUnbind()");
        }

        return super.onUnbind(intent);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected()");
            mService = new Messenger(service);

            for ( Message msg : mSendMap ) {
                try {
                    Log.e(TAG, "sending queued message");
                    Message routeMessage = Message.obtain();
                    routeMessage.copyFrom(msg);
                    mService.send(routeMessage);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to route message", e);
                }
            }
            mSendMap.clear();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(TAG, "onServiceDisconnected()");
            mService = null;
        }
    };

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INCOMING_MSG_INTENT_FORWARD:
                    if (mService == null) {
                        mSendMap.add(msg);
                    } else {
                        try {
                            Log.e(TAG, "queued message");
                            Message routeMessage = Message.obtain();
                            routeMessage.copyFrom(msg);
                            mService.send(routeMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to route message", e);
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
