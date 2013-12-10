package ktlab.lib.connection;

public interface ConnectionCallback {

	public void onConnectComplete();
	public void onConnectionFailed();
    public void onConnectionLost();
	public void onDataSendComplete(int id);
	public void onCommandReceived(ConnectionCommand command);
}
