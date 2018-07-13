package com.isctechnologies.NanoScan;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * This activity controls the view for the Nano stored scan configurations.
 * These configurations have to be individually read from the Nano
 *
 * WARNING: This activity uses JNI function calls. It is important that the name and location of
 *          this activity remain unchanged or the Spectrum C library call will fail
 *
 * @author collinmast
 */
public class ScanConfActivity extends Activity {

    private static Context mContext;

    private final BroadcastReceiver scanConfReceiver = new ScanConfReceiver();
    private final IntentFilter scanConfFilter = new IntentFilter(NIRScanSDK.SCAN_CONF_DATA);
    private ScanConfAdapter scanConfAdapter;
    private ArrayList<NIRScanSDK.ScanConfiguration> configs = new ArrayList<>();
    private ListView lv_configs;
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final IntentFilter disconnFilter = new IntentFilter(NIRScanSDK.ACTION_GATT_DISCONNECTED);
    private BroadcastReceiver scanConfSizeReceiver;
    private BroadcastReceiver getActiveScanConfReceiver;
    private int storedConfSize;
    private int receivedConfSize;
    private Menu mMenu;

    ProgressDialog barProgressDialog;
    public static Boolean saveConfig =false;
    public static ArrayList<NIRScanSDK.ScanConfiguration> bufconfigs = new ArrayList<>();
    public static ArrayList<String>ScanConfigName = new ArrayList<>();

    @Override
    public void finishActivity(int requestCode) {
        super.finishActivity(requestCode);
    }
//Spectrum C library call. Only the activity by this name is allowed call this function
    //public native Object dlpSpecScanReadConfiguration(byte[] data);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_conf);

