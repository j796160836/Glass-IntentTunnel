package com.masterbaron.intenttunnel.glass;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.masterbaron.intenttunnel.R;
import com.masterbaron.intenttunnel.service.BluetoothService;
import com.masterbaron.intenttunnel.service.ClientService;
import com.masterbaron.intenttunnel.service.ServerService;

import java.util.Set;

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
        boolean running = BluetoothService.areServicesRunning();

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
                BluetoothService.startServices(this);
                invalidateOptionsMenu();
                textView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openOptionsMenu();
                    }
                }, 1000);
                return true;
            case R.id.stop:
                BluetoothService.stopServices(this);
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
        boolean running = BluetoothService.areServicesRunning();

        if (running) {
            String text = "Running:";
            text += "\nClient Service: " + ClientService.getServiceStatus();
            text += "\nServer Service: " + ServerService.getServiceStatus();
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
