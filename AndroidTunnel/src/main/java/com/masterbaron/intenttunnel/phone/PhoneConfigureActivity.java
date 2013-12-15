package com.masterbaron.intenttunnel.phone;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
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

public class PhoneConfigureActivity extends Activity {
    private static String TAG = PhoneConfigureActivity.class.getName();

    TextView textView;
    private boolean mPaused = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_config);
        textView = (TextView) findViewById(R.id.textView);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
            case R.id.start:
                BluetoothService.startServices(this);
                ActivityCompat.invalidateOptionsMenu(this);
                textView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ActivityCompat.invalidateOptionsMenu(PhoneConfigureActivity.this);
                    }
                }, 1000);
                return true;
            case R.id.stop:
                BluetoothService.stopServices(this);
                textView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ActivityCompat.invalidateOptionsMenu(PhoneConfigureActivity.this);
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

    public BluetoothDevice getClientDevice() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.getBondedDevices().size() > 0) {
            Set<BluetoothDevice> bondedDevices = defaultAdapter.getBondedDevices();
            if (bondedDevices != null && bondedDevices.size() > 0) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        if( device.getName().toLowerCase().contains("glass")) {
                            Log.d(TAG, "BT Device: " + device.getName());
                            return device;
                        }
                    }
                }
            }
        }
        return null;
    }
}
