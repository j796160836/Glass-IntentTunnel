package com.masterbaron.intenttunnel.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.masterbaron.intenttunnel.android.R;
import com.masterbaron.intenttunnel.router.RouterService;

public class AndroidConfigureActivity extends Activity {
    private static String TAG = AndroidConfigureActivity.class.getName();

    private TextView textView;
    private View mProgress;
    private boolean mPaused = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_config);
        textView = (TextView) findViewById(R.id.textView);
        mProgress = findViewById(R.id.progressBar);
        mProgress.setVisibility(View.INVISIBLE);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        showServerState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.android_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean running = RouterService.isServicesRunning();

        menu.findItem(R.id.start).setVisible(!running);
        menu.findItem(R.id.stop).setVisible(running);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        long itemId = item.getItemId();
        if (itemId == R.id.start) {
            mProgress.setVisibility(View.VISIBLE);
            startService(new Intent(this, RouterService.class));
            ActivityCompat.invalidateOptionsMenu(this);
            textView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ActivityCompat.invalidateOptionsMenu(AndroidConfigureActivity.this);
                    mProgress.setVisibility(View.INVISIBLE);
                }
            }, 1000);
            return true;
        } else if (itemId == R.id.stop) {
            mProgress.setVisibility(View.VISIBLE);
            stopService(new Intent(this, RouterService.class));
            textView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ActivityCompat.invalidateOptionsMenu(AndroidConfigureActivity.this);
                    mProgress.setVisibility(View.INVISIBLE);
                }
            }, 1000);
            return true;
        } else if (itemId == R.id.device) {
            startActivity(new Intent(this, DeviceSelectActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showServerState() {
        if (!mPaused) {
            boolean running = RouterService.isServicesRunning();

            if (running) {
                String text = "Statuses:";
                text += "\nClient Service: " + RouterService.getClientStatus();
                text += "\nServer Service: " + RouterService.getServerStatus();
                textView.setText(text);
            } else {
                textView.setText("Not Started");
            }

            textView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showServerState();
                }
            }, 1000);
        }
    }
}
