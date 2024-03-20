package com.example.fitfurlife;

public interface BLEControllerListener {
    public void BLEControllerConnected();
    public void BLEControllerDisconnected();
    public void BLEDeviceFound(String name, String address);
    public void BLERead(byte[] value);
}
