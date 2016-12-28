package com.example.raducazacu.speedometer;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class ArduinoUsbService extends IntentService {

    private String TAG = "ArduinoUsbService";
    private static final int NOTIFICATION_ID = 123;
    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_SEND_ASCII_TO_CLIENT = 3;
    static final int MSG_SEND_BYTES_TO_CLIENT = 4;
    static final int MSG_SEND_ASCII_TO_SERVER = 5;
    static final int MSG_SEND_BYTES_TO_SERVER = 6;
    static final int MSG_SEND_ECHO_TO_SERVER = 7;
    static final int MSG_SEND_EXIT_TO_CLIENT  = 20;
    static final String MSG_KEY = "msg";

    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    private boolean accessoryDetached_ = false;
    private boolean canRun_ = false;

    private Thread connectingThread_;
    private String deviceName_;

    private UsbManager mUsbManager_;
    private UsbAccessory accessory_;
    private ParcelFileDescriptor descriptor_;
    private FileInputStream inputStream_;
    private FileOutputStream outputStream_;

    private boolean startApplication_ = true;
    private boolean stopApplication_ = true;
    private Class applicationClass_ = MainActivity.class;
    private Intent startApplicationIntent_;
    private boolean debug_ = false;

    /* Constructor */
    public ArduinoUsbService() {
        super("ArduinoUsbService");
    }


    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger_ = new Messenger(new IncomingHandler());
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger_.getBinder();
    }

    /** Messenger helper method - used to send message to Android service clients (ex. Main Activity)
     * The message is sent finally by IncomingHandler SubClass
     */
    public void sendStringToClients(String message) {
        try {
            Message msg = Message.obtain(null, ArduinoUsbService.MSG_SEND_ASCII_TO_CLIENT, message);

            Bundle b = new Bundle();
            b.putCharSequence(ArduinoUsbService.MSG_KEY, message);
            msg.setData(b);
            msg.replyTo = mMessenger_;
            mMessenger_.send(msg);
        } catch (RemoteException e) {
            Log.d(TAG, "sendMessageToClients Exception: " + Utils.getExceptionStack(e, true));
        }
    }
    /** Messenger helper method - used to send data to Android service clients (ex. Main Activity)
     * The message is sent finally by IncomingHandler SubClass
     */
    public void sendBytesToClients(byte[] data) {
        try {

            mMessenger_.send(createByteMessage(data,MSG_SEND_BYTES_TO_CLIENT));

        } catch (RemoteException e) {
            Log.d(TAG, "sendBytesToClients Exception: " + Utils.getExceptionStack(e, true));
        }
    }
    /** Messenger helper method - used to send exit signal to the Messenger handler
     * The message is sent finally by IncomingHandler SubClass
     */
    public void sendExitToClients() {

        sendMessageTo(createStringMessage(null, MSG_SEND_EXIT_TO_CLIENT), mMessenger_);

    }
    /** Send the text to the Usb Accessory using the output stream that was open at connection time
     */
    public void sendStringToAccessory(String message)
    {
        byte[] buffer = message.getBytes();
        sendBytesToAccessory(buffer);
    }
    /** Send the byte[] to the Usb Accessory using the output stream that was open at connection time
     */
    public void sendBytesToAccessory(byte[] data)
    {
        try {
            if (outputStream_ != null && data != null) {
                try {
                    outputStream_.write(data);
                } catch (IOException e) {
                    Log.d(TAG, "Send Data to USB fails: " + Utils.getExceptionStack(e, true));
                    sendStringToClients("Send Data to USB fails: "+e.getMessage());
                    disconnect();
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "MSG_SEND_ASCII_TO_SERVER: " + Utils.getExceptionStack(e, true));
            sendStringToClients("Exception " + e.getMessage());
        }
    }

    /** Create a Message instance with the text and specified What
     */
    public Message createByteMessage(byte[] data, int msgWhat)
    {
        Bundle b = new Bundle();
        b.putByteArray(ArduinoUsbService.MSG_KEY, data);

        Message msg = Message.obtain(null, msgWhat);
        msg.setData(b);

        return msg;
    }
    public Message duplicateMessage(Message msg)
    {
        Message m = Message.obtain(null, msg.what);
        m.replyTo = msg.replyTo;
        m.setData(msg.getData());

        return m;
    }
    /** Create a Message instance with the data and specified What
     */
    public Message createStringMessage(String message, int msgWhat)
    {
        Bundle b = new Bundle();
        b.putCharSequence(ArduinoUsbService.MSG_KEY, message);

        Message msg = Message.obtain(null, msgWhat);
        msg.setData(b);

        return msg;
    }
    /** Send the Message to the specified Messenger
     */
    public void sendMessageTo(Message message, Messenger to)
    {
        try {
            to.send(message);
        }
        catch (RemoteException e)
        {
            Log.d(TAG, "sendMessage: " + Utils.getExceptionStack(e, true));
        }
    }
    /** Send the Message to all the registered clients in our mMessenger_ Messenger
     */
    public void sendMessageToAllRegisteredClients(Message message)
    {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                mClients.get(i).send(message);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
                Log.d(TAG, "sendMessageToAllRegistered(" + i + "):" + Utils.getExceptionStack(e, true));
            }
        }
    }
    /**
     * Messenger internal logic
     * Handles the incoming messages from clients (like MainActivity) and from self (like Accessory receive Thread)
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                Log.d(TAG, "Service handleMessage: " +  msg.what);

                // Unpack the message data to Log it
                Bundle msgBundle = msg.getData();
                String msgText = null;
                byte[] msgData = new byte[0];

                Object msgDataObject = msgBundle.get(ArduinoUsbService.MSG_KEY);
                if(msgDataObject != null) {
                    if (msgDataObject.getClass() == String.class)
                        msgText = (String) msgDataObject;

                    if (msgDataObject.getClass() == byte[].class)
                        msgData = (byte[]) msgDataObject;
                }

                switch (msg.what) {
                    case MSG_REGISTER_CLIENT:

                        Log.d(TAG, "Service handleMessage: MSG_REGISTER_CLIENT " + msg.replyTo);

                        // Register the client in the Messenger Handler
                        mClients.add(msg.replyTo);

                        if (debug_) {
                            String connectMessage = null;
                            if (deviceName_  != null) {
                                connectMessage = getResources().getString(R.string.connected_to_usb_device_message) +
                                        ": " + deviceName_ + "\n";
                            } else {
                                connectMessage = getResources().getString(R.string.no_usb_devices_attached_message) +
                                        "\n";
                            }

                            sendMessageTo(createStringMessage(connectMessage, MSG_SEND_ASCII_TO_CLIENT), msg.replyTo);
                        }
                        break;
                    case MSG_UNREGISTER_CLIENT:
                        Log.d(TAG, "Service handleMessage: MSG_UNREGISTER_CLIENT " + msg.replyTo);

                        // UnRegister the client from the Messenger Handler
                        mClients.remove(msg.replyTo);

                        break;
                    case MSG_SEND_EXIT_TO_CLIENT:
                        Log.d(TAG, "Service handleMessage: MSG_SEND_EXIT_TO_CLIENT " + mClients.size());

                        sendMessageToAllRegisteredClients(createStringMessage(null, MSG_SEND_EXIT_TO_CLIENT));

                        break;
                    case MSG_SEND_ASCII_TO_CLIENT:
                        sendMessageToAllRegisteredClients(duplicateMessage(msg));
                        break;
                    case MSG_SEND_BYTES_TO_CLIENT:
                        sendMessageToAllRegisteredClients(duplicateMessage(msg));
                        break;
                    case MSG_SEND_ASCII_TO_SERVER:
                        if (deviceName_ == null) {
                            String noConnectMessage = getResources().getString(R.string.no_usb_devices_attached_message) + "\n";
                            sendStringToClients(noConnectMessage);
                        } else {
                            sendStringToAccessory(msgText);
                        }
                        break;
                    case MSG_SEND_BYTES_TO_SERVER:
                        if (deviceName_ == null) {
                            String noConnectMessage = getResources().getString(R.string.no_usb_devices_attached_message) +
                                    "\n";
                            sendStringToClients(noConnectMessage);
                        } else {
                            sendBytesToAccessory(msgData);
                        }
                        break;
                    case MSG_SEND_ECHO_TO_SERVER:
                        sendMessageToAllRegisteredClients(duplicateMessage(msg));
                        break;
                    default:
                        super.handleMessage(msg);
                }
            } catch (Exception ee) {
                if (debug_) {
                    Log.e(TAG, "Server handleMessage Exception: "+ Utils.getExceptionStack(ee, true));
                }
            }
        }
    }

    /* We use this to catch the USB accessory detached message */
    private BroadcastReceiver mUsbReceiver_ = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            final String TAG = "mUsbReceiver";
            String action = intent.getAction();

            Log.d(TAG,"onReceive entered: " + action);

            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {

                UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                Log.d(TAG, "Accessory detached");

                // TODO: Check it's us here?

                accessoryDetached_ = true;

                unregisterReceiver(mUsbReceiver_);

                if (accessory != null) {
                    // TODO: call method to clean up and close communication with the accessory?
                }
            }

            Log.d(TAG,"onReceive exited");
        }
    };
    /* Notification used by startForeground method. Enable users to access the stop service action */
    Notification getNotification() {

        if (mUsbManager_ == null) {
            mUsbManager_ = (UsbManager) getSystemService(Context.USB_SERVICE);
        }
        int perm = 0;
        UsbAccessory[] accessories = mUsbManager_.getAccessoryList();
        accessory_ = (accessories == null ? null : accessories[0]);
        if (accessory_ != null) {
            if (mUsbManager_.hasPermission(accessory_)) {
                perm = 10;
            } else {
                perm = 1;
            }
        } else {
            perm = -1;
        }
        Log.d(TAG, "getNotification before notification: perm="+perm+", mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);

        Context context = getApplicationContext();
        CharSequence contentTitle = getResources().getString(R.string.usb_device_attached) + " " + perm;
        CharSequence contentText = getAccessoryName(accessory_);

        // This can be changed if we want to launch an activity when notification clicked
        Intent notificationIntent = new Intent();
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(context)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(contentIntent)
                .build();

        return notification;
    }
    /* Start service and connect to UsbManager - Entry point !! */
    @Override
    protected void onHandleIntent(Intent arg0) {

        Log.d(TAG, "onHandleIntent entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);

        startForeground(NOTIFICATION_ID, getNotification());

        // Register to receive detached messages
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver_, filter);

        mUsbManager_ = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbAccessory[] accessories = mUsbManager_.getAccessoryList();
        accessory_ = (accessories == null ? null : accessories[0]);

        int perm = 0;
        deviceName_ = getAccessoryName(accessory_);

        if (accessory_ == null) {
            accessoryDetached_ = true;
            sendStringToClients(getResources().getString(R.string.no_usb_devices_attached_message) + "\n");
        } else if (!mUsbManager_.hasPermission(accessory_)) {
            accessoryDetached_ = true;
            sendStringToClients(getResources().getString(R.string.no_permissions_for_this_usb_device_message) + ": " + deviceName_ + "\n");
        } else {
            accessoryDetached_ = false;
            String message = getResources().getString(R.string.connected_to_usb_device_message) + ": " + deviceName_ + "\n";
            sendStringToClients(message);
            connect();
        }

        int count = 0;
        while(!accessoryDetached_) {
            // Wait until the accessory detachment is flagged
            if (accessoryDetached_) {
                break;
            }

            // In reality we'd do stuff here.
            SystemClock.sleep(300);
        }

        mUsbReceiver_ = null;

        String message = "\n" + getResources().getString(R.string.usb_device_detached) + ": " + deviceName_ + "\n";
        sendStringToClients(message);

        disconnect();
        stopForeground(true);

        Log.d(TAG, "onHandleIntent exited: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
        stopSelf();

    }

    /* Connect to UsbManager and start DoWork Thread to handle communication */
    private void connect() {
        Log.d(TAG, "connect entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
        disconnect(false);

        accessoryDetached_ = false;

        try {
            mUsbManager_ = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessories = mUsbManager_.getAccessoryList();
            accessory_ = (accessories == null ? null : accessories[0]);
            deviceName_ = getAccessoryName(accessory_);

            if (accessory_ == null) {
                accessoryDetached_ = true;
                return;
            }

            // Initialize the input and output streams associated with the Accessory descriptor
            descriptor_ = mUsbManager_.openAccessory(accessory_);
            FileDescriptor fd = descriptor_.getFileDescriptor();
            inputStream_  = new FileInputStream(fd);
            outputStream_ = new FileOutputStream(fd);

            connectingThread_ = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        doWork();
                    } catch (Exception ee) {
                        Log.d(TAG, "doWork fail: " + Utils.getExceptionStack(ee, true));
                        sendStringToClients("doWork fail: " + ee.getMessage() + "\n");
                    }
                }
            });
            canRun_ = true;
            connectingThread_.start();

            if (startApplication_) {
                startApplicationIntent_ = new Intent(this, applicationClass_);
                startApplicationIntent_.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                startActivity(startApplicationIntent_);
            }
        } catch (Exception e) {
            Log.d(TAG, "Connect fail: " + Utils.getExceptionStack(e, true));
            sendStringToClients("Connect fail: " + e.getMessage() + "\n");
        }
        Log.d(TAG, "connect exited: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
    }
    /* Listen the inputStream of the Connected Accessory descriptor */
    public void doWork() {
        Log.d(TAG, "doWork entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);

        int ret = 0;
        byte[] buffer = new byte[16384];
        int i;
        while (ret >= 0 & canRun_) {
            try {
                ret = inputStream_.read(buffer);
            } catch (IOException e) {
                break;
            }
            byte[] data = Arrays.copyOf(buffer, ret);

            sendBytesToClients(data);
        }
        Log.d(TAG, "doWork exited: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
    }

    public void disconnect() {
        disconnect(stopApplication_);
    }
    public void disconnect(boolean stopApp) {
        Log.d(TAG, "disconnect entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
        if (debug_)
            sendStringToClients(getResources().getString(R.string.disconnected_from_usb_device_message) + ": " + deviceName_ + "\n");

        canRun_ = false;
        try {
            try {
                if (connectingThread_ != null) {
                    connectingThread_.stop();
                    connectingThread_ = null;
                }
            } catch (Exception e) {
                Log.d(TAG, "Stop Thread fail: " + Utils.getExceptionStack(e, true));
                sendStringToClients("Stop Thread fail: "+e.getMessage());
            }

            if (descriptor_ != null) {
                descriptor_.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "Disconnect Exception: " + Utils.getExceptionStack(e, true));
            sendStringToClients("Disconnect Exception: "+e.getMessage());

        } finally {
            mUsbManager_	= null;
            descriptor_ 	= null;
            accessory_  	= null;
            deviceName_ 	= null;
            outputStream_ 	= null;
            inputStream_ 	= null;
        }

        if (stopApp) {
            try {

                sendExitToClients();
            } catch (Exception e) {
                Log.d(TAG, "Stop Application fail: " + Utils.getExceptionStack(e, true));
                sendStringToClients("Stop Application fail: " + Utils.getExceptionStack(e, true));
            }

        }
        Log.d(TAG, "disconnect exited");
    }

    /**
     * Get the accessory name
     * @param accessory
     * @return
     */
    public static String getAccessoryName(UsbAccessory accessory) {
        if (accessory == null) {
            return null;
        }
        String tmpString = accessory.getDescription();
        if (tmpString != null && tmpString.length() > 0) {
            return tmpString;
        } else {
            return accessory.getModel() + " : " + accessory.getSerial();
        }
    }
}
