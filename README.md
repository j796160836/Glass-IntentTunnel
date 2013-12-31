Glass-IntentTunnel
==================

Very unpolished at this point, but proof of concept is working.


Tunnel Intents over bluetooth.  
The idea is to have a service app on Glass and the Phone that would handle traffic using intents.  No need for every app developer to write bluetooth connection code.   Developers can simply send intents to the service to have them broadcast on the other side of the bluetooth connection. 



Install GlassTunnel to Glass.  Run the IntentTunnel app to start the tunnel services (Use Glass Launcher)

Install AndroidTunnel to your phone.  Run the IntentTunnel app, press menu to start the tunnel services

Install MusicPusherSample on Glass.  Say "phome music start" or "phone music stop".
The MusicPusherSample sends "com.android.music.musicservicecommand" intents to the service to have them broadcasted on the phone.  Most music players on the phone respond to these intent allowing control of them.


Thanks to https://github.com/KTlab/BluetoothCommunicationLibrary_Android for providing a solid bluetooth library to work off of.


Features:
* Stays disconnected until needed.
* Stays connected for 15 seconds after last message is sent incase there is a flurry of traffic.
* Remaining bound to the router servvice will keep the connection open until all binds to the service are disconnected.  Use this if the 15 seconds is too low of a timeout.
* Either side can initiate a message and start a bluetooth connection.
* uses pings to ensure connection is actually working.
* Intents are converted to strings using intent.toUri() 
* Added support to also include byte[], string list, and integer list.