        mContext = this;
        ScanConfigName.clear();
        //Set up the action bar title, and enable the back button
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(getString(R.string.scan_configurations));
        }

        /* Initialize the receiver for the # of scan configurations
         * When the configuration size is received, show the progress dialog.
         */
        scanConfSizeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                storedConfSize = intent.getIntExtra(NIRScanSDK.EXTRA_CONF_SIZE, 0);
                if (storedConfSize > 0) {
                    barProgressDialog = new ProgressDialog(ScanConfActivity.this);

                    barProgressDialog.setTitle(getString(R.string.reading_configurations));
                    barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    barProgressDialog.setProgress(0);
                    barProgressDialog.setMax(intent.getIntExtra(NIRScanSDK.EXTRA_CONF_SIZE, 0));
                    barProgressDialog.setCancelable(false);
                  /*  barProgressDialog.setCancelable(true);//set true may have issue
                    barProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            finish();
                        }
                    });*/
                    barProgressDialog.show();
                    receivedConfSize = 0;
                }
            }
        };

        /* Initialize the receiver for the active scan configuration
         * When the active configuration is received, set the color of the name of the active
         * scan configuration to green
         */
        getActiveScanConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int index = intent.getByteArrayExtra(NIRScanSDK.EXTRA_ACTIVE_CONF)[0];
                barProgressDialog.dismiss();
                lv_configs.setVisibility(View.VISIBLE);

                for (NIRScanSDK.ScanConfiguration c : scanConfAdapter.configs) {
                    //get the first one byte
                    int ScanConfigIndextoByte = (byte)c.getScanConfigIndex();
                    if (c.getScanConfigIndex() == index  || ScanConfigIndextoByte == index) {
                        c.setActive(true);
                        lv_configs.setAdapter(scanConfAdapter);
                    } else {
                        c.setActive(false);
                    }
                }
            }
        };

        //Send broadcast to retrieve the scan configurations
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_SCAN_CONF));

        //register the necessary broadcast receivers
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfReceiver, scanConfFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfSizeReceiver, new IntentFilter(NIRScanSDK.SCAN_CONF_SIZE));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(getActiveScanConfReceiver, new IntentFilter(NIRScanSDK.SEND_ACTIVE_CONF));
    }

    /*
     * On resume, make a call to the super class.
     * Nothing else is needed here besides calling
     * the super method.
     */

    @Override
    public void onResume() {
        super.onResume();
        if(saveConfig == true)
        {
            configs.clear();
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_SCAN_CONF));
            saveConfig = false;
        }
    }

    /*
     * When the activity is destroyed, unregister the BroadcastReceivers
     * handling receiving scan configurations, disconnect events, the # of configurations,
     * and the active configuration
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfSizeReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getActiveScanConfReceiver);
    }

    /*
     * Inflate the options menu
     * In this case, there is no menu and only an up indicator,
     * so the function should always return true.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_stored_configurations, menu);
        mMenu = menu;
        mMenu.findItem(R.id.action_add).setEnabled(false);

        return true;
    }

    /*
     * Handle the selection of a menu item.
     * In this case, there is only the up indicator. If selected, this activity should finish.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add) {
            Intent configureIntent = new Intent(mContext, NewSectionConfigActivity.class);
            configureIntent.putExtra("Store config size",storedConfSize);
            configureIntent.putExtra("Serial Number",configs.get(0).getScanConfigSerialNumber());
            startActivity(configureIntent);
        }
        if (id == android.R.id.home) {
            bufconfigs.clear();
            for(int i=0;i<configs.size();i++)
            {
                bufconfigs.add(configs.get(i));
            }
            this.finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /*
     * Broadcast receiver for scan configurations. When the expected number of configurations are
      * received, the dialog will be closed, and the list of configurations will be displayed.
     */
    private class ScanConfReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            NIRScanSDK.ScanConfiguration scanConf = NewScanActivity.GetScanConfiguration(intent.getByteArrayExtra(NIRScanSDK.EXTRA_DATA));
            lv_configs = (ListView) findViewById(R.id.lv_configs);

            lv_configs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    byte[] index = {0, 0};
                    index[0] = (byte) scanConfAdapter.configs.get(i).getScanConfigIndex();
                    //the index over 256 should calculate index[1]
                    index[1] = (byte) (scanConfAdapter.configs.get(i).getScanConfigIndex()/256);
                    Intent setActiveConfIntent = new Intent(NIRScanSDK.SET_ACTIVE_CONF);
                    setActiveConfIntent.putExtra(NIRScanSDK.EXTRA_SCAN_INDEX, index);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(setActiveConfIntent);
                }
            });

            receivedConfSize++;
            if (receivedConfSize == storedConfSize) {
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_ACTIVE_CONF));
                mMenu.findItem(R.id.action_add).setEnabled(true);
            } else {
                barProgressDialog.setProgress(receivedConfSize);
            }

            configs.add(scanConf);
            scanConfAdapter = new ScanConfAdapter(mContext, configs);
            lv_configs.setAdapter(scanConfAdapter);
            ScanConfigName.add(scanConf.getConfigName());
        }
    }

    /**
     * Custom adapter that holds {@link NIRScanSDK.ScanConfiguration} objects for the listview
     */
    public class ScanConfAdapter extends ArrayAdapter<NIRScanSDK.ScanConfiguration> {
        private final ArrayList<NIRScanSDK.ScanConfiguration> configs;


        public ScanConfAdapter(Context context, ArrayList<NIRScanSDK.ScanConfiguration> values) {
            super(context, -1, values);
            this.configs = values;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(this.getContext())
                        .inflate(R.layout.row_scan_configuration_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.scanType = (TextView) convertView.findViewById(R.id.tv_scan_type);
                viewHolder.rangeStart = (TextView) convertView.findViewById(R.id.tv_range_start_value);
                viewHolder.rangeEnd = (TextView) convertView.findViewById(R.id.tv_range_end_value);
                viewHolder.width = (TextView) convertView.findViewById(R.id.tv_width_value);
                viewHolder.patterns = (TextView) convertView.findViewById(R.id.tv_patterns_value);
                viewHolder.repeats = (TextView) convertView.findViewById(R.id.tv_repeats_value);
                viewHolder.serial = (TextView) convertView.findViewById(R.id.tv_serial_value);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            final NIRScanSDK.ScanConfiguration config = getItem(position);
            if (config != null) {
                viewHolder.scanType.setText(config.getConfigName());
                viewHolder.rangeStart.setText(getString(R.string.range_start_value, config.getWavelengthStartNm()));
                viewHolder.rangeEnd.setText(getString(R.string.range_end_value, config.getWavelengthEndNm()));
                viewHolder.width.setText(getString(R.string.width_value, config.getWidthPx()));
                viewHolder.patterns.setText(getString(R.string.patterns_value, config.getNumPatterns()));
                viewHolder.repeats.setText(getString(R.string.repeats_value, config.getNumRepeats()));
                viewHolder.serial.setText(config.getScanConfigSerialNumber());
                if (config.isActive()) {
                    viewHolder.scanType.setTextColor(ContextCompat.getColor(mContext, R.color.active_conf));
                    SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, config.getConfigName());
                } else {
                    viewHolder.scanType.setTextColor(ContextCompat.getColor(mContext, R.color.black));
                }
            }
            return convertView;
        }
    }

    /**
     * View holder for the {@link NIRScanSDK.ScanConfiguration} class
     */
    private class ViewHolder {
        private TextView scanType;
        private TextView rangeStart;
        private TextView rangeEnd;
        private TextView width;
        private TextView patterns;
        private TextView repeats;
        private TextView serial;
    }

    /**
     * Broadcast Receiver handling the disconnect event. If the Nano disconnects,
     * this activity should finish so that the user is taken back to the {@link ScanListActivity}
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
