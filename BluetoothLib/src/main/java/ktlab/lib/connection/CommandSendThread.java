package ktlab.lib.connection;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

import android.os.Message;
import android.util.Log;

public class CommandSendThread extends Thread {

    private OutputStream mOut;
    private Message mMessage;
    private ConnectionCommand mCommand;
    private ByteOrder mOrder;

    public CommandSendThread(OutputStream out, Message msg, ByteOrder order) {
        mOut = out;
        mCommand = (ConnectionCommand) msg.obj;
        mMessage = msg;
        mOrder = order;
    }

    @Override
    public void run() {
        try {
            mOut.write(ConnectionCommand.toByteArray(mCommand, mOrder));
        } catch (Exception e) {
            Log.e("CommandSendThread", "error", e);
            mMessage.what = Connection.EVENT_CONNECTION_SEND_FAIL;
        }

        mMessage.sendToTarget();
    }
}
