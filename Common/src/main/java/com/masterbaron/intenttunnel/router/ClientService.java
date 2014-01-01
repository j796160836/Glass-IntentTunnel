package com.masterbaron.intenttunnel.router;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Message;
import android.util.Log;

import com.masterbaron.intenttunnel.R;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import ktlab.lib.connection.bluetooth.BluetoothConnection;
import ktlab.lib.connection.bluetooth.ClientBluetoothConnection;

/**
 * Created by Van Etten on 12/2/13.
 */
public class ClientService extends BluetoothService {

    private static ClientService service;

    private BluetoothDevice mDevice;

    public ClientService(RouterService routerService) {
        super(routerService);
    }

    @Override
    protected BluetoothConnection createNewBTConnection() {
        mDevice = getBTDevice();

        // only return a connection if we found a device
        if (mDevice != null) {
            Log.d(getTag(), "Binding to: " + mDevice.getName());
            return new ClientBluetoothConnection(UUID.fromString(mRouterService.getString(R.string.bluetooth_client_uuid)), this, false, mDevice);
        }
        return null;
    }

    // Look for a connected device with glass in the name, or the one and only bound device
    public BluetoothDevice getBTDevice() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && defaultAdapter.getBondedDevices().size() > 0) {
            Set<BluetoothDevice> bondedDevices = defaultAdapter.getBondedDevices();
            if (bondedDevices != null) {
                if (bondedDevices.size() == 1) {
                    BluetoothDevice device = bondedDevices.iterator().next();
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        Log.d(getTag(), "One BT Device: " + device.getName());
                        return device;
                    }
                } else if (bondedDevices.size() > 0) {
                    for (BluetoothDevice device : bondedDevices) {
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                            if (device.getName().toLowerCase().contains("glass")) {
                                Log.d(getTag(), "BT Device: " + device.getName());
                                return device;
                            }
                        }
                    }
                }
            }
        }
        Log.d(getTag(), "NO BT Device found");
        return null;
    }
}
