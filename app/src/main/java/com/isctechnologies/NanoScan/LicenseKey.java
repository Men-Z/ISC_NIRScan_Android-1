package com.isctechnologies.NanoScan;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by iris.lin on 2018/3/19.
 */

public class LicenseKey extends Activity {
    private static Context mContext;
    Button btn_submit;
    Button btn_clear;
    TextView et_status;
    TextView et_license_key;
    private AlertDialog alertDialog;
    private final BroadcastReceiver RetrunActivateStatusReceiver = new RetrunActivateStatusReceiver();
    private final IntentFilter RetrunActivateStatusFilter = new IntentFilter(NIRScanSDK.ACTION_RETURN_ACTIVATE);
    private Boolean Licensestatusfalg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.license_key);
        mContext = this;

        //Set up the action bar title, and enable the back button
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        initComponent();
        LocalBroadcastManager.getInstance(mContext).registerReceiver(RetrunActivateStatusReceiver, RetrunActivateStatusFilter);

    }
    private void initComponent()
    {
        btn_clear = (Button)findViewById(R.id.btn_clear);
        btn_submit = (Button)findViewById(R.id.btn_submit);
        et_license_key = (TextView)findViewById(R.id.et_license_key);
        et_status = (TextView)findViewById(R.id.et_status);
        btn_submit.setOnClickListener(ButtonListenser);
        btn_clear.setOnClickListener(ButtonListenser);

        String licensekey = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.licensekey, null);
        if(licensekey!=null)
        {
           et_license_key.setText(licensekey);
        }
        String avticavateStatus =  SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, null);
        et_status.setText(avticavateStatus);
        /*Licensestatusfalg = NewScanActivity.Licensestatusfalg;
        if(Licensestatusfalg)
        {
            et_status.setText("Activated");
        }
        else
        {
            et_status.setText("Function is locked.");
        }*/
    }


    /*
       * On resume, make a call to the super class.
       * Nothing else is needed here besides calling
       * the super method.
       */
    @Override
    public void onResume() {
        super.onResume();
    }

    /*
     * When the activity is destroyed, unregister the BroadcastReceivers
     * handling receiving scan configurations, disconnect events, the # of configurations,
     * and the active configuration
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(RetrunActivateStatusReceiver);
    }

    /*
     * Inflate the options menu
     * In this case, there is no menu and only an up indicator,
     * so the function should always return true.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    /*
     * Handle the selection of a menu item.
     * In this case, there is only the up indicator. If selected, this activity should finish.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /*** 把非數字、/ 及 - 的字元全部過濾掉 ***/
    public static String filterDate(String Str) {
        String filter = "[^0-9^A-Z^a-z]"; // 指定要過濾的字元
        Pattern p = Pattern.compile(filter);
        Matcher m = p.matcher(Str);
        return m.replaceAll("").trim(); // 將非上列所設定的字元全部replace 掉
    }
    public static byte[] hexToBytes(String hexString) {

        char[] hex = hexString.toCharArray();
        //轉rawData長度減半
        int length = hex.length / 2;
        byte[] rawData = new byte[length];
        for (int i = 0; i < length; i++) {
            //先將hex資料轉10進位數值
            int high = Character.digit(hex[i * 2], 16);
            int low = Character.digit(hex[i * 2 + 1], 16);
            //將第一個值的二進位值左平移4位,ex: 00001000 => 10000000 (8=>128)
            //然後與第二個值的二進位值作聯集ex: 10000000 | 00001100 => 10001100 (137)
            int value = (high << 4) | low;
            //與FFFFFFFF作補集
            if (value > 127)
                value -= 256;
            //最後轉回byte就OK
            rawData [i] = (byte) value;
        }
        return rawData ;
    }
    private Boolean checkLicenseKeyLength()
    {
        String filterdata = filterDate(et_license_key.getText().toString());
        if(filterdata.length()!=24)
        {
            return false;
        }
        return true;
    }
    private void Dialog_Pane(String title,String content)
    {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage(content);

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
            }
        });
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }
    private void Submit_Dialog_Pane(String title,String content)
    {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage(content);

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
            }
        });
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private Button.OnClickListener ButtonListenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_submit:
                    Boolean checklength = checkLicenseKeyLength();
                    if(!checklength)
                    {
                        Dialog_Pane("Error","License key length is not correct.");
                    }
                    else
                    {
                        setActivateStateKey();
                    }
                    break;

                case R.id.btn_clear:
                    et_license_key.setText("");
                    break;
            }
        }
    };

    private void setActivateStateKey()
    {
        String filterdata = filterDate(et_license_key.getText().toString());
        byte data[] = hexToBytes(filterdata);
        Intent ActivateStateKeyset = new Intent(NIRScanSDK.ACTION_ACTIVATE_STATE);
        ActivateStateKeyset.putExtra(NIRScanSDK.ACTIVATE_STATE_KEY, data);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(ActivateStateKeyset);
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class RetrunActivateStatusReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            Submit_Dialog_Pane("","Set activation key is completed.");
            byte state[] = intent.getByteArrayExtra(NIRScanSDK.RETURN_ACTIVATE_STATUS);
            if(state[0] == 1)
            {
                et_status.setText("Activated");
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.licensekey, et_license_key.getText().toString());
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Activated.");
            }
            else
            {
                et_status.setText("Function is locked.");
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Function is locked.");
            }
        }
    }
}

