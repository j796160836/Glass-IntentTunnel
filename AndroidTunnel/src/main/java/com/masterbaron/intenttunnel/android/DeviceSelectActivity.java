package com.masterbaron.intenttunnel.android;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.masterbaron.intenttunnel.router.RouterService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Van Etten on 1/4/14.
 */
public class DeviceSelectActivity extends ListActivity {
    private static String TAG = DeviceSelectActivity.class.getName();
    List<BluetoothDevice> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<String> deviceNames = new ArrayList<String>();
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null) {
            devices = new ArrayList(defaultAdapter.getBondedDevices());
            for ( BluetoothDevice device : devices ) {
                deviceNames.add(device.getName());
            }
        }

        if (deviceNames.size() == 0) {
            Toast.makeText(this, "Unable to locate and Bluetooth Devices", Toast.LENGTH_SHORT).show();
            this.finish();
        } else {
            ListAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, deviceNames);
            this.setListAdapter(adapter);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        RouterService.setDeviceAddress(this, devices.get(position).getAddress());
        this.finish();
    }
}
