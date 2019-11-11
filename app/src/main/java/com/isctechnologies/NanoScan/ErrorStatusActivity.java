package com.isctechnologies.NanoScan;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by iris.lin on 2019/8/20.
 */

public class ErrorStatusActivity extends Activity {
    private ListView listView;
    private static Context mContext;
    byte[] bufByteError;
    private Button btn_clear_error;

    int[] images = { R.drawable.leg_gray, R.drawable.leg_gray, R.drawable.leg_gray,
            R.drawable.leg_gray, R.drawable.leg_gray, R.drawable.leg_gray, R.drawable.leg_gray,R.drawable.leg_gray,R.drawable.leg_gray,R.drawable.leg_gray,R.drawable.leg_gray};
    String[] title = { "Scan", "ADC", "EEPROM", "Bluetooth", "Spectrum Library", "Hardware","TMP006" ,"HDC1000","Battery","Memory","UART"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error_status);
        mContext = this;
        //Set up the action bar title and enable the back arrow
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(getString(R.string.detail_error_status));
        }
        btn_clear_error = (Button)findViewById(R.id.btn_clear_error);
        btn_clear_error.setOnClickListener(clear_error_listenser);
        Bundle bundle = getIntent().getExtras();
        String errstatus = bundle.getString("ERRSTATUS" );
        Log.d("aaa", "dev status aaa:" + errstatus);
        byte[] errbyte = bundle.getByteArray("ERRBYTE");
        bufByteError  = errbyte;

        int[] images = { R.drawable.leg_gray, R.drawable.leg_gray, R.drawable.leg_gray,
                R.drawable.leg_gray, R.drawable.leg_gray, R.drawable.leg_gray, R.drawable.leg_gray,R.drawable.leg_gray,R.drawable.leg_gray,R.drawable.leg_gray,R.drawable.leg_gray};

        int data = errbyte[0]&0xFF | (errbyte[1] << 8);//0XFF avoid nagtive number

        int error_scan = 0x00000001;
        for(int j=0;j<2;j++)
        {
            int ret = data & error_scan;
            if(ret == error_scan)
            {
                images[j] = R.drawable.led_r;
            }
            error_scan = error_scan<<1;
        }
        error_scan = error_scan<<1;

        for(int j=2;j<11;j++)
        {
            int ret = data & error_scan;
            if(ret == error_scan)
            {
                images[j] = R.drawable.led_r;
            }
            error_scan = error_scan<<1;
        }
        listView = (ListView) findViewById(R.id.error_status_listview);
        // 建立資料來源


        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        // 將圖片和文字放入集合中
        for (int i = 0; i < images.length; i++) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("images", images[i]);
            map.put("title", title[i]);
            list.add(map);
        }
        // 建立介面卡
        SimpleAdapter adapter = new SimpleAdapter(this, list,
                R.layout.activity_error_status_item, new String[] { "images", "title" }, new int[] {
                R.id.image, R.id.error_test });
        // 繫結介面卡
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(onClickListView);

    }
    /***
     * 點擊ListView事件Method
     */
    private AdapterView.OnItemClickListener onClickListView = new AdapterView.OnItemClickListener(){
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            switch (position)
            {
                case 0:
                    Intent graphIntent = new Intent(mContext, ErrorScanStatusActivity.class);
                    graphIntent.putExtra("POS",position);
                    graphIntent.putExtra("ERRBYTE",bufByteError);
                    startActivity(graphIntent);
                    break;
                case 1:
                    Intent graphIntent1 = new Intent(mContext, ErrorScanStatusActivity.class);
                    graphIntent1.putExtra("POS",position);
                    graphIntent1.putExtra("ERRBYTE",bufByteError);
                    startActivity(graphIntent1);
                    break;
                case 5:
                    Intent graphIntent5 = new Intent(mContext, ErrorScanStatusActivity.class);
                    graphIntent5.putExtra("POS",position);
                    graphIntent5.putExtra("ERRBYTE",bufByteError);
                    startActivity(graphIntent5);
                    break;
                case 6:
                    Intent graphIntent6 = new Intent(mContext, ErrorScanStatusActivity.class);
                    graphIntent6.putExtra("POS",position);
                    graphIntent6.putExtra("ERRBYTE",bufByteError);
                    startActivity(graphIntent6);
                    break;
                case 7:
                    Intent graphIntent7 = new Intent(mContext, ErrorScanStatusActivity.class);
                    graphIntent7.putExtra("POS",position);
                    graphIntent7.putExtra("ERRBYTE",bufByteError);
                    startActivity(graphIntent7);
                    break;
                case 8:
                    Intent graphIntent8 = new Intent(mContext, ErrorScanStatusActivity.class);
                    graphIntent8.putExtra("POS",position);
                    graphIntent8.putExtra("ERRBYTE",bufByteError);
                    startActivity(graphIntent8);
                    break;
            }
        }

    };
    /*
 * Handle the selection of a menu item.
 * In this case, there is only the up indicator. If selected, this activity should finish.
 */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if(id == android.R.id.home){
            this.finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private Button.OnClickListener clear_error_listenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            Intent ClearErrorStatus = new Intent(NIRScanSDK.CLEAR_ERROR_STATUS);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(ClearErrorStatus);

            List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
            // 將圖片和文字放入集合中

            for (int i = 0; i < images.length; i++) {
                images[i] = R.drawable.leg_gray;
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("images", images[i]);
                map.put("title", title[i]);
                list.add(map);
            }
            SimpleAdapter adapter = new SimpleAdapter(mContext, list,
                    R.layout.activity_error_status_item, new String[] { "images", "title" }, new int[] {
                    R.id.image, R.id.error_test });
            // 繫結介面卡
            listView.setAdapter(adapter);
            for(int i=0;i<bufByteError.length;i++)
            {
                bufByteError[i] = 0x00;
            }
        }
    };

}
