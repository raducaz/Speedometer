package com.example.raducazacu.speedsensormonitor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.raducazacu.speedsensormonitor.interfaces.Connectable;

public class MainActivity extends AppCompatActivity {

    public static final int DISABLE_CONTROLS_TYPE 			= 0;
    public static final int ENABLE_CONTROLS_TYPE 			= 1;
    public static final int CLEAR_UI_TYPE 					= 2;
    public static final int CHANGE_TITLE_TYPE 				= 3;
    public static final int SET_VIEW_FROM_PREFERENCES_TYPE 	= 4;
    public static final int SHOW_LATEST_MESSAGES 			= 5;
    public static final int CHAR_SEQUENCE_TYPE 				= 10;
    public static final int BYTE_SEQUENCE_TYPE 				= 11;
    public static final int INFO_MESSAGE_TYPE 				= 22;
    public static final int DEBUG_MESSAGE_TYPE 				= 24;
    public static final int CONNECTION_ACTION 				= 100;
    public static final int EXIT_ACTION 					= 101;
    private int messageLevel_			= 22;
    private int cursorPosition_ = 0;

    private static final String ACTION_USB_PERMISSION = "com.example.raducazacu.speedsensormonitor.USB_PERMISSION";
    private static final String TAG = "UsbActivity";
    private static final int scrollDelay = 300;
    static final int NO_USB_DEVICES_DIALOG = 1;
    static final int SELECT_USB_DEVICE_DIALOG = 2;

