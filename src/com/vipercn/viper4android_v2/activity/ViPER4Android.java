package com.vipercn.viper4android_v2.activity;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.vipercn.viper4android_v2.R;
import com.vipercn.viper4android_v2.service.ViPER4AndroidService;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public final class ViPER4Android extends FragmentActivity {

    private boolean checkFirstRun() {
        PackageManager packageMgr = getPackageManager();
        PackageInfo packageInfo;
        String mVersion = "";
        try {
            packageInfo = packageMgr.getPackageInfo(getPackageName(), 0);
            mVersion = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            return false;
        }

        SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", 0);
        String mLastVersion = prefSettings.getString("viper4android.settings.lastversion", "");
        if (mLastVersion == null) return true;
        if (mLastVersion.equals("")) return true;
        if (mLastVersion.equalsIgnoreCase(mVersion)) return false;
        return true;
    }

    private void setFirstRun() {
        PackageManager packageMgr = getPackageManager();
        PackageInfo packageInfo = null;
        String mVersion = "";
        try {
            packageInfo = packageMgr.getPackageInfo(getPackageName(), 0);
            mVersion = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            return;
        }

        SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", 0);
        Editor editSettings = prefSettings.edit();
        if (editSettings != null) {
            editSettings.putString("viper4android.settings.lastversion", mVersion);
            editSettings.commit();
        }
    }

    private boolean checkSoftwareActive() {
        SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", 0);
        boolean mActived = prefSettings.getBoolean("viper4android.settings.onlineactive", false);
        return !mActived;
    }

    private void setSoftwareActive() {
        SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", 0);
        Editor editSettings = prefSettings.edit();
        if (editSettings != null) {
            editSettings.putBoolean("viper4android.settings.onlineactive", true);
            editSettings.commit();
        }
    }

    private boolean submitInformation() {
        String mCode = "";
        byte[] mHexTab = (new String("0123456789abcdef")).getBytes();
        Random rndMachine = new Random(System.currentTimeMillis());
        for (int i = 0; i < 8; i++) {
            byte btCode = (byte) rndMachine.nextInt(256);
            if (btCode < 0) {
                short shortData = (short) (256 + btCode);
                mCode = mCode + String.format("%c%c", mHexTab[shortData >> 4], mHexTab[shortData & 0x0F]);
            } else
                mCode = mCode + String.format("%c%c", mHexTab[btCode >> 4], mHexTab[btCode & 0x0F]);
        }
        mCode = mCode + "-";
        for (int i = 0; i < 4; i++) {
            byte btCode = (byte) rndMachine.nextInt(256);
            if (btCode < 0) {
                short shortData = (short) (256 + btCode);
                mCode = mCode + String.format("%c%c", mHexTab[shortData >> 4], mHexTab[shortData & 0x0F]);
            } else
                mCode = mCode + String.format("%c%c", mHexTab[btCode >> 4], mHexTab[btCode & 0x0F]);
        }
        mCode = mCode + "-" + Build.VERSION.SDK_INT;

        String mURL = "http://vipersaudio.com/stat/v4a_stat.php?code=" + mCode + "&ver=viper4android-fx";
        Log.i("ViPER4Android", "Submit code = \"" + mURL + "\"");

        try {
            HttpGet httpRequest = new HttpGet(mURL);
            HttpClient httpClient = new DefaultHttpClient();
            HttpResponse httpResponse = httpClient.execute(httpRequest);
            if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                return true;
            return false;
        } catch (Exception e) {
            Log.i("ViPER4Android", "Submit failed, error = " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean processDriverCheck() {
        Log.i("ViPER4Android", "Enter processDriverCheck()");

        if (mAudioServiceInstance != null) {
            try {
                if (!mAudioServiceInstance.GetServicePrepared()) {
                    Log.i("ViPER4Android", "Service not prepared");
                    return false;
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                Log.i("ViPER4Android", "Service instance is broken");
                return false;
            }

            // Check driver version
            PackageManager packageMgr = getPackageManager();
            PackageInfo packageInfo = null;
            String mApkVer = "";
            try {
                packageInfo = packageMgr.getPackageInfo(getPackageName(), 0);
                mApkVer = packageInfo.versionName;
                String mDrvVer = mAudioServiceInstance.GetDriverVersion();

                Log.i("ViPER4Android", "Proceeding drvier check");
                if (!mApkVer.equalsIgnoreCase(mDrvVer)) {
                    Message message = new Message();
                    message.what = 0xA00A;
                    message.obj = this;
                    hProceedDriverHandler.sendMessage(message);
                }
                return true;
            } catch (NameNotFoundException e) {
                Log.i("ViPER4Android", "Can not get application version, error = " + e.getMessage());
                return false;
            } catch (Exception e) {
                Log.i("ViPER4Android", "Driver check error = " + e.getMessage());
                return false;
            }
        } else {
            Log.i("ViPER4Android", "Service instance is null");
            return false;
        }
    }

    public static String determineCPUWithDriver() {
        String mDriverFile = "libv4a_fx_";

        if (Build.VERSION.SDK_INT >= 18)
            mDriverFile = mDriverFile + "jb_";
        else mDriverFile = mDriverFile + "ics_";

        Utils.CpuInfo mCPUInfo = new Utils.CpuInfo();
        if (mCPUInfo.HasNEON()) mDriverFile = mDriverFile + "NEON";
        else if (mCPUInfo.HasVFP()) mDriverFile = mDriverFile + "VFP";
        else mDriverFile = mDriverFile + "NOVFP";

        mDriverFile = mDriverFile + ".so";
        Log.i("ViPER4Android", "Driver selection = " + mDriverFile);

        return mDriverFile;
    }

    public static String readTextFile(InputStream inputStream) {
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            return "";
        }
        BufferedReader reader = new BufferedReader(inputStreamReader);
        StringBuilder sb = new StringBuilder("");
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            return "";
        }
        return sb.toString();
    }

    public static final String SHARED_PREFERENCES_BASENAME = "com.vipercn.viper4android_v2";
    public static final String ACTION_UPDATE_PREFERENCES = "com.vipercn.viper4android_v2.UPDATE";
    public static final String ACTION_SHOW_NOTIFY = "com.vipercn.viper4android_v2.SHOWNOTIFY";
    public static final String ACTION_CANCEL_NOTIFY = "com.vipercn.viper4android_v2.CANCELNOTIFY";
    public static final int NOTIFY_FOREGROUND_ID = 1;

    protected MyAdapter pagerAdapter;
    protected ActionBar actionBar;
    protected ViewPager viewPager;

    private ArrayList<String> mProfileList = new ArrayList<String>();
    private boolean mKillAllThread = false;
    private Context mActivityContext = this;
    private ViPER4AndroidService mAudioServiceInstance = null;

    // Driver install handler
    private static Handler hProceedDriverHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                if (msg.what == 0xA00A) {
                    if (msg.obj == null) {
                        super.handleMessage(msg);
                        return;
                    }
                    final Context ctxInstance = (Context) msg.obj;
                    AlertDialog.Builder mUpdateDrv = new AlertDialog.Builder(ctxInstance);
                    mUpdateDrv.setTitle("ViPER4Android");
                    mUpdateDrv.setMessage(ctxInstance.getResources().getString(R.string.text_drvvernotmatch));
                    mUpdateDrv.setPositiveButton(ctxInstance.getResources().getString(R.string.text_yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Install/Update driver
                            String szDriverFileName = determineCPUWithDriver();
                            if (Utils.installDrv_FX(ctxInstance, szDriverFileName)) {
                                AlertDialog.Builder mResult = new AlertDialog.Builder(ctxInstance);
                                mResult.setTitle("ViPER4Android");
                                mResult.setMessage(ctxInstance.getResources().getString(R.string.text_drvinst_ok));
                                mResult.setNegativeButton(ctxInstance.getResources().getString(R.string.text_ok), null);
                                mResult.show();
                            } else {
                                AlertDialog.Builder mResult = new AlertDialog.Builder(ctxInstance);
                                mResult.setTitle("ViPER4Android");
                                mResult.setMessage(ctxInstance.getResources().getString(R.string.text_drvinst_failed));
                                mResult.setNegativeButton(ctxInstance.getResources().getString(R.string.text_ok), null);
                                mResult.show();
                            }
                        }
                    });
                    mUpdateDrv.setNegativeButton(ctxInstance.getResources().getString(R.string.text_no), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            return;
                        }
                    });
                    mUpdateDrv.show();
                }
                super.handleMessage(msg);
            } catch (Exception e) {
                super.handleMessage(msg);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Welcome window
        if (checkFirstRun()) {
            // TODO: Welcome window
            // Maybe leave this until v2.3.0.2
        }

        // Start background service
        mKillAllThread = false;
        Log.i("ViPER4Android", "Starting service, reason = ViPER4Android::onCreate");
        Intent serviceIntent = new Intent(this, ViPER4AndroidService.class);
        startService(serviceIntent);

        // Setup ui
        setContentView(R.layout.top);
        pagerAdapter = new MyAdapter(getFragmentManager(), this);
        actionBar = getActionBar();
        viewPager = (ViewPager) findViewById(R.id.viewPager);
        pagerTabStrip = (PagerTabStrip) findViewById(R.id.pagerTabStrip);

        // Setup action bar
        actionBar.setDisplayShowTitleEnabled(true);

        // Setup effect setting page
        viewPager.setAdapter(pagerAdapter);
        pagerTabStrip.setDrawFullUnderline(true);
        pagerTabStrip.setTabIndicatorColor(getResources().getColor(android.R.color.holo_blue_light));

        // Show changelog
        if (checkFirstRun()) {
            String mLocale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry();
            String mChangelog_AssetsName = "Changelog_";
            if (mLocale.equalsIgnoreCase("zh_CN"))
                mChangelog_AssetsName = mChangelog_AssetsName + "zh_CN";
            else if (mLocale.equalsIgnoreCase("zh_TW"))
                mChangelog_AssetsName = mChangelog_AssetsName + "zh_TW";
            else mChangelog_AssetsName = mChangelog_AssetsName + "en_US";
            mChangelog_AssetsName = mChangelog_AssetsName + ".txt";

            String mChangeLog = "";
            InputStream isHandle = null;
            try {
                isHandle = getAssets().open(mChangelog_AssetsName);
                mChangeLog = readTextFile(isHandle);
                isHandle.close();
            } catch (Exception e) {
            }
            setFirstRun();
            if (!mChangeLog.equalsIgnoreCase("")) {
                AlertDialog.Builder mChglog = new AlertDialog.Builder(this);
                mChglog.setTitle(R.string.text_changelog);
                mChglog.setMessage(mChangeLog);
                mChglog.setNegativeButton(getResources().getString(R.string.text_ok), null);
                mChglog.show();
            }
        }

        // Start active thread
        Thread activeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (checkSoftwareActive()) {
                    if (submitInformation())
                        setSoftwareActive();
                }
            }
        });
        activeThread.start();

        // Start driver check thread
        Thread driverCheckThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i("ViPER4Android", "Begin driver check ...");
                while (!mKillAllThread) {
                    if (processDriverCheck()) break;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
                Log.i("ViPER4Android", "Driver check finished");
            }
        });
        driverCheckThread.start();
    }

    @Override
    public void onDestroy() {
        Log.i("ViPER4Android", "Main activity onDestroy()");
        mKillAllThread = true;
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.i("ViPER4Android", "Main activity onResume()");
        mKillAllThread = false;

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                ViPER4AndroidService service = ((ViPER4AndroidService.LocalBinder) binder).getService();
                mAudioServiceInstance = service;

                String routing = service.getAudioOutputRouting();
                String[] entries = pagerAdapter.getEntries();
                for (int i = 0; i < entries.length; i++) {
                    if (routing.equals(entries[i])) {
                        viewPager.setCurrentItem(i);
                        break;
                    }
                }
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i("ViPER4Android", "ViPER4Android service disconnected.");
            }
        };

        Log.i("ViPER4Android", "Binding service, reason = ViPER4Android::onResume");
        Intent serviceIntent = new Intent(this, ViPER4AndroidService.class);
        bindService(serviceIntent, connection, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i("ViPER4Android", "Enter onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        Log.i("ViPER4Android", "Exit onCreateOptionsMenu()");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences preferences = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);
        boolean mEnableNotify = preferences.getBoolean("viper4android.settings.show_notify_icon", false);
        String mDriverMode = preferences.getString("viper4android.settings.compatiblemode", "global");

    	/* Just for debug */
        String mLockedEffect = preferences.getString("viper4android.settings.lock_effect", "none");
        Log.i("ViPER4Android", "lock_effect = " + mLockedEffect);
        /******************/

        // Notification icon menu
        if (mEnableNotify) {
            MenuItem mNotify = menu.findItem(R.id.notify);
            String mNotifyTitle = getResources().getString(R.string.text_hidetrayicon);
            mNotify.setTitle(mNotifyTitle);
        } else {
            MenuItem mNotify = menu.findItem(R.id.notify);
            String mNotifyTitle = getResources().getString(R.string.text_showtrayicon);
            mNotify.setTitle(mNotifyTitle);
        }

        // Driver mode menu
        boolean mDriverInGlobalMode = true;
        if (!mDriverMode.equalsIgnoreCase("global"))
            mDriverInGlobalMode = false;
        if (!mDriverInGlobalMode) {
            /* If the driver is in compatible mode, driver status is invalid */
            MenuItem mDrvStatus = menu.findItem(R.id.drvstatus);
            mDrvStatus.setEnabled(false);
        } else {
            MenuItem mDrvStatus = menu.findItem(R.id.drvstatus);
            mDrvStatus.setEnabled(true);
        }

        // Driver install/uninstall menu
        if (mAudioServiceInstance == null) {
            MenuItem drvInstItem = menu.findItem(R.id.drvinst);
            String menuTitle = getResources().getString(R.string.text_install);
            drvInstItem.setTitle(menuTitle);
        } else {
            boolean bDriverIsReady = mAudioServiceInstance.GetDriverIsReady();
            if (bDriverIsReady) {
                MenuItem drvInstItem = menu.findItem(R.id.drvinst);
                String menuTitle = getResources().getString(R.string.text_uninstall);
                drvInstItem.setTitle(menuTitle);
            } else {
                MenuItem drvInstItem = menu.findItem(R.id.drvinst);
                String menuTitle = getResources().getString(R.string.text_install);
                drvInstItem.setTitle(menuTitle);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    // For convenient parameter passing, we use global variable
    private String szSaveProfileNameGlobal = "";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences prefSettings = getSharedPreferences(
                ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);

        int choice = item.getItemId();
        switch (choice) {
            case R.id.about: {
                PackageManager packageMgr = getPackageManager();
                PackageInfo packageInfo = null;
                String mVersion = "";
                try {
                    packageInfo = packageMgr.getPackageInfo(getPackageName(), 0);
                    mVersion = packageInfo.versionName;
                } catch (NameNotFoundException e) {
                    mVersion = "N/A";
                }
                String mAbout = getResources().getString(R.string.about_text);
                mAbout = String.format(mAbout, mVersion) + "\n";
                mAbout = mAbout + getResources().getString(R.string.text_help_content);

                AlertDialog.Builder mHelp = new AlertDialog.Builder(this);
                mHelp.setTitle(getResources().getString(R.string.about_title));
                mHelp.setMessage(mAbout);
                mHelp.setPositiveButton(getResources().getString(R.string.text_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        return;
                    }
                });
                mHelp.setNegativeButton(getResources().getString(R.string.text_view_forum), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Uri uri = Uri.parse(getResources().getString(R.string.text_forum_link));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        ViPER4Android.this.startActivity(intent);
                    }
                });
                mHelp.show();
                return true;
            }

            case R.id.checkupdate: {
                Uri uri = Uri.parse(getResources().getString(R.string.text_updatelink));
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                ViPER4Android.this.startActivity(intent);
                return true;
            }

            case R.id.drvstatus: {
                DialogFragment df = new DialogFragment() {
                    @Override
                    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
                        if (mAudioServiceInstance == null) {
                            View v = inflater.inflate(R.layout.drvstatus, null);
                            TextView tv = (TextView) v.findViewById(R.id.drv_status);
                            tv.setText(R.string.text_service_error);
                            return v;
                        } else {
                            mAudioServiceInstance.StartStatusUpdating();
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                mAudioServiceInstance.StopStatusUpdating();
                                View view = inflater.inflate(R.layout.drvstatus, null);
                                TextView textView = (TextView) view.findViewById(R.id.drv_status);
                                textView.setText(R.string.text_service_error);
                                return view;
                            }
                            mAudioServiceInstance.StopStatusUpdating();

                            String mDrvNEONEnabled = getResources().getString(R.string.text_yes);
                            if (!mAudioServiceInstance.getDriverNEON())
                                mDrvNEONEnabled = getResources().getString(R.string.text_no);
                            String mDrvEnabled = getResources().getString(R.string.text_yes);
                            if (!mAudioServiceInstance.getDriverEnabled())
                                mDrvEnabled = getResources().getString(R.string.text_no);
                            String mDrvUsable = getResources().getString(R.string.text_normal);
                            if (!mAudioServiceInstance.getDriverUsable())
                                mDrvUsable = getResources().getString(R.string.text_abnormal);
                            String mDrvProcess = getResources().getString(R.string.text_yes);
                            if (!mAudioServiceInstance.getDriverProcess())
                                mDrvProcess = getResources().getString(R.string.text_no);

                            String mDrvEffType = getResources().getString(R.string.text_disabled);
                            if (mAudioServiceInstance.getDriverEffectType() == ViPER4AndroidService.V4A_FX_TYPE_HEADPHONE)
                                mDrvEffType = getResources().getString(R.string.text_headset);
                            else if (mAudioServiceInstance.getDriverEffectType() == ViPER4AndroidService.V4A_FX_TYPE_SPEAKER)
                                mDrvEffType = getResources().getString(R.string.text_speaker);

                            String mDrvStatus = "";
                            mDrvStatus = getResources().getString(R.string.text_drv_status_view);
                            mDrvStatus = String.format(mDrvStatus,
                                    mAudioServiceInstance.GetDriverVersion(), mDrvNEONEnabled,
                                    mDrvEnabled, mDrvUsable, mDrvProcess,
                                    mDrvEffType,
                                    mAudioServiceInstance.getDriverSamplingRate(),
                                    mAudioServiceInstance.getDriverChannels());

                            View view = inflater.inflate(R.layout.drvstatus, null);
                            TextView textView = (TextView) view.findViewById(R.id.drv_status);
                            textView.setText(mDrvStatus);
                            return view;
                        }
                    }
                };
                df.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
                df.show(getFragmentManager(), "v4astatus");
                return true;
            }

            case R.id.changelog: {
                // Proceed changelog file name
                String mLocale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry();
                String mChangelog_AssetsName = "Changelog_";
                if (mLocale.equalsIgnoreCase("zh_CN"))
                    mChangelog_AssetsName = mChangelog_AssetsName + "zh_CN";
                else if (mLocale.equalsIgnoreCase("zh_TW"))
                    mChangelog_AssetsName = mChangelog_AssetsName + "zh_TW";
                else mChangelog_AssetsName = mChangelog_AssetsName + "en_US";
                mChangelog_AssetsName = mChangelog_AssetsName + ".txt";

                String mChangeLog = "";
                InputStream isHandle = null;
                try {
                    isHandle = getAssets().open(mChangelog_AssetsName);
                    mChangeLog = readTextFile(isHandle);
                    isHandle.close();
                } catch (Exception e) {
                }

                if (mChangeLog.equalsIgnoreCase("")) return true;
                AlertDialog.Builder mChglog = new AlertDialog.Builder(this);
                mChglog.setTitle(R.string.text_changelog);
                mChglog.setMessage(mChangeLog);
                mChglog.setNegativeButton(getResources().getString(R.string.text_ok), null);
                mChglog.show();
                return true;
            }

            case R.id.loadprofile: {
                // Profiles are stored at external storage
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                    return true;

                // Lets cache all profiles first
                String mProfilePath = Environment.getExternalStorageDirectory() + "/ViPER4Android/Profile/";
                mProfileList = Utils.getProfileList(mProfilePath);
                if (mProfileList.size() <= 0) return true;
                String arrayProfileList[] = new String[mProfileList.size()];
                for (int mPrfIdx = 0; mPrfIdx < mProfileList.size(); mPrfIdx++)
                    arrayProfileList[mPrfIdx] = mProfileList.get(mPrfIdx);

                // Get current audio mode
                final int mCurrentPage = actionBar.getSelectedNavigationIndex();

                // Now please choose which profile you want to load
                new AlertDialog.Builder(this)
                        .setTitle(R.string.text_loadfxprofile)
                        .setIcon(R.drawable.icon)
                        .setItems(arrayProfileList, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String mProfilePath = Environment.getExternalStorageDirectory() + "/ViPER4Android/Profile/";
                                Log.i("ViPER4Android", "Load effect profile, current page = " + mCurrentPage);
                                String mPreferenceName[] = new String[3];
                                mPreferenceName[0] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".headset";
                                mPreferenceName[1] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".speaker";
                                mPreferenceName[2] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".bluetooth";

                                // Make sure index is in range
                                int index = mCurrentPage;
                                if (index < 0) index = 0;
                                if (index > 2) index = 2;

                                // Now load the profile please
                                String arrayProfileList[] = new String[mProfileList.size()];
                                for (int mPrfIdx = 0; mPrfIdx < mProfileList.size(); mPrfIdx++)
                                    arrayProfileList[mPrfIdx] = mProfileList.get(mPrfIdx);
                                String mProfileName = arrayProfileList[which];
                                if (Utils.loadProfile(mProfileName, mProfilePath, mPreferenceName[index], mActivityContext)) {
                                    AlertDialog.Builder mResult = new AlertDialog.Builder(mActivityContext);
                                    mResult.setTitle("ViPER4Android");
                                    mResult.setMessage(getResources().getString(R.string.text_profileload_ok));
                                    mResult.setNegativeButton(getResources().getString(R.string.text_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    });
                                    mResult.show();
                                } else {
                                    AlertDialog.Builder mResult = new AlertDialog.Builder(mActivityContext);
                                    mResult.setTitle("ViPER4Android");
                                    mResult.setMessage(getResources().getString(R.string.text_profileload_err));
                                    mResult.setNegativeButton(getResources().getString(R.string.text_ok), null);
                                    mResult.show();
                                }
                            }
                        }).setNegativeButton(R.string.text_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        return;
                    }
                }).create().show();

                return true;
            }

            case R.id.saveprofile: {
                // Profiles are stored at external storage
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                    return true;

                // Get current audio mode
                final int nCurrentPage = actionBar.getSelectedNavigationIndex();

                // Now please give me the name of profile
                DialogFragment df = new DialogFragment() {
                    private EditText editProfileName = null;

                    @Override
                    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
                        View v = inflater.inflate(R.layout.saveprofile, null);
                        editProfileName = (EditText) v.findViewById(R.id.save_profile_name);
                        Button btnSaveProfile = (Button) v.findViewById(R.id.profile_save_button);
                        btnSaveProfile.setOnClickListener(new OnClickListener() {
                            public void onClick(View v) {
                                /* Really sanity check */
                                if (editProfileName == null) {
                                    dismiss();
                                    return;
                                }
                                if (editProfileName.getText() == null) {
                                    dismiss();
                                    return;
                                }
                                if (editProfileName.getText().toString() == null) {
                                    dismiss();
                                    return;
                                }
                                /***********************/

                                String mProfileName = editProfileName.getText().toString().trim();
                                if (mProfileName == null)
                                    Toast.makeText(mActivityContext,
                                            getResources().getString(R.string.text_profilesaved_err), Toast.LENGTH_LONG).show();
                                else if (mProfileName.equals(""))
                                    Toast.makeText(mActivityContext,
                                            getResources().getString(R.string.text_profilesaved_err), Toast.LENGTH_LONG).show();
                                else {
                                    // Deal with the directory
                                    String mProfilePath = Environment.getExternalStorageDirectory() + "/ViPER4Android/Profile/";
                                    File mProfileDir = new File(mProfilePath);
                                    if (!mProfileDir.exists()) {
                                        mProfileDir.mkdirs();
                                        mProfileDir.mkdir();
                                    }
                                    mProfileDir = new File(mProfilePath);
                                    if (!mProfileDir.exists()) {
                                        Toast.makeText(mActivityContext, getResources().getString(R.string.text_rwsd_error), Toast.LENGTH_LONG).show();
                                        dismiss();
                                        return;
                                    }

                                    szSaveProfileNameGlobal = mProfileName;
                                    if (Utils.checkProfileExists(mProfileName, Environment.getExternalStorageDirectory() + "/ViPER4Android/Profile/")) {
                                        // Name already exist, overwritten ?
                                        AlertDialog.Builder mConfirm = new AlertDialog.Builder(mActivityContext);
                                        mConfirm.setTitle("ViPER4Android");
                                        mConfirm.setMessage(getResources().getString(R.string.text_profilesaved_overwrite));
                                        mConfirm.setPositiveButton(getResources().getString(R.string.text_yes), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                Log.i("ViPER4Android", "Save effect profile, current page = " + nCurrentPage);
                                                String szPreferenceName[] = new String[3];
                                                szPreferenceName[0] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".headset";
                                                szPreferenceName[1] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".speaker";
                                                szPreferenceName[2] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".bluetooth";
                                                int nIndex = nCurrentPage;
                                                if (nIndex < 0) nIndex = 0;
                                                if (nIndex > 2) nIndex = 2;
                                                Utils.saveProfile(szSaveProfileNameGlobal, Environment.getExternalStorageDirectory() + "/ViPER4Android/Profile/", szPreferenceName[nIndex], mActivityContext);
                                                Toast.makeText(mActivityContext, mActivityContext.getResources().getString(R.string.text_profilesaved_ok), Toast.LENGTH_LONG).show();
                                            }
                                        });
                                        mConfirm.setNegativeButton(getResources().getString(R.string.text_no), null);
                                        mConfirm.show();
                                        dismiss();
                                        return;
                                    }

                                    // Save the profile please
                                    Log.i("ViPER4Android", "Save effect profile, current page = " + nCurrentPage);
                                    String mPreferenceName[] = new String[3];
                                    mPreferenceName[0] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".headset";
                                    mPreferenceName[1] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".speaker";
                                    mPreferenceName[2] = ViPER4Android.SHARED_PREFERENCES_BASENAME + ".bluetooth";
                                    int index = nCurrentPage;
                                    if (index < 0) index = 0;
                                    if (index > 2) index = 2;
                                    Utils.saveProfile(mProfileName, Environment.getExternalStorageDirectory() + "/ViPER4Android/Profile/", mPreferenceName[index], mActivityContext);
                                    Toast.makeText(mActivityContext, getResources().getString(R.string.text_profilesaved_ok), Toast.LENGTH_LONG).show();
                                }
                                dismiss();
                            }
                        });

                        Button btnCancelProfile = (Button) v.findViewById(R.id.profile_cancel_button);
                        btnCancelProfile.setOnClickListener(new OnClickListener() {
                            public void onClick(View v) {
                                dismiss();
                            }
                        });

                        return v;
                    }
                };
                df.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
                df.show(getFragmentManager(), "v4a_saveprofile");
                return true;
            }

            case R.id.drvinst: {
                String mMenuText = item.getTitle().toString();
                if (getResources().getString(R.string.text_uninstall).equals(mMenuText)) {
                    // Please confirm the process
                    AlertDialog.Builder mConfirm = new AlertDialog.Builder(this);
                    mConfirm.setTitle("ViPER4Android");
                    mConfirm.setMessage(getResources().getString(R.string.text_drvuninst_confim));
                    mConfirm.setPositiveButton(getResources().getString(R.string.text_yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Uninstall driver
                            Utils.uninstallDrv_FX();
                            AlertDialog.Builder mResult = new AlertDialog.Builder(mActivityContext);
                            mResult.setTitle("ViPER4Android");
                            mResult.setMessage(getResources().getString(R.string.text_drvuninst_ok));
                            mResult.setNegativeButton(getResources().getString(R.string.text_ok), null);
                            mResult.show();
                        }
                    });
                    mConfirm.setNegativeButton(getResources().getString(R.string.text_no), null);
                    mConfirm.show();
                } else if (getResources().getString(R.string.text_install).equals(mMenuText)) {
                    // Install driver
                    String mDriverFileName = determineCPUWithDriver();
                    if (Utils.installDrv_FX(mActivityContext, mDriverFileName)) {
                        AlertDialog.Builder mResult = new AlertDialog.Builder(mActivityContext);
                        mResult.setTitle("ViPER4Android");
                        mResult.setMessage(getResources().getString(R.string.text_drvinst_ok));
                        mResult.setNegativeButton(getResources().getString(R.string.text_ok), null);
                        mResult.show();
                    } else {
                        AlertDialog.Builder mResult = new AlertDialog.Builder(mActivityContext);
                        mResult.setTitle("ViPER4Android");
                        mResult.setMessage(getResources().getString(R.string.text_drvinst_failed));
                        mResult.setNegativeButton(getResources().getString(R.string.text_ok), null);
                        mResult.show();
                    }
                } else {
                    String mTip = getResources().getString(R.string.text_service_error);
                    Toast.makeText(this, mTip, Toast.LENGTH_LONG).show();
                }
                return true;
            }

            case R.id.compatible: {
                String mCompatibleMode = prefSettings.getString("viper4android.settings.compatiblemode", "global");
                int mSelectIndex = 0;
                if (mCompatibleMode.equals("global")) mSelectIndex = 0;
                else mSelectIndex = 1;
                Dialog selectDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.text_commode)
                        .setIcon(R.drawable.icon)
                        .setSingleChoiceItems(R.array.compatible_mode, mSelectIndex, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);
                                Editor e = prefSettings.edit();
                                switch (which) {
                                    case 0:
                                        e.putString("viper4android.settings.compatiblemode", "global");
                                        break;
                                    case 1:
                                        e.putString("viper4android.settings.compatiblemode", "local");
                                        break;
                                }
                                e.commit();
                                // Tell background service to change the mode
                                sendBroadcast(new Intent(ViPER4Android.ACTION_UPDATE_PREFERENCES));
                                dialog.dismiss();
                            }
                        }).setCancelable(false).create();
                selectDialog.show();
                return true;
            }

            case R.id.notify: {
                boolean enableNotify = prefSettings.getBoolean("viper4android.settings.show_notify_icon", false);
                enableNotify = !enableNotify;
                if (enableNotify)
                    item.setTitle(getResources().getString(R.string.text_hidetrayicon));
                else item.setTitle(getResources().getString(R.string.text_showtrayicon));
                Editor e = prefSettings.edit();
                e.putBoolean("viper4android.settings.show_notify_icon", enableNotify);
                e.commit();
                // Tell background service to deal with the notification icon
                if (enableNotify) sendBroadcast(new Intent(ViPER4Android.ACTION_SHOW_NOTIFY));
                else sendBroadcast(new Intent(ViPER4Android.ACTION_CANCEL_NOTIFY));
                return true;
            }

            case R.id.lockeffect: {
                String szLockedEffect = prefSettings.getString("viper4android.settings.lock_effect", "none");
                int nLockIndex = -1;
                if (szLockedEffect.equalsIgnoreCase("none")) nLockIndex = 0;
                else if (szLockedEffect.equalsIgnoreCase("headset")) nLockIndex = 1;
                else if (szLockedEffect.equalsIgnoreCase("speaker")) nLockIndex = 2;
                else if (szLockedEffect.equalsIgnoreCase("bluetooth")) nLockIndex = 3;
                else nLockIndex = 4;

                String[] szModeList =
                        {
                                getResources().getString(R.string.text_disabled),
                                getResources().getString(R.string.text_headset),
                                getResources().getString(R.string.text_speaker),
                                getResources().getString(R.string.text_bluetooth)
                        };

                Dialog selectDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.text_lockeffect)
                        .setIcon(R.drawable.icon)
                        .setSingleChoiceItems(szModeList, nLockIndex, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);
                                Editor edit = prefSettings.edit();
                                switch (which) {
                                    case 0:
                                        edit.putString("viper4android.settings.lock_effect", "none");
                                        break;
                                    case 1:
                                        edit.putString("viper4android.settings.lock_effect", "headset");
                                        break;
                                    case 2:
                                        edit.putString("viper4android.settings.lock_effect", "speaker");
                                        break;
                                    case 3:
                                        edit.putString("viper4android.settings.lock_effect", "bluetooth");
                                        break;
                                }
                                edit.commit();
                                // Tell background service to change the mode
                                sendBroadcast(new Intent(ViPER4Android.ACTION_UPDATE_PREFERENCES));
                                dialog.dismiss();
                            }
                        }).setCancelable(false).create();
                selectDialog.show();

                return true;
            }

            default:
                return false;
        }
    }
}

