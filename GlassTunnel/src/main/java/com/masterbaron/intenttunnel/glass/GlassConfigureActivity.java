package com.masterbaron.intenttunnel.glass;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.masterbaron.intenttunnel.R;
import com.masterbaron.intenttunnel.router.RouterService;

/**
 * Created by Van Etten on 12/2/13.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class GlassConfigureActivity extends Activity {
    private static String TAG = GlassConfigureActivity.class.getName();

    TextView textView;
    private boolean mPaused = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glass_config);
        textView = (TextView) findViewById(R.id.textView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        showServerState();
        openOptionsMenu();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
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
    public void onContextMenuClosed(Menu menu) {
        textView.setText("onContextMenuClosed()");
        this.finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
            case R.id.start:
                startService(new Intent(this, RouterService.class));
                invalidateOptionsMenu();
                textView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openOptionsMenu();
                    }
                }, 1000);
                return true;
            case R.id.stop:
                stopService(new Intent(this, RouterService.class));
                invalidateOptionsMenu();
                textView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openOptionsMenu();
                    }
                }, 1000);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showServerState() {
        boolean running = RouterService.isServicesRunning();

        if (running) {
            String text = "Running:";
            text += "\nClient Service: " + RouterService.getClientStatus();
            text += "\nServer Service: " + RouterService.getServerStatus();
            textView.setText(text);
        } else {
            textView.setText("Not Started");
        }

        if (!mPaused) {
            textView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showServerState();
                }
            }, 1000);
        }
    }
}
