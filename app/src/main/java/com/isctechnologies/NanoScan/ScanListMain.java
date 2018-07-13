package com.isctechnologies.NanoScan;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Created by iris.lin on 2018/2/2.
 */

public class ScanListMain  extends Activity {
    private static Context mContext;
    private ImageButton main_connect;
    private ImageButton main_info;
    private ImageButton main_setting;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);
        mContext = this;
        initComponent();


    }

    private void initComponent()
    {
        main_connect = (ImageButton)findViewById(R.id.main_connect);
        main_info = (ImageButton)findViewById(R.id.main_info);
        main_setting = (ImageButton)findViewById(R.id.main_setting);

        main_connect.setOnClickListener(main_connect_listenser);
        main_info.setOnClickListener(main_info_listenser);
        main_setting.setOnClickListener(main_setting_listenser);
    }

    private Button.OnClickListener main_connect_listenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            Intent graphIntent = new Intent(mContext, NewScanActivity.class);
            graphIntent.putExtra("file_name", getString(R.string.newScan));
            startActivity(graphIntent);
        }
    };

    private Button.OnClickListener main_info_listenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            Intent infoIntent = new Intent(mContext, InfoActivity.class);
            startActivity(infoIntent);
        }
    };

    private Button.OnClickListener main_setting_listenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            Intent settingsIntent = new Intent(mContext, SettingsActivity.class);
            startActivity(settingsIntent);
        }
    };

    public static StoreCalibration storeCalibration = new StoreCalibration();
    public static class StoreCalibration
    {
        String device;
        byte[] storrefCoeff;
        byte[] storerefMatrix;
    }
}
