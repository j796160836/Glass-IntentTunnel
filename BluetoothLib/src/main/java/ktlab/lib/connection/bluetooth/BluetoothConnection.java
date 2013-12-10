package ktlab.lib.connection.bluetooth;

import java.nio.ByteOrder;
import java.util.UUID;

import android.os.Message;

import ktlab.lib.connection.Connection;
import ktlab.lib.connection.ConnectionCallback;

public abstract class BluetoothConnection extends Connection {
    /*
    protected static final UUID SERVICE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); */

    protected final UUID mUUID;

    public BluetoothConnection(UUID uuid, ConnectionCallback cb, boolean canQueueing) {
        this(uuid, cb, canQueueing, ByteOrder.nativeOrder());
    }

    public BluetoothConnection(UUID uuid, ConnectionCallback cb, boolean canQueueing, ByteOrder order) {
        super(cb, canQueueing, order);
        mUUID = uuid;
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
    }
}
