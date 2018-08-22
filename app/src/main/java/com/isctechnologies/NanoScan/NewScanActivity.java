package com.isctechnologies.NanoScan;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;

import org.apache.commons.lang3.ObjectUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.isctechnologies.NanoScan.SettingsManager.SharedPreferencesKeys.preferredDeviceModel;

/**
 * Activity controlling the Nano once it is connected
 * This activity allows a user to initiate a scan, as well as access other "connection-only"
 * settings. When first launched, the app will scan for a preferred device
 * for {@link NanoBLEService#SCAN_PERIOD}, if it is not found, then it will start another "open"
 * scan for any Nano.
 *
 * If a preferred Nano has not been set, it will start a single scan. If at the end of scanning, a
 * Nano has not been found, a message will be presented to the user indicating and error, and the
 * activity will finish
 *
 * WARNING: This activity uses JNI function calls for communicating with the Spectrum C library, It
 * is important that the name and file structure of this activity remain unchanged, or the functions
 * will NOT work
 *
 * @author collinmast
 */
public class NewScanActivity extends Activity {

    static {
        System.loadLibrary("native-lib");
    }
    public static native int GetMaxPatternJNI(int scan_type,int start_nm,int end_nm, int width_index, int num_repeat,byte SpectrumCalCoefficients[]);
    public native int dlpSpecScanInterpReference(byte scanData[], byte CalCoefficients[],byte RefCalMatrix[], double wavelength[],int intensity[],int uncalibratedIntensity[]);
    public static native int dlpSpecScanReadConfiguration(byte ConfigData[],int scanType[],int scanConfigIndex[],byte[] scanConfigSerialNumber,byte configName[],byte bufnumSections[],  byte sectionScanType[],
                                                   byte sectionWidthPx[], int sectionWavelengthStartNm[], int sectionWavelengthEndNm[], int sectionNumPatterns[], int sectionNumRepeats[], int sectionExposureTime[]);
    public static native int dlpSpecScanReadOneSectionConfiguration(byte ConfigData[],int scanType[],  byte ScanType[],
                                                                    byte WidthPx[], int WavelengthStartNm[], int WavelengthEndNm[], int NumPatterns[], int NumRepeats[], int ExposureTime[],int scanConfigIndex[],byte[] scanConfigSerialNumber,byte configName[]);
    public static native int dlpSpecScanWriteConfiguration(int scanType,int scanConfigIndex,int numRepeat,byte[] scanConfigSerialNumber,byte[] configName,byte numSections,
                                                           byte[] sectionScanType, byte[] sectionWidthPx, int[] sectionWavelengthStartNm, int[] sectionWavelengthEndNm, int[] sectionNumPatterns
            , int[] sectionExposureTime,byte[] EXTRA_DATA);
    public native int dlpSpecScanInterpConfigInfo(byte scanData[],int scanType[],byte[] scanConfigSerialNumber,byte configName[],byte bufnumSections[],  byte sectionScanType[],
                                                  byte sectionWidthPx[], int sectionWavelengthStartNm[], int sectionWavelengthEndNm[], int sectionNumPatterns[], int sectionNumRepeats[], int sectionExposureTime[],int pga[],int systemp[],int syshumidity[],
                                                  int lampintensity[],double shift_vector_coff[],double pixel_coff[],int day[]);
    public native int dlpSpecScanInterpReferenceInfo(byte scanData[], byte CalCoefficients[],byte RefCalMatrix[],int refsystemp[],int refsyshumidity[],
                                                     int reflampintensity[],int numpattren[],int width[],int numrepeat[],int refday[]);


    private static Context mContext;

    private ProgressDialog barProgressDialog;

    private ViewPager mViewPager;
    private String fileName;
    private ArrayList<String> mXValues;

    private ArrayList<Entry> mIntensityFloat;
    private ArrayList<Entry> mAbsorbanceFloat;
    private ArrayList<Entry> mReflectanceFloat;
    private ArrayList<Entry> mReferenceFloat;
    private ArrayList<Float> mWavelengthFloat;

    private final BroadcastReceiver scanDataReadyReceiver = new scanDataReadyReceiver();
    private final BroadcastReceiver refReadyReceiver = new refReadyReceiver();
    private final BroadcastReceiver notifyCompleteReceiver = new notifyCompleteReceiver();
    private final BroadcastReceiver scanStartedReceiver = new ScanStartedReceiver();
    private final BroadcastReceiver requestCalCoeffReceiver = new requestCalCoeffReceiver();
    private final BroadcastReceiver requestCalMatrixReceiver = new requestCalMatrixReceiver();
    private final BroadcastReceiver disconnReceiver = new DisconnReceiver();
    private final BroadcastReceiver SpectrumCalCoefficientsReadyReceiver = new SpectrumCalCoefficientsReadyReceiver();
    private final BroadcastReceiver RetrunReadActivateStatusReceiver = new RetrunReadActivateStatusReceiver();
    private final IntentFilter RetrunReadActivateStatusFilter = new IntentFilter(NIRScanSDK.ACTION_RETURN_READ_ACTIVATE_STATE);
    private final BroadcastReceiver RetrunActivateStatusReceiver = new RetrunActivateStatusReceiver();
    private final BroadcastReceiver ReturnCurrentScanConfigurationDataReceiver = new ReturnCurrentScanConfigurationDataReceiver();
    private final BroadcastReceiver mInfoReceiver = new mInfoReceiver();
    private final BroadcastReceiver getUUIDReceiver = new getUUIDReceiver();
    private final BroadcastReceiver getBatteryReceiver = new getBatteryReceiver();


    private final IntentFilter scanDataReadyFilter = new IntentFilter(NIRScanSDK.SCAN_DATA);
    private final IntentFilter refReadyFilter = new IntentFilter(NIRScanSDK.REF_CONF_DATA);
    private final IntentFilter notifyCompleteFilter = new IntentFilter(NIRScanSDK.ACTION_NOTIFY_DONE);
    private final IntentFilter requestCalCoeffFilter = new IntentFilter(NIRScanSDK.ACTION_REQ_CAL_COEFF);
    private final IntentFilter requestCalMatrixFilter = new IntentFilter(NIRScanSDK.ACTION_REQ_CAL_MATRIX);
    private final IntentFilter disconnFilter = new IntentFilter(NIRScanSDK.ACTION_GATT_DISCONNECTED);
    private final IntentFilter scanStartedFilter = new IntentFilter(NanoBLEService.ACTION_SCAN_STARTED);
    private final IntentFilter SpectrumCalCoefficientsReadyFilter = new IntentFilter(NIRScanSDK.SPEC_CONF_DATA);

    private final BroadcastReceiver scanConfReceiver = new ScanConfReceiver();
    private final IntentFilter scanConfFilter = new IntentFilter(NIRScanSDK.SCAN_CONF_DATA);
    private final IntentFilter RetrunActivateStatusFilter = new IntentFilter(NIRScanSDK.ACTION_RETURN_ACTIVATE);
    private final IntentFilter  ReturnCurrentScanConfigurationDataFilter = new IntentFilter(NIRScanSDK.RETURN_CURRENT_CONFIG_DATA);

    private ProgressBar calProgress;
    private NIRScanSDK.ScanResults results;
    private EditText filePrefix;
   // private ToggleButton btn_os;
    private ToggleButton btn_continuous;
    private Button btn_scan;
    private EditText et_normal_repeat;
    private EditText et_normal_interval_time;
    private Button btn_continuous_stop;

    private NanoBLEService mNanoBLEService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler;
    private static final String DEVICE_NAME = "NIR";
    private boolean connected;
    private AlertDialog alertDialog;
    private TextView tv_scan_conf;
    private String preferredDevice;
    private LinearLayout ll_conf;
    private NIRScanSDK.ScanConfiguration activeConf;

    private Menu mMenu;
    private int numberOfaverage;
    private int receivedConfSize=-1;
    private int storedConfSize;
    private BroadcastReceiver scanConfSizeReceiver=  new ScanConfSizeReceiver();
    private BroadcastReceiver getActiveScanConfReceiver;
    private ArrayList<NIRScanSDK.ScanConfiguration> ScanConfigList = new ArrayList<NIRScanSDK.ScanConfiguration>();
    private ArrayList<NIRScanSDK.ScanConfiguration> bufScanConfigList = new ArrayList<NIRScanSDK.ScanConfiguration>();//from scan configurations
    int index;
    private Long startTime;
    private Long EndTime;

    private float minWavelength=900;
    private float maxWavelength=1700;
    private float minAbsorbance=0;
    private float maxAbsorbance=2;
    private float minReflectance=-2;
    private float maxReflectance=2;
    private float minIntensity=-7000;
    private float maxIntensity=7000;
    private float minReference=-7000;
    private float maxReference=7000;
    private int numSections=0;

    private Button btn_normal;
    private Button btn_quickset;
    private Button btn_manual;
    private Button btn_maintain;
    private TextView tv_scan_conf_manual;
    private LinearLayout ly_conf_manual;
    private ToggleButton btn_scan_mode;
    private ToggleButton btn_lamp;
    private EditText et_lamptime;
    private EditText et_pga;
    private EditText et_repead;
    private int function = 1; //1->normal,2->quickset,3->manual,4->maintain
    private static final int NIRScanConfigType=1;
    private static final int NIRScanConfigWidth=5;
    private static final int NIRScanConfigSet=10;
    private static final int NIRScanConfigSave=11;
    private static final int NIRScanConfigIndex=2;
    private static final int NIRScanConfigStart_nm=3;
    private static final int NIRScanConfigEnd_nm=4;
    private static final int NIRScanConfigNumPattern=6;
    private static final int NIRScanConfigNumRepeats=7;
    private static final int NIRScanConfigSerialNumber=8;
    private static final int NIRScanConfigName=9;
    private static final int NIRScanConfigNumSections=12;
    private static final int NIRScanConfigExposureTime=13;
    private static final int NIRScsnConfigEraseAllConfig=14;

    //quick set-------------------------------------------------------
    private Spinner spin_scan_method;
    private Spinner spin_time;
    private Spinner spin_scan_width;
    private EditText et_prefix_lamp_quickset;
    private EditText et_spec_start;
    private EditText et_spec_end;
    private EditText et_res;
    private EditText et_aver_scan;
    private ToggleButton btn_continuous_scan_mode;
    private EditText scan_interval_time;
    private EditText et_repeat_quick;
    private Button btn_set_value;
    private Button btn_continuous_stop_quick;
    private TextView tv_res;
    int scan_method_index =0;
    int exposure_time_index =0;
    int scan_width_index = 2;
    private int continuous_count=0;
    Boolean show_finish_continous_dialog = false;
    public static boolean showActiveconfigpage = false;
    private int init_start_nm ;
    private int init_end_nm ;
    private int init_res ;
    //maintain ------------------------------------------------------------
    private ToggleButton btn_reference;
    boolean stop_continuous = false;

    byte[] SpectrumCalCoefficients = new byte[144];
    char[] convertedChar = new char[SpectrumCalCoefficients.length];
    int MaxPattern = 0;
    Boolean init_viewpage_valuearray = false;

    public static byte []passSpectrumCalCoefficients = new byte[144];
    public static Boolean Licensestatusfalg = false;
    Boolean downloadspecFlag = false;

    Boolean isScan = false;
    int tabPosition = 0;


