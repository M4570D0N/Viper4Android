package com.vipercn.viper4android.preference;

import java.util.Locale;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.vipercn.viper4android.R;
import com.vipercn.viper4android.service.HeadsetService;
import com.vipercn.viper4android.preference.EqualizerSurface;

public class EqualizerPreference extends DialogPreference {
    protected static final String TAG = EqualizerPreference.class.getSimpleName();

    protected EqualizerSurface mListEqualizer, mDialogEqualizer;
    private HeadsetService mHeadsetService;

    private final ServiceConnection mConnectionForDialog = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "Acquiring connection to headsetservice");
            mHeadsetService = ((HeadsetService.LocalBinder) binder).getService();
            updateDspFromDialogEqualizer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mHeadsetService = null;
        }
    };

    public EqualizerPreference(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setLayoutResource(R.layout.equalizer);
        setDialogLayoutResource(R.layout.equalizer_popup);
    }

    protected void updateDspFromDialogEqualizer() {
        if (mHeadsetService != null) {
            float[] levels = new float[10];
            for (int i = 0; i < levels.length; i ++) {
                levels[i] = mDialogEqualizer.getBand(i);
            }
            mHeadsetService.setEqualizerLevels(levels);
        }
    }

    private void updateListEqualizerFromValue() {
        String value = getPersistedString(null);
        if (value != null && mListEqualizer != null) {
            String[] levelsStr = value.split(";");
            for (int i = 0; i < 10; i ++) {
                mListEqualizer.setBand(i, Float.valueOf(levelsStr[i]));
            }
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mDialogEqualizer = (EqualizerSurface) view.findViewById(R.id.FrequencyResponse);
        mDialogEqualizer.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX();
                float y = event.getY();

                /* Which band is closest to the position user pressed? */
                int band = mDialogEqualizer.findClosest(x);

                int wy = v.getHeight();
                float level = (y / wy) * (EqualizerSurface.MIN_DB - EqualizerSurface.MAX_DB)
                        - EqualizerSurface.MIN_DB;
                if (level < EqualizerSurface.MIN_DB) {
                    level = EqualizerSurface.MIN_DB;
                } else if (level > EqualizerSurface.MAX_DB) {
                    level = EqualizerSurface.MAX_DB;
                }

                mDialogEqualizer.setBand(band, level);
                updateDspFromDialogEqualizer();
                return true;
            }
        });

        if (mListEqualizer != null) {
            for (int i = 0; i < 10; i ++) {
                mDialogEqualizer.setBand(i, mListEqualizer.getBand(i));
            }
        }

        Intent serviceIntent = new Intent(getContext(), HeadsetService.class);
        getContext().bindService(serviceIntent, mConnectionForDialog, 0);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 10; i ++) {
                value.append(String.format(Locale.ROOT, "%.1f", mDialogEqualizer.getBand(i)));
                value.append(';');
            }
            persistString(value.toString());
            updateListEqualizerFromValue();
        }

        if (mHeadsetService != null) {
            mHeadsetService.setEqualizerLevels(null);
        }
        getContext().unbindService(mConnectionForDialog);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mListEqualizer = (EqualizerSurface) view.findViewById(R.id.FrequencyResponse);
        updateListEqualizerFromValue();
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String value = restorePersistedValue ? getPersistedString(null) : (String) defaultValue;
        if (shouldPersist()) {
            persistString(value);
        }
    }

    public void refreshFromPreference() {
        onSetInitialValue(true, null);
    }
}
