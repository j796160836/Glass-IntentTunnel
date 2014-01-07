Glass-IntentTunnel
==================

Very unpolished at this point, but proof of concept is working.


Tunnel Intents over bluetooth.  
The idea is to have a service app on Glass and the Phone that would handle traffic using intents.  No need for every app developer to write bluetooth connection code.   Developers can simply send intents to the service to have them broadcast on the other side of the bluetooth connection. 

Intall Instructions
===================

1. Download the APK [here](https://github.com/TheMasterBaron/Glass-IntentTunnel/blob/master/apk/IntentTunnel-debug-unaligned-0.0.1.apk?raw=true).  The same APK is for the phone and Glass

2. Intall the APK on you Phone and run IntentTunnel.  Press menu and choose Select BT Device.  Then select your Glass BT Device from the list.  Press Menu again and select Start to start the router (If it's not already started)

3. Intall the APK on Glass using this command:
<code>adb install -r IntentTunnel-debug-unaligned.apk</code>

4. After installing the APK on Glass, start IntentTunnel on Glass usng Glass Launcher, Launchy, or using this command adb:
<code>adb shell am start -n com.masterbaron.intenttunnel/com.masterbaron.intenttunnel.IntentTunnelActivity</code>

5. Select: Select BT Device from the menu.  Then select your Phone BT Device from the menu choices.  Select Start to start the router (If it's not already started)

Thanks to https://github.com/KTlab/BluetoothCommunicationLibrary_Android for providing a solid bluetooth library to work off of.


Features:
=========
* Stays disconnected until needed.
* Stays connected for 30 seconds after last message is sent incase there is a flurry of traffic.
* Remaining bound to the router servvice will keep the connection open until all binds to the service are disconnected.  Use this if the 30 seconds is too low of a timeout.
* Either side can initiate a message and start a bluetooth connection.
* uses pings to ensure connection is actually working.
* Intents are converted to strings using intent.toUri() 
* Added support to also include byte[], string list, and integer list.


SAMPLE:
=======
Install MusicPusherSample on Glass.  Say "phome music start" or "phone music stop".
The MusicPusherSample sends "com.android.music.musicservicecommand" intents to the service to have them broadcasted on the phone.  Most music players on the phone respond to these intent allowing control of them.


Change Log:
===========
0.0.1: Initial Release