    private final IntentFilter WriteScanConfigStatusFilter = new IntentFilter(NIRScanSDK.ACTION_RETURN_WRITE_SCAN_CONFIG_STATUS);
    private final BroadcastReceiver WriteScanConfigStatusReceiver = new WriteScanConfigStatusReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_scan);
        findViewById(R.id.layout_manual).setVisibility(View.GONE);
        findViewById(R.id.layout_quickset).setVisibility(View.GONE);
        findViewById(R.id.layout_maintain).setVisibility(View.GONE);
        Disable_Stop_Continous_button();

        mContext = this;
        calProgress = (ProgressBar) findViewById(R.id.calProgress);
        calProgress.setVisibility(View.VISIBLE);
        connected = false;

        ll_conf = (LinearLayout)findViewById(R.id.ll_conf);
        ll_conf.setClickable(false);
        ll_conf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(activeConf != null) {
                    Intent activeConfIntent = new Intent(mContext, ActiveScanActivity.class);
                    activeConfIntent.putExtra("conf",activeConf);
                    startActivity(activeConfIntent);
                }
            }
        });
        //manual------------------------------------------
        ly_conf_manual = (LinearLayout)findViewById(R.id.ly_conf_manual);
        ly_conf_manual.setClickable(false);
        ly_conf_manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(activeConf != null) {
                    Intent activeConfIntent = new Intent(mContext, ActiveScanActivity.class);
                    activeConfIntent.putExtra("conf",activeConf);
                    startActivity(activeConfIntent);
                }
            }
        });
        //------------------------------------------------

        //Set the filename from the intent
        Intent intent = getIntent();
        fileName = intent.getStringExtra("file_name");

        //Set up action bar enable tab navigation
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(getString(R.string.new_scan));
            ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            mViewPager = (ViewPager) findViewById(R.id.viewpager);
            mViewPager.setOffscreenPageLimit(2);

            // Create a tab listener that is called when the user changes tabs.
            ActionBar.TabListener tl = new ActionBar.TabListener() {
                @Override
                public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
                    //1.if select tab0 then scan, onTabSelected can't invoke. But select other tab can invoke.

                    if(isScan)
                    {
                        if(tabPosition == 0) //2. select tab0 then scan. choose tab1, 這時iscan -true但tabPosition會等於0會造成page錯誤，
                        //因此如果tabPosition是0的時候， 會選擇做mViewPager.setCurrentItem(tab.getPosition());看當下的狀態
                        {
                            mViewPager.setCurrentItem(tab.getPosition());
                        }
                        else//tabPosition 在做完scan之後會先記錄當下的tab再做更新
                        {
                            mViewPager.setCurrentItem(tabPosition);
                        }

                        isScan = false;
                    }
                    else
                    {
                        mViewPager.setCurrentItem(tab.getPosition());
                    }

                }

                @Override
                public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

                }

                @Override
                public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

                }
            };

            // Add 3 tabs, specifying the tab's text and TabListener
            for (int i = 0; i < 4; i++) {
                ab.addTab(
                        ab.newTab()
                                .setText(getResources().getStringArray(R.array.graph_tab_index)[i])
                                .setTabListener(tl));
            }
        }

        //Set up UI elements and event handlers
        filePrefix = (EditText) findViewById(R.id.et_prefix);
        //btn_os = (ToggleButton) findViewById(R.id.btn_saveOS);
        btn_continuous = (ToggleButton) findViewById(R.id.btn_continuous);
        et_normal_interval_time = (EditText) findViewById(R.id.et_normal_interval_time);
        et_normal_repeat = (EditText) findViewById(R.id.et_normal_repeat);
        btn_continuous_stop = (Button)findViewById(R.id.btn_continuous_stop);
        btn_scan = (Button) findViewById(R.id.btn_scan);
        tv_scan_conf = (TextView) findViewById(R.id.tv_scan_conf);
        btn_normal = (Button) findViewById(R.id.btn_normal);
        btn_quickset = (Button) findViewById(R.id.btn_quickset);
        btn_manual = (Button) findViewById(R.id.btn_manual);
        btn_maintain = (Button) findViewById(R.id.btn_maintain);
        tv_scan_conf_manual = (TextView)findViewById(R.id.tv_scan_conf_manual) ;
        btn_scan_mode = (ToggleButton) findViewById(R.id.btn_scan_mode);
        btn_lamp = (ToggleButton) findViewById(R.id.btn_lamp);
        et_lamptime = (EditText) findViewById(R.id.et_prefix_lamp);
        et_pga = (EditText) findViewById(R.id.et_pga);
        et_repead = (EditText) findViewById(R.id.et_repeat);
        btn_continuous_stop.setOnClickListener(stop_continous_listenser);

        //quickset------------------------------------------------------
        et_prefix_lamp_quickset = (EditText)findViewById(R.id.et_prefix_lamp_quickset);
        et_spec_start = (EditText)findViewById(R.id.et_spec_start);
        et_spec_end = (EditText)findViewById(R.id.et_spec_end);
        et_res = (EditText)findViewById(R.id.et_res);
        et_aver_scan = (EditText)findViewById(R.id.et_aver_scan);
        scan_interval_time = (EditText)findViewById(R.id.scan_interval_time);
        et_repeat_quick = (EditText)findViewById(R.id.et_repeat_quick);
        btn_continuous_scan_mode = (ToggleButton) findViewById(R.id.btn_continuous_scan_mode);
        btn_set_value = (Button)findViewById(R.id.btn_set_value);
        btn_continuous_stop_quick = (Button)findViewById(R.id.btn_continuous_stop_quick);
        tv_res = (TextView)findViewById(R.id.tv_res);

        init_start_nm = (Integer.parseInt(et_spec_start.getText().toString()));
        init_end_nm = (Integer.parseInt(et_spec_end.getText().toString()));
        init_res = (Integer.parseInt(et_res.getText().toString()));
        et_spec_start.setOnEditorActionListener(quick_spec_start);
        et_spec_end.setOnEditorActionListener(quick_spec_end);
        et_res.setOnEditorActionListener(quick_res_listener);

        spin_scan_method = (Spinner)findViewById(R.id.spin_scan_method);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.scan_method_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin_scan_method.setAdapter(adapter);
        spin_scan_method.setOnItemSelectedListener(scanmethodlistener);

        spin_time = (Spinner)findViewById(R.id.spin_time);
        ArrayAdapter<CharSequence> adapter_time = ArrayAdapter.createFromResource(this,
                R.array.exposure_time, android.R.layout.simple_spinner_item);
        adapter_time.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin_time.setAdapter(adapter_time);
        spin_time.setOnItemSelectedListener(spin_time_listener);

        spin_scan_width = (Spinner)findViewById(R.id.spin_scan_width);
        ArrayAdapter<CharSequence> adapter_width = ArrayAdapter.createFromResource(this,
                R.array.scan_width, android.R.layout.simple_spinner_item);
        adapter_width.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin_scan_width.setAdapter(adapter_width);
        spin_scan_width.setOnItemSelectedListener(scan_width_listener);
        btn_set_value.setOnClickListener(set_value_quickset_listenser);
        et_prefix_lamp_quickset.setOnEditorActionListener(set_quickset_lamp_time_listener);
        btn_continuous_stop_quick.setOnClickListener(stop_continous_listenser);
        //maintain--------------------------------------------------------------------------
        btn_reference = (ToggleButton)findViewById(R.id.btn_reference);
        //-----------------------------------------------------------------------------------------------------------------
        //btn_os.setChecked(SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveOS, false));
        btn_continuous.setChecked(SettingsManager.getBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.continuousScan, false));
        btn_lamp.setEnabled(false);
        et_repead.setEnabled(false);
        et_pga.setEnabled(false);
        et_lamptime.setEnabled(false);

        et_lamptime.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if(Integer.parseInt(et_lamptime.getText().toString())!=625)
                    {
                        controlLampTime(Integer.parseInt(et_lamptime.getText().toString()));
                    }
                    return false;
                }
                return false;
            }
        });

        btn_scan_mode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(btn_scan_mode.getText().toString().equals("Off"))// off->On
                {
                    btn_lamp.setEnabled(true);
                    et_repead.setEnabled(true);
                    et_pga.setEnabled(true);
                    et_lamptime.setEnabled(false);
                    controlManul(0);//open manual

                }
                else
                {
                    btn_lamp.setEnabled(false);
                    et_repead.setEnabled(false);
                    et_pga.setEnabled(false);
                    et_lamptime.setEnabled(true);
                    btn_lamp.setChecked(false);

                    controlLamp(2);
                    controlLamp(0);

                    controlManul(1);//close manual
                }
            }
        });

        btn_lamp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

                if(btn_lamp.getText().toString().equals("Off"))// off->On
                {
                    controlLamp(1);
                }
                else
                {
                    controlLamp(2);//reset
                    controlLamp(0);//close lamp
                }


            }
        });



        btn_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.prefix, filePrefix.getText().toString());
                if(function == 3)
                {
                    if( checkValidPga()==false)
                    {
                        OverFlowPGADialog();
                        return;
                    }
                    else if( checkValidRepeat()==false)
                    {
                        OverFlowRepeatDialog();
                        return;
                    }
                    else if(btn_scan_mode.getText().toString().equals("On"))
                    {
                        DisableAllComponent();
                        btn_scan.setText(getString(R.string.scanning));
                        if(btn_lamp.isChecked())
                        {
                            controlLamp(1);
                        }
                        else
                        {
                            controlLamp(0);
                        }

                        controlPGA();
                        controlRepeat();

                        Intent scan = new Intent(NIRScanSDK.ACTION_INTER_SCAN); calProgress.setVisibility(View.VISIBLE);
                        calProgress.setVisibility(View.VISIBLE);
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(scan);
                        startTime = System.currentTimeMillis();

                    }
                    else
                    {
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.START_SCAN));
                    }
                }
                else if(function == 2)//quick set
                {

                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.START_SCAN));

                }
                else if(function == 4)//maintain
                {
                    if(btn_reference.isChecked())
                    {
                        Dialog_Pane_maintain("Warning","Replace Factory Reference is ON !!! \n This sacn result will REPLACE the Factory Reference and can NOT be reversed!");
                    }
                    else
                    {
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.START_SCAN));
                    }
                }
                else
                {

                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.START_SCAN));

                }
                //---------------------------------------------------------------------------------------------------
                if(function == 4 && btn_reference.isChecked())
                {

                }
                else
                {
                    DisableAllComponent();
                    calProgress.setVisibility(View.VISIBLE);
                    btn_scan.setText(getString(R.string.scanning));
                    startTime = System.currentTimeMillis();
                }

            }
        });

        btn_scan.setClickable(false);
        btn_scan.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));
        //Add get active config index ------------------------------------------------------------------------
        getActiveScanConfReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                index = intent.getByteArrayExtra(NIRScanSDK.EXTRA_ACTIVE_CONF)[0];

                if(ScanConfigList.size()!=0)
                {
                     GetActiveConfigOnResume();
                }
                else
                {
                      LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_SCAN_CONF));
                }
            }
        };
        //control layout-----------------------------------------------------------------------
        btn_normal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(function == 3 && btn_scan_mode.isChecked())//manul->normal
                {
                    controlLamp(2);
                    controlLamp(0);
                    controlManul(1);//close manual
                }
                findViewById(R.id.layout_normal).setVisibility(View.VISIBLE);
                findViewById(R.id.layout_manual).setVisibility(View.GONE);
                findViewById(R.id.layout_quickset).setVisibility(View.GONE);
                findViewById(R.id.layout_maintain).setVisibility(View.GONE);
                btn_normal.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                btn_manual.setBackgroundColor(0xFF0099CC);
                btn_quickset.setBackgroundColor(0xFF0099CC);
                btn_maintain.setBackgroundColor(0xFF0099CC);
                function = 1;
                //----------------------------------------------------
                if(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Function is locked.").contains("Activated"))
                {
                    openFunction();
                }
                else
                {
                    closeFunction();
                }
            }
        });
        btn_normal.setClickable(false);
        btn_normal.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));

        btn_manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                findViewById(R.id.layout_manual).setVisibility(View.VISIBLE);
                findViewById(R.id.layout_normal).setVisibility(View.GONE);
                findViewById(R.id.layout_quickset).setVisibility(View.GONE);
                findViewById(R.id.layout_maintain).setVisibility(View.GONE);
                btn_manual.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                btn_normal.setBackgroundColor(0xFF0099CC);
                btn_quickset.setBackgroundColor(0xFF0099CC);
                btn_maintain.setBackgroundColor(0xFF0099CC);
                if(function !=3)
                {
                    btn_scan_mode.setChecked(false);
                    btn_lamp.setEnabled(false);
                    et_repead.setEnabled(false);
                    et_pga.setEnabled(false);
                    et_lamptime.setEnabled(true);
                    et_repead.setText("6");
                    et_pga.setText("1");
                    et_lamptime.setText("625");
                }
                function = 3;
            }
        });
        btn_manual.setClickable(false);
        btn_manual.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));

        btn_quickset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(function == 3 && btn_scan_mode.isChecked())//manul->quickset
                {
                    controlLamp(2);
                    controlLamp(0);
                    controlManul(1);//close manual
                }

                findViewById(R.id.layout_quickset).setVisibility(View.VISIBLE);
                findViewById(R.id.layout_manual).setVisibility(View.GONE);
                findViewById(R.id.layout_normal).setVisibility(View.GONE);
                findViewById(R.id.layout_maintain).setVisibility(View.GONE);
                btn_quickset.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                btn_manual.setBackgroundColor(0xFF0099CC);
                btn_normal.setBackgroundColor(0xFF0099CC);
                btn_maintain.setBackgroundColor(0xFF0099CC);
                function = 2;
                //---------------------------------------------------------------
                GetMaxPattern();
            }
        });
        btn_quickset.setClickable(false);
        btn_quickset.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));

        btn_maintain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(function == 3 && btn_scan_mode.isChecked())//manul->quickset
                {
                    controlLamp(2);
                    controlLamp(0);

                    controlManul(1);//close manual
                }

                findViewById(R.id.layout_maintain).setVisibility(View.VISIBLE);
                findViewById(R.id.layout_manual).setVisibility(View.GONE);
                findViewById(R.id.layout_normal).setVisibility(View.GONE);
                findViewById(R.id.layout_quickset).setVisibility(View.GONE);
                btn_maintain.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                btn_manual.setBackgroundColor(0xFF0099CC);
                btn_normal.setBackgroundColor(0xFF0099CC);
                btn_quickset.setBackgroundColor(0xFF0099CC);
                function = 4;

            }
        });
        btn_maintain.setClickable(false);
        btn_maintain.setBackgroundColor(ContextCompat.getColor(mContext, R.color.btn_unavailable));
        //------------------------------------------------------------------
        //Bind to the service. This will start it, and call the start command function
        Intent gattServiceIntent = new Intent(this, NanoBLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //Register all needed broadcast receivers
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanDataReadyReceiver, scanDataReadyFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(refReadyReceiver, refReadyFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(notifyCompleteReceiver, notifyCompleteFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(requestCalCoeffReceiver, requestCalCoeffFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(requestCalMatrixReceiver, requestCalMatrixFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(disconnReceiver, disconnFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfReceiver, scanConfFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanStartedReceiver, scanStartedFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfSizeReceiver, new IntentFilter(NIRScanSDK.SCAN_CONF_SIZE));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(getActiveScanConfReceiver, new IntentFilter(NIRScanSDK.SEND_ACTIVE_CONF));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(SpectrumCalCoefficientsReadyReceiver, SpectrumCalCoefficientsReadyFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(RetrunReadActivateStatusReceiver, RetrunReadActivateStatusFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(RetrunActivateStatusReceiver, RetrunActivateStatusFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(ReturnCurrentScanConfigurationDataReceiver, ReturnCurrentScanConfigurationDataFilter);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mInfoReceiver, new IntentFilter(NIRScanSDK.ACTION_INFO));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(getUUIDReceiver, new IntentFilter(NIRScanSDK.SEND_DEVICE_UUID));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(getBatteryReceiver, new IntentFilter(NIRScanSDK.SEND_BATTERY));
        //LocalBroadcastManager.getInstance(mContext).registerReceiver(WriteScanConfigStatusReceiver, WriteScanConfigStatusFilter);

        //----------------------------------------------------------------
    }
    private void Dialog_Pane_maintain(String title,String content)
    {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setMessage(content);

        alertDialogBuilder.setNegativeButton(getResources().getString(R.string.yes_i_know), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {

                ReferenceConfig(NIRScanConfigNumRepeats);
                ReferenceConfig(NIRScanConfigNumSections);
                ReferenceConfig(NIRScanConfigType);
                ReferenceConfig(NIRScanConfigWidth);
                ReferenceConfig(NIRScanConfigStart_nm);
                ReferenceConfig(NIRScanConfigEnd_nm);
                ReferenceConfig(NIRScanConfigNumPattern);
                ReferenceConfig(NIRScanConfigExposureTime);
                ReferenceConfig(NIRScanConfigSet);
                alertDialog.dismiss();
                ReferenceConfigSaveSuccess();
            }
        });

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                btn_reference.setChecked(false);
                alertDialog.dismiss();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void ReferenceConfigSaveSuccess() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle("Finish");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage("Complete save reference config, start scan");

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.START_SCAN));

                DisableAllComponent();
                calProgress.setVisibility(View.VISIBLE);
                btn_scan.setText(getString(R.string.scanning));
                startTime = System.currentTimeMillis();

                alertDialog.dismiss();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }

    private void OverFlowRepeatDialog() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle("Error");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage("Scan repeat range is 1~100.");

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }
    private void OverFlowPGADialog() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle("Error");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage("PGA vlaue is 1,2,4,8,16,32,64.");

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }
    // back to this page shoule get active config index
    private void GetActiveConfigOnResume()
    {
        bufScanConfigList = ScanConfActivity.bufconfigs;//from scan configuration
        int storenum = bufScanConfigList.size();
        if(storenum!=ScanConfigList.size())
        {
            ScanConfigList.clear();
            for(int i=0;i<bufScanConfigList.size();i++)
            {
                ScanConfigList.add(bufScanConfigList.get(i));
            }
        }

        for(int i=0;i<ScanConfigList.size();i++)
         {
             int ScanConfigIndextoByte = (byte)ScanConfigList.get(i).getScanConfigIndex();
             if(index == ScanConfigIndextoByte )
             {
                 activeConf = ScanConfigList.get(i);
                 tv_scan_conf.setText(activeConf.getConfigName());
                 tv_scan_conf_manual.setText(activeConf.getConfigName());
             }
         }
    }

    @Override
    public void onResume() {

        super.onResume();
        numSections=0;
        //Fix set Scan Configurations then back to New scan,put scan configuration ,the title and info always the last info.
        //EX:scan configuration {Column 1, Hadamard 1, aaa ,bbb},select Hadamard 1 then back to new sacn.
        //push Hadamard1,it title is show bbb and bbb info

        if(showActiveconfigpage == true) //in active page back to this page,do nothing
        {

        }
        else
        {
            LocalBroadcastManager.getInstance(mContext).registerReceiver(scanConfSizeReceiver, new IntentFilter(NIRScanSDK.SCAN_CONF_SIZE));
            LocalBroadcastManager.getInstance(mContext).registerReceiver(getActiveScanConfReceiver, new IntentFilter(NIRScanSDK.SEND_ACTIVE_CONF));
            LocalBroadcastManager.getInstance(mContext).registerReceiver(WriteScanConfigStatusReceiver, WriteScanConfigStatusFilter);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_ACTIVE_CONF));
        }

        //-----------------------------------------------------------------------------------------------------------

        if(showActiveconfigpage ==true)//in active page back to this page,do nothing,don't init scan Configuration text
        {
            showActiveconfigpage = false;
        }
        else
        {
            tv_scan_conf.setText(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, "Column 1"));
            tv_scan_conf_manual.setText(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, "Column 1"));
        }
        if(init_viewpage_valuearray == false)
        {
            init_viewpage_valuearray = true;
            //Initialize view pager
            CustomPagerAdapter pagerAdapter = new CustomPagerAdapter(this);
            mViewPager.setAdapter(pagerAdapter);
            mViewPager.invalidate();
            mViewPager.setOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageSelected(int position) {
                            // When swiping between pages, select the
                            // corresponding tab.
                            ActionBar ab = getActionBar();
                            if (ab != null) {
                                getActionBar().setSelectedNavigationItem(position);
                            }
                        }
                    });

            mXValues = new ArrayList<>();
            mIntensityFloat = new ArrayList<>();
            mAbsorbanceFloat = new ArrayList<>();
            mReflectanceFloat = new ArrayList<>();
            mWavelengthFloat = new ArrayList<>();
            mReferenceFloat = new ArrayList<>();
        }
        else
        {
            if(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, null).contains("Activated"))
            {
                openFunction();
            }
            else
            {
                closeFunction();
            }
        }
        //------------------------------------------

    }

    /*
     * When the activity is destroyed, unregister all broadcast receivers, remove handler callbacks,
     * and store all user preferences
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanDataReadyReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(refReadyReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(notifyCompleteReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(requestCalCoeffReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(requestCalMatrixReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(disconnReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfSizeReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getActiveScanConfReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(SpectrumCalCoefficientsReadyReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(RetrunReadActivateStatusReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(RetrunActivateStatusReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(ReturnCurrentScanConfigurationDataReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(WriteScanConfigStatusReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mInfoReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getUUIDReceiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getBatteryReceiver);

        mHandler.removeCallbacksAndMessages(null);

        //SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.saveOS, btn_os.isChecked());
        SettingsManager.storeBooleanPref(mContext, SettingsManager.SharedPreferencesKeys.continuousScan, btn_continuous.isChecked());
    }

    /*
     * Inflate the options menu so that user actions are present
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_new_scan, menu);
        mMenu = menu;
        mMenu.findItem(R.id.action_settings).setEnabled(false);
        mMenu.findItem(R.id.action_key).setEnabled(false);
        return true;
    }

    /*
     * Handle the selection of a menu item.
     * In this case, the user has the ability to access settings while the Nano is connected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            //avoid conflict when go to scan config page
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfReceiver);
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(scanConfSizeReceiver);
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(getActiveScanConfReceiver);
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(WriteScanConfigStatusReceiver);

            Intent configureIntent = new Intent(mContext, ConfigureActivity.class);
            startActivity(configureIntent);
        }
        if (id == R.id.action_key) {
            Intent configureIntent = new Intent(mContext, LicenseKey.class);
            startActivity(configureIntent);
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(RetrunActivateStatusReceiver);
        }
        if (id == android.R.id.home) {
            this.finish();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Pager enum to control tab tile and layout resource
     */
    public enum CustomPagerEnum {

        REFLECTANCE(R.string.reflectance, R.layout.page_graph_reflectance),
        ABSORBANCE(R.string.absorbance, R.layout.page_graph_absorbance),
        INTENSITY(R.string.intensity, R.layout.page_graph_intensity),
        REFERENCE(R.string.reference_tab,R.layout.page_graph_reference);

        private final int mTitleResId;
        private final int mLayoutResId;

        CustomPagerEnum(int titleResId, int layoutResId) {
            mTitleResId = titleResId;
            mLayoutResId = layoutResId;
        }

        public int getLayoutResId() {
            return mLayoutResId;
        }

    }

    /**
     * Custom pager adapter to handle changing chart data when pager tabs are changed
     */
    public class CustomPagerAdapter extends PagerAdapter {

        private final Context mContext;

        public CustomPagerAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            CustomPagerEnum customPagerEnum = CustomPagerEnum.values()[position];
            LayoutInflater inflater = LayoutInflater.from(mContext);
            ViewGroup layout = (ViewGroup) inflater.inflate(customPagerEnum.getLayoutResId(), collection, false);
            collection.addView(layout);

            if (customPagerEnum.getLayoutResId() == R.layout.page_graph_intensity) {
                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartInt);
                mChart.setDrawGridBackground(false);

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setAxisMaximum(maxWavelength);
                xAxis.setAxisMinimum(minWavelength);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(true);
                leftAxis.setAxisMaximum(maxIntensity);
                leftAxis.setAxisMinimum(minIntensity);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);

                // add data

                if(activeConf != null && activeConf.getScanType().equals("Slew")) {
                    numSections = activeConf.getSlewNumSections();
                }
                if(numSections>=2 &&(Float.isNaN(minIntensity)==false && Float.isNaN(maxIntensity)==false) && function!=2)//function!=2 because quickset only one section
                {
                    setDataSlew(mChart, mIntensityFloat,numSections);
                }
                else if(Float.isNaN(minIntensity)==false && Float.isNaN(maxIntensity)==false)
                {
                    setData(mChart, mXValues, mIntensityFloat,ChartType.INTENSITY);
                }


                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);
                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_absorbance) {

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartAbs);
                mChart.setDrawGridBackground(false);

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setAxisMaximum(maxWavelength);
                xAxis.setAxisMinimum(minWavelength);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
                leftAxis.setAxisMaximum(maxAbsorbance);
                leftAxis.setAxisMinimum(minAbsorbance);

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                int numSections=0;
                if(activeConf != null && activeConf.getScanType().equals("Slew")) {
                    numSections = activeConf.getSlewNumSections();
                }
                if(numSections>=2 &&(Float.isNaN(minAbsorbance)==false && Float.isNaN(maxAbsorbance)==false)&& function!=2)//function!=2 because quickset only one section
                {
                    setDataSlew(mChart, mAbsorbanceFloat,numSections);
                }
                else if( Float.isNaN(minAbsorbance)==false && Float.isNaN(maxAbsorbance)==false)
                {
                    setData(mChart, mXValues, mAbsorbanceFloat, ChartType.ABSORBANCE);
                }


                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);

                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_reflectance) {

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartRef);
                mChart.setDrawGridBackground(false);


                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setAxisMaximum(maxWavelength);
                xAxis.setAxisMinimum(minWavelength);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
                leftAxis.setAxisMaximum(maxReflectance);
                leftAxis.setAxisMinimum(minReflectance);

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                int numSections=0;
                if(activeConf != null && activeConf.getScanType().equals("Slew")) {
                   numSections = activeConf.getSlewNumSections();
                }
                if(numSections>=2 &&(Float.isNaN(minReflectance)==false && Float.isNaN(maxReflectance)==false)&& function!=2)//function!=2 because quickset only one section
                {
                    setDataSlew(mChart, mReflectanceFloat,numSections);
                }

                else if(Float.isNaN(minReflectance)==false && Float.isNaN(maxReflectance)==false)
                {
                    setData(mChart, mXValues, mReflectanceFloat, ChartType.REFLECTANCE);
                }


                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);
                return layout;
            } else if (customPagerEnum.getLayoutResId() == R.layout.page_graph_reference) {

                LineChart mChart = (LineChart) layout.findViewById(R.id.lineChartReference);
                mChart.setDrawGridBackground(false);

                // enable touch gestures
                mChart.setTouchEnabled(true);

                // enable scaling and dragging
                mChart.setDragEnabled(true);
                mChart.setScaleEnabled(true);

                // if disabled, scaling can be done on x- and y-axis separately
                mChart.setPinchZoom(true);

                // x-axis limit line
                LimitLine llXAxis = new LimitLine(10f, "Index 10");
                llXAxis.setLineWidth(4f);
                llXAxis.enableDashedLine(10f, 10f, 0f);
                llXAxis.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                llXAxis.setTextSize(10f);

                XAxis xAxis = mChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setAxisMaximum(maxWavelength);
                xAxis.setAxisMinimum(minWavelength);

                YAxis leftAxis = mChart.getAxisLeft();
                leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
                leftAxis.setAxisMaximum(maxReference);
                leftAxis.setAxisMinimum(minReference);

                mChart.setAutoScaleMinMaxEnabled(true);

                leftAxis.setStartAtZero(false);
                leftAxis.enableGridDashedLine(10f, 10f, 0f);

                // limit lines are drawn behind data (and not on top)
                leftAxis.setDrawLimitLinesBehindData(true);

                mChart.getAxisRight().setEnabled(false);


                // add data
                int numSections=0;
                if(activeConf != null && activeConf.getScanType().equals("Slew")) {
                    numSections = activeConf.getSlewNumSections();
                }
                if(numSections>=2 &&(Float.isNaN(minReference)==false && Float.isNaN(maxReference)==false)&& function!=2)//function!=2 because quickset only one section
                {
                    setDataSlew(mChart, mReferenceFloat,numSections);
                }
                else if( Float.isNaN(minReference)==false && Float.isNaN(maxReference)==false)
                {
                    setData(mChart, mXValues, mReferenceFloat, ChartType.INTENSITY);
                }


                mChart.animateX(2500, Easing.EasingOption.EaseInOutQuart);

                // get the legend (only possible after setting data)
                Legend l = mChart.getLegend();

                // modify the legend ...
                l.setForm(Legend.LegendForm.LINE);
                mChart.getLegend().setEnabled(false);

                return layout;
            }else {
                return layout;
            }
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }

        @Override
        public int getCount() {
            return CustomPagerEnum.values().length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.reflectance);
                case 1:
                    return getString(R.string.absorbance);
                case 2:
                    return getString(R.string.intensity);
            }
            return null;
        }

    }

    private void setData(LineChart mChart, ArrayList<String> xValues, ArrayList<Entry> yValues, ChartType type) {

        if (type == ChartType.REFLECTANCE) {
            //init yvalues
            int size = yValues.size();
            if(size == 0)
            {
               return;
            }
            //---------------------------------------------------------
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);
            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.RED);
            set1.setLineWidth(1f);
            set1.setCircleSize(2f);
            set1.setDrawCircleHole(false);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.RED);
            set1.setDrawFilled(true);
            set1.setValues(yValues);

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(set1);
            LineData data = new LineData(dataSets);
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else if (type == ChartType.ABSORBANCE) {
            int size = yValues.size();
            if(size == 0)
            {
               return;
            }
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.GREEN);
            set1.setLineWidth(1f);
            set1.setCircleSize(2f);
            set1.setDrawCircleHole(false);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.GREEN);
            set1.setDrawFilled(true);
            set1.setValues(yValues);

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(set1);
            LineData data = new LineData(dataSets);
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(20);
        } else if (type == ChartType.INTENSITY) {
            int size = yValues.size();
            if(size == 0)
            {
               return;
            }
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.BLUE);
            set1.setLineWidth(1f);
            set1.setCircleSize(2f);
            set1.setDrawCircleHole(false);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.BLUE);
            set1.setDrawFilled(true);
            set1.setValues(yValues);

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(set1);
            LineData data = new LineData(dataSets);
            mChart.setData(data);


            mChart.setMaxVisibleValueCount(20);
        } else {
            int size = yValues.size();
            if(size == 0)
            {
                yValues.add(new Entry((float) -10, (float) -10));
            }
            // create a dataset and give it a type
            LineDataSet set1 = new LineDataSet(yValues, fileName);

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.BLACK);
            set1.setLineWidth(1f);
            set1.setCircleSize(3f);
            set1.setDrawCircleHole(true);
            set1.setValueTextSize(9f);
            set1.setFillAlpha(65);
            set1.setFillColor(Color.BLACK);
            set1.setDrawFilled(true);
            set1.setValues(yValues);

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(set1);
            LineData data = new LineData(dataSets);
            mChart.setData(data);

            mChart.setMaxVisibleValueCount(10);
        }
    }

    private void setDataSlew(LineChart mChart, ArrayList<Entry> yValues,int slewnum)
    {
        if(yValues.size()<=1)
        {
            return;
        }
        ArrayList<Entry> yValues1 = new ArrayList<Entry>();
        ArrayList<Entry> yValues2 = new ArrayList<Entry>();
        ArrayList<Entry> yValues3 = new ArrayList<Entry>();
        ArrayList<Entry> yValues4 = new ArrayList<Entry>();
        ArrayList<Entry> yValues5 = new ArrayList<Entry>();

        for(int i=0;i<activeConf.getSectionNumPatterns()[0];i++)
        {
            if(Float.isInfinite(yValues.get(i).getY()) == false)
            {
                yValues1.add(new Entry(yValues.get(i).getX(),yValues.get(i).getY()));
            }
        }

        int offset = activeConf.getSectionNumPatterns()[0];
        for(int i=0;i<activeConf.getSectionNumPatterns()[1];i++)
        {
            if(Float.isInfinite(yValues.get(offset+ i).getY()) == false)
            {
                yValues2.add(new Entry(yValues.get(offset + i).getX(),yValues.get(offset+ i).getY()));
            }
        }
        if(slewnum>=3)
        {
            offset = activeConf.getSectionNumPatterns()[0] + activeConf.getSectionNumPatterns()[1];
            for(int i=0;i<activeConf.getSectionNumPatterns()[2];i++)
            {
                if(Float.isInfinite(yValues.get(offset+ i).getY()) == false)
                {
                    yValues3.add(new Entry(yValues.get(offset + i).getX(),yValues.get(offset+ i).getY()));
                }

            }
        }
        if(slewnum>=4)
        {
            offset = activeConf.getSectionNumPatterns()[0] + activeConf.getSectionNumPatterns()[1]+ activeConf.getSectionNumPatterns()[2];
            for(int i=0;i<activeConf.getSectionNumPatterns()[3];i++)
            {
                if(Float.isInfinite(yValues.get(offset+ i).getY()) == false)
                {
                    yValues4.add(new Entry(yValues.get(offset + i).getX(),yValues.get(offset+ i).getY()));
                }
            }
        }
        if(slewnum==5)
        {
            offset = activeConf.getSectionNumPatterns()[0] + activeConf.getSectionNumPatterns()[1]+ activeConf.getSectionNumPatterns()[2]+ activeConf.getSectionNumPatterns()[3];
            for(int i=0;i<activeConf.getSectionNumPatterns()[4];i++)
            {
                if(Float.isInfinite(yValues.get(offset+ i).getY()) == false)
                {
                    yValues5.add(new Entry(yValues.get(offset + i).getX(),yValues.get(offset+ i).getY()));
                }
            }
        }

        // create a dataset and give it a type
        LineDataSet set1 = new LineDataSet(yValues1, "Slew1");
        LineDataSet set2 = new LineDataSet(yValues2, "Slew2");
        LineDataSet set3 = new LineDataSet(yValues3, "Slew3");
        LineDataSet set4 = new LineDataSet(yValues4, "Slew4");
        LineDataSet set5 = new LineDataSet(yValues5, "Slew5");

        // set the line to be drawn like this "- - - - - -"
        set1.enableDashedLine(10f, 5f, 0f);
        set1.enableDashedHighlightLine(10f, 5f, 0f);
        set1.setColor(Color.BLUE);
        set1.setCircleColor(Color.BLUE);
        set1.setLineWidth(1f);
        set1.setCircleSize(2f);
        set1.setDrawCircleHole(false);
        set1.setValueTextSize(9f);
        set1.setFillAlpha(65);
        set1.setFillColor(Color.BLUE);
        set1.setDrawFilled(true);
        set1.setValues(yValues1);

        // set the line to be drawn like this "- - - - - -"
        set2.enableDashedLine(10f, 5f, 0f);
        set2.enableDashedHighlightLine(10f, 5f, 0f);
        set2.setColor(Color.RED);
        set2.setCircleColor(Color.RED);
        set2.setLineWidth(1f);
        set2.setCircleSize(2f);
        set2.setDrawCircleHole(false);
        set2.setValueTextSize(9f);
        set2.setFillAlpha(65);
        set2.setFillColor(Color.RED);
        set2.setDrawFilled(true);
        set2.setValues(yValues2);
        // set the line to be drawn like this "- - - - - -"
        set3.enableDashedLine(10f, 5f, 0f);
        set3.enableDashedHighlightLine(10f, 5f, 0f);
        set3.setColor(Color.GREEN);
        set3.setCircleColor(Color.GREEN);
        set3.setLineWidth(1f);
        set3.setCircleSize(2f);
        set3.setDrawCircleHole(false);
        set3.setValueTextSize(9f);
        set3.setFillAlpha(65);
        set3.setFillColor(Color.GREEN);
        set3.setDrawFilled(true);
        set3.setValues(yValues3);
        // set the line to be drawn like this "- - - - - -"
        set4.enableDashedLine(10f, 5f, 0f);
        set4.enableDashedHighlightLine(10f, 5f, 0f);
        set4.setColor(Color.YELLOW);
        set4.setCircleColor(Color.YELLOW);
        set4.setLineWidth(1f);
        set4.setCircleSize(2f);
        set4.setDrawCircleHole(false);
        set4.setValueTextSize(9f);
        set4.setFillAlpha(65);
        set4.setFillColor(Color.YELLOW);
        set4.setDrawFilled(true);
        set4.setValues(yValues4);

        // set the line to be drawn like this "- - - - - -"
        set5.enableDashedLine(10f, 5f, 0f);
        set5.enableDashedHighlightLine(10f, 5f, 0f);
        set5.setColor(Color.LTGRAY);
        set5.setCircleColor(Color.LTGRAY);
        set5.setLineWidth(1f);
        set5.setCircleSize(2f);
        set5.setDrawCircleHole(false);
        set5.setValueTextSize(9f);
        set5.setFillAlpha(65);
        set5.setFillColor(Color.LTGRAY);
        set5.setDrawFilled(true);
        set5.setValues(yValues5);

        if(slewnum==2)
        {
            LineData data = new LineData(set1, set2);
            mChart.setData(data);
            mChart.setMaxVisibleValueCount(20);
        }
        if(slewnum==3)
        {
            LineData data = new LineData(set1, set2,set3);
            mChart.setData(data);
            mChart.setMaxVisibleValueCount(20);
        }

        if(slewnum==4)
        {
            LineData data = new LineData(set1, set2,set3,set4);
            mChart.setData(data);
            mChart.setMaxVisibleValueCount(20);
        }

        if(slewnum==5)
        {
            LineData data = new LineData(set1, set2,set3,set4,set5);
            mChart.setData(data);
            mChart.setMaxVisibleValueCount(20);
        }


    }


    /**
     * Custom enum for chart type
     */
    public enum ChartType {
        REFLECTANCE,
        ABSORBANCE,
        INTENSITY
    }

    /**
     * Custom receiver for handling scan data and setting up the graphs properly
     */
    boolean continuous = false;
    byte[] scanData;
    NIRScanSDK.ReferenceCalibration ref;
    String filetsName;
    public class scanDataReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            calProgress.setVisibility(View.GONE);
            btn_scan.setText(getString(R.string.scan));
            EnableAllComponent();
            Disable_Stop_Continous_button();
            scanData = intent.getByteArrayExtra(NIRScanSDK.EXTRA_DATA);

            String scanType = intent.getStringExtra(NIRScanSDK.EXTRA_SCAN_TYPE);
            /*
            * 7 bytes representing the current data
            * byte0: uint8_t     year; //< years since 2000
            * byte1: uint8_t     month; /**< months since January [0-11]
            * byte2: uint8_t     day; /**< day of the month [1-31]
            * byte3: uint8_t     day_of_week; /**< days since Sunday [0-6]
            * byte3: uint8_t     hour; /**< hours since midnight [0-23]
            * byte5: uint8_t     minute; //< minutes after the hour [0-59]
            * byte6: uint8_t     second; //< seconds after the minute [0-60]
            */
            String scanDate = intent.getStringExtra(NIRScanSDK.EXTRA_SCAN_DATE);

            ref = NIRScanSDK.ReferenceCalibration.currentCalibration.get(0);
            //---------------------------------------------------------------------------------------------------------
            double[] wavelength = new double[700];
            int[] intensity = new int[700];
            int[] uncalibratedIntensity = new int[700];
            int length=0;
            length = dlpSpecScanInterpReference(scanData, ref.getRefCalCoefficients(), ref.getRefCalMatrix(),wavelength,intensity,uncalibratedIntensity);
            results = new NIRScanSDK.ScanResults(wavelength,intensity,uncalibratedIntensity,length);
            //-------------------------------------------------------------------------------------------------------------------

            mXValues.clear();
            mIntensityFloat.clear();
            mAbsorbanceFloat.clear();
            mReflectanceFloat.clear();
            mWavelengthFloat.clear();
            mReferenceFloat.clear();

            int index;
            for (index = 0; index < results.getLength(); index++) {
                mXValues.add(String.format("%.02f", NIRScanSDK.ScanResults.getSpatialFreq(mContext, results.getWavelength()[index])));
                mIntensityFloat.add(new Entry((float) results.getWavelength()[index],(float) results.getUncalibratedIntensity()[index]));
                mAbsorbanceFloat.add(new Entry((float) results.getWavelength()[index],(-1) * (float) Math.log10((double) results.getUncalibratedIntensity()[index] / (double) results.getIntensity()[index])));
                mReflectanceFloat.add(new Entry((float) results.getWavelength()[index],(float) results.getUncalibratedIntensity()[index] / results.getIntensity()[index]));
                mWavelengthFloat.add((float) results.getWavelength()[index]);
                mReferenceFloat.add(new Entry((float) results.getWavelength()[index],(float) results.getIntensity()[index]));
            }

            minWavelength = mWavelengthFloat.get(0);
            maxWavelength = mWavelengthFloat.get(0);

            for (Float f : mWavelengthFloat) {
                if (f < minWavelength) minWavelength = f;
                if (f > maxWavelength) maxWavelength = f;
            }

            minAbsorbance = mAbsorbanceFloat.get(0).getY();
            maxAbsorbance = mAbsorbanceFloat.get(0).getY();

            for (Entry e : mAbsorbanceFloat) {
                if (e.getY() < minAbsorbance || Float.isNaN(minAbsorbance)) minAbsorbance = e.getY();
                if (e.getY() > maxAbsorbance || Float.isNaN(maxAbsorbance)) maxAbsorbance = e.getY();
            }
            if(minAbsorbance==0 && maxAbsorbance==0)
            {
                maxAbsorbance=2;
            }

             minReflectance = mReflectanceFloat.get(0).getY();
             maxReflectance = mReflectanceFloat.get(0).getY();

            for (Entry e : mReflectanceFloat) {
                if (e.getY() < minReflectance|| Float.isNaN(minReflectance) ) minReflectance = e.getY();
                if (e.getY() > maxReflectance|| Float.isNaN(maxReflectance) ) maxReflectance = e.getY();
            }
            if(minReflectance==0 && maxReflectance==0)
            {
                maxReflectance=2;
            }

            minIntensity = mIntensityFloat.get(0).getY();
            maxIntensity = mIntensityFloat.get(0).getY();

            for (Entry e : mIntensityFloat) {
                if (e.getY() < minIntensity|| Float.isNaN(minIntensity)) minIntensity = e.getY();
                if (e.getY() > maxIntensity|| Float.isNaN(maxIntensity)) maxIntensity = e.getY();
            }
            if(minIntensity==0 && maxIntensity==0)
            {
                maxIntensity=1000;
            }

            minReference = mReferenceFloat.get(0).getY();
            maxReference = mReferenceFloat.get(0).getY();

            for (Entry e : mReferenceFloat) {
                if (e.getY() < minReference || Float.isNaN(minReference)) minReference = e.getY();
                if (e.getY() > maxReference || Float.isNaN(maxReference)) maxReference = e.getY();
            }
            if(minReference==0 && maxReference==0)
            {
                maxReference=1000;
            }
            isScan = true;
            tabPosition = mViewPager.getCurrentItem();
            mViewPager.setAdapter(mViewPager.getAdapter());
            mViewPager.invalidate();
            
            //Show the right scan type------------------------------
            if(activeConf != null && activeConf.getScanType().equals("Slew")){
                int numSections = activeConf.getSlewNumSections();
                if(numSections >1)
                {
                    scanType = "Slew";
                }
                else if(activeConf.getSectionScanType()[0] == 1)
                {
                    scanType  = "Hadamard";
                }
                else
                {
                    scanType = "Column";
                }
            }
            //number of slew
            String slew="";
            if(activeConf != null && activeConf.getScanType().equals("Slew")){
                int numSections = activeConf.getSlewNumSections();
                int i;
                for(i = 0; i < numSections; i++){
                   slew = slew + activeConf.getSectionNumPatterns()[i]+"%";
                }
            }


            float mesureTime =(float) (EndTime - startTime)/1000;

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault());
            SimpleDateFormat filesimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault());
            String ts = simpleDateFormat.format(new Date());
            filetsName = filesimpleDateFormat.format(new Date());
            ActionBar ab = getActionBar();
            if (ab != null) {

                if (filePrefix.getText().toString().equals("")) {
                    ab.setTitle("ISC" + ts);
                } else {
                    ab.setTitle(filePrefix.getText().toString() + ts);
                }
                ab.setSelectedNavigationItem(0);
            }

           // boolean saveOS = btn_os.isChecked();

            if(function == 1)
            {
                continuous = btn_continuous.isChecked();
            }
            else
            {
                continuous = btn_continuous_scan_mode.isChecked();
            }

            SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.prefix, filePrefix.getText().toString());
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_BATTERY));

        }
    }
    private void ScanDataResult()
    {
        if(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, null).contains("Activated") ==false)
        {
            closeFunction();
        }
        writeCSV(scanData,filetsName, results, true,ref.getRefCalCoefficients(), ref.getRefCalMatrix());
        if(function == 4 && btn_reference.isChecked())
        {
            saveReference();
            SaveReferenceDialog();
        }
        //-----------------------------------------------

        int interval_time = 0;
        int repeat = 0;
        if(function == 1)
        {
            interval_time = Integer.parseInt(et_normal_interval_time.getText().toString());
            repeat = Integer.parseInt(et_normal_repeat.getText().toString()) -1;//-1 want to match scan count
        }
        else
        {
            interval_time = Integer.parseInt(scan_interval_time.getText().toString());
            repeat = Integer.parseInt(et_repeat_quick.getText().toString()) -1;//-1 want to match scan count
        }

        if(show_finish_continous_dialog == true)
        {
            String content = "There were totally " + (continuous_count+1) + " scans has been performed!.";
            Dialog_Pane("Continuous Scan Completed!",content);
            show_finish_continous_dialog = false;
            continuous_count = 0;
        }
        if (continuous) {
            // Dialog_Pane_Bottom("aaa","bbb");
            continuous_count ++;
            calProgress.setVisibility(View.VISIBLE);
            btn_scan.setText(getString(R.string.scanning));
            DisableAllComponent();
            Enable_Stop_Continous_button();
            try {
                Thread.sleep(interval_time*1000);
            }catch (Exception e)
            {

            }
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.SEND_DATA));
            if(continuous_count == repeat || stop_continuous == true)
            {
                // continuous_count = 0;
                continuous = false;
                stop_continuous = false;
                btn_continuous_scan_mode.setChecked(false);
                btn_continuous.setChecked(false);
                show_finish_continous_dialog = true;
                Disable_Stop_Continous_button();
            }
        }
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class SpectrumCalCoefficientsReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            SpectrumCalCoefficients = intent.getByteArrayExtra(NIRScanSDK.EXTRA_SPEC_COEF_DATA);
            passSpectrumCalCoefficients = SpectrumCalCoefficients;
            //read current ActivateState------------------------------------------------------------------------------------
           // readActivateState();
            //Send broadcast to the BLE service to request device information
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_INFO));
        }
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    String model_name="";
    String serial_num = "";
    String HWrev = "";
    String Tivarev ="";
    String Specrev = "";
    public class mInfoReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            model_name = intent.getStringExtra(NIRScanSDK.EXTRA_MODEL_NUM);
            serial_num = intent.getStringExtra(NIRScanSDK.EXTRA_SERIAL_NUM);
            HWrev = intent.getStringExtra(NIRScanSDK.EXTRA_HW_REV);
            Tivarev = intent.getStringExtra(NIRScanSDK.EXTRA_TIVA_REV);
            Specrev = intent.getStringExtra(NIRScanSDK.EXTRA_SPECTRUM_REV);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_UUID));
        }
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    String uuid="";

    public class getUUIDReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {

           byte buf[] = intent.getByteArrayExtra(NIRScanSDK.EXTRA_DEVICE_UUID);
           for(int i=0;i<buf.length;i++)
           {
               uuid += Integer.toHexString( 0xff & buf[i] );
               if(i!= buf.length-1)
               {
                   uuid +=":";
               }
           }
            //read current ActivateState------------------------------------------------------------------------------------
            readActivateState();
        }
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    String battery="";

    public class getBatteryReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {

            battery = Integer.toString(intent.getIntExtra(NIRScanSDK.EXTRA_BATTERY,0));
            ScanDataResult();
        }
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class refReadyReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            byte[] refCoeff = intent.getByteArrayExtra(NIRScanSDK.EXTRA_REF_COEF_DATA);
            byte[] refMatrix = intent.getByteArrayExtra(NIRScanSDK.EXTRA_REF_MATRIX_DATA);
            ArrayList<NIRScanSDK.ReferenceCalibration> refCal = new ArrayList<>();
            refCal.add(new NIRScanSDK.ReferenceCalibration(refCoeff, refMatrix));
            NIRScanSDK.ReferenceCalibration.writeRefCalFile(mContext, refCal);
            calProgress.setVisibility(View.GONE);
            //------------------------------------------------------------------
            ScanListMain.storeCalibration.device = preferredDevice;
            ScanListMain.storeCalibration.storrefCoeff = refCoeff;
            ScanListMain.storeCalibration.storerefMatrix = refMatrix;
        }
    }

    /**
     * Custom receiver for returning the event that a scan has been initiated from the button
     */
    public class ScanStartedReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            calProgress.setVisibility(View.VISIBLE);
            btn_scan.setText(getString(R.string.scanning));
            EndTime = System.currentTimeMillis();
        }
    }

    /**
     * Custom receiver that will request the time once all of the GATT notifications have been
     * subscribed to
     */
    public class notifyCompleteReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            Boolean reference = false;
            if(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.ReferenceScan, "Not").equals("ReferenceScan"))
            {
                reference = true;
            }
            if(preferredDevice.equals(ScanListMain.storeCalibration.device) && reference == false)
            {
                byte[] refCoeff = ScanListMain.storeCalibration.storrefCoeff;
                byte[] refMatrix = ScanListMain.storeCalibration.storerefMatrix;
                ArrayList<NIRScanSDK.ReferenceCalibration> refCal = new ArrayList<>();
                refCal.add(new NIRScanSDK.ReferenceCalibration(refCoeff, refMatrix));
                NIRScanSDK.ReferenceCalibration.writeRefCalFile(mContext, refCal);
                calProgress.setVisibility(View.INVISIBLE);
                barProgressDialog = new ProgressDialog(NewScanActivity.this);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_ACTIVE_CONF));
            }
            else
            {
                if(reference == true)
                {
                    SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.ReferenceScan, "Not");
                }
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.SET_TIME));
            }
        }
    }

    /**
     * Write scan data to CSV file
     * @param currentTime the current time to save
     * @param scanResults the {@link NIRScanSDK.ScanResults} structure to save
     * @param saveOS boolean indicating if the CSV file should be saved to the OS
     */
    private void writeCSV(byte[] scandata,String currentTime, NIRScanSDK.ScanResults scanResults, boolean saveOS,byte[]RefCalCoefficients,byte[]RefCalMatrix) {

        int scanType=0;
        int scanConfigIndex=0;
        byte[] scanConfigSerialNumber = new byte[8];
        byte[] configName = new byte[40];
        int numSections=0;
        byte[] sectionScanType=new byte[5];
        byte[] sectionWidthPx=new byte[5];
        int[] sectionWavelengthStartNm = new int[5];
        int[] sectionWavelengthEndNm = new int[5];
        int[] sectionNumPatterns = new int[5];
        int[] sectionNumRepeats = new int[5];
        int[] sectionExposureTime = new int[5];
        String widthnm[] ={"","","2.34","3.51","4.68","5.85","7.03","8.20","9.37","10.54","11.71","12.88","14.05","15.22","16.39","17.56","18.74"
                ,"19.91","21.08","22.25","23.42","24.59","25.76","26.93","28.10","29.27","30.44","31.62","32.79","33.96","35.13","36.30","37.47","38.64","39.81"
                ,"40.98","42.15","43.33","44.50","45.67","46.84","48.01","49.18","50.35","51.52","52.69","53.86","55.04","56.21","57.38","58.55","59.72","60.89"};
        String exposureTime[] = {"0.635ms","1.27ms"," 2.54ms"," 5.08ms","15.24ms","30.48ms","60.96ms"};
        int index = 0;
        double temp;
        double humidity;

        int refsystemp[] =new int[1];
        int refsyshumidity[] =new int[1];
        int reflampintensity[] =new int[1];
        int numpattren[] =new int[1];
        int width[] =new int[1];
        int numrepeat[] =new int[1];
        int refday[] =new int[6];

        int systemp[] =new int[1];
        int syshumidity[] =new int[1];
        int lampintensity[] =new int[1];
        int day[] =new int[6];
        double shift_vector_coff[] = new double[3];
        double pixel_coff[] = new double[3];
        //-------------------------------------------------
        int []bufscanType = new int[1];
        byte[]bufnumSections = new byte[1];
        int []pga = new int[1];
        String newdate = "";

        String CSV[][] = new String[35][15];
        for (int i = 0; i < 35; i++)
            for (int j = 0; j < 15; j++)
                CSV[i][j] = ",";
        dlpSpecScanInterpConfigInfo(scandata,bufscanType,scanConfigSerialNumber,configName,bufnumSections,
                sectionScanType,sectionWidthPx,sectionWavelengthStartNm,sectionWavelengthEndNm,sectionNumPatterns,sectionNumRepeats,sectionExposureTime,pga,systemp,syshumidity,lampintensity,
                shift_vector_coff,pixel_coff,day);
        dlpSpecScanInterpReferenceInfo(scandata,RefCalCoefficients,RefCalMatrix,refsystemp,refsyshumidity,reflampintensity,numpattren,width,numrepeat,refday);
        numSections = bufnumSections[0];
        scanType = bufscanType[0];
        //----------------------------------------------------------------
        String  configname = getBytetoString(configName);
        if(function == 4 && btn_reference.isChecked())
        {
            configname = "Reference";
           /* Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month=cal.get(Calendar.MONTH);
            int date=cal.get(Calendar.DATE);
            int hour=cal.get(Calendar.HOUR);
            int minute=cal.get(Calendar.MINUTE);
            int second=cal.get(Calendar.SECOND);
            refday[0] = year;
            refday[1] = month;
            refday[2] = date;
            refday[3] = hour;
            refday[4] = minute;
            refday[5] = second;*/

            Date datetime = new Date();
            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yy-HH:mm:ss");
            newdate = format.format(datetime);
            CSV[19][4] = newdate;
        }
        else
        {
            CSV[19][4] = refday[1] + "/"+ refday[2] + "/"+ refday[0] + "-" + refday[3] + ":" + refday[4] + ":" + refday[5];
        }
        String prefix = filePrefix.getText().toString();
        if (prefix.equals("")) {
            prefix = "ISC";
        }

        if (saveOS) {
            String csvOS = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + prefix+"_" + configname + "_" + currentTime + ".csv";


            // Section information field names
            CSV[0][0] = "***ISC NIRScan Scan Result ***,";
            CSV[2][0] = "---General Information---";
            CSV[12][0] = "---Device Status Information---,";
            CSV[12][3] = "---Reference Scan Information---";
            CSV[22][0] = "---Scan Config Information---";
            CSV[34][0] = "---Scan Data---";
            //General Information
            CSV[3][0] = "Model Number:,";
            CSV[4][0] = "UUID:,";
            CSV[4][2] = "Serial Number:,";
            CSV[5][0] = "Hardware Rev:,";
            CSV[6][0] = "Tiva Rev:,";
            CSV[6][2] = "Spectrum Rev:,";
            CSV[7][0] = "Shift Vector Coefficient:,";
            CSV[8][0] = "Pixel to Wavelength Coefficient:,";
            CSV[9][0] = "System Temp:,";
            CSV[9][2] = "System Humidity:,";
            CSV[10][0] = "Lamp Intensity:,";

            CSV[3][1] = model_name + ",";
            CSV[4][1] = uuid + ",";
            CSV[4][3] = serial_num + ",";
            CSV[5][1] = HWrev + ",";
            CSV[6][1] = Tivarev + ",";
            CSV[6][3] = Specrev + ",";

            temp = systemp[0];
            temp = temp/100;
            CSV[9][1] = temp + "C" + ",";
            humidity =  syshumidity[0];
            humidity =  humidity/100;
            CSV[9][3] = humidity + "%RH" + ",";
            CSV[10][1] = lampintensity[0] + ",";

            CSV[7][1] = shift_vector_coff[0] + ",";
            CSV[7][2] = shift_vector_coff[1] + ",";
            CSV[7][3] = shift_vector_coff[2] + ",";

            CSV[8][1] = pixel_coff[0] + ",";
            CSV[8][2] = pixel_coff[1] + ",";
            CSV[8][3] = pixel_coff[2] + ",";
            //Device Status Information
            CSV[13][0] = "Battery Capacity:,";
            CSV[13][1] = battery + "%,";
            CSV[14][0] = "Scan TimeStamp:,";
            CSV[14][1] = day[1] + "/"+ day[2] + "/"+ day[0] + "-" + day[3] + ":" + day[4] + ":" + day[5] + ",";

            //referense info
            CSV[13][3] = "System Temp:,";
            CSV[14][3] = "System Humidity:,";
            CSV[15][3] = "Lamp Intensity:,";
            CSV[16][3] = "Sample Points:,";
            CSV[17][3] = "Scan Resolution:,";
            CSV[18][3] = "Number of Scans to Average:,";
            CSV[19][3] = "Scan TimeStamp:,";

            temp = refsystemp[0];
            temp = temp/100;
            CSV[13][4] = temp + "C";
            humidity =  syshumidity[0];
            humidity =  refsyshumidity[0]/100;
            CSV[14][4] = humidity + "%RH";
            CSV[15][4] = reflampintensity[0] +"";
            CSV[16][4] = numpattren[0] + "pts";
            index = width[0];
            CSV[17][4] = widthnm[index] + "nm";
            CSV[18][4] = numrepeat[0] + "";


            //Scan Configuration
            CSV[23][0] = "Scan Type:,";
            CSV[24][0] = "Scan Config Name:,";
            CSV[25][0] = "Section Scan Type:,";
            CSV[26][0] = "Spectral Start:,";
            CSV[27][0] = "Spectral End:,";
            CSV[28][0] = "Sample Points:,";
            CSV[29][0] = "Scan Width:,";
            CSV[30][0] = "Exposure Time:,";
            CSV[31][0] = "Scan Number to Average:,";
            CSV[32][0] = "PGA Gain:,";



            CSV[23][1] = scanType + " (0:Col; 1:Had; 2:Slew),";

            CSV[24][1] = configname ;
            for(int i=0;i<numSections;i++)
            {
                if(sectionScanType[i] ==0)
                {
                    CSV[25][i+1] = "Col,";
                }
                else
                {
                    CSV[25][i+1] = "Had,";
                }
                CSV[26][i+1] = sectionWavelengthStartNm[i] + "nm,";
                CSV[27][i+1] = sectionWavelengthEndNm[i] + "nm,";
                CSV[28][i+1] = sectionNumPatterns[i] + "pts,";
                index = sectionWidthPx[i];
                CSV[29][i+1] = widthnm[index] + "nm,";
                index = sectionExposureTime[i];
                CSV[30][i+1] = exposureTime[index] + ",";
            }
            CSV[31][1] = sectionNumRepeats[0] + ",";
            CSV[32][1] = pga[0] + ",";


            CSVWriter writer;
            try {
                writer = new CSVWriter(new FileWriter(csvOS), ',', CSVWriter.NO_QUOTE_CHARACTER);
                List<String[]> data = new ArrayList<String[]>();

                String buf = "";
                for (int i = 0; i < 34; i++)
                {
                    for (int j = 0; j < 15; j++)
                    {
                        buf += CSV[i][j];
                        if (j == 14)
                        {
                            data.add(new String[]{buf});
                        }
                    }
                    buf = "";
                }

                data.add(new String[]{"Wavelength,Intensity,Absorbance,Reflectance"});
                int csvIndex;
                for (csvIndex = 0; csvIndex < scanResults.getLength(); csvIndex++) {
                    double waves = scanResults.getWavelength()[csvIndex];
                    int intens = scanResults.getUncalibratedIntensity()[csvIndex];
                    float absorb = (-1) * (float) Math.log10((double) scanResults.getUncalibratedIntensity()[csvIndex] / (double) scanResults.getIntensity()[csvIndex]);
                    float reflect = (float) results.getUncalibratedIntensity()[csvIndex] / results.getIntensity()[csvIndex];
                    data.add(new String[]{String.valueOf(waves), String.valueOf(intens), String.valueOf(absorb), String.valueOf(reflect)});
                }

                writer.writeAll(data);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getBytetoString(byte configName[]) {
        byte[] byteChars = new byte[40];
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] var3 = byteChars;
        int i = byteChars.length;

        for(int var5 = 0; var5 < i; ++var5) {
            byte b = var3[var5];
            byteChars[b] = 0;
        }

        String s = null;

        for(i = 0; i < configName.length; ++i) {
            byteChars[i] = configName[i];
            if(configName[i] == 0) {
                break;
            }

            os.write(configName[i]);
        }
        try {
            s = new String(os.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException var7) {
            var7.printStackTrace();
        }

        return s;
    }

    /**
     * Write the dictionary for a CSV files
     * @param currentTime the current time to be saved
     * @param scanType the scan type to be saved
     * @param timeStamp the timestamp to be saved
     * @param spectStart the spectral range start
     * @param spectEnd the spectral range end
     * @param numPoints the number of data points
     * @param resolution the scan resolution
     * @param numAverages the number of scans to average
     * @param measTime the total measurement time
     * @param saveOS boolean indicating if this file should be saved to the OS
     */
    private void writeCSVDict(String currentTime, String scanType, String timeStamp, String spectStart, String spectEnd, String numPoints, String resolution, String numAverages, String measTime, boolean saveOS,String slew) {

        String prefix = filePrefix.getText().toString();
        if (prefix.equals("")) {
            prefix = "Nano";
        }

        if (saveOS) {
            String csv = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + prefix + currentTime + ".dict";

            CSVWriter writer;
            try {
                writer = new CSVWriter(new FileWriter(csv));
                List<String[]> data = new ArrayList<String[]>();
                data.add(new String[]{"Method", scanType});
                data.add(new String[]{"Timestamp", timeStamp});
                data.add(new String[]{"Spectral Range Start (nm)", spectStart});
                data.add(new String[]{"Spectral Range End (nm)", spectEnd});
                data.add(new String[]{"Number of Wavelength Points", numPoints});
                data.add(new String[]{"Digital Resolution", resolution});
                data.add(new String[]{"Number of Scans to Average", numAverages});
                data.add(new String[]{"Total Measurement Time (s)", measTime});
                data.add(new String[]{"Slew Section", slew});

                writer.writeAll(data);

                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            //Get a reference to the service from the service connection
            mNanoBLEService = ((NanoBLEService.LocalBinder) service).getService();

            //initialize bluetooth, if BLE is not available, then finish
            if (!mNanoBLEService.initialize()) {
                finish();
            }

            //Start scanning for devices that match DEVICE_NAME
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if(mBluetoothLeScanner == null){
                finish();
                Toast.makeText(NewScanActivity.this, "Please ensure Bluetooth is enabled and try again", Toast.LENGTH_SHORT).show();
            }
            mHandler = new Handler();
            if (SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null) != null) {
                preferredDevice = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);
                scanPreferredLeDevice(true);
            } else {
                scanLeDevice(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mNanoBLEService = null;
        }
    };

    /**
     * Callback function for Bluetooth scanning. This function provides the instance of the
     * Bluetooth device {@link BluetoothDevice} that was found, it's rssi, and advertisement
     * data (scanRecord).
     * <p>
     * When a Bluetooth device with the advertised name matching the
     * string DEVICE_NAME {@link NewScanActivity#DEVICE_NAME} is found, a call is made to connect
     * to the device. Also, the Bluetooth should stop scanning, even if
     * the {@link NanoBLEService#SCAN_PERIOD} has not expired
     */
    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            String preferredNano = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);

            if (name != null) {
                if (device.getName().contains(DEVICE_NAME) && device.getAddress().equals(preferredNano)) {
                    mNanoBLEService.connect(device.getAddress());
                    connected = true;
                    scanLeDevice(false);
                }
            }
        }
    };

    /**
     * Callback function for preferred Nano scanning. This function provides the instance of the
     * Bluetooth device {@link BluetoothDevice} that was found, it's rssi, and advertisement
     * data (scanRecord).
     * <p>
     * When a Bluetooth device with the advertised name matching the
     * string DEVICE_NAME {@link NewScanActivity#DEVICE_NAME} is found, a call is made to connect
     * to the device. Also, the Bluetooth should stop scanning, even if
     * the {@link NanoBLEService#SCAN_PERIOD} has not expired
     */
    private final ScanCallback mPreferredLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            String preferredNano = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.preferredDevice, null);
            if (name != null) {
                if (device.getName().contains(DEVICE_NAME) && device.getAddress().equals(preferredNano)) {
                    if (device.getAddress().equals(preferredDevice)) {
                        mNanoBLEService.connect(device.getAddress());
                        connected = true;
                        scanPreferredLeDevice(false);
                    }
                }
            }
        }
    };

    /**
     * Scans for Bluetooth devices on the specified interval {@link NanoBLEService#SCAN_PERIOD}.
     * This function uses the handler {@link NewScanActivity#mHandler} to delay call to stop
     * scanning until after the interval has expired. The start and stop functions take an
     * LeScanCallback parameter that specifies the callback function when a Bluetooth device
     * has been found {@link NewScanActivity#mLeScanCallback}
     *
     * @param enable Tells the Bluetooth adapter {@link NIRScanSDK#mBluetoothAdapter} if
     *               it should start or stop scanning
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mBluetoothLeScanner != null) {
                        mBluetoothLeScanner.stopScan(mLeScanCallback);
                        if (!connected) {
                            notConnectedDialog();
                        }
                    }
                }
            }, NanoBLEService.SCAN_PERIOD);
            if(mBluetoothLeScanner != null) {
                mBluetoothLeScanner.startScan(mLeScanCallback);
            }else{
                finish();
                Toast.makeText(NewScanActivity.this, "Please ensure Bluetooth is enabled and try again", Toast.LENGTH_SHORT).show();
            }
        } else {
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    /**
     * Scans for preferred Nano devices on the specified interval {@link NanoBLEService#SCAN_PERIOD}.
     * This function uses the handler {@link NewScanActivity#mHandler} to delay call to stop
     * scanning until after the interval has expired. The start and stop functions take an
     * LeScanCallback parameter that specifies the callback function when a Bluetooth device
     * has been found {@link NewScanActivity#mPreferredLeScanCallback}
     *
     * @param enable Tells the Bluetooth adapter {@link NIRScanSDK#mBluetoothAdapter} if
     *               it should start or stop scanning
     */
    private void scanPreferredLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(mPreferredLeScanCallback);
                    if (!connected) {

                        scanLeDevice(true);
                    }
                }
            }, NanoBLEService.SCAN_PERIOD);
            if(mBluetoothLeScanner == null)
            {
                notConnectedDialog();
            }
            else
            {
                mBluetoothLeScanner.startScan(mPreferredLeScanCallback);
            }

        } else {
            mBluetoothLeScanner.stopScan(mPreferredLeScanCallback);
        }
    }

    /**
     * Dialog that tells the user that a Nano is not connected. The activity will finish when the
     * user selects ok
     */
    private void notConnectedDialog() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(mContext.getResources().getString(R.string.not_connected_title));
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage(mContext.getResources().getString(R.string.not_connected_message));

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
                finish();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }

    private void SaveReferenceDialog() {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle("Finish");
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage("Replace Factory Reference is complete.\n Should reconnect bluetooth to reload reference.");

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                alertDialog.dismiss();
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.ReferenceScan, "ReferenceScan");
                finish();
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }

    /**
     * Custom receiver for receiving calibration coefficient data.
     */
    public class requestCalCoeffReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            intent.getIntExtra(NIRScanSDK.EXTRA_REF_CAL_COEFF_SIZE, 0);
            Boolean size = intent.getBooleanExtra(NIRScanSDK.EXTRA_REF_CAL_COEFF_SIZE_PACKET, false);
            if (size) {
                calProgress.setVisibility(View.INVISIBLE);
                barProgressDialog = new ProgressDialog(NewScanActivity.this);

                barProgressDialog.setTitle(getString(R.string.dl_ref_cal));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(intent.getIntExtra(NIRScanSDK.EXTRA_REF_CAL_COEFF_SIZE, 0));
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {
                barProgressDialog.setProgress(barProgressDialog.getProgress() + intent.getIntExtra(NIRScanSDK.EXTRA_REF_CAL_COEFF_SIZE, 0));
            }
        }
    }

    /**
     * Custom receiver for receiving calibration matrix data. When this receiver action complete, it
     * will request the active configuration so that it can be displayed in the listview
     */
    public class requestCalMatrixReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            intent.getIntExtra(NIRScanSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0);
            Boolean size = intent.getBooleanExtra(NIRScanSDK.EXTRA_REF_CAL_MATRIX_SIZE_PACKET, false);
            if (size) {
                barProgressDialog.dismiss();
                barProgressDialog = new ProgressDialog(NewScanActivity.this);

                barProgressDialog.setTitle(getString(R.string.dl_cal_matrix));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(intent.getIntExtra(NIRScanSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0));
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {
                barProgressDialog.setProgress(barProgressDialog.getProgress() + intent.getIntExtra(NIRScanSDK.EXTRA_REF_CAL_MATRIX_SIZE, 0));
            }
            if (barProgressDialog.getProgress() == barProgressDialog.getMax()) {
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(NIRScanSDK.GET_ACTIVE_CONF));
            }
        }
    }

    /**
     * Custom receiver for handling scan configurations
     */
    private class ScanConfReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            receivedConfSize++;
            NIRScanSDK.ScanConfiguration scanConf;
            if(intent.getByteArrayExtra(NIRScanSDK.EXTRA_DATA).length>100)
            {
                 scanConf = GetScanConfiguration(intent.getByteArrayExtra(NIRScanSDK.EXTRA_DATA));
            }
            else
            {
               scanConf = GetOneSectionScanConfiguration(intent.getByteArrayExtra(NIRScanSDK.EXTRA_DATA));
            }
            ScanConfigList.add(scanConf);

            if (storedConfSize>0 && receivedConfSize==0) {
                barProgressDialog.dismiss();
                barProgressDialog = new ProgressDialog(NewScanActivity.this);

                barProgressDialog.setTitle(getString(R.string.reading_configurations));
                barProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                barProgressDialog.setProgress(0);
                barProgressDialog.setMax(storedConfSize);
                barProgressDialog.setCancelable(false);
                barProgressDialog.show();
            } else {
                barProgressDialog.setProgress(receivedConfSize+1);

            }
            if (barProgressDialog.getProgress() == barProgressDialog.getMax() || barProgressDialog.getMax()==1) {

                byte[] smallArray = intent.getByteArrayExtra(NIRScanSDK.EXTRA_DATA);
                byte[] addArray = new byte[smallArray.length * 3];
                byte[] largeArray = new byte[smallArray.length + addArray.length];

                System.arraycopy(smallArray, 0, largeArray, 0, smallArray.length);
                System.arraycopy(addArray, 0, largeArray, smallArray.length, addArray.length);

                Log.w("_JNI","largeArray Size: "+ largeArray.length);

                for(int i=0;i<ScanConfigList.size();i++)
                {
                    int ScanConfigIndextoByte = (byte)ScanConfigList.get(i).getScanConfigIndex();
                    if(index == ScanConfigIndextoByte )
                    {
                        activeConf = ScanConfigList.get(i);
                    }
                }

                //activeConf = scanConf;
                barProgressDialog.dismiss();
                btn_scan.setClickable(true);
                btn_scan.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                btn_normal.setClickable(true);
                btn_normal.setBackgroundColor(0xFF0099CC);
                btn_quickset.setClickable(true);
                btn_quickset.setBackgroundColor(0xFF0099CC);
                btn_manual.setClickable(true);
                btn_manual.setBackgroundColor(0xFF0099CC);
                btn_maintain.setClickable(true);
                btn_maintain.setBackgroundColor(0xFF0099CC);
                if(function == 1)
                {
                    btn_normal.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                }
                if(function ==3)
                {
                    btn_manual.setBackgroundColor(ContextCompat.getColor(mContext, R.color.red));
                }
                mMenu.findItem(R.id.action_settings).setEnabled(true);

                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.scanConfiguration, scanConf.getConfigName());

                tv_scan_conf.setText(activeConf.getConfigName());
                tv_scan_conf_manual.setText(activeConf.getConfigName());

                //NIRScanSDK.requestSpectrumCalCoefficients();
                //only download SpectrumCalCoefficients once
                if(downloadspecFlag ==false)
                {
                    NIRScanSDK.requestSpectrumCalCoefficients();
                    downloadspecFlag = true;
                }
                //-----------------------------------------------------
                if(SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Function is locked.").contains("Activated"))
                {
                    openFunction();
                }
                else
                {
                    closeFunction();
                }
            }

        }
    }
    private void saveReference()
    {
        Intent save_reference = new Intent(NIRScanSDK.ACTION_SAVE_REFERENCE);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(save_reference);
    }
    private void controlLampTime(int value)
    {
        Intent lampclose = new Intent(NIRScanSDK.ACTION_LAMP_TIME);
        lampclose.putExtra(NIRScanSDK.LAMP_TIME, value);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(lampclose);
    }
    private void controlLamp(int value)
    {
        Intent lampclose = new Intent(NIRScanSDK.ACTION_LAMP);
        lampclose.putExtra(NIRScanSDK.LAMP_ON_OFF, value);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(lampclose);
    }
    private void controlManul(int value)
    {
        Intent scan_mode = new Intent(NIRScanSDK.ACTION_SCAN_MODE);
        scan_mode.putExtra(NIRScanSDK.SCAN_MODE_ON_OFF, value);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(scan_mode);
    }
    private void controlPGA()
    {
        Intent setpga = new Intent(NIRScanSDK.ACTION_PGA);
        setpga.putExtra(NIRScanSDK.PGA_SET, Integer.parseInt(et_pga.getText().toString()));
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(setpga);
    }

    private void controlRepeat()
    {
        Intent setrepeat = new Intent(NIRScanSDK.ACTION_REPEAT);
        setrepeat.putExtra(NIRScanSDK.REPEAT_SET, Integer.parseInt(et_repead.getText().toString()));
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(setrepeat);
    }
    private Boolean checkValidRepeat()
    {
        int value = Integer.parseInt(et_repead.getText().toString());
        if(value>=1&&value<=100)
        {
            return true;
        }
        return false;
    }
    private Boolean checkValidPga()
    {
        int value = Integer.parseInt(et_pga.getText().toString());
        if(value==1 || value == 2 || value == 4 || value==8 || value==16 || value==32 ||value==64)
        {
            return true;
        }
        return false;
    }

    private void quickset(int index)
    {
        Intent quickset = new Intent(NIRScanSDK.ACTION_QUICK_SET);
        switch (index)
        {
            case NIRScanConfigType://may have five value for slew type
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x81;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;//payload length
                data[4] = (byte) scan_method_index;//0:column 1:hadamard
                //data[4] = (byte) 0x01;//0:column 1:hadamard
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case  NIRScanConfigWidth:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x85;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) scan_width_index;//2:2.34,3:3.54...
                //data[4] = (byte) 0x05;//2:2.34,3:3.54...
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigSet:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x8A;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) 0x01;
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigStart_nm:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF; //Command Start Flag
                data[1] = (byte) 0x83; //Command
                data[2] = (byte) 0x02; //Command Group
                data[3] = (byte) 0x02; //Payload length
                int start_nm_value = Integer.parseInt(et_spec_start.getText().toString());
                data[4] = (byte)(start_nm_value); //Payload
                data[5] = (byte)(start_nm_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigEnd_nm:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x84;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;//payload length
                int end_nm_value = Integer.parseInt(et_spec_end.getText().toString());
                data[4] = (byte)(end_nm_value);
                data[5] = (byte)(end_nm_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigNumPattern:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x86;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;//payload length
                int num_pattern_value = Integer.parseInt(et_res.getText().toString());
                data[4] = (byte) num_pattern_value;
                data[5] = (byte)(num_pattern_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigNumRepeats:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x87;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;
                int num_repeat_value = Integer.parseInt(et_aver_scan.getText().toString());
                data[4] = (byte) num_repeat_value;
                data[5] = (byte)(num_repeat_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigNumSections:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x8C;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) 0x01;
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigExposureTime:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x8D;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;
                data[4] = (byte) exposure_time_index;
                data[5] = (byte)(exposure_time_index>>8);
               // data[4] = (byte) 5;
               // data[5] = (byte)(5>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigSave:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;

                data[1] = (byte) 0x8B;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) 0x01;
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigName:
            {
                byte data[] = new byte[7];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x89;
                data[2] = (byte) 0x02;
                String isoString = "gty";
                byte[] midbytes=isoString.getBytes();
                data[3] = (byte) 0x03;
                data[4] = midbytes[0];
                data[5] = midbytes[1];
                data[6] = midbytes[2];
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigIndex:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x82;
                data[2] = (byte) 0x02;
                int len = ScanConfigList.size();
                int configindex = ScanConfigList.get(len-1).getScanConfigIndex()+1;
                data[3] = (byte) 0x02;
                data[4] = (byte) configindex;
                data[5] = (byte) (configindex>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigSerialNumber:
            {
                byte data[] = new byte[11];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x88;
                data[2] = (byte) 0x02;
                String SerialNumber = activeConf.getScanConfigSerialNumber();
                byte[] SerialNumberbytes=SerialNumber.getBytes();
                data[3] = SerialNumberbytes[0];
                data[4] = SerialNumberbytes[1];
                data[5] = SerialNumberbytes[2];
                data[6] = SerialNumberbytes[3];
                data[7] = SerialNumberbytes[4];
                data[8] = SerialNumberbytes[5];
                data[9] = SerialNumberbytes[6];
                data[10] = SerialNumberbytes[7];
            }
        }

    }

    private void ReferenceConfig(int index)
    {
        Intent quickset = new Intent(NIRScanSDK.ACTION_QUICK_SET);
        switch (index)
        {
            case NIRScanConfigType://may have five value for slew type
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x81;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;//payload length
                data[4] = (byte) 0x00;//0:column 1:hadamard
                //data[4] = (byte) 0x01;//0:column 1:hadamard
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case  NIRScanConfigWidth:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x85;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) 6;//2:2.34,3:3.54...
                //data[4] = (byte) 0x05;//2:2.34,3:3.54...
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigSet:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x8A;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) 0x01;
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigStart_nm:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF; //Command Start Flag
                data[1] = (byte) 0x83; //Command
                data[2] = (byte) 0x02; //Command Group
                data[3] = (byte) 0x02; //Payload length
                int start_nm_value = 900;
                data[4] = (byte)(start_nm_value); //Payload
                data[5] = (byte)(start_nm_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigEnd_nm:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x84;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;//payload length
                int end_nm_value = 1700;
                data[4] = (byte)(end_nm_value);
                data[5] = (byte)(end_nm_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigNumPattern:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x86;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;//payload length
                int num_pattern_value = 228;
                data[4] = (byte) num_pattern_value;
                data[5] = (byte)(num_pattern_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

            case NIRScanConfigNumRepeats:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x87;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;
                int num_repeat_value = 6;
                data[4] = (byte) num_repeat_value;
                data[5] = (byte)(num_repeat_value>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigNumSections:
            {
                byte data[] = new byte[5];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x8C;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x01;
                data[4] = (byte) 0x01;
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }
            case NIRScanConfigExposureTime:
            {
                byte data[] = new byte[6];
                data[0] = (byte) 0xFF;
                data[1] = (byte) 0x8D;
                data[2] = (byte) 0x02;
                data[3] = (byte) 0x02;
                data[4] = (byte) 0;
                data[5] = (byte)(0>>8);
                // data[4] = (byte) 5;
                // data[5] = (byte)(5>>8);
                quickset.putExtra(NIRScanSDK.QUICK_SET_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(quickset);
                break;
            }

        }

    }
    //Add get storedConfSize ---------------------------------------------
    private class  ScanConfSizeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            storedConfSize = intent.getIntExtra(NIRScanSDK.EXTRA_CONF_SIZE, 0);
        }
    }
    /**
     * Broadcast Receiver handling the disconnect event. If the Nano disconnects,
     * this activity should finish so that the user is taken back to the {@link ScanListActivity}
     * and display a toast message
     */
    public class DisconnReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(mContext, R.string.nano_disconnected, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    private void DisableLinearComponet(LinearLayout layout)
    {

        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            child.setEnabled(false);
        }
    }
    private void DisableAllComponent()
    {
        //normal------------------------------------------------
        LinearLayout layout = (LinearLayout) findViewById(R.id.ll_prefix);
        DisableLinearComponet(layout);
       // layout = (LinearLayout) findViewById(R.id.ll_os);
       // DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_normal_interval_time);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_normal_repeat);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous_stop);
        DisableLinearComponet(layout);
        //manual-----------------------------------------------------------------------
        layout = (LinearLayout) findViewById(R.id.ll_conf);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_prefix_manual);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_mode);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_lamp);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_pga);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_repeat);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_conf_manual);
        DisableLinearComponet(layout);
        //quick set ----------------------------------
        layout = (LinearLayout) findViewById(R.id.ll_prefix_quickset);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_method);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_spec_start);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_spec_end);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_width);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_res);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_aver_scan);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_ex_time);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_continus_scan_mode);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_interval_time);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_repeat_quick);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_set_value);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous_stop_quick);
        DisableLinearComponet(layout);
        //maintain------------------------------------------
        layout = (LinearLayout) findViewById(R.id.ly_reference);
        DisableLinearComponet(layout);
        //------------------------------------------
        btn_scan.setClickable(false);

        btn_normal.setClickable(false);
        btn_quickset.setClickable(false);
        btn_manual.setClickable(false);
        btn_maintain.setClickable(false);
    }

    private void Disable_Stop_Continous_button()
    {
        LinearLayout layout = (LinearLayout) findViewById(R.id.ll_continuous_stop);
        DisableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous_stop_quick);
        DisableLinearComponet(layout);
    }

    private void Enable_Stop_Continous_button()
    {
        LinearLayout layout = (LinearLayout) findViewById(R.id.ll_continuous_stop);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous_stop_quick);
        EnableLinearComponet(layout);
    }

    private void EnableLinearComponet(LinearLayout layout)
    {

        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            child.setEnabled(true);
        }
    }
    private void EnableAllComponent()
    {
        //normal------------------------------------------
        LinearLayout layout = (LinearLayout) findViewById(R.id.ll_prefix);
        EnableLinearComponet(layout);
       // layout = (LinearLayout) findViewById(R.id.ll_os);
       // EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_normal_interval_time);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_normal_repeat);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous_stop);
        EnableLinearComponet(layout);
        //manual-------------------------------------------------------------
        layout = (LinearLayout) findViewById(R.id.ll_conf);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_prefix_manual);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_mode);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_lamp);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_pga);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_repeat);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_conf_manual);
        EnableLinearComponet(layout);
        //quick set ----------------------------------
        layout = (LinearLayout) findViewById(R.id.ll_prefix_quickset);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_method);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_spec_start);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_spec_end);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_width);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_res);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_aver_scan);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_ex_time);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_continus_scan_mode);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_scan_interval_time);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_repeat_quick);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ly_set_value);
        EnableLinearComponet(layout);
        layout = (LinearLayout) findViewById(R.id.ll_continuous_stop_quick);
        EnableLinearComponet(layout);
        //maintain------------------------------------------
        layout = (LinearLayout) findViewById(R.id.ly_reference);
        EnableLinearComponet(layout);
        //------------------------------------------
        btn_scan.setClickable(true);
        btn_normal.setClickable(true);
        btn_quickset.setClickable(true);
        btn_manual.setClickable(true);
        btn_maintain.setClickable(true);
    }

     private Spinner.OnItemSelectedListener scanmethodlistener = new Spinner.OnItemSelectedListener()
     {

         @Override
         public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
             scan_method_index = i;
             GetMaxPattern();
         }

         @Override
         public void onNothingSelected(AdapterView<?> adapterView) {

         }
     };

    private Spinner.OnItemSelectedListener spin_time_listener = new Spinner.OnItemSelectedListener()
    {

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            exposure_time_index = i;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };

    private Spinner.OnItemSelectedListener scan_width_listener = new Spinner.OnItemSelectedListener()
    {

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            scan_width_index = i+2;
            GetMaxPattern();
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };
    private Button.OnClickListener set_value_quickset_listenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            if(checkQuicksetValue()==false)
            {

            }
            else
            {
                btn_set_value.setClickable(false);
                btn_scan.setClickable(false);
                calProgress.setVisibility(view.VISIBLE);
               /* quickset(NIRScanConfigNumRepeats);
                quickset(NIRScanConfigNumSections);
                quickset(NIRScanConfigType);
                quickset(NIRScanConfigWidth);
                quickset(NIRScanConfigStart_nm);
                quickset(NIRScanConfigEnd_nm);
                quickset(NIRScanConfigNumPattern);
                quickset(NIRScanConfigExposureTime);
                quickset(NIRScanConfigSet);
                Dialog_Pane_Delay("Finish","Complete set config.");*/
                SetScanConfiguration();
            }

        }
    };

    private EditText.OnEditorActionListener set_quickset_lamp_time_listener = new EditText.OnEditorActionListener()
    {

        @Override
        public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
               if(Integer.parseInt(et_prefix_lamp_quickset.getText().toString())!=625)
               {
                   controlLampTime(Integer.parseInt(et_prefix_lamp_quickset.getText().toString()));
               }
                return false; // consume.
            }
            return false;
        }
    };

    private Boolean checkQuicksetValue()
    {
        if(Integer.parseInt(et_spec_start.getText().toString())<900 || Integer.parseInt(et_spec_start.getText().toString())>1700)
        {
            Dialog_Pane("Error","Spectral Start (nm) range is 900~1700.");
            return false;
        }
        if(Integer.parseInt(et_spec_end.getText().toString())<900 || Integer.parseInt(et_spec_end.getText().toString())>1700)
        {
            Dialog_Pane("Error","Spectral End (nm) range is 900~1700.");
            return false;
        }
        if(Integer.parseInt(et_spec_end.getText().toString())<= Integer.parseInt(et_spec_start.getText().toString()))
        {
            Dialog_Pane("Error","Spectral End (nm) should larger than  Spectral Start (nm).");
            return false;
        }
        if(Integer.parseInt(et_aver_scan.getText().toString())>65535)
        {
            Dialog_Pane("Error","Average Scans (times) range is 0~65535.");
            return false;
        }
        if(Integer.parseInt(et_res.getText().toString())>MaxPattern || Integer.parseInt(et_res.getText().toString())<2)
        {
            Dialog_Pane("Error","D-Res. range is 2~" + MaxPattern + ".");
            return false;
        }
        return true;
    }

    private void Dialog_Pane_Delay(String title,String content)
    {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setMessage(content);

        alertDialogBuilder.setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {


                alertDialog.dismiss();
                btn_set_value.setClickable(true);
                btn_scan.setClickable(true);

                try {
                    Thread.sleep(1000);
                }catch (Exception e)
                {

                }
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
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
                btn_set_value.setClickable(true);
            }
        });

        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void Dialog_Pane_Bottom(String title,String content)
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
        alertDialog.getWindow().setGravity(Gravity.BOTTOM);
        alertDialog.show();
    }

    private Button.OnClickListener stop_continous_listenser = new Button.OnClickListener()
    {

        @Override
        public void onClick(View view) {
            stop_continuous = true;
        }
    };

    private void GetMaxPattern()
    {
        int start_nm = Integer.parseInt(et_spec_start.getText().toString());
        int end_nm =  Integer.parseInt(et_spec_end.getText().toString());
        int width_index = scan_width_index;
        int num_repeat = Integer.parseInt(et_aver_scan.getText().toString());
        int scan_type = scan_method_index;
        MaxPattern =  GetMaxPatternJNI(scan_type,start_nm,end_nm,width_index,num_repeat,SpectrumCalCoefficients);
        String text = "D-Res. (pts, max:" + MaxPattern +")";
        tv_res.setText(text);
    }

    private EditText.OnEditorActionListener quick_spec_start = new EditText.OnEditorActionListener()
    {

        @Override
        public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if(et_spec_start.getText().toString().matches("")|| Integer.parseInt(et_spec_start.getText().toString())> Integer.parseInt(et_spec_end.getText().toString()) || Integer.parseInt(et_spec_start.getText().toString())<900)
                {
                    et_spec_start.setText(Integer.toString(init_start_nm));
                    Dialog_Pane("Error","Start wavelength should be between 900nm and end wavelength!");
                    return false; // consume.

                }
            }
            init_start_nm = Integer.parseInt(et_spec_start.getText().toString());
            GetMaxPattern();
            return false;
        }
    };
    //spec end listener-------------------------------------------------------------------------------------------------------------
    private EditText.OnEditorActionListener quick_spec_end = new EditText.OnEditorActionListener()
    {

        @Override
        public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if(et_spec_end.getText().toString().matches("")|| Integer.parseInt(et_spec_end.getText().toString())< Integer.parseInt(et_spec_start.getText().toString()) || Integer.parseInt(et_spec_end.getText().toString())>1700)
                {
                    et_spec_end.setText(Integer.toString(init_end_nm));
                    Dialog_Pane("Error","End wavelength should be between start wavelength and 1700nm!");
                    return false; // consume.

                }
            }
            init_end_nm = Integer.parseInt(et_spec_end.getText().toString());
            GetMaxPattern();
            return false;
        }
    };

    private EditText.OnEditorActionListener quick_res_listener = new EditText.OnEditorActionListener()
    {

        @Override
        public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                if(et_res.getText().toString().matches("")|| Integer.parseInt(et_res.getText().toString())< 2 || Integer.parseInt(et_res.getText().toString())>MaxPattern)
                {
                    et_res.setText(Integer.toString(init_res));
                    Dialog_Pane("Error","D-Res. range is 2~" + MaxPattern + ".");
                    return false; // consume.

                }
            }
            init_res = Integer.parseInt(et_res.getText().toString());
            return false;
        }
    };

    public static NIRScanSDK.ScanConfiguration GetScanConfiguration(byte EXTRA_DATA[])
    {
        int scanType=0;
        int scanConfigIndex=0;
        byte[] scanConfigSerialNumber = new byte[8];
        byte[] configName = new byte[40];
        byte numSections=0;
        byte[] sectionScanType=new byte[5];
        byte[] sectionWidthPx=new byte[5];
        int[] sectionWavelengthStartNm = new int[5];
        int[] sectionWavelengthEndNm = new int[5];
        int[] sectionNumPatterns = new int[5];
        int[] sectionNumRepeats = new int[5];
        int[] sectionExposureTime = new int[5];
        //-------------------------------------------------
        int []bufscanType = new int[1];
        int []bufscanConfigIndex= new int[1];
        byte[]bufnumSections = new byte[1];

        dlpSpecScanReadConfiguration(EXTRA_DATA,bufscanType,bufscanConfigIndex,scanConfigSerialNumber,configName,bufnumSections,
                sectionScanType,sectionWidthPx,sectionWavelengthStartNm,sectionWavelengthEndNm,sectionNumPatterns,sectionNumRepeats,sectionExposureTime);
        scanConfigIndex = bufscanConfigIndex[0];
        scanType = bufscanType[0];
        numSections = bufnumSections[0];
        NIRScanSDK.ScanConfiguration config = new  NIRScanSDK.ScanConfiguration(scanType,scanConfigIndex,scanConfigSerialNumber,configName,numSections,
                sectionScanType,sectionWidthPx,sectionWavelengthStartNm,sectionWavelengthEndNm,sectionNumPatterns,sectionNumRepeats,sectionExposureTime);
        return config;
    }
    public NIRScanSDK.ScanConfiguration GetOneSectionScanConfiguration(byte EXTRA_DATA[])
    {
        int scanType=0;
        byte[] ScanType=new byte[1];
        byte[] WidthPx=new byte[1];
        int[] WavelengthStartNm = new int[1];
        int[] WavelengthEndNm = new int[1];
        int[] NumPatterns = new int[1];
        int[] NumRepeats = new int[1];
        int[] ExposureTime = new int[1];
        int scanConfigIndex=0;
        int []bufscanConfigIndex= new int[1];
        byte[] scanConfigSerialNumber = new byte[8];
        byte[] configName = new byte[40];
        //-------------------------------------------------
        int []bufscanType = new int[1];

        dlpSpecScanReadOneSectionConfiguration(EXTRA_DATA,bufscanType,
                ScanType,WidthPx,WavelengthStartNm,WavelengthEndNm,NumPatterns,NumRepeats,ExposureTime,bufscanConfigIndex,scanConfigSerialNumber,configName);
        scanType = bufscanType[0];
        scanConfigIndex = bufscanConfigIndex[0];
        NIRScanSDK.ScanConfiguration config = new  NIRScanSDK.ScanConfiguration(scanType, scanConfigIndex,  scanConfigSerialNumber, configName, WavelengthStartNm[0],WavelengthEndNm[0],WidthPx[0], NumPatterns[0], NumRepeats[0]);
        return config;

    }

    private void readActivateState()
    {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mInfoReceiver);
        Intent ReadActivateState = new Intent(NIRScanSDK.ACTION_READ_ACTIVATE_STATE);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(ReadActivateState);
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class RetrunReadActivateStatusReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            byte state[] = intent.getByteArrayExtra(NIRScanSDK.RETURN_READ_ACTIVATE_STATE);
            if(state[0] == 1)
            {
                Dialog_Pane("Device Activated","Device advanced functions are all unlocked.");
                Licensestatusfalg = true;
                mMenu.findItem(R.id.action_settings).setEnabled(true);
                mMenu.findItem(R.id.action_key).setEnabled(true);
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Activated.");
                openFunction();
            }
            else
            {
                 String licensekey = SettingsManager.getStringPref(mContext, SettingsManager.SharedPreferencesKeys.licensekey, null);
                if(licensekey!=null)
                {
                    calProgress.setVisibility(View.VISIBLE);
                    setActivateStateKey(licensekey);
                }
                else
                {
                    Dialog_Pane("Unlock device","Some functions are locked.");
                    mMenu.findItem(R.id.action_settings).setEnabled(true);
                    mMenu.findItem(R.id.action_key).setEnabled(true);
                    SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Function is locked.");
                    closeFunction();
                }
            }
        }
    }
    //set key--------------------------------------------------------------------------------------------
    private void setActivateStateKey(String key)
    {

        Intent ActivateStateKeyset = new Intent(NIRScanSDK.ACTION_ACTIVATE_STATE);
        String filterdata = filterDate(key);
        byte data[] = hexToBytes(filterdata);
        ActivateStateKeyset.putExtra(NIRScanSDK.ACTIVATE_STATE_KEY, data);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(ActivateStateKeyset);
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
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class RetrunActivateStatusReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            mMenu.findItem(R.id.action_settings).setEnabled(true);
            mMenu.findItem(R.id.action_key).setEnabled(true);
            calProgress.setVisibility(View.GONE);
            byte state[] = intent.getByteArrayExtra(NIRScanSDK.RETURN_ACTIVATE_STATUS);

            if(state[0] == 1)
            {
                Dialog_Pane("Device Activated","Device advanced functions are all unlocked.");
                Licensestatusfalg = true;
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Activated.");
                openFunction();
            }
            else
            {
                Dialog_Pane("Unlock device","Some functions are locked.");
                SettingsManager.storeStringPref(mContext, SettingsManager.SharedPreferencesKeys.Activacatestatus, "Function is locked.");
                closeFunction();
            }
        }
    }
    //--------------------------------------------------------------------------------------------------
    private void ReadCurrentScanConfig()
    {

        Intent ReadConfig = new Intent(NIRScanSDK.ACTION_READ_CONFIG);
        byte data[] = new byte[1];
        data[0] = (byte) 0x01;
        ReadConfig.putExtra(NIRScanSDK.READ_CONFIG_DATA, data);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(ReadConfig);
    }
    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class ReturnCurrentScanConfigurationDataReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            byte currentconfig[ ] = intent.getByteArrayExtra(NIRScanSDK.EXTRA_CURRENT_CONFIG_DATA);
            Boolean flag = Compareconfig(intent.getByteArrayExtra(NIRScanSDK.EXTRA_CURRENT_CONFIG_DATA));
            calProgress.setVisibility(View.GONE);
            if(flag)
            {
                Dialog_Pane("Success","Complete to set configuration.");
            }
            else
            {
                Dialog_Pane("Fail","Set configuration fail.");
            }
        }
    }
    public Boolean Compareconfig(byte EXTRA_DATA[])
    {
        if(EXTRA_DATA.length!=155)
        {
            return false;
        }
        int scanType=0;
        int scanConfigIndex=0;
        byte[] scanConfigSerialNumber = new byte[8];
        byte[] configName = new byte[40];
        byte numSections=0;
        byte[] sectionScanType=new byte[5];
        byte[] sectionWidthPx=new byte[5];
        int[] sectionWavelengthStartNm = new int[5];
        int[] sectionWavelengthEndNm = new int[5];
        int[] sectionNumPatterns = new int[5];
        int[] sectionNumRepeats = new int[5];
        int[] sectionExposureTime = new int[5];
        //-------------------------------------------------
        int []bufscanType = new int[1];
        int []bufscanConfigIndex= new int[1];
        byte[]bufnumSections = new byte[1];

        dlpSpecScanReadConfiguration(EXTRA_DATA,bufscanType,bufscanConfigIndex,scanConfigSerialNumber,configName,bufnumSections,
                sectionScanType,sectionWidthPx,sectionWavelengthStartNm,sectionWavelengthEndNm,sectionNumPatterns,sectionNumRepeats,sectionExposureTime);

        scanType = bufscanType[0];
        numSections = bufnumSections[0];
        NIRScanSDK.ScanConfiguration config = new  NIRScanSDK.ScanConfiguration(scanType,scanConfigIndex,scanConfigSerialNumber,configName,numSections,
                sectionScanType,sectionWidthPx,sectionWavelengthStartNm,sectionWavelengthEndNm,sectionNumPatterns,sectionNumRepeats,sectionExposureTime);

        if(config.getSectionScanType()[0]!= spin_scan_method.getSelectedItemPosition())
        {
            return false;
        }
        if(Integer.parseInt(et_spec_start.getText().toString())!=config.getSectionWavelengthStartNm()[0])
        {
            return false;
        }
        if(Integer.parseInt(et_spec_end.getText().toString())!=config.getSectionWavelengthEndNm()[0])
        {
            return false;
        }
        if(config.getSectionWidthPx()[0]!= spin_scan_width.getSelectedItemPosition()+2)
        {
            return false;
        }
        if(Integer.parseInt(et_res.getText().toString())!=config.getSectionNumPatterns()[0])
        {
            return false;
        }
        if(Integer.parseInt(et_aver_scan.getText().toString())!= config.getSectionNumRepeats()[0])
        {
            return false;
        }
        if(spin_time.getSelectedItemPosition() != config.getSectionExposureTime()[0])
        {
            return false;
        }

        return true;
    }

    public void SetScanConfiguration()
    {
        int scanType=0;
        int scanConfigIndex=0;
        int numRepeat=0;
        byte[] scanConfigSerialNumber = new byte[8];
        byte[] configName = new byte[40];
        byte numSections=0;
        byte[] sectionScanType=new byte[5];
        byte[] sectionWidthPx=new byte[5];
        int[] sectionWavelengthStartNm = new int[5];
        int[] sectionWavelengthEndNm = new int[5];
        int[] sectionNumPatterns = new int[5];
        int[] sectionExposureTime = new int[5];
        byte[] EXTRA_DATA = new byte[155];
        //transfer config name to byte
        String isoString ="QuickSet";
        int name_size = isoString.length();
        byte[] ConfigNamebytes=isoString.getBytes();
        for(int i=0;i<name_size;i++)
        {
            configName[i] = ConfigNamebytes[i];
        }
        scanType = 2;
        //transfer SerialNumber to byte
        String SerialNumber = "12345678";
        byte[] SerialNumberbytes=SerialNumber.getBytes();
        int SerialNumber_size = SerialNumber.length();
        for(int i=0;i<SerialNumber_size;i++)
        {
            scanConfigSerialNumber[i] = SerialNumberbytes[i];
        }
        scanConfigIndex = 255;
        numSections =(byte) 1;
        numRepeat = Integer.parseInt(et_aver_scan.getText().toString());

        for(int i=0;i<numSections;i++)
        {
            sectionScanType[i] = (byte)spin_scan_method.getSelectedItemPosition();
        }
        for(int i=0;i<numSections;i++)
        {
            sectionWavelengthStartNm[i] =Integer.parseInt(et_spec_start.getText().toString());
        }
        for(int i=0;i<numSections;i++)
        {
            sectionWavelengthEndNm[i] =Integer.parseInt(et_spec_end.getText().toString());
        }
        for(int i=0;i<numSections;i++)
        {
            sectionNumPatterns[i] =Integer.parseInt(et_res.getText().toString());
        }
        for(int i=0;i<numSections;i++)
        {
            sectionWidthPx[i] = (byte)(spin_scan_width.getSelectedItemPosition()+2);
        }
        for(int i=0;i<numSections;i++)
        {
            sectionExposureTime[i] =spin_time.getSelectedItemPosition();
        }
        dlpSpecScanWriteConfiguration(scanType,scanConfigIndex,numRepeat,scanConfigSerialNumber,configName,numSections,
                sectionScanType, sectionWidthPx, sectionWavelengthStartNm, sectionWavelengthEndNm, sectionNumPatterns,
                sectionExposureTime,EXTRA_DATA);
        SetConfig(EXTRA_DATA);
    }

    private void SetConfig(byte[]EXTRA_DATA)
    {
        //package type - 12: CMD, 34: Data.
        // If the package type is "CMD", the 2nd byte is the "Set Config to Memory" flag, the 3rd byte is the "Save Config to EEPROM" flag, 4th byte is the total data size.
        // If the package type is "Data", the 2nd byte indicates the bytes remaining to send and data payload starts from 3rd byte.

        // Prepare and send command type package
        //CMD
        byte cmd_data[] = new byte[4];
        int CMD_TYPE = 12;

        cmd_data[0] = (byte) CMD_TYPE; //cmd_type
        cmd_data[1] = (byte) 1; //set
        cmd_data[2] = (byte) 0;//save
        cmd_data[3] = (byte) 155;//data size

        Intent writescanconfig = new Intent(NIRScanSDK.ACTION_WRITE_SCAN_CONFIG);
        writescanconfig.putExtra(NIRScanSDK.WRITE_SCAN_CONFIG_VALUE, cmd_data);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(writescanconfig);

        //data 瘥活憛?byte header 50byte data--------------------------------------------------
        int totalBytes = 155;
        int chunkSize = 18;
        int DATA_TYPE = 34;
        int byteToSend = totalBytes;
        int HEADER_SIZE = 2;
        for (int i=0; i < (totalBytes/chunkSize + 1); i++)
        {
            byte data[] = new byte[20];
            data[0] = (byte)DATA_TYPE;
            data[1] = (byte)byteToSend;

            if(byteToSend>=chunkSize)
            {
                for(int j=0;j<chunkSize;j++)
                {
                    data[HEADER_SIZE+j] = EXTRA_DATA[i*chunkSize + j];
                }
                Intent writescanconfig_data = new Intent(NIRScanSDK.ACTION_WRITE_SCAN_CONFIG);
                writescanconfig_data.putExtra(NIRScanSDK.WRITE_SCAN_CONFIG_VALUE, data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(writescanconfig_data);
            }
            else
            {
                byte last_data[] = new byte[totalBytes%chunkSize + HEADER_SIZE ];
                last_data[0] = (byte)DATA_TYPE;
                last_data[1] = (byte)(totalBytes%chunkSize);
                for(int j=0;j<(totalBytes%chunkSize);j++)
                {
                    last_data[HEADER_SIZE+j] = EXTRA_DATA[ totalBytes - (totalBytes%chunkSize) +j];
                }
                Intent writescanconfig_data = new Intent(NIRScanSDK.ACTION_WRITE_SCAN_CONFIG);
                writescanconfig_data.putExtra(NIRScanSDK.WRITE_SCAN_CONFIG_VALUE, last_data);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(writescanconfig_data);
            }
            byteToSend-=chunkSize;

        }

    }

    /**
     * Custom receiver for returning the event that reference calibrations have been read
     */
    public class WriteScanConfigStatusReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            calProgress.setVisibility(View.GONE);
            byte status[] = intent.getByteArrayExtra(NIRScanSDK.RETURN_WRITE_SCAN_CONFIG_STATUS);
            btn_scan.setClickable(true);
            if((int)status[0] == 1)
            {
                if((int)status[2] == -1 && (int)status[3]==-1)
                {
                    Dialog_Pane("Fail","Set configuration fail!");
                }
                else
                {
                    ReadCurrentScanConfig();
                }
            }
            else if((int)status[0] == -1)
            {
                Dialog_Pane("Fail","Set configuration fail!");
            }
            else if((int)status[0] == -2)
            {
                Dialog_Pane("Fail","Set configuration fail! Hardware not compatible!");
            }
            else if((int)status[0] == -3)
            {
                Dialog_Pane("Fail","Set configuration fail! Function is currently locked!" );
            }
        }
    }
    private void openFunction()
    {
        btn_quickset.setClickable(true);
        btn_manual.setClickable(true);
        btn_maintain.setClickable(true);
        btn_manual.setBackgroundColor(0xFF0099CC);
        btn_quickset.setBackgroundColor(0xFF0099CC);
        btn_maintain.setBackgroundColor(0xFF0099CC);
    }
    private void closeFunction()
    {
        btn_quickset.setClickable(false);
        btn_manual.setClickable(false);
        btn_maintain.setClickable(false);
        btn_manual.setBackgroundColor(ContextCompat.getColor(mContext, R.color.gray));
        btn_quickset.setBackgroundColor(ContextCompat.getColor(mContext, R.color.gray));
        btn_maintain.setBackgroundColor(ContextCompat.getColor(mContext, R.color.gray));
    }
}
