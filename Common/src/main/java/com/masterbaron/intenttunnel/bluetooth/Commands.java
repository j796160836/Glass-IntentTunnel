package com.masterbaron.intenttunnel.bluetooth;

/**
 * Created by Van Etten on 12/2/13.
 */
public class Commands {
    private final static byte MUSIC = 0;

    public final static class MUSIC_ACTION {
        public static final byte PLAY = MUSIC + 0;
        public static final byte PAUSE = MUSIC + 1;
        public static final byte NEXT = MUSIC + 2;
        public static final byte PREVIOUS = MUSIC + 3;
        public static final byte TOGGLE = MUSIC + 4;
        public static final byte BACK = MUSIC + 5;
        public static final byte RESTART = MUSIC + 6;
    }
}
