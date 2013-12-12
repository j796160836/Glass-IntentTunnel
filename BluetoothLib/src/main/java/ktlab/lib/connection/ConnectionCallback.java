package ktlab.lib.connection;

import java.util.Queue;

public interface ConnectionCallback {

	public void onConnectComplete();
	public void onConnectionFailed(Queue<PendingData> left);
    public void onConnectionLost(Queue<PendingData> commands);
	public void onDataSendComplete(int id);
	public void onCommandReceived(ConnectionCommand command);
}