class MyAdapter extends FragmentPagerAdapter {
    private final ArrayList<String> tmpEntries;
    private final ArrayList<String> tmpTitles;
    private final String[] entries;
    private final String[] titles;

    public MyAdapter(FragmentManager fm, Context context) {
        super(fm);

        Resources res = context.getResources();
        ArrayList<String> tmpEntries = new ArrayList<String>();
        tmpEntries.add("headset");
        tmpEntries.add("speaker");
        tmpEntries.add("bluetooth");
        tmpEntries.add("usb");

        tmpTitles = new ArrayList<String>();
        tmpTitles.add(res.getString(R.string.headset_title).toUpperCase());
        tmpTitles.add(res.getString(R.string.speaker_title).toUpperCase());
        tmpTitles.add(res.getString(R.string.bluetooth_title).toUpperCase());
        tmpTitles.add(res.getString(R.string.usb_title).toUpperCase());

        entries = (String[]) tmpEntries.toArray(new String[tmpEntries.size()]);
        titles = (String[]) tmpTitles.toArray(new String[tmpTitles.size()]);
        }
    @Override
    public CharSequence getPageTitle(int position) {
    return titles[position]
    }

    public String[] getEntries() {
        return entries;
    }

    @Override
    public int getCount() {
        return entries.length;
    }

    @Override
    public Fragment getItem(int position) {
        final MainDSPScreen dspFragment = new MainDSPScreen();
        Bundle bundle = new Bundle();
        bundle.putString("config", entries[position]);
        dspFragment.setArguments(bundle);
        return dspFragment;
    }
}
