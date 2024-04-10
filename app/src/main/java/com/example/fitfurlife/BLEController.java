package com.example.fitfurlife;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class BLEController {

    private static final String TAG = "BLEController";
    private static final UUID IMU_SERVICE_UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214");
    private static final UUID SENSOR_CHARACTERISTIC_UUID = UUID.fromString("19b10003-e8f2-537e-4f6c-d104768a1214");

    private static BLEController instance;
    private databaseHelper dbHelper;


    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private Context context;

    private BLEController(Context context) {
        this.context = context;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.dbHelper = new databaseHelper(context);
    }

    public static synchronized BLEController getInstance(Context context) {
        if (instance == null) {
            instance = new BLEController(context);
        }
        return instance;
    }

    @SuppressLint("MissingPermission")
    public void init() {
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled");
            return;
        }
        this.scanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(IMU_SERVICE_UUID)).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(Collections.singletonList(scanFilter), settings, bleCallback);
        Log.d(TAG, "Scan started");
    }

    private final ScanCallback bleCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "Device found: " + device.getName() + " - " + device.getAddress());
            connectToDevice(device);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Scan failed with error code: " + errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        scanner.stopScan(bleCallback);
        Log.d(TAG, "Connecting to device " + device.getAddress());
        bluetoothGatt = device.connectGatt(context, true, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                init(); // Re-initiate scan
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if (IMU_SERVICE_UUID.equals(service.getUuid())) {
                        Log.d(TAG, "IMU service found.");
                        subscribeToSensorCharacteristic(gatt, service);
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (SENSOR_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                // Log that we've entered the characteristic changed method for the right UUID
                Log.d(TAG, "onCharacteristicChanged: Combined sensor data characteristic changed.");

                byte[] combinedData = characteristic.getValue();
                // Log the raw combined data received
                Log.d(TAG, "Received combined sensor data: " + Arrays.toString(combinedData));

                if (combinedData.length >= 12) { // Ensure there's enough data for both sensors
                    // Unpack the data
                    float[] gyroData = unpackSensorData(Arrays.copyOfRange(combinedData, 0, 6));
                    float[] accelData = unpackSensorData(Arrays.copyOfRange(combinedData, 6, 12));

                    // Log the unpacked sensor data
                    Log.d(TAG, String.format("Unpacked Gyro Data: X=%.2f, Y=%.2f, Z=%.2f", gyroData[0], gyroData[1], gyroData[2]));
                    Log.d(TAG, String.format("Unpacked Accel Data: X=%.2f, Y=%.2f, Z=%.2f", accelData[0], accelData[1], accelData[2]));

                    float gyroMagnitude = calculateMagnitude(gyroData);
                    float accelMagnitude = calculateMagnitude(accelData);
                    float currentTime = System.currentTimeMillis();

                    // Log the magnitudes
                    Log.d(TAG, "Gyro Magnitude: " + gyroMagnitude);
                    Log.d(TAG, "Accel Magnitude: " + accelMagnitude);


                    dbHelper.insertAll(new accGyro(0, accelMagnitude, gyroMagnitude, currentTime));
                } else {
                    // Log a warning if the received data does not have the expected length
                    Log.w(TAG, "Received sensor data is not of expected length: " + combinedData.length);
                }
            }
        }


        private void subscribeToSensorCharacteristic(BluetoothGatt gatt, BluetoothGattService service) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(SENSOR_CHARACTERISTIC_UUID);
            if (characteristic != null) {
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    boolean status = gatt.writeDescriptor(descriptor);
                    Log.d(TAG, "Subscribed to SENSOR_CHARACTERISTIC_UUID: " + status);
                } else {
                    Log.e(TAG, "Failed to find descriptor for SENSOR_CHARACTERISTIC_UUID");
                }
            } else {
                Log.e(TAG, "Characteristic with UUID SENSOR_CHARACTERISTIC_UUID not found");
            }
        }


    };

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            Log.d(TAG, "Disconnected and resources released");
        }
    }


    private float[] unpackSensorData(byte[] data) {
        float[] sensorData = new float[3];
        for (int i = 0; i < 3; i++) {
            int value = (data[i * 2] << 8) | (data[i * 2 + 1] & 0xFF);
            sensorData[i] = value / 100.0f;
        }
        return sensorData;
    }

    private float calculateMagnitude(float[] vector) {
        return (float) Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
    }

}
