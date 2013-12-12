package ktlab.lib.connection;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public abstract class Connection extends Handler {

    private static final String TAG = "Connection";

    // Event
    public static final int EVENT_CONNECT_COMPLETE = 1;
    public static final int EVENT_DATA_RECEIVED = 2;
    public static final int EVENT_DATA_SEND_COMPLETE = 3;
    public static final int EVENT_CONNECT_PING = 4;
    public static final int EVENT_CONNECTION_FAIL = 101;

    private static final long PING_INTERVAL = 1000;
    private static final long PING_LATE_TIME = 4000;
    private static final long PING_WORRY_TIME = 3000;

    // Event
    private static byte PING = Byte.MAX_VALUE;
    private static int PING_ID = Integer.MAX_VALUE;

    protected ConnectionCallback mCallback;

    // communication thread
    protected ConnectionThread mConnectionThread;
    protected CommandReceiveThread mReceiveThread;
    protected CommandSendThread mSendThread;

    // stream
    protected InputStream mInput;
    protected OutputStream mOutput;

    // send/close flag
    private boolean isSending = false;
    private boolean forceStop = false;

    // send data queue
    private final boolean canQueueing;
    private Queue<PendingData> mQueue = null;
    private final ByteOrder mOrder;

    // ping
    private long mLastActivity;
    private boolean hasOpenConnection = false;

    @Override
    public void handleMessage(Message msg) {

        if (forceStop) {
            mConnectionThread.close();
            return;
        }

        switch (msg.what) {
            case EVENT_CONNECT_COMPLETE:
                Log.i(TAG, "connect complete");
                mInput = mConnectionThread.getInputStream();
                mOutput = mConnectionThread.getOutputStream();
                //mCallback.onConnectComplete();

                // receive thread starting
                mReceiveThread = new CommandReceiveThread(mInput, obtainMessage(EVENT_DATA_RECEIVED),
                        mOrder);
                mReceiveThread.start();

                mLastActivity = System.currentTimeMillis();
                sendEmptyMessageDelayed(EVENT_CONNECT_PING, PING_INTERVAL);

                // if queueing data exists, send first data
                sendPendingData();

                break;

            case EVENT_DATA_RECEIVED:
                Log.i(TAG, "data received");
                mLastActivity = System.currentTimeMillis();

                if ( !hasOpenConnection) {
                    hasOpenConnection = true;
                    mCallback.onConnectComplete();
                }

                ConnectionCommand cmd = (ConnectionCommand) msg.obj;
                if (cmd.type == PING) {
                    Log.v(TAG, "data received: ping");
                } else {
                    mCallback.onCommandReceived(cmd);
                }

                // receive thread starting
                mReceiveThread = null;
                mReceiveThread = new CommandReceiveThread(mInput, obtainMessage(EVENT_DATA_RECEIVED),
                        mOrder);
                mReceiveThread.start();

                break;

            case EVENT_DATA_SEND_COMPLETE:
                int id = msg.arg1;

                if( id != PING_ID ) {
                    Log.i(TAG, "data send complete, id : " + id);
                } else {
                    Log.v(TAG, "data send complete, id : " + id);
                }

                mSendThread = null;
                isSending = false;

                mLastActivity = System.currentTimeMillis();
                if ( !hasOpenConnection) {
                    hasOpenConnection = true;
                    mCallback.onConnectComplete();
                }

                if (id != PING_ID) {
                    mCallback.onDataSendComplete(id);
                } else {
                    Log.v(TAG, "send complete: ping");
                }

                // if queueing data exists, send first data
                sendPendingData();

                break;

            case EVENT_CONNECT_PING:
                Log.v(TAG, "connect ping");
                try {
                    long timeSinceActivity = System.currentTimeMillis() - mLastActivity;
                    if (timeSinceActivity > PING_LATE_TIME) {
                        throw new TimeoutException("late ping");
                    } else if ( !isSending && mQueue.size() == 0 && timeSinceActivity > PING_WORRY_TIME ) {
                        mInput.available();
                        mOutput.flush();
                        sendData(PING, PING_ID);
                    }
                    sendEmptyMessageDelayed(EVENT_CONNECT_PING, PING_INTERVAL);
                } catch (Exception e) {
                    mSendThread = null;
                    isSending = false;
                    Queue<PendingData> left = getPendingDatas(msg);
                    if ( !hasOpenConnection) {
                        Log.e(TAG, "connection failed", e);
                        mCallback.onConnectionFailed(left);
                    } else {
                        Log.e(TAG, "connection lost", e);
                        mCallback.onConnectionLost(left);
                    }
                }
                break;

            case EVENT_CONNECTION_FAIL:
                mSendThread = null;
                isSending = false;
                Queue<PendingData> left = getPendingDatas(msg);
                if ( !hasOpenConnection) {
                    Log.e(TAG, "connection failed");
                    mCallback.onConnectionFailed(left);
                    break;
                } else {
                    Log.e(TAG, "connection lost");
                    mCallback.onConnectionLost(left);
                }
                break;

            default:
                Log.e(TAG, "Unknown Event:" + msg.what);
        }
    }

    private Queue<PendingData> getPendingDatas(Message msg) {
        Queue<PendingData> left = new LinkedList<PendingData>();
        left.add(new PendingData(msg.arg1, (ConnectionCommand)msg.obj));
        for ( PendingData data : mQueue ) {
            if ( data.id != PING_ID ) {
                left.add(data);
            }
        }
        return left;
    }

    /**
     * Constructor
     *
     * @param cb          callback for communication result
     * @param canQueueing true if can queue sending data
     * @param order       byte order of the destination
     */
    protected Connection(ConnectionCallback cb, boolean canQueueing, ByteOrder order) {
        mCallback = cb;

        this.canQueueing = canQueueing;
        this.mQueue = new LinkedList<PendingData>();

        mOrder = order;
    }

    /**
     * stop connection. this method must be called when application will stop
     * connection
     */
    public void stopConnection() {
        forceStop = true;

        // stop connection thread
        mConnectionThread.close();

        // stop receive thread
        if (mReceiveThread != null) {
            mReceiveThread.forceStop();
            mReceiveThread = null;
        }

        // stop send thread
        mSendThread = null;
        clearQueuedData();

        mInput = null;
        mOutput = null;
        hasOpenConnection = false;
    }

    public boolean resendData(PendingData data) {
        return sendData(data.getCommand().type, data.getCommand().option, data.getId());
    }

    /**
     * @param type command type
     * @param data option data
     * @param id   send id
     * @return return true if success sending or queueing data. if "canQueueing"
     * is false and sending any data, return false.
     */
    public boolean sendData(byte type, byte[] data, int id) {

        // if sending data, queueing...
        if (isSending || !hasOpenConnection) {
            if (canQueueing && ( !hasOpenConnection && type != PING)) {
                synchronized (mQueue) {
                    PendingData p = new PendingData(id, new ConnectionCommand(type, data));
                    mQueue.offer(p);
                }
                Log.i(TAG, "sendData(), pending...");
                return true;
            } else {
                return false;
            }
        }

        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;
        msg.obj = new ConnectionCommand(type, data);
        mSendThread = new CommandSendThread(mOutput, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * @param type command type
     * @param id   send id
     * @return return true if success sending or queueing data. if "canQueueing"
     * is false and sending any data, return false.
     */
    public boolean sendData(byte type, int id) {

        // if sending data, queueing...
        if (isSending || !hasOpenConnection) {
            if (canQueueing && ( !hasOpenConnection && type != PING)) {
                synchronized (mQueue) {
                    PendingData p = new PendingData(id, new ConnectionCommand(type));
                    mQueue.offer(p);
                }
                Log.i(TAG, "sendData(), pending...");
                return true;
            } else {
                return false;
            }
        }

        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = id;
        msg.obj = new ConnectionCommand(type);

        Log.v(TAG, "sendData(" + id +")");
        mSendThread = new CommandSendThread(mOutput, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * send data internal.
     *
     * @param pendingData pending data
     * @return always true
     * @hide
     */
    private boolean sendData(PendingData pendingData) {

        Log.i(TAG, "send PendingData");
        Message msg = obtainMessage(EVENT_DATA_SEND_COMPLETE);
        msg.arg1 = pendingData.id;
        msg.obj = pendingData.command;
        mSendThread = new CommandSendThread(mOutput, msg, mOrder);
        mSendThread.start();

        isSending = true;
        return true;
    }

    /**
     * send pending data if exists.
     *
     * @hide
     */
    private void sendPendingData() {
        PendingData pendingData = null;
        synchronized (mQueue) {
            if (mQueue.size() > 0) {
                pendingData = mQueue.poll();
            }
        }
        if (pendingData != null) {
            sendData(pendingData);
        }
    }

    /**
     * clear queue data
     *
     * @hide
     */
    private void clearQueuedData() {
        synchronized (mQueue) {
            mQueue.clear();
        }
    }

    public boolean isSending() {
        return isSending;
    }

    public boolean hasPending() {
        synchronized (mQueue) {
            return mQueue.size() > 0 ;
        }
    }

    abstract public void startConnection();
}
