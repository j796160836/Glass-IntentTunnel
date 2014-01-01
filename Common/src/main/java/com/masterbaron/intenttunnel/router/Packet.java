package com.masterbaron.intenttunnel.router;

import android.content.Intent;

import java.util.concurrent.TimeUnit;

/**
 * Created by Van Etten on 12/31/13.
 */
public class Packet {
    private final long EXPIRED = TimeUnit.SECONDS.toMillis(30);

    private final long expiredTime = System.currentTimeMillis() + EXPIRED;
    private final int type;
    private final Intent intent;

    public Packet(int type, Intent intent) {
        this.type = type;
        this.intent = intent;
    }

    protected Intent getIntent() {
        return intent;
    }

    public int getType() {
        return type;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiredTime;
    }
}
