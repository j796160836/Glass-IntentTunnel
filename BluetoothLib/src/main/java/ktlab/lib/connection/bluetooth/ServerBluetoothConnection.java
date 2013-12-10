package ktlab.lib.connection.bluetooth;

import java.nio.ByteOrder;
import java.util.UUID;

import ktlab.lib.connection.ConnectionCallback;
import android.os.Message;

public class ServerBluetoothConnection extends BluetoothConnection {

    public ServerBluetoothConnection(UUID uuid, ConnectionCallback cb, boolean canQueueing) {
        super(uuid, cb, canQueueing);
    }

    public ServerBluetoothConnection(UUID uuid, ConnectionCallback cb, boolean canQueueing, ByteOrder order) {
        super(uuid, cb, canQueueing, order);
    }

    @Override
    public void startConnection() {
        Message msg = obtainMessage(EVENT_CONNECT_COMPLETE);
        mConnectionThread = new ServerBluetoothConnectionThread(mUUID, msg);
        mConnectionThread.start();
    }
}
