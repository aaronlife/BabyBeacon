package com.aaronlife.babybeacon;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by aaron on 12/12/16.
 */

public class DeviceListAdapter extends BaseAdapter
{
    private Context c;
    private ArrayList<MainActivity.DeviceData> devices;

    private LayoutInflater inflater;

    public DeviceListAdapter(Context c, ArrayList<MainActivity.DeviceData> devices)
    {
        this.c = c;
        this.devices = devices;
        this.inflater = LayoutInflater.from(c);
    }

    @Override
    public int getCount()
    {
        return devices.size();
    }

    @Override
    public MainActivity.DeviceData getItem(int position)
    {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = inflater.inflate(R.layout.scan_list_item, parent, false);

        MainActivity.DeviceData device = getItem(position);

        TextView name = (TextView)convertView.findViewById(R.id.device_name);
        TextView distance = (TextView)convertView.findViewById(R.id.distance);
        convertView.findViewById(R.id.btn_send).setTag(device.name);

        name.setText(device.name);
        distance.setText("(" + device.distance + ")");

        return convertView;
    }
}