    /** Messenger for communicating with service. */
    Messenger mService_ = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    protected Resources resources_;
    protected boolean exitOnDetach_ = true;
    boolean debug_ = true;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger_ = new Messenger(new IncomingHandler());

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                Log.d(TAG, "USBActivity handleMessage: " + msg.what);
                switch (msg.what) {
                    case ArduinoUsbService.MSG_SEND_ASCII_TO_CLIENT:
                        Bundle b = msg.getData();
                        CharSequence asciiMessage = b.getCharSequence(ArduinoUsbService.MSG_KEY);
                        logMessage("USBActivity handleMessage: TO_CLIENT " + asciiMessage);
                        showMessage(asciiMessage);
                        break;
                    case ArduinoUsbService.MSG_SEND_BYTES_TO_CLIENT:
                        Bundle bb = msg.getData();
                        byte[] data = bb.getByteArray(ArduinoUsbService.MSG_KEY);
                        signalToUi(BYTE_SEQUENCE_TYPE, data);
                        break;
                    case ArduinoUsbService.MSG_SEND_ASCII_TO_SERVER:
                        Bundle sb = msg.getData();
                        CharSequence sAsciiMessage = sb.getCharSequence(ArduinoUsbService.MSG_KEY);
                        Log.d(TAG, "USBActivity handleMessage: TO_SERVER " + sAsciiMessage);
                        showMessage(sAsciiMessage);
                        break;
                    case ArduinoUsbService.MSG_SEND_EXIT_TO_CLIENT:
                        try {
                            if (debug_) showMessage("on Exit Signal\n");
                            if (exitOnDetach_) close();
                        } catch (Exception e) {
                            if (debug_) showMessage("Close App: " +e.getMessage() + "\n");
                        }
                        if (exitOnDetach_) finish();
                        break;
                    default:
                        super.handleMessage(msg);
                }
            } catch (Exception ee) {
                if (debug_) {
                    Log.e(TAG, "Client handleMessage Exception: "+ Utils.getExceptionStack(ee, true));
                }
            }
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService_ = new Messenger(service);
            logMessage("Attached.");

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        ArduinoUsbService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger_;
                mService_.send(msg);

            } catch (RemoteException e) {
                logMessage("Problem connecting to Server: " + e.getMessage());
            }

            // As part of the sample, tell the user what happened.
            logMessage("Server Connected");
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService_ = null;
            logMessage("Server Disconnected");
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        bindService(new Intent(this,
                ArduinoUsbService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        logMessage("Bound.");
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService_ != null) {
                try {
                    Message msg = Message.obtain(null,
                            ArduinoUsbService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger_;
                    mService_.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            logMessage("Unbound.");
        }
    }

    protected void showMessage(CharSequence message) {
        signalToUi(CHAR_SEQUENCE_TYPE, message);
    }

    private final BroadcastReceiver usbReceiver_ = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (resources_ == null) {
                resources_ = getResources();
            }
            if (ACTION_USB_PERMISSION.equals(action)) {
                logMessage("Got ACTION_USB_PERMISSION");
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                logMessage(resources_.getString(R.string.usb_device_detached));
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                logMessage(resources_.getString(R.string.usb_device_attached));
            }
        }
    };


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

        if (resources_ == null) {
            resources_ = getResources();
        }

        logMessage("before register receiver");

        doBindService();

        setContentView(R.layout.activity_main);
    }

    @Override
    public void onDestroy() {
        logMessage("onDestroy");
        close();

        doUnbindService();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        logMessage("onResume");
        super.onResume();
        signalToUi(SET_VIEW_FROM_PREFERENCES_TYPE, null);
        signalToUi(SHOW_LATEST_MESSAGES, scrollDelay);
    }

    @Override
    public void onPause() {
        logMessage("onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        logMessage("onStop");
        doUnbindService();
        super.onStop();
    }

    @Override
    public void onStart() {
        logMessage("onStart");
        doBindService();
        super.onStart();
    }

    @Override
    public void onRestart() {
        logMessage("onRestart");
        super.onRestart();
        signalToUi(SHOW_LATEST_MESSAGES, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        logMessage("onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Scroll down when orientation has been changed
        signalToUi(SHOW_LATEST_MESSAGES, new Integer(scrollDelay));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        logMessage("onOptionsItemSelected");
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.mainMenuConnect:
                onConnectMenu();
                return true;
            case R.id.mainMenuSettings:
                startActivity(new Intent(this, Preferences.class));
                return true;
            case R.id.mainMenuExit:
                try {
                    logMessage("on Exit menu");
                    close();
                } catch (Exception e) {
                    logMessage("Close menu fail: " +e.getMessage());
                }
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onConnectMenu() {
        logMessage("On Connect Menu");
        doUnbindService();

        Intent stopServiceIntent = new Intent(this, ArduinoUsbService.class);
        this.stopService(stopServiceIntent);

        SystemClock.sleep(1500);

        Intent startServiceIntent = new Intent(this, ArduinoUsbService.class);
        startService(startServiceIntent);

        SystemClock.sleep(1500);
        doBindService();
    }

    public void disconnect() {
        logMessage("Disconnect");

        doUnbindService();
    }

    public void close() {
        logMessage("close");

        disconnect();

        saveState();
        close();
    }

    public void saveState() {
//        if (activity_ == null) {
//            return;
//        }
//
//        CharSequence text = commandText_.getText();
//        if (text == null || text.length() == 0) {
//            text = "";
//        }
//        CommandStore = text.toString().trim();
//
//
//        text = historyBuffer_.getText();
//        CursorStateStore = cursorPosition_;
//        if (text == null || text.length() == 0) {
//            text = "";
//        }
//        HistoryStore = text.toString().trim();
//
//        int inputState = View.GONE;
//        if (activity_ != null) {
//            LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
//            inputState = inputLayout.getVisibility();
//        }
//
//        SharedPreferences settings = activity_.getPreferences(android.content.Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = settings.edit();
//        editor.putString(CommandStoreKey, CommandStore);
//        editor.putString(HistoryStoreKey, HistoryStore);
//        editor.putInt(InputStateStoreKey, inputState);
//        editor.putInt(CursorStateStoreKey, CursorStateStore);
//        editor.commit();
    }

    public void sendEcho(byte[] data) {
        try {
            Log.d(TAG, "USBActivity sendEcho: TO_SERVER " + data + ", mService: " + mService_);
            Message msg = Message.obtain(null,
                    ArduinoUsbService.MSG_SEND_ECHO_TO_SERVER, data);
            msg.replyTo = mMessenger_;
            Bundle b = new Bundle();
            if (mService_ != null) {
                b.putByteArray(ArduinoUsbService.MSG_KEY, data);
                msg.setData(b);
                mService_.send(msg);
            } else if (mMessenger_ != null)  {
                b.putCharSequence(ArduinoUsbService.MSG_KEY, "Server not Available :: " + data);
                msg.setData(b);
                mMessenger_.send(msg);
            }
        } catch (RemoteException e) {
            logMessage("Problem sending echo to Server: " + e.getMessage());
        }

    }

    public void sendData(CharSequence data) {
        try {
            Log.d(TAG, "USBActivity sendData: TO_SERVER " + data + ", mService: " + mService_);
            Message msg = Message.obtain(null,
                    ArduinoUsbService.MSG_SEND_ASCII_TO_SERVER, data);
            msg.replyTo = mMessenger_;
            Bundle b = new Bundle();
            if (mService_ != null) {
                b.putCharSequence(ArduinoUsbService.MSG_KEY, data);
                msg.setData(b);
                mService_.send(msg);
            } else if (mMessenger_ != null)  {
                b.putCharSequence(ArduinoUsbService.MSG_KEY, "Server not Available :: " + data);
                msg.setData(b);
                mMessenger_.send(msg);
            }
        } catch (RemoteException e) {
            logMessage("Problem sending message to Server: " + e.getMessage());
        }
    }

    public void sendData(int type, byte[] data) {
        byte[] buffer = new byte[3];
        byte command = data[0];
        byte target  = data[1];
        byte value   = data[2];

        buffer[0] = command;
        buffer[1] = target;
        buffer[2] = (byte) value;
    }

    public void signalToUi(int type, Object data) {
        Runnable runnable = null;
        if (type == CONNECTION_ACTION) {
            onConnectMenu();
        } else if (type == CHAR_SEQUENCE_TYPE) {
            if (data == null || ((CharSequence) data).length() == 0) {
                return;
            }
            final CharSequence tmpData = (CharSequence) data;
            addToHistory(tmpData);
        } else if (( type == DEBUG_MESSAGE_TYPE || type == INFO_MESSAGE_TYPE ) && ( type <= messageLevel_ )) {
            if (data == null || ((CharSequence) data).length() == 0) {
                return;
            }
            final CharSequence tmpData = (CharSequence) data;
            addToHistory(tmpData);
        } else if (type == BYTE_SEQUENCE_TYPE) {
            if (data == null || ((byte[]) data).length == 0) {
                return;
            }
            final byte[] byteArray = (byte[]) data;
            addToHistory(byteArray);
        }

    }
    private void addToHistory(CharSequence text) {
        if (text == null || text.length() == 0) return;

        if (debug_) {
            Log.i(TAG, "addToHistoryC1: text="+text+", cursorPosition="+cursorPosition_);
        }

        TextView logText = (TextView) findViewById(R.id.text_log);

        if (text.charAt(0) != '\n') {
            CharSequence allText = logText.getText();
            if (allText.length() > 0 && allText.charAt(allText.length()-1) != '\n') {
                logText.append("\n");
            }
        }
        logText.append(text);
        cursorPosition_ = logText.getText().length();

        if (debug_) {
            Log.i(TAG, "addToHistoryC2: cursorPosition="+cursorPosition_+", ALL_TEXT="+logText.getText());
        }
        scrollDown1();
    }

    private void addToHistory(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        TextView logText = (TextView) findViewById(R.id.text_log);

        CharSequence allText = logText.getText();
        int length = allText.length();
        int lf_index = 0;
        int start = 0;
        if (length == 0) {
            cursorPosition_ = 0;
            start = 0;
            lf_index = 0;
        } else {
            lf_index = 0;

            for (int i=length-1; i>=0; i--) {
                if (allText.charAt(i) == '\n') {
                    lf_index = i;
                    break;
                }
            }
            if (lf_index == 0) {
                if (allText.charAt(0) == '\n') {
                    lf_index = 1;
                    if (cursorPosition_ == 0) cursorPosition_ = 1;
                }
            } else {
                lf_index++;
            }
            start = cursorPosition_ - lf_index;

        }
        if (debug_) {
            Log.i(TAG, "addToHistory1: length="+length+", cursorPosition="+cursorPosition_+", lf_index="+lf_index+", ALL_TEXT="+allText);
        }

        CharSequence prefix = allText.subSequence(lf_index, length);
        StringBuilder line = new StringBuilder(prefix);

        if (debug_) {
            Log.i(TAG, "addToHistory2: start="+start+", line="+line);
        }

        start = Utils.processCRBytes(line, data, start);

        if (lf_index < length) ((Editable) allText).delete(lf_index, length);
        ((Editable) allText).append(line);
        cursorPosition_ = lf_index + start;

        if (debug_) {
            Log.i(TAG, "addToHistory3: start="+start+", cursorPosition="+cursorPosition_+
                    ", newLength="+logText.getText().length()+", line="+line+", NEW_ALL_TEXT="+logText.getText());
        }

        scrollDown1();
    }

    void scrollDown1() {
        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollViewLog);

        if (scrollView == null) return;
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);

    }

    public void logMessage(String msg) {
        logMessage(msg, null);
    }

    public void logMessage(String msg, Exception e) {
        if (debug_) {
            Log.d(TAG, msg + "\n" + Utils.getExceptionStack(e, true));
        }
    }

    /** Called when the user clicks the Send button */
    public void sendMessage(View view) {

        EditText sendText = (EditText) findViewById(R.id.sendText);
        CharSequence text = sendText.getText();

        if (!(text == null || text.length() == 0)) {

            sendData(text + "\r\n");
        }
    }

























    /* From starter application example */
//    public final static String EXTRA_MESSAGE = "com.example.raducazacu.speedsensormonitor.MESSAGE";
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//    }
//
//    /** Called when the user clicks the Send button */
//    public void sendMessage(View view) {
//        Intent intent = new Intent(this, DisplayMessageActivity.class);
//        EditText editText = (EditText) findViewById(R.id.edit_message);
//        String message = editText.getText().toString();
//        intent.putExtra(EXTRA_MESSAGE, message);
//        startActivity(intent);
//    }
}
