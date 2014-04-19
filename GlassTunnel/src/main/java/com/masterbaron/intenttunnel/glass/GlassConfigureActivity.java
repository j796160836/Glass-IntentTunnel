package com.masterbaron.intenttunnel.glass;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.masterbaron.intenttunnel.router.RouterService;

/**
 * Created by Van Etten on 12/2/13.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class GlassConfigureActivity extends Activity {
    private static String TAG = GlassConfigureActivity.class.getName();

    private TextView textView;
    private View mProgress;
    private boolean mPaused = true;
    private boolean waitForAction = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glass_config);
        textView = (TextView) findViewById(R.id.textView);
        mProgress = findViewById(R.id.progressBar);
        mProgress.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        waitForAction = false;
        showServerState();
        textView.post(new Runnable() {
            public void run() {
                openOptionsMenu();
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.glass_menu, menu);
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
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        if (!waitForAction) {
            this.finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        long itemId = item.getItemId();

        // Handle item selection.
        if (itemId == R.id.start) {
            waitForAction = true;
            mProgress.setVisibility(View.VISIBLE);
            startService(new Intent(this, RouterService.class));
            invalidateOptionsMenu();
            textView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    waitForAction = false;
                    openOptionsMenu();
                    mProgress.setVisibility(View.INVISIBLE);
                }
            }, 1000);
            return true;
        } else if (itemId == R.id.stop) {
            waitForAction = true;
            mProgress.setVisibility(View.VISIBLE);
            stopService(new Intent(this, RouterService.class));
            invalidateOptionsMenu();
            textView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    waitForAction = false;
                    openOptionsMenu();
                    mProgress.setVisibility(View.INVISIBLE);
                }
            }, 1000);
            return true;
        } else if (itemId == R.id.device) {
            waitForAction = true;
            startActivity(new Intent(this, DeviceSelectActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showServerState() {
        if (!mPaused) {
            boolean running = RouterService.isServicesRunning();

            if (running) {
                String text = "Running:";
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
