package com.vipercn.viper4android.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.vipercn.viper4android.R;
import com.vipercn.viper4android.preference.EqualizerPreference;
import com.vipercn.viper4android.preference.SummariedListPreference;
import com.vipercn.viper4android.service.HeadsetService;

public final class MainDSPScreen extends PreferenceFragment {

    public static final String PREF_KEY_EQ = "viper4android.headphonefx.fireq";
    public static final String PREF_KEY_CUSTOM_EQ = "viper4android.headphonefx.fireq.custom";
    public static final String EQ_VALUE_CUSTOM = "custom";

    public static final String PREF_KEY_CONVOLVER = "viper4android.headphonefx.convolver";
    public static final String PREF_KEY_KERNEL_CONVOLVER = "viper4android.headphonefx.convolver.kernel";
    public static final String CONVOLVER_VALUE_KERNEL = "kernel";

    private Context mParentContext = null;

    public void setParentContext(Context mCtx) {
        mParentContext = mCtx;
    }

    private final OnSharedPreferenceChangeListener listener =
            new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Log.i("ViPER4Android", "Update key = " + key);

            if (PREF_KEY_EQ.equals(key)) {
                String newValue = prefs.getString(key, null);
                if (!EQ_VALUE_CUSTOM.equals(newValue)) {
                    prefs.edit().putString(PREF_KEY_CUSTOM_EQ, newValue).commit();

                    /* Now tell the equalizer that it must display something else. */
                    EqualizerPreference eq =
                            (EqualizerPreference) findPreference(PREF_KEY_CUSTOM_EQ);
                    eq.refreshFromPreference();
                }
            }

            /* If the equalizer surface is updated, select matching pref entry or "custom". */
            if (PREF_KEY_CUSTOM_EQ.equals(key)) {
                String newValue = prefs.getString(key, null);

                String desiredValue = EQ_VALUE_CUSTOM;
                SummariedListPreference preset =
                        (SummariedListPreference) findPreference(PREF_KEY_EQ);

                for (CharSequence entry : preset.getEntryValues()) {
                    if (entry.equals(newValue)) {
                        desiredValue = newValue;
                        break;
                    }
                }

                /* Tell listpreference that it must display something else. */
                if (!desiredValue.equals(preset.getEntry())) {
                    prefs.edit().putString(PREF_KEY_EQ, desiredValue).commit();
                    preset.refreshFromPreference();
                }
            }

            if ("PREF_KEY_KERNEL_CONVOLVER".equals(key))
            {
                if (mParentContext != null)
                {
                    String szSrcIRFile = prefs.getString(key, "");
                    if (szSrcIRFile != "")
                    {
                        szSrcIRFile = "\"" + szSrcIRFile + "\"";

                        Log.i("ViPER4Android", "IR sample = " + szSrcIRFile);
                        int iAndroidVersion = Build.VERSION.SDK_INT;
                        Log.i("ViPER4Android", "System version: " + iAndroidVersion);

                        String szDstFile = "/data/v4a_conv.irs";
                        Log.i("ViPER4Android", "Copy ir sample to " + szDstFile);

                        String szToolbox = Utils.GetSavedToolbox(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", mParentContext);
                        if (!szToolbox.equals(""))
                        {
                            String szCopy   = Utils.MakeCopyCmdLine(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", mParentContext, szSrcIRFile, szDstFile);
                            String szChmod  = szToolbox + " chmod";
                            String szSync   = szToolbox + " sync";
                            String szCmdLine[] = new String[3];
                            szCmdLine[0] = szCopy;
                            szCmdLine[1] = szSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
                            szCmdLine[2] = szChmod + " 777 " + szDstFile;
                            Utils.runRootCommand(szCmdLine, 100);
                        }
                    }
                    else
                    {
                        Log.i("ViPER4Android", "Remove /data/v4a_conv.irs");
                        String szDstFile = "/data/v4a_conv.irs";
                        String szToolbox = Utils.GetSavedToolbox(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", mParentContext);
                        if (!szToolbox.equals(""))
                        {
                            String szRemove = szToolbox + " rm";
                            String szSync   = szToolbox + " sync";
                            String szCmdLine[] = new String[2];
                            szCmdLine[0] = szRemove + " " + szDstFile;
                            szCmdLine[1] = szSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
                            Utils.runRootCommand(szCmdLine, 100);
                        }
                    }
                }
            }

            getActivity().sendBroadcast(new Intent(ViPER4Android.ACTION_UPDATE_PREFERENCES));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String config = getArguments().getString("config");
        PreferenceManager prefMgr = getPreferenceManager();

        prefMgr.setSharedPreferencesName(ViPER4Android.SHARED_PREFERENCES_BASENAME + "." + config);

        try {
            int xmlId = R.xml.class.getField(config + "_preferences").getInt(null);
            addPreferencesFromResource(xmlId);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        prefMgr.getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
    }
}
