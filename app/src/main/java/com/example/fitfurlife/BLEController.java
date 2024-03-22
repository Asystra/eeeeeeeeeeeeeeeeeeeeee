package com.example.fitfurlife;

import android.annotation.SuppressLint;
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
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BLEController {

    private static final UUID uuid
            = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214");
    private static BLEController instance;

    private BluetoothManager bluetoothManager;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic btGattCharGyro;
    private BluetoothGattCharacteristic btGattCharAccel;

    private ArrayList<BLEControllerListener> listeners = new ArrayList<>();

    private HashMap<String, BluetoothDevice> devices = new HashMap<>();
    private BluetoothLeScanner scanner;

    private BLEController(Context context) {
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public static BLEController getInstance(Context context) {
        if (null == instance) {
            instance = new BLEController((context));
        }
        return instance;
    }

    public void addBLEControllerListener(BLEControllerListener l) {
        if (!this.listeners.contains(l))
            this.listeners.add(l);
    }

    public void removeBLEControllerListener(BLEControllerListener l) {
        this.listeners.remove(l);
    }

    @SuppressLint("MissingPermission")
    public void init() {
        this.devices.clear();
        this.scanner = this.bluetoothManager.getAdapter().getBluetoothLeScanner();
        scanner.startScan(bleCallback);
    }

    private ScanCallback bleCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if(!devices.containsKey(device.getAddress()) && isThisTheDevice(device)) {
                deviceFound(device);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for(ScanResult sr : results) {
                BluetoothDevice device = sr.getDevice();
                if(!devices.containsKey(device.getAddress()) && isThisTheDevice(device)) {
                    deviceFound(device);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i("[BLE]", "scan failed with errorcode " + errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private boolean isThisTheDevice(BluetoothDevice device) {
        return null != device.getName() && device.getName().startsWith("FitFurLife");
    }

    private void deviceFound(BluetoothDevice device) {
        this.devices.put(device.getAddress(), device);
        fireDeviceFound(device);
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(String address) {
        this.device = this.devices.get(address);
        this.scanner.stopScan(this.bleCallback);
        Log.i("[BLE]","connect to device " + device.getAddress());
        this.bluetoothGatt = device.connectGatt(null, false, this.bleConnectCallback);
    }

    private final BluetoothGattCallback bleConnectCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
            }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                btGattCharGyro = null;
                btGattCharAccel = null;
                fireDisconnected();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(btGattCharGyro == null || btGattCharAccel == null) {
                    for (BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().toString().toUpperCase().startsWith("19B10000")) {
                            List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
                            for (BluetoothGattCharacteristic bgc : gattCharacteristics) {
                                if (bgc.getUuid().toString().toUpperCase().startsWith("19B10001")) {
                                    //Gyroscope input
                                    int characteristicProperties = bgc.getProperties();
                                    if (((characteristicProperties & BluetoothGattCharacteristic.PROPERTY_READ) | (characteristicProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) {
                                        btGattCharGyro = bgc;
                                        //Set local notifications
                                        gatt.setCharacteristicNotification(bgc, true);
                                        //Set remote notifications
                                        BluetoothGattDescriptor descriptor = bgc.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        boolean success = bluetoothGatt.writeDescriptor(descriptor);
                                        Log.d("[BLE]","writeDescriptor Status : " + success);
                                        fireConnected();
                                    }
                                }
                                if(bgc.getUuid().toString().toUpperCase().startsWith("19B10002")){
                                    int characteristicProperties = bgc.getProperties();
                                    if (((characteristicProperties & BluetoothGattCharacteristic.PROPERTY_READ) | (characteristicProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) {
                                        btGattCharAccel = bgc;
                                        //Set local notifications
                                        gatt.setCharacteristicNotification(bgc, true);
                                        //Set remote notifications
                                        BluetoothGattDescriptor descriptor = bgc.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        boolean success = bluetoothGatt.writeDescriptor(descriptor);
                                        Log.d("[BLE]", "writeDescriptor Status : " + success);
                                        fireConnected();
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("[BLE]", "Characteristic read successfully");
                fireRead(characteristic.getValue());
            } else {
                Log.d("[BLE]", "Characteristic read failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            //TODO: What happens when we write to the Arduino
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.d("[BLE]","write op to " + descriptor.toString()+" succeeded");
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);
            if(btGattCharAccel.equals(characteristic)){
                byte[] data = characteristic.getValue();
                Log.i("[DATA_ACCEL]", Arrays.toString(data));
            }
            if(btGattCharGyro.equals(characteristic)){
                byte[] data = characteristic.getValue();
                Log.i("[DATA_GYRO]", Arrays.toString(data));
            }
        }
    };

    private void fireDisconnected() {
        for (BLEControllerListener l : this.listeners)
            l.BLEControllerDisconnected();

        this.device = null;
    }

    private void fireConnected() {
        for (BLEControllerListener l : this.listeners)
            l.BLEControllerConnected();
    }

    @SuppressLint("MissingPermission")
    private void fireDeviceFound(BluetoothDevice device) {
        for (BLEControllerListener l : this.listeners)
            l.BLEDeviceFound(device.getName().trim(), device.getAddress());
    }

    private void fireRead(byte[] value){
        for (BLEControllerListener l : this.listeners)
            l.BLERead(value);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        this.bluetoothGatt.disconnect();
    }

    @SuppressLint("MissingPermission")
    public void read(String name) {
        if ("gyro".equals(name) && btGattCharGyro != null) {
            if ((btGattCharGyro.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                boolean res = bluetoothGatt.readCharacteristic(btGattCharGyro);
                Log.d("[GYRO]", "readCharacteristic called, success: " + res);
            } else {
                Log.d("[GYRO]", "Characteristic does not support read");
            }
        } else if ("accel".equals(name) && btGattCharAccel != null) {
            if ((btGattCharAccel.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                boolean res = bluetoothGatt.readCharacteristic(btGattCharAccel);
                Log.d("[ACCEL]", "readCharacteristic called, success: " + res);
            } else {
                Log.d("[ACCEL]", "Characteristic does not support read");
            }
        }
    }

}
