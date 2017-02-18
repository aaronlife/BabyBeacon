package com.aaronlife.babybeacon;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
{
    public static class DeviceData
    {
        public String name;
        public String distance;
        public long updateTime;
    }

    // 監控更新清單
    private class MonitorThread extends Thread
    {
        @Override
        public void run()
        {
            super.run();

            while(true)
            {
                for(DeviceData d : devices)
                {
                    if(System.currentTimeMillis() - d.updateTime > 5000)
                    {
                        d.distance = "消失";

                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                deviceListAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }

                // 每6秒判斷一次
                try
                {
                    Thread.sleep(6000);
                }
                catch(InterruptedException e)
                {}
            }
        }
    }

    public static final int REQUEST_DISCOVERY_PERMISSIONS = 0;

    private ListView listItem;
    private TextView txtMessage;
    private ScrollView scrollView;

    private ArrayList<DeviceData> devices = new ArrayList<>();
    private DeviceListAdapter deviceListAdapter;
    private String messages = "";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d("aarontest", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar bar = getSupportActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#f44336")));

        // 顯示版本名稱
        TextView txtVersion = (TextView)findViewById(R.id.version);
        PackageInfo pInfo = null;
        try
        {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        }
        catch(PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        txtVersion.setText(pInfo.versionName);

        txtMessage = (TextView)findViewById(R.id.message);
        listItem = (ListView)findViewById(R.id.list_item);
        scrollView = (ScrollView)findViewById(R.id.scroll);

        deviceListAdapter = new DeviceListAdapter(this, devices);
        listItem.setAdapter(deviceListAdapter);

        // 注意順序
        bleServiceCheck();

        // 啟動監視
        new MonitorThread().start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
        case R.id.menu_refresh:
            devices.clear();
            deviceListAdapter.notifyDataSetChanged();
            break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume()
    {
        Log.d("aarontest", "RemoteActivity resume");

        super.onResume();
    }

    @Override
    protected void onDestroy()
    {
        Log.d("aarontest", "onDestroy");
        super.onDestroy();

        // 關閉BLE Central掃瞄
        if(BleCentral.getInstance(this) != null)
            BleCentral.getInstance(this).stop();

        // 關閉BLE Peripheral廣播
        if(BlePeripheral.getInstance(this) != null)
            BlePeripheral.getInstance(this).stop();
    }

    @Override
    public void onBackPressed()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("確定要結束並離開嗎？\n\n備註：可透過Home鍵移到背景執行");
        builder.setCancelable(true);
        builder.setPositiveButton("是", new DialogInterface.OnClickListener()
        {
            public void onClick(final DialogInterface dialog, final int id)
            {
                finish();
            }
        });

        builder.setNegativeButton("不要", new DialogInterface.OnClickListener()
        {
            public void onClick(final DialogInterface dialog, final int id)
            {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void checkDiscoveryPermissions()
    {
        if(ContextCompat.checkSelfPermission(this,
                                    Manifest.permission.ACCESS_COARSE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED)
        {
            // 啟動BLE Central掃瞄
            if(BleCentral.getInstance(this) != null)
                BleCentral.getInstance(this).start();

            // 啟動BLE Peripheral廣播
            if(BlePeripheral.getInstance(this) != null)
                BlePeripheral.getInstance(this).start();
        }
        else
        {
            // 目前沒有權限，要求權限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_DISCOVERY_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults)
    {
        switch(requestCode)
        {
        case REQUEST_DISCOVERY_PERMISSIONS:
            if(grantResults.length >= 1 &&
               permissions[0].equals(Manifest.permission.ACCESS_COARSE_LOCATION) &&
               grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                // 啟動BLE Central掃瞄
                if(BleCentral.getInstance(this) != null)
                    BleCentral.getInstance(this).start();

                // 啟動BLE Peripheral廣播
                if(BlePeripheral.getInstance(this) != null)
                    BlePeripheral.getInstance(this).start();
            }
            else
            {
                Toast.makeText(this, "無法取得定位服務權限", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void onSend(View v)
    {
        if(BleCentral.getInstance(this) != null)
        {
            BleCentral.getInstance(this).sendNotify((String)v.getTag());
            addMessage("向 " + (String)v.getTag() + " 發出聲音");
        }
    }

    public void playSound(String fromDeviceName)
    {
        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.bb);
        mp.start();

        addMessage(fromDeviceName + " 向你發出聲音");
    }

    public void vibrate(String deviceName)
    {
        Vibrator vibrator =
            (Vibrator)getApplication().getSystemService(Service.VIBRATOR_SERVICE);
        vibrator.vibrate(500);

        addMessage("已經向 " + deviceName + " 發出聲音");
    }

    public synchronized void addDevice(DeviceData device)
    {
        for(DeviceData d : devices)
        {
            // 判斷是不是已經存在了
            if(d.name.equals(device.name))
            {
                d.distance = device.distance;
                d.updateTime = device.updateTime;

                deviceListAdapter.notifyDataSetChanged();

                return;
            }
        }

        devices.add(device);
        deviceListAdapter.notifyDataSetChanged();
    }

    public void addMessage(final String msg)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if(messages.length() > 0) messages += "\n";

                // 時間字串
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
                String datetime = sdf.format(Calendar.getInstance().getTime());

                messages += datetime + " " + msg;

                if(messages.length() > 20000) messages.substring(messages.length() - 20000);

                txtMessage.setText(messages);

                // 將訊息捲動到最下面
                scrollView.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private final int LOCATION_REUEST_CODE = 0;

    private void locationServiceCheck()
    {
        final int LOCATION_REUEST_CODE = 0;

        Log.d("aarontest", "SDK version: " + Build.VERSION.SDK_INT);

        // Android 6.0以下（不含6.0）不需要Location Service
        if(Build.VERSION.SDK_INT >= 23)
        {
            LocationManager manager =
                    (LocationManager)getSystemService(Context.LOCATION_SERVICE);

            if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
               !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("定位服務尚未開啟，無法掃描裝置，是否要進行設定？");
                builder.setCancelable(false);
                builder.setPositiveButton("是", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        Intent it =
                            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

                        startActivityForResult(it, LOCATION_REUEST_CODE);
                    }
                });

                builder.setNegativeButton("不要",
                                          new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        dialog.cancel();
                    }
                });

                builder.show();

                return;
            }
        }

        checkDiscoveryPermissions(); // 明確要求定位服務權限
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == LOCATION_REUEST_CODE)
        {
            checkDiscoveryPermissions();  // 明確要求定位服務權限
        }
    }

    private void bleServiceCheck()
    {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if(adapter != null)
        {
            while(!adapter.isEnabled())
            {
                adapter.enable();

                try
                {
                    Thread.sleep(1000);
                }
                catch(InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            locationServiceCheck(); // 定位服務是否開啟
        }
    }
}
