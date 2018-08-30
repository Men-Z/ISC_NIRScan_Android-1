package com.isctechnologies.NanoScan;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
    private static final int REQUEST_WRITE_STORAGE = 112;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);
        mContext = this;
        initComponent();

        LocationManager locationManager
                = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
//        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
//        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean hasPermission = (ContextCompat.checkSelfPermission(getBaseContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
            if (!hasPermission) {
                ActivityCompat.requestPermissions(ScanListMain.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.INTERNET, Manifest.permission.ACCESS_COARSE_LOCATION
                                , Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_WRITE_STORAGE);
            }
        }

       /* int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);*/

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
