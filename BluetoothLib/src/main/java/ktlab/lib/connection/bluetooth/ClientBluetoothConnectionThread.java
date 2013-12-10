package ktlab.lib.connection.bluetooth;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.os.Message;

public class ClientBluetoothConnectionThread extends BluetoothConnectionThread {

    protected final BluetoothDevice mDevice;
    protected final UUID mUUID;

    public ClientBluetoothConnectionThread(UUID uuid, BluetoothDevice device, Message msg) {
        super(msg);
        mUUID = uuid;
        mDevice = device;
    }

    @Override
    protected void getSocket() {

        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(mUUID);
        } catch (IOException e) {
            mSocket = null;
            return;
        }

        int count = 0;
        do {
            try {
                if (mSocket != null) {
                    mSocket.connect();
                }
                break;
            } catch (IOException e) {
                // DO NOTHING
            }
            // retry
        } while (count++ < 5);
    }
}
