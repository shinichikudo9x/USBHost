package edu.fpt.prm.com.testusbhost;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.*;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, ChannelListener{

    private static final String ACTION_USB_PERMISSION = "edu.fpt.prm.com.testusbhost.USB_PERMISSION";

    SensorManager sensorManager;
    Sensor mSensor;
    float forward, turn;
    float[] value;
    int[] dc = new int[]{100, 100, 100, 100};

    PendingIntent mPermissionIntent;

    UsbManager usbManager;
    static UsbSerialPort port;

    ImageButton btnUp, btnDown;
    ToggleButton btnOnOff;
    EditText test;
    Handler handler;
    boolean adjust = true;

    WifiP2pManager mManager;
    Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnUp = (ImageButton) findViewById(R.id.btnUp);
        btnDown = (ImageButton) findViewById(R.id.btnDown);
        btnOnOff = (ToggleButton) findViewById(R.id.btnOnOff);
        test = (EditText) findViewById(R.id.test);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        ;
        sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        handler = new Handler();
        btnUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (adjust) {
                    writeData("3 " + increaseValue(2, 1));
                }
                return adjust;
            }
        });
        btnDown.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (adjust) {
                    writeData("3 " + decreaseValue(2, 1));
                }
                return true;
            }
        });
        btnOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    adjust = true;
                    turnOff();
                }
            }
        });

        //wifi
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WifiBroadCastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mManager.discoverPeers(mChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this,"Success",Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this,"Failure",Toast.LENGTH_SHORT).show();
            }
        });

    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Toast.makeText(MainActivity.this, "Permision Success", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d("Error", "permission denied for device " + device);
                    }
                }
            }
//            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
//                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//                if (device != null) {
//                    Toast.makeText(MainActivity.this, "Arduino Disconnected",Toast.LENGTH_SHORT).show();
//                    close();
//
//                }
//            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) && port == null) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    Toast.makeText(MainActivity.this, "Arduino Connected", Toast.LENGTH_SHORT).show();
                    usbManager.requestPermission(device, PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0));
                }
            }
        }
    };

    private void connectUsb(int baudRate) throws IOException {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            return;
        }

// Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
        if (connection == null) {
            mPermissionIntent = PendingIntent.getActivity(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(driver.getDevice(), mPermissionIntent);
            return;
        }

// Read some data! Most have just one port (port 0).
        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            // Deal with error.
        }
    }

    private int writeData(String cmd) {
        try {
            if (port == null) {
                connectUsb(9600);
            } else {
                if (btnOnOff.isChecked()) {
//                    test.setText(cmd);
                    return port.write(cmd.getBytes(), 10);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
        close();
    }

    private void close() {
        try {
            if (port != null)
                port.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    setValue(event.values);
                    resetSensor(btnOnOff.isChecked());
                    int forwardValue, turnValue;
                    forwardValue = round2(value[0] - forward);
                    turnValue = round2(value[1] - turn);
                    if (forwardValue > 0) {
                        int n = writeData("3 " + increaseValue(2, Math.abs(forwardValue)));
                    }
                    if (round2(value[0] - forward) < 0) {
                        writeData("3 " + decreaseValue(2, Math.abs(forwardValue)));
                    }
                    if (turnValue > 0) {

                    }
                    if (round2(value[1] - turn) < 0) {

                    }
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mUsbReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        registerReceiver(mReceiver, mIntentFilter);
        try {
            connectUsb(9600);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    private int increaseValue(int pos, int value) {
        if (btnOnOff.isChecked())
            return dc[pos] = dc[pos] < 255 ? dc[pos] += value : 255;
        return 0;

    }

    private int decreaseValue(int pos, int value) {
        if (btnOnOff.isChecked())
            return dc[pos] = dc[pos] > 0 ? dc[pos] -= value : 0;
        return 0;
    }

    public void setValue(float[] value) {
        this.value = value;
    }

    private void turnOff() {
        writeData("3 0");
        dc = new int[]{100, 100, 100, 100};
    }

    private int round2(float number) {
        String f = String.format(Locale.ENGLISH, "%.0f", number);
        return Integer.valueOf(String.format(Locale.ENGLISH, "%.0f", number));
    }

    private void resetSensor(boolean res) {
        if (res && adjust) {
            forward = value[0];
            turn = value[1];
            adjust = false;
        }
    }

    public void setIsWifiP2pEnabled(boolean enable) {
    }

    @Override
    public void onChannelDisconnected() {

    }
}
