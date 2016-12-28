package com.example.raducazacu.speedometer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

public class StartServiceActivity extends Activity {
    /** Called when the activity is first created. */

    private String TAG = "StartServiceActivity";

    private boolean mPermissionRequestPending;
    private PendingIntent mPermissionIntent;
    private UsbManager mUsbManager;

    static final String ACTION_USB_PERMISSION = "com.example.raducazacu.usb.StartServiceActivity.action.USB_PERMISSION";
    static final String ACTION_STOP_SERVICE = "com.example.raducazacu.usb.StartServiceActivity.action.STOP_SERVICE";

    private Intent startServiceIntent;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate entered");

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        startServiceIntent = new Intent(this, ArduinoUsbService.class);

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                startService(startServiceIntent);
                unregisterReceiver(mUsbReceiver);
                finish();
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }

        Log.d(TAG, "onCreate exited");
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        startService(startServiceIntent);
                    } else {
                        Log.d(TAG, "permission denied for accessory "
                                + accessory);
                    }
                    mPermissionRequestPending = false;
                }
                unregisterReceiver(mUsbReceiver);
                finish();
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                // TODO: Do something if action is Detach
            }
        }
    };

}
