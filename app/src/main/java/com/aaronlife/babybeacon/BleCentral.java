package com.aaronlife.babybeacon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;


public class BleCentral
{
    private static BleCentral bleCentral;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;

    private Context context;
    private String deviceName = "";

    private boolean isBusy = false;

    public static synchronized BleCentral getInstance(Context context)
    {
        if(bleCentral == null) bleCentral = new BleCentral(context);

        if(bleCentral.isBleCentralSupport())
        {
            Log.d("aarontest", "Support central");

            return bleCentral;
        }
        else
        {
            Log.d("aarontest", "Not support central");
            return null;
        }
    }

    private BleCentral(Context context)
    {
        this.context = context;

        adapter = BluetoothAdapter.getDefaultAdapter();

    }

    public void start()
    {
        bluetoothLeScanner = adapter.getBluetoothLeScanner(); // xx
        bluetoothLeScanner.startScan(scanCallback);
        Log.d("aarontest", "startScan");
    }

    public void stop()
    {
        if(bluetoothLeScanner != null)
        {
            bluetoothLeScanner.stopScan(scanCallback);
            bluetoothLeScanner = null;
        }

        disconnect();

        bleCentral = null;
    }

    private void disconnect()
    {
        deviceName = "";

        if(bluetoothGatt != null)
        {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        notifyCharacteristic = null;
        writeCharacteristic = null;
        isBusy = false;
    }

    public void sendNotify(String deviceName)
    {
        if(deviceName.length() > 0 && bluetoothGatt != null)
        {
            ((MainActivity)context).addMessage("取消連線" + deviceName);
            deviceName = "";

            bluetoothGatt.disconnect(); // 取消連線
            bluetoothGatt = null;
        }

        this.deviceName = deviceName;
    }

    private boolean isBleCentralSupport()
    {
        if(false == context.getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            return false;

        return true;
    }

    private ScanCallback scanCallback = new ScanCallback()
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            BluetoothDevice device = result.getDevice();
            //Log.d("aarontest", (device.getName() == null ? "null" : device.getName()) + ":" + result.getRssi() + ":" + result.getScanRecord().getTxPowerLevel());

            if(device.getName() == null || device.getName().length() == 0) return;

            // 加入清單
            MainActivity.DeviceData d = new MainActivity.DeviceData();
            d.name = device.getName();
            d.updateTime = System.currentTimeMillis();
            d.distance = Integer.toString(result.getRssi() * -1); // 每個手機都不同
            // 在ScanRecord裡可以取得TxPowerLevel()，但如果不是專門的BLE週邊設備，通常這個值都是無效的
            // result.getScanRecord().getTxPowerLevel()

            ((MainActivity)context).addDevice(d);

            // 掃瞄到的裝置
            if(device.getName().equals(deviceName) && !isBusy)
            {
                isBusy = true;

                // 建立GATT連線
                bluetoothGatt = device.connectGatt(context, false, gattCallback);
            }

            super.onScanResult(callbackType, result);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt,
                                            int status,
                                            int newState)
        {
            if(status == GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED)
            {
                //bluetoothLeScanner.stopScan(scanCallback);

                bluetoothGatt.discoverServices();

                Log.d("aarontest", "onConnectionStateChange: connected");
            }
            else if(newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                disconnect();
                isBusy = false;
                
                Log.d("aarontest", "onConnectionStateChange: disconnected");
            }
            else if(status == 133)
            {
                adapter.disable();
                while(adapter.isEnabled());
                adapter.enable();
                while(!adapter.isEnabled());

                isBusy = false;
            }

            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            List<BluetoothGattService> services = bluetoothGatt.getServices();

            for(BluetoothGattService s : services)
            {
                if(s.getUuid().compareTo(UUID.fromString(BleDefine.CUSTOM_SERVICE)) == 0)
                {
                    List<BluetoothGattCharacteristic> gattCharacteristics =
                                                              s.getCharacteristics();

                    for(BluetoothGattCharacteristic c : gattCharacteristics)
                    {
                        if(c.getUuid()
                            .compareTo(UUID.fromString(BleDefine.CHAR_NOTIFY)) == 0)
                        {
                            bluetoothGatt.setCharacteristicNotification(c, true);
                            notifyCharacteristic = c;
                        }
                        else if(c.getUuid()
                                 .compareTo(UUID.fromString(BleDefine.CHAR_WRITE)) == 0)
                        {
                            writeCharacteristic = c;
                        }
                    }
                }
            }

            // 發出通知聲
            if(notifyCharacteristic != null && writeCharacteristic != null)
            {
                // 發送自己的名字給對方，通知對方發出聲音
                writeCharacteristic.setValue(adapter.getName().getBytes());
                writeCharacteristic.setWriteType(
                                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

                bluetoothGatt.writeCharacteristic(writeCharacteristic);
            }
            else
            {
                ((MainActivity)context).addMessage(deviceName + "不是BabyBeacon設備");
                disconnect();
            }

            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic chara)
        {
            ((MainActivity)context).vibrate(deviceName);

            super.onCharacteristicChanged(gatt, chara);

            // 關閉連線並釋放資源
            disconnect();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic chara,
                                         int status)
        {
            super.onCharacteristicRead(gatt, chara, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic chara,
                                          int status)
        {
            if(status != GATT_SUCCESS)
            {
                Log.d("aarontest", "onCharacteristicWrite failed");
                ((MainActivity)context).addMessage("無法向對方發出聲音");
                disconnect();
            }

            Log.d("aarontest", "onCharacteristicWrite" + new String(chara.getValue()));

            super.onCharacteristicWrite(gatt, chara, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor descriptor,
                                     int status)
        {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status)
        {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status)
        {
            super.onMtuChanged(gatt, mtu, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
        {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status)
        {
            Log.d("aarontest", "onReliableWriteCompleted");
            super.onReliableWriteCompleted(gatt, status);
        }
    };
}
