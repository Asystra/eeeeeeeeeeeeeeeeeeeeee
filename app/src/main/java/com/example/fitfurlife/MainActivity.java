package com.example.fitfurlife;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements BLEControllerListener {

    private static final String TAG = "MainActivity";
    private TextView logView;
    private Button connectButton;
    private Button disconnectButton;
    private Button accelButton, gyroButton;
    private BLEController bleController;
    private String deviceAddress;
    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Toast.makeText(MainActivity.this, "Bluetooth Enabled!", Toast.LENGTH_SHORT).show();
                    } else {
                        //Bluetooth enable refused
                        MainActivity.this.finish();
                    }
                }
            }
    );

    private void initConnectButton() {
        this.connectButton = findViewById(R.id.connectButton);
        this.connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectButton.setEnabled(false);
                log("Connecting...");
                bleController.connectToDevice(deviceAddress);
            }
        });
    }

    private void initDisconnectButton() {
        this.disconnectButton = findViewById(R.id.disconnectButton);
        this.disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectButton.setEnabled(false);
                log("Disconnecting...");
                bleController.disconnect();
            }
        });
    }

    private void initAccelButton(){
        this.accelButton = findViewById(R.id.accelButton);
        this.accelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bleController.read("accel");
            }
        });
    }

    private void initGyroButton(){
        this.gyroButton = findViewById(R.id.gyroButton);
        this.gyroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bleController.read("gyro");
            }
        });
    }

    private void disableButtons() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectButton.setEnabled(false);
                disconnectButton.setEnabled(false);
                accelButton.setEnabled(false);
                gyroButton.setEnabled(false);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        this.logView = findViewById(R.id.logView);
        this.logView.setMovementMethod(new ScrollingMovementMethod());
        checkBLESupport();
        checkPermissions();
        this.bleController = BLEController.getInstance(this);
        initConnectButton();
        initDisconnectButton();
        initAccelButton();
        initGyroButton();
        disableButtons();
    }

    private void log(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String tmp = logView.getText() + "\n" + text;
                logView.setText(tmp);
            }
        });
    }

    private void checkBLESupport() {
        // Check if Bluetooth Low Energy is supported on the device.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private void checkPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                log("\"Bluetooth Connect\" permission not granted yet!");
                log("Without this permission Bluetooth devices cannot be searched!");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},42
                );
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                log("\"Bluetooth Scan\" permission not granted yet!");
                log("Without this permission Bluetooth devices cannot be searched!");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN},42
                );
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            log("\"Access Fine Location\" permission not granted yet!");
            log("Without this permission Bluetooth devices cannot be searched!");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    42);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        checkPermissions();
        if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activityResultLauncher.launch(enableBTIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.bleController.addBLEControllerListener(this);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            log("[BLE]\tSearching for Fit Fur Life device...");
            this.bleController.init();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.bleController.removeBLEControllerListener(this);
    }

    @Override
    public void BLEControllerConnected() {
        log("[BLE]\tConnected");
        disableButtons();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disconnectButton.setEnabled(true);
                accelButton.setEnabled(true);
                gyroButton.setEnabled(true);
            }
        });
    }

    @Override
    public void BLEControllerDisconnected() {
        log("[BLE]\tDisconnected");
        disableButtons();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectButton.setEnabled(true);
            }
        });
    }

    @Override
    public void BLEDeviceFound(String name, String address) {
        log("Device " + name + " found with address " + address);
        this.deviceAddress = address;
        this.connectButton.setEnabled(true);
    }

    @Override
    public void BLERead(byte[] value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String data = Arrays.toString(value);
                log("[BLE Read] " + data);
            }
        });
    }
}
