package com.masterbaron.musicpusher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

import java.net.BindException;

public class MusicActivity extends Activity {
    private static String TAG = MusicActivity.class.getName();

    public Messenger mService;
    public boolean mBound;
    private TextView mErrorText;
    private String mVoiceCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mErrorText = (TextView) findViewById(R.id.errorText);

        try {
            Log.d(TAG, "ActivityInfo");
            ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), 0);
            mVoiceCommand = activityInfo.name.substring(activityInfo.name.lastIndexOf(".") + 1);
            mErrorText.setText(activityInfo.loadLabel(getPackageManager()));
            Log.d(TAG, "mVoiceCommand=" + mVoiceCommand);
        } catch (Exception e) {
            mErrorText.setText("Error: " + e.getMessage());
            Log.e(TAG, "onCreate error", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            Intent in = new Intent();
            in.setClassName("com.masterbaron.intenttunnel", "com.masterbaron.intenttunnel.router.RouterService");
            if ( !bindService(in, mConnection, Context.BIND_AUTO_CREATE) ) {
                throw new BindException("failed to bind");
            }
        } catch (Exception e) {
            mErrorText.setText("Error: " + e.getMessage());
            Log.e(TAG, "onStart error", e);
        }
    }

    @Override
    protected void onStop() {
        if ( mBound ) {
            unbindService(mConnection);
            mBound = false;
        }
        super.onStop();
    }

    private void sendCommand(Intent action) throws RemoteException {
        Log.d("MusicPusher", action.toUri(Intent.URI_INTENT_SCHEME));
        mService.send(Message.obtain(null, 1000, action));
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected()");

            mService = new Messenger(service);
            mBound = true;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    actOn(mVoiceCommand);
                }
            });
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e(TAG, "onServiceDisconnected()");

            mService = null;
            mBound = false;
        }
    };

    private void actOn(String command) {
        try {
            Intent i = new Intent("com.android.music.musicservicecommand");
            if ("play".equals(command)) {
                // start google music since play won't start unless it's been activated
                Intent start = new Intent("android.intent.action.MAIN");
                start.setPackage("com.google.android.music");
                start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mService.send(Message.obtain(null, 1002, start));
                Thread.sleep(2000);

                i.putExtra("command", "play");
                sendCommand(i);
            } else if ("pause".equals(command)) {
                i.putExtra("command", "pause");
                sendCommand(i);
            } else if ("next".equals(command)) {
                i.putExtra("command", "next");
                sendCommand(i);
            } else if ("back".equals(command)) {
                i.putExtra("command", "previous");
                sendCommand(i);
            } else if ("toggle".equals(command)) {
                i.putExtra("command", "togglepause");
                sendCommand(i);
            } else if ("restart".equals(command)) {
                i.putExtra("command", "stop");
                sendCommand(i);
                i.putExtra("command", "play");
                sendCommand(i);
            } else if ("previous".equals(command)) {
                i.putExtra("command", "stop");
                sendCommand(i);
                i.putExtra("command", "play");
                sendCommand(i);
                i.putExtra("command", "previous");
                sendCommand(i);
            } else {
                throw new IllegalArgumentException("invalid voice command: " + command);
            }
            this.finish();
        }
        catch ( Exception e) {
            Log.e(TAG, "Failed: ", e);
            mErrorText.setText("Error: " + e.getMessage());
        }

        if ( mBound ) {
            unbindService(mConnection);
            mBound = false;
        }
    }
}
