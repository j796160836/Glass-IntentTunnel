package ktlab.lib.connection.bluetooth;

import java.nio.ByteOrder;
import java.util.UUID;

import ktlab.lib.connection.ConnectionCallback;
import android.bluetooth.BluetoothDevice;
import android.os.Message;

public class ClientBluetoothConnection extends BluetoothConnection {

    private final BluetoothDevice mDevice;

    /**
     * create Bluetooth socket
     */
    public ClientBluetoothConnection(UUID uuid, ConnectionCallback callback,
            boolean canQueueing, BluetoothDevice device) {
        super(uuid, callback, canQueueing);
        mDevice = device;
    }

    public ClientBluetoothConnection(UUID uuid, ConnectionCallback callback,
            boolean canQueueing, ByteOrder order, BluetoothDevice device) {
        super(uuid, callback, canQueueing, order);
        mDevice = device;
    }

    public void startConnection() {
        Message msg = obtainMessage(EVENT_CONNECT_COMPLETE);
        mConnectionThread = new ClientBluetoothConnectionThread(mUUID, mDevice, msg);
        mConnectionThread.start();
    }
}
