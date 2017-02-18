package com.aaronlife.babybeacon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.util.UUID;


public class BlePeripheral
{
    //private final String SERVICE_UUID = "3C455092-F190-4C62-BD23-E99F5D7E016D";

    private static BlePeripheral blePeripheral = null;

    private BluetoothManager manager;
    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;

    private AdvertiseSettings settings;
    private AdvertiseData data;

    private BluetoothGattCharacteristic notifyCharacteristic;

    private Context ctx;

    public static synchronized BlePeripheral getInstance(Context context)
    {
        if(blePeripheral == null)
        {
            blePeripheral = new BlePeripheral(context);

            if(blePeripheral.isBlePeripheralSupport())
            {
                Log.d("aarontest", "Support peripheral");
            }
            else
            {
                Log.d("aarontest", "Not support peripheral");
                blePeripheral = null;
            }
        }

        return blePeripheral;
    }

    private BlePeripheral(Context context)
    {
        this.ctx = context;

        manager =
            (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);

        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBlePeripheralSupport()
    {
        return adapter.isMultipleAdvertisementSupported();
    }

    public void start()
    {
        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        gattServer = manager.openGattServer(ctx, gattServerCallback);

        if(gattServer != null && advertiser != null)
        {
            setupAdvertisement();
            addDeviceInfoService();
            addCustomService();

            advertiser.startAdvertising(settings, data, advertisingCallback);
        }
        else
        {
            ((MainActivity)ctx).addMessage("無法建立裝置");
        }
    }

    public void stop()
    {
        advertiser.stopAdvertising(advertisingCallback);

        if(gattServer != null)
        {
            gattServer.close();
            gattServer = null;
        }

        blePeripheral = null;
    }

    private void setupAdvertisement()
    {
        ((MainActivity)ctx).addMessage("我的名稱：" + adapter.getName());

        settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // 預設：ADVERTISE_MODE_LOW_POWER
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // ADVERTISE_TX_POWER_MEDIUM
                .setConnectable(true) // 預設true
                .setTimeout(0) // 0=不逾時(預設)，最大180000 mill
                .build();

        //ParcelUuid pUuid = new ParcelUuid(UUID.fromString(SERVICE_UUID));

        data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                //.addServiceUuid(pUuid) // 這行跟setIncludeDeviceName無法同時呼叫
                //.addServiceData( pUuid, "0000".getBytes( Charset.forName( "UTF-8" ) ) )
                .build();
    }

    private AdvertiseCallback advertisingCallback = new AdvertiseCallback()
    {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect)
        {
            Log.d( "aarontest", "廣播成功" );

            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode)
        {
            super.onStartFailure(errorCode);

            if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
                Toast.makeText(ctx, "廣播錯誤：不支援", Toast.LENGTH_LONG).show();
            else if (errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                Toast.makeText(ctx, "廣播錯誤：過多廣播", Toast.LENGTH_LONG).show();
            else if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED)
                Toast.makeText(ctx, "廣播錯誤：廣播已經啟動", Toast.LENGTH_LONG).show();
            else if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE)
                Toast.makeText(ctx, "廣播錯誤：廣播資料過大。", Toast.LENGTH_LONG).show();
            else if (errorCode == ADVERTISE_FAILED_INTERNAL_ERROR)
                Toast.makeText(ctx, "廣播失敗：內部錯誤", Toast.LENGTH_LONG).show();
            else
                Toast.makeText(ctx, "廣播失敗：未知的錯誤", Toast.LENGTH_LONG).show();
        }
    };

    private void addDeviceInfoService()
    {
        UUID deviceInfoUuid = UUID.fromString(BleDefine.SERVICE_DEVICE_INFORMATION);
        UUID swRevUuid = UUID.fromString(BleDefine.SOFTWARE_REVISION_STRING);

        // 新增裝置資訊服務
        BluetoothGattService previousService = gattServer.getService(deviceInfoUuid);

        // 移除已經存在的服務
        if(null != previousService) gattServer.removeService(previousService);

        BluetoothGattCharacteristic swVerCharacteristic =
                                    new BluetoothGattCharacteristic(swRevUuid,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ);

        // 取得App的版本號碼
        PackageInfo pInfo = null;
        try
        {
            pInfo = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            swVerCharacteristic.setValue(pInfo.versionName);
        }
        catch(PackageManager.NameNotFoundException e)
        {
            swVerCharacteristic.setValue("Unknown");
        }

        BluetoothGattService deviceInfoService =
                new BluetoothGattService(deviceInfoUuid,
                                         BluetoothGattService.SERVICE_TYPE_PRIMARY);

        deviceInfoService.addCharacteristic(swVerCharacteristic);

        gattServer.addService(deviceInfoService);
    }

    private void addCustomService()
    {
        UUID customeSerUuid = UUID.fromString(BleDefine.CUSTOM_SERVICE);
        UUID readUuid = UUID.fromString(BleDefine.CHAR_READ);
        UUID writeUuid = UUID.fromString(BleDefine.CHAR_WRITE);
        UUID notifyUuid = UUID.fromString(BleDefine.CHAR_NOTIFY);

        BluetoothGattService previousService = gattServer.getService(customeSerUuid);

        // 移除已經存在的服務
        if(null != previousService) gattServer.removeService(previousService);

        BluetoothGattCharacteristic readCharacteristic =
                                new BluetoothGattCharacteristic(readUuid,
                                    BluetoothGattCharacteristic.PROPERTY_READ,
                                    BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic writeCharacteristic =
                    new BluetoothGattCharacteristic(writeUuid,
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE);

        notifyCharacteristic =
                new BluetoothGattCharacteristic(notifyUuid,
                                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                    BluetoothGattCharacteristic.PERMISSION_READ);

        notifyCharacteristic.setValue("ImStillHere");

        BluetoothGattService customService =
                new BluetoothGattService(customeSerUuid,
                                         BluetoothGattService.SERVICE_TYPE_PRIMARY);

        customService.addCharacteristic(readCharacteristic);
        customService.addCharacteristic(writeCharacteristic);
        customService.addCharacteristic(notifyCharacteristic);

        gattServer.addService(customService);
    }

    private final BluetoothGattServerCallback gattServerCallback =
                                                    new BluetoothGattServerCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothDevice device,
                                            int status, int newState)
        {
            Log.d("aarontest", "onConnectionStateChange(): " + newState);

            //if(null != mConnectionCallback && BluetoothGatt.GATT_SUCCESS == status)
            //    mConnectionCallback.onConnectionStateChange(device, newState);

            super.onConnectionStateChange(device, status, newState);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service)
        {
            Log.d("aarontest", "onServiceAdded()");
            super.onServiceAdded(status, service);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic chara)
        {
            Log.d("aarontest", "onCharacteristicReadRequest()");

            gattServer.sendResponse(device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS, offset,
                                    chara.getValue());

            super.onCharacteristicReadRequest(device, requestId, offset, chara);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic chara,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value)
        {
            Log.d("aarontest", "onCharacteristicWriteRequest(): " + new String(value));

            gattServer.notifyCharacteristicChanged(device, notifyCharacteristic, false);

            ((MainActivity)ctx).playSound(new String(value));

            super.onCharacteristicWriteRequest(device, requestId,
                                               chara, preparedWrite,
                                               responseNeeded, offset, value);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status)
        {
            Log.d("aarontest", "onNotificationSent(): " + status);

            super.onNotificationSent(device, status);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId,
                                   boolean execute)
        {
            Log.d("aarontest", "onExecuteWrite()");
            super.onExecuteWrite(device, requestId, execute);
        }
    };
}