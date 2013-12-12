package ktlab.lib.connection;

import ktlab.lib.connection.ConnectionCommand;

/**
 * pending data
 *
 * @hide
 */
public class PendingData {
    final int id;
    final ConnectionCommand command;
    final long created = System.currentTimeMillis();

    PendingData(int id, ConnectionCommand command) {
        this.id = id;
        this.command = command;
    }

    public int getId() {
        return id;
    }

    public ConnectionCommand getCommand() {
        return command;
    }

    public long getCreatedDate() {
        return created;
    }

}

