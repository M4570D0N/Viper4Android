package com.vipercn.viper4android_v2.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.vipercn.viper4android_v2.activity.IrsUtils;
import com.vipercn.viper4android_v2.activity.Utils;
import com.vipercn.viper4android_v2.activity.V4AJniInterface;
import com.vipercn.viper4android_v2.activity.ViPER4Android;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class ViPER4AndroidService extends Service {

    private class ResourceMutex {
        private Semaphore mSignal = new Semaphore(1);

        public boolean acquire() {
            try {
                mSignal.acquire();
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        }

        public void release() {
            mSignal.release();
        }
    }

    private class V4ADSPModule {
        private final UUID EFFECT_TYPE_NULL = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210");
        public AudioEffect mInstance;

        public V4ADSPModule(UUID mModuleID, int mAudioSession) {
            try {
                Log.i("ViPER4Android", "Creating viper4android module, " + mModuleID.toString());
                mInstance = AudioEffect.class.getConstructor(
                        UUID.class, UUID.class, Integer.TYPE, Integer.TYPE).newInstance(
                        EFFECT_TYPE_NULL, mModuleID, 0, mAudioSession);

                if (mInstance != null) {
                    AudioEffect.Descriptor adModuleDescriptor = mInstance.getDescriptor();
                    Log.i("ViPER4Android", "Effect name : " + adModuleDescriptor.name);
                    Log.i("ViPER4Android", "Type id : " + adModuleDescriptor.type.toString());
                    Log.i("ViPER4Android", "Unique id : " + adModuleDescriptor.uuid.toString());
                    Log.i("ViPER4Android", "Implementor : " + adModuleDescriptor.implementor);
                    Log.i("ViPER4Android", "Connect mode : " + adModuleDescriptor.connectMode);

                    mInstance.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
                        @Override
                        public void onControlStatusChange(AudioEffect effect, boolean controlGranted) {
                            if (!controlGranted) {
                                Log.i("ViPER4Android", "We lost effect control token");
                                Toast.makeText(ViPER4AndroidService.this,
                                    getString(getResources().getIdentifier("text_token_lost", "string", getApplicationInfo().packageName)),
                                    Toast.LENGTH_LONG).show();
                            } else {
                                Log.i("ViPER4Android", "We got effect control token");
                                updateSystem(true);
                            }
                        }
                    });

                    mInstance.setEnableStatusListener(new AudioEffect.OnEnableStatusChangeListener() {
                        @Override
                        public void onEnableStatusChange(AudioEffect effect, boolean enabled) {
                            String mode = getAudioOutputRouting(getSharedPreferences(
                                            ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE));
                            SharedPreferences preferences = getSharedPreferences(
                                            ViPER4Android.SHARED_PREFERENCES_BASENAME + "." + mode, 0);
                            String enableKey = "viper4android.headphonefx.enable";
                            if (mode.equalsIgnoreCase("speaker"))
                                enableKey = "viper4android.speakerfx.enable";
                            boolean shouldEnabled = preferences.getBoolean(enableKey, false);
                            if (shouldEnabled != enabled) {
                                Log.i("ViPER4Android", "Engine status is " + enabled + ", but we expected " + shouldEnabled);
                                Log.i("ViPER4Android",
                                                "Im sure you are experiencing no effect, because the effect is controlling by system now");
                                Log.i("ViPER4Android",
                                                "I really have no idea to solve this problem, the fucking android, Im sorry bro");
                                Toast.makeText(ViPER4AndroidService.this,
                                                getString(getResources().getIdentifier("text_token_lost",
                                                "string", getApplicationInfo().packageName)),
                                                Toast.LENGTH_LONG).show();
                            } else
                                Log.i("ViPER4Android", "Everything is under control for now");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("ViPER4Android", "Can not create audio effect instance, V4A driver not installed or not supported by this rom");
                mInstance = null;
            }
        }

        public void release() {
            Log.i("ViPER4Android", "Free viper4android module.");
            if (mInstance != null) {
                try {
                    mInstance.release();
                } catch (Exception e) {
                }
            }
            mInstance = null;
        }

        private byte[] intToByteArray(int value) {
            ByteBuffer converter = ByteBuffer.allocate(4);
            converter.order(ByteOrder.nativeOrder());
            converter.putInt(value);
            return converter.array();
        }

        private int byteArrayToInt(byte[] valueBuf) {
            ByteBuffer converter = ByteBuffer.wrap(valueBuf);
            converter.order(ByteOrder.nativeOrder());
            return converter.getInt(0);
        }

        private byte[] concatArrays(byte[]... arrays) {
            int len = 0;
            for (byte[] a : arrays) {
                len += a.length;
            }
            byte[] b = new byte[len];
            int offs = 0;
            for (byte[] a : arrays) {
                System.arraycopy(a, 0, b, offs, a.length);
                offs += a.length;
            }
            return b;
        }

        public void setParameter_px4_vx4x1(int param, int valueL) {
            try {
                byte[] p = intToByteArray(param);
                byte[] v = intToByteArray(valueL);
                setParameter_Native(p, v);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_px4_vx4x1: " + e.getMessage());
            }
        }

        public void setParameter_px4_vx4x2(int param, int valueL, int valueH) {
            try {
                byte[] p = intToByteArray(param);
                byte[] vL = intToByteArray(valueL);
                byte[] vH = intToByteArray(valueH);
                byte[] v = concatArrays(vL, vH);
                setParameter_Native(p, v);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_px4_vx4x2: " + e.getMessage());
            }
        }

        public void setParameter_px4_vx4x3(int param, int valueL, int valueH, int valueE) {
            try {
                byte[] p = intToByteArray(param);
                byte[] vL = intToByteArray(valueL);
                byte[] vH = intToByteArray(valueH);
                byte[] vE = intToByteArray(valueE);
                byte[] v = concatArrays(vL, vH, vE);
                setParameter_Native(p, v);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_px4_vx4x3: " + e.getMessage());
            }
        }

        @SuppressWarnings("unused")
        /* For future use */
        public void setParameter_px4_vx4x4(int param, int valueL, int valueH, int valueE, int valueR) {
            try {
                byte[] p = intToByteArray(param);
                byte[] vL = intToByteArray(valueL);
                byte[] vH = intToByteArray(valueH);
                byte[] vE = intToByteArray(valueE);
                byte[] vR = intToByteArray(valueR);
                byte[] v = concatArrays(vL, vH, vE, vR);
                setParameter_Native(p, v);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_px4_vx4x4: " + e.getMessage());
            }
        }

        public void setParameter_px4_vx1x256(int param, int dataLength, byte[] byteData) {
            try {
                byte[] p = intToByteArray(param);
                byte[] vL = intToByteArray(dataLength);
                byte[] v = concatArrays(vL, byteData);
                if (v.length < 256) {
                    int zeroPad = 256 - v.length;
                    byte[] zeroArray = new byte[zeroPad];
                    v = concatArrays(v, zeroArray);
                }
                setParameter_Native(p, v);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_px4_vx1x256: " + e.getMessage());
            }
        }

        public void setParameter_px4_vx2x8192(int param, int valueL, int dataLength, byte[] byteData) {
            try {
                byte[] p = intToByteArray(param);
                byte[] vL = intToByteArray(valueL);
                byte[] vH = intToByteArray(dataLength);
                byte[] v = concatArrays(vL, vH, byteData);
                if (v.length < 8192) {
                    int zeroPad = 8192 - v.length;
                    byte[] zeroArray = new byte[zeroPad];
                    v = concatArrays(v, zeroArray);
                }
                setParameter_Native(p, v);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_px4_vx2x8192: " + e.getMessage());
            }
        }

        @SuppressWarnings("unused")
        public void setParameter_px4_vxString(int param, String mData) {
            int stringLen = mData.length();
            byte[] stringBytes = mData.getBytes(Charset.forName("US-ASCII"));
            setParameter_px4_vx1x256(param, stringLen, stringBytes);
        }

        public void setParameter_Native(byte[] parameter, byte[] value) {
            if (mInstance == null)
                return;
            try {
                Method setParameter = AudioEffect.class.getMethod(
                                "setParameter", byte[].class, byte[].class);
                setParameter.invoke(mInstance, parameter, value);
            } catch (Exception e) {
                Log.i("ViPER4Android", "setParameter_Native: " + e.getMessage());
            }
        }

        public int getParameter_px4_vx4x1(int param) {
            try {
                byte[] p = intToByteArray(param);
                byte[] v = new byte[4];
                getParameter_Native(p, v);
                int val = byteArrayToInt(v);
                return val;
            } catch (Exception e) {
                Log.i("ViPER4Android", "getParameter_px4_vx4x1: " + e.getMessage());
                return -1;
            }
        }

        public void getParameter_Native(byte[] parameter, byte[] value) {
            if (mInstance == null)
                return;
            try {
                Method getParameter = AudioEffect.class.getMethod(
                                "getParameter", byte[].class, byte[].class);
                getParameter.invoke(mInstance, parameter, value);
            } catch (Exception e) {
                Log.i("ViPER4Android", "getParameter_Native: " + e.getMessage());
            }
        }

        private void proceedIRBuffer_Speaker(String convolverIrFile, int mChannels, int mFrames, int mBytes) {
            // 1. Tell driver to prepare kernel buffer
            Random rndMachine = new Random();
            int mKernelBufferID = rndMachine.nextInt();
            setParameter_px4_vx4x3(
                            ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, mKernelBufferID, mChannels, 0);

            // 2. Read entire ir data and get hash code
            byte[] mKernelData = V4AJniInterface.ReadImpulseResponseToArray(convolverIrFile);
            if (mKernelData == null) {
                // Read failed
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mKernelData.length <= 0) {
                // Empty ir file
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            int[] mHashCode = V4AJniInterface.GetHashImpulseResponseArray(mKernelData);
            if (mHashCode == null) {
                // Wrong with hash
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mHashCode.length != 2) {
                // Wrong with hash
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mHashCode[0] == 0) {
                // Wrong with hash
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            int hashCode = mHashCode[1];

            Log.i("ViPER4Android", "[Kernel] Channels = " + mChannels
                            + ", Frames = " + mFrames + ", Bytes = "
                            + mKernelData.length + ", Hash = " + Arrays.toString(mHashCode));

            // 3. Split kernel data and send to driver
            /* 8192(packet size) - sizeof(int) - sizeof(int), 8184 bytes = 2046 float samples = 1023 stereo frames */
            int mBlockSize = 8184;
            int mRestBytes = mKernelData.length, mSendOffset = 0;
            while (mRestBytes > 0) {
                int mMinBlockSize = Math.min(mBlockSize, mRestBytes);
                byte[] mSendData = new byte[mMinBlockSize];
                System.arraycopy(mKernelData, mSendOffset, mSendData, 0, mMinBlockSize);
                mSendOffset += mMinBlockSize;
                mRestBytes -= mMinBlockSize;
                // Send to driver
                int mFramesCount = mMinBlockSize / 4; /* sizeof(float) = 4 */
                setParameter_px4_vx2x8192(
                                ViPER4AndroidService.PARAM_SPKFX_CONV_SETBUFFER,
                                mKernelBufferID, mFramesCount, mSendData);
            }

            // 4. Tell driver to commit kernel buffer
            byte[] mIrsName = convolverIrFile.getBytes();
            int mIrsNameHashCode = 0;
            if (mIrsName != null)
                mIrsNameHashCode = (int) IrsUtils.hashIrs(mIrsName, mIrsName.length);
            setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_COMMITBUFFER, mKernelBufferID, hashCode, mIrsNameHashCode);
        }

        private void proceedIRBuffer_Headphone(String convolverIrFile, int mChannels, int mFrames, int mBytes) {
            // 1. Tell driver to prepare kernel buffer
            Random rndMachine = new Random();
            int mKernelBufferID = rndMachine.nextInt();
            setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, mKernelBufferID, mChannels, 0);

            // 2. Read entire ir data and get hash code
            byte[] mKernelData = V4AJniInterface.ReadImpulseResponseToArray(convolverIrFile);
            if (mKernelData == null) {
                // Read failed
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mKernelData.length <= 0) {
                // Empty ir file
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            int[] mHashCode = V4AJniInterface.GetHashImpulseResponseArray(mKernelData);
            if (mHashCode == null) {
                // Wrong with hash
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mHashCode.length != 2) {
                // Wrong with hash
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mHashCode[0] == 0) {
                // Wrong with hash
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            int hashCode = mHashCode[1];

            Log.i("ViPER4Android", "[Kernel] Channels = " + mChannels
                            + ", Frames = " + mFrames + ", Bytes = "
                            + mKernelData.length + ", Hash = " + hashCode);

            // 3. Split kernel data and send to driver
            /* 8192(packet size) - sizeof(int) - sizeof(int), 8184 bytes = 2046 float samples = 1023 stereo frames */
            int blockSize = 8184; 
            int mRestBytes = mKernelData.length, mSendOffset = 0, mPacketIndex = 0;
            while (mRestBytes > 0) {
                int mMinBlockSize = Math.min(blockSize, mRestBytes);
                byte[] mSendData = new byte[mMinBlockSize];
                System.arraycopy(mKernelData, mSendOffset, mSendData, 0, mMinBlockSize);
                mSendOffset += mMinBlockSize;
                mRestBytes -= mMinBlockSize;
                Log.i("ViPER4Android", "Setting kernel buffer, index = " + mPacketIndex + ", length = " + mMinBlockSize);
                mPacketIndex++;
                // Send to driver
                int framesCount = mMinBlockSize / 4; /* sizeof(float) = 4 */
                setParameter_px4_vx2x8192(
                                ViPER4AndroidService.PARAM_HPFX_CONV_SETBUFFER,
                                mKernelBufferID, framesCount, mSendData);
            }

            // 4. Tell driver to commit kernel buffer
            byte[] mIrsName = convolverIrFile.getBytes();
            int mIrsNameHashCode = 0;
            if (mIrsName != null)
                mIrsNameHashCode = (int) IrsUtils.hashIrs(mIrsName, mIrsName.length);
            setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_COMMITBUFFER, mKernelBufferID, hashCode, mIrsNameHashCode);
        }

        private void proceedIRBuffer_Speaker(IrsUtils irs, String convolverIrFile) {
            // 1. Tell driver to prepare kernel buffer
            Random rndMachine = new Random();
            int mKernelBufferID = rndMachine.nextInt();
            setParameter_px4_vx4x3(
                            ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER,
                            mKernelBufferID, irs.getChannels(), 0);

            // 2. Read entire ir data and get hash code
            byte[] mKernelData = irs.readEntireData();
            if (mKernelData == null) {
                // Read failed
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mKernelData.length <= 0) {
                // Empty ir file
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            long hashCode = IrsUtils.hashIrs(mKernelData, mKernelData.length);
            int mHashCode = (int) (long) hashCode;

            Log.i("ViPER4Android", "[Kernel] Channels = " + irs.getChannels()
                            + ", Frames = " + irs.getSampleCount()
                            + ", Bytes = " + mKernelData.length + ", Hash = "
                            + mHashCode);

            // 3. Split kernel data and send to driver
            /* 8192(packet size) - sizeof(int) - sizeof(int), 8184 bytes = 2046 float samples = 1023 stereo frames */
            int blockSize = 8184;
            int mRestBytes = mKernelData.length, mSendOffset = 0;
            while (mRestBytes > 0) {
                int mMinBlockSize = Math.min(blockSize, mRestBytes);
                byte[] mSendData = new byte[mMinBlockSize];
                System.arraycopy(mKernelData, mSendOffset, mSendData, 0,
                                mMinBlockSize);
                mSendOffset += mMinBlockSize;
                mRestBytes -= mMinBlockSize;
                // Send to driver
                int framesCount = mMinBlockSize / 4; /* sizeof(float) = 4 */
                setParameter_px4_vx2x8192(
                                ViPER4AndroidService.PARAM_SPKFX_CONV_SETBUFFER,
                                mKernelBufferID, framesCount, mSendData);
            }

            // 4. Tell driver to commit kernel buffer
            byte[] mIrsName = convolverIrFile.getBytes();
            int mIrsNameHashCode = 0;
            if (mIrsName != null)
                mIrsNameHashCode = (int) IrsUtils.hashIrs(mIrsName, mIrsName.length);
            setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_COMMITBUFFER, mKernelBufferID, mHashCode, mIrsNameHashCode);
        }

        private void proceedIRBuffer_Headphone(IrsUtils irs, String convolverIrFile) {
            // 1. Tell driver to prepare kernel buffer
            Random rndMachine = new Random();
            int mKernelBufferID = rndMachine.nextInt();
            setParameter_px4_vx4x3(
                            ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER,
                            mKernelBufferID, irs.getChannels(), 0);

            // 2. Read entire ir data and get hash code
            byte[] mKernelData = irs.readEntireData();
            if (mKernelData == null) {
                // Read failed
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            if (mKernelData.length <= 0) {
                // Empty ir file
                setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
                return;
            }
            long hashCode = IrsUtils.hashIrs(mKernelData, mKernelData.length);
            int mHashCode = (int) hashCode;

            Log.i("ViPER4Android", "[Kernel] Channels = " + irs.getChannels()
                            + ", Frames = " + irs.getSampleCount()
                            + ", Bytes = " + mKernelData.length + ", Hash = "
                            + mHashCode);

            // 3. Split kernel data and send to driver
            /* 8192(packet size) - sizeof(int) - sizeof(int), 8184 bytes = 2046 float samples = 1023 stereo frames */
            int blockSize = 8184;
            int mRestBytes = mKernelData.length, mSendOffset = 0, mPacketIndex = 0;
            while (mRestBytes > 0) {
                int mMinBlockSize = Math.min(blockSize, mRestBytes);
                byte[] mSendData = new byte[mMinBlockSize];
                System.arraycopy(mKernelData, mSendOffset, mSendData, 0,
                                mMinBlockSize);
                mSendOffset += mMinBlockSize;
                mRestBytes -= mMinBlockSize;
                Log.i("ViPER4Android", "Setting kernel buffer, index = "
                                + mPacketIndex + ", length = " + mMinBlockSize);
                mPacketIndex++;
                // Send to driver
                int framesCount = mMinBlockSize / 4; /* sizeof(float) = 4 */
                setParameter_px4_vx2x8192(
                                ViPER4AndroidService.PARAM_HPFX_CONV_SETBUFFER,
                                mKernelBufferID, framesCount, mSendData);
            }

            // 4. Tell driver to commit kernel buffer
            byte[] mIrsName = convolverIrFile.getBytes();
            int mIrsNameHashCode = 0;
            if (mIrsName != null)
                mIrsNameHashCode = (int) IrsUtils.hashIrs(mIrsName, mIrsName.length);
            setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_COMMITBUFFER, mKernelBufferID, mHashCode, mIrsNameHashCode);
        }

        public void setConvIRFile(String convolverIrFile, boolean mSpeakerParam) {
            /* Commit irs when called here */

            if (convolverIrFile == null) {
                Log.i("ViPER4Android", "Clear convolver kernel");
                // Clear convolver ir file
                if (mSpeakerParam)
                    setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER, 0, 0, 1);
                else
                    setParameter_px4_vx4x3(ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER, 0, 0, 1);
            } else {
                Log.i("ViPER4Android", "Convolver kernel = " + convolverIrFile);

                // Set convolver ir file
                if (convolverIrFile.equals("")) {
                    Log.i("ViPER4Android", "Clear convolver kernel");
                    // Clear convolver ir file
                    if (mSpeakerParam)
                        setParameter_px4_vx4x3(
                                        ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER,
                                        0, 0, 1);
                    else
                        setParameter_px4_vx4x3(
                                        ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER,
                                        0, 0, 1);
                } else {
                    boolean mNeedSendIrsToDriver = true;
                    byte[] mIrsName = convolverIrFile.getBytes();
                    if (mIrsName != null) {
                        long mIrsNameHash = IrsUtils.hashIrs(mIrsName, mIrsName.length);
                        int irsNameHash = (int) mIrsNameHash;
                        int mIrsNameHashInDriver = getParameter_px4_vx4x1(PARAM_GET_CONVKNLID);
                        Log.i("ViPER4Android", "Kernel ID = [driver: " + mIrsNameHashInDriver + ", client: " + irsNameHash + "]");
                        if (irsNameHash == mIrsNameHashInDriver)
                            mNeedSendIrsToDriver = false;
                    }

                    if (!mNeedSendIrsToDriver) {
                        Log.i("ViPER4Android", "Driver is holding the same irs now");
                        return;
                    }

                    int mCommand = ViPER4AndroidService.PARAM_HPFX_CONV_PREPAREBUFFER;
                    if (mSpeakerParam)
                        mCommand = ViPER4AndroidService.PARAM_SPKFX_CONV_PREPAREBUFFER;

                    Log.i("ViPER4Android", "We are going to load irs through internal method");
                    IrsUtils irs = new IrsUtils();
                    if (irs.loadIRS(convolverIrFile)) {
                        /* Proceed buffer */
                        if (mSpeakerParam)
                            proceedIRBuffer_Speaker(irs, convolverIrFile);
                        else
                            proceedIRBuffer_Headphone(irs, convolverIrFile);
                        irs.Release();
                    } else {
                        if (V4AJniInterface.isLibraryUsable()) {
                            Log.i("ViPER4Android", "We are going to load irs through jni");
                            // Get ir file info
                            int[] iaIRInfo = V4AJniInterface.GetImpulseResponseInfoArray(convolverIrFile);
                            if (iaIRInfo == null)
                                setParameter_px4_vx4x3(mCommand, 0, 0, 1);
                            else {
                                if (iaIRInfo.length != 4)
                                    setParameter_px4_vx4x3(mCommand, 0, 0, 1);
                                else {
                                    if (iaIRInfo[0] == 0)
                                        setParameter_px4_vx4x3(mCommand, 0, 0, 1);
                                    else {
                                        /* Proceed buffer */
                                        if (mSpeakerParam)
                                            proceedIRBuffer_Speaker(convolverIrFile, iaIRInfo[1], iaIRInfo[2], iaIRInfo[3]);
                                        else
                                            proceedIRBuffer_Headphone(convolverIrFile, iaIRInfo[1], iaIRInfo[2], iaIRInfo[3]);
                                    }
                                }
                            }
                        } else
                            Log.i("ViPER4Android", "Failed to load " + convolverIrFile);
                    }
                }
            }
        }
    }

    public class LocalBinder extends Binder {
        public ViPER4AndroidService getService() {
            return ViPER4AndroidService.this;
        }
    }

    public static final UUID ID_V4A_GENERAL_FX =
            UUID.fromString("41d3c987-e6cf-11e3-a88a-11aba5d5c51b");

    public static final int DEVICE_GLOBAL_OUTPUT_MIXER = 0;

    /* ViPER4Android Driver Status */
    public static final int PARAM_GET_DRIVER_VERSION = 32769;
    public static final int PARAM_GET_NEONENABLED = 32770;
    public static final int PARAM_GET_ENABLED = 32771;
    public static final int PARAM_GET_CONFIGURE = 32772;
    public static final int PARAM_GET_STREAMING = 32773;
    public static final int PARAM_GET_EFFECT_TYPE = 32774;
    public static final int PARAM_GET_SAMPLINGRATE = 32775;
    public static final int PARAM_GET_CHANNELS = 32776;
    public static final int PARAM_GET_CONVUSABLE = 32777;
    public static final int PARAM_GET_CONVKNLID = 32778;
    /*******************************/

    /* ViPER4Android Driver Status Control */
    public static final int PARAM_SET_COMM_STATUS = 36865;
    public static final int PARAM_SET_UPDATE_STATUS = 36866;
    public static final int PARAM_SET_RESET_STATUS = 36867;
    public static final int PARAM_SET_DOPROCESS_STATUS = 36868;
    public static final int PARAM_SET_FORCEENABLE_STATUS = 36869;
    /***************************************/

    /* ViPER4Android FX Types */
    public static final int V4A_FX_TYPE_NONE = 0;
    public static final int V4A_FX_TYPE_HEADPHONE = 1;
    public static final int V4A_FX_TYPE_SPEAKER = 2;
    /**************************/

    /* ViPER4Android General FX Parameters */
    public static final int PARAM_FX_TYPE_SWITCH = 65537;
    public static final int PARAM_HPFX_CONV_PROCESS_ENABLED = 65538;
    public static final int PARAM_HPFX_CONV_UPDATEKERNEL_DEPRECATED = 65539;  /* DEPRECATED in 4.x system, use buffer instead */
    public static final int PARAM_HPFX_CONV_PREPAREBUFFER = 65540;
    public static final int PARAM_HPFX_CONV_SETBUFFER = 65541;
    public static final int PARAM_HPFX_CONV_COMMITBUFFER = 65542;
    public static final int PARAM_HPFX_CONV_CROSSCHANNEL = 65543;
    public static final int PARAM_HPFX_VHE_PROCESS_ENABLED = 65544;
    public static final int PARAM_HPFX_VHE_EFFECT_LEVEL = 65545;
    public static final int PARAM_HPFX_FIREQ_PROCESS_ENABLED = 65546;
    public static final int PARAM_HPFX_FIREQ_BANDLEVEL = 65547;
    public static final int PARAM_HPFX_COLM_PROCESS_ENABLED = 65548;
    public static final int PARAM_HPFX_COLM_WIDENING = 65549;
    public static final int PARAM_HPFX_COLM_MIDIMAGE = 65550;
    public static final int PARAM_HPFX_COLM_DEPTH = 65551;
    public static final int PARAM_HPFX_DIFFSURR_PROCESS_ENABLED = 65552;
    public static final int PARAM_HPFX_DIFFSURR_DELAYTIME = 65553;
    public static final int PARAM_HPFX_REVB_PROCESS_ENABLED = 65554;
    public static final int PARAM_HPFX_REVB_ROOMSIZE = 65555;
    public static final int PARAM_HPFX_REVB_WIDTH = 65556;
    public static final int PARAM_HPFX_REVB_DAMP = 65557;
    public static final int PARAM_HPFX_REVB_WET = 65558;
    public static final int PARAM_HPFX_REVB_DRY = 65559;
    public static final int PARAM_HPFX_AGC_PROCESS_ENABLED = 65560;
    public static final int PARAM_HPFX_AGC_RATIO = 65561;
    public static final int PARAM_HPFX_AGC_VOLUME = 65562;
    public static final int PARAM_HPFX_AGC_MAXSCALER = 65563;
    public static final int PARAM_HPFX_DYNSYS_PROCESS_ENABLED = 65564;
    public static final int PARAM_HPFX_DYNSYS_XCOEFFS = 65565;
    public static final int PARAM_HPFX_DYNSYS_YCOEFFS = 65566;
    public static final int PARAM_HPFX_DYNSYS_SIDEGAIN = 65567;
    public static final int PARAM_HPFX_DYNSYS_BASSGAIN = 65568;
    public static final int PARAM_HPFX_VIPERBASS_PROCESS_ENABLED = 65569;
    public static final int PARAM_HPFX_VIPERBASS_MODE = 65570;
    public static final int PARAM_HPFX_VIPERBASS_SPEAKER = 65571;
    public static final int PARAM_HPFX_VIPERBASS_BASSGAIN = 65572;
    public static final int PARAM_HPFX_VIPERCLARITY_PROCESS_ENABLED = 65573;
    public static final int PARAM_HPFX_VIPERCLARITY_MODE = 65574;
    public static final int PARAM_HPFX_VIPERCLARITY_CLARITY = 65575;
    public static final int PARAM_HPFX_CURE_PROCESS_ENABLED = 65576;
    public static final int PARAM_HPFX_CURE_CROSSFEED = 65577;
    public static final int PARAM_HPFX_TUBE_PROCESS_ENABLED = 65578;
    public static final int PARAM_HPFX_OUTPUT_VOLUME = 65579;
    public static final int PARAM_HPFX_OUTPUT_PAN = 65580;
    public static final int PARAM_HPFX_LIMITER_THRESHOLD = 65581;
    public static final int PARAM_SPKFX_CONV_PROCESS_ENABLED = 65582;
    public static final int PARAM_SPKFX_CONV_UPDATEKERNEL_DEPRECATED = 65583; /* DEPRECATED in 4.x system, use buffer instead */
    public static final int PARAM_SPKFX_CONV_PREPAREBUFFER = 65584;
    public static final int PARAM_SPKFX_CONV_SETBUFFER = 65585;
    public static final int PARAM_SPKFX_CONV_COMMITBUFFER = 65586;
    public static final int PARAM_SPKFX_CONV_CROSSCHANNEL = 65587;
    public static final int PARAM_SPKFX_FIREQ_PROCESS_ENABLED = 65588;
    public static final int PARAM_SPKFX_FIREQ_BANDLEVEL = 65589;
    public static final int PARAM_SPKFX_REVB_PROCESS_ENABLED = 65590;
    public static final int PARAM_SPKFX_REVB_ROOMSIZE = 65591;
    public static final int PARAM_SPKFX_REVB_WIDTH = 65592;
    public static final int PARAM_SPKFX_REVB_DAMP = 65593;
    public static final int PARAM_SPKFX_REVB_WET = 65594;
    public static final int PARAM_SPKFX_REVB_DRY = 65595;
    public static final int PARAM_SPKFX_CORR_PROCESS_ENABLED = 65596;
    public static final int PARAM_SPKFX_AGC_PROCESS_ENABLED = 65597;
    public static final int PARAM_SPKFX_AGC_RATIO = 65598;
    public static final int PARAM_SPKFX_AGC_VOLUME = 65599;
    public static final int PARAM_SPKFX_AGC_MAXSCALER = 65600;
    public static final int PARAM_SPKFX_OUTPUT_VOLUME = 65601;
    public static final int PARAM_SPKFX_LIMITER_THRESHOLD = 65602;
    /***************************************/

    private final LocalBinder mBinder = new LocalBinder();

    protected static boolean mUseHeadset;
    protected static boolean mUseBluetooth;
    protected static boolean mUseUSB;
    protected static String mPreviousMode = "none";

    private float[] mOverriddenEqualizerLevels;
    private boolean mDriverIsReady;
    private V4ADSPModule mGeneralFX;
    private SparseArray<V4ADSPModule> mGeneralFXList = new SparseArray<V4ADSPModule>();
    private ResourceMutex mV4AMutex = new ResourceMutex();

    private static final String ACTION_QUERY_DRIVERSTATUS = "com.vipercn.viper4android_v2.QUERY_DRIVERSTATUS";
    private static final String ACTION_QUERY_DRIVERSTATUS_RESULT = "com.vipercn.viper4android_v2.QUERY_DRIVERSTATUS_RESULT";
    private static final String ACTION_QUERY_EQUALIZER = "com.vipercn.viper4android_v2.QUERY_EQUALIZER";
    private static final String ACTION_QUERY_EQUALIZER_RESULT = "com.vipercn.viper4android_v2.QUERY_EQUALIZER_RESULT";
    private static final String ACTION_TAKEOVER_EFFECT = "com.vipercn.viper4android_v2.TAKEOVER_EFFECT";
    private static final String ACTION_TAKEOVER_EFFECT_RESULT = "com.vipercn.viper4android_v2.TAKEOVER_EFFECT_RESULT";
    private static final String ACTION_RELEASE_EFFECT = "com.vipercn.viper4android_v2.RELEASE_EFFECT";
    private static final String ACTION_SET_ENABLED = "com.vipercn.viper4android_v2.SET_ENABLED";
    private static final String ACTION_SET_EQUALIZER = "com.vipercn.viper4android_v2.SET_EQUALIZER";
    private boolean m3rdEnabled;
    private boolean m3rdEqualizerEnabled;
    private float[] m3rdEqualizerLevels;
    private boolean mWorkingWith3rd;

    private boolean mediaMounted;
    private final Timer mediaStatusTimer = new Timer();
    private TimerTask mediaTimerTask = new TimerTask() {
        @Override
        public void run() {
            /* This is the *best* way to solve the fragmentation of android system */
            /* Use a media mounted broadcast is not safe */

            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                mediaMounted = false;
            else {
                if (!mediaMounted) {
                    Log.i("ViPER4Android", "Media mounted, now updating parameters");
                    mediaMounted = true;
                    updateSystem(false);
                }
            }
        }
    };

    /****** 3rd API Interface ******/
    private final BroadcastReceiver m3rdAPI_QUERY_DRIVERSTATUS_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "m3rdAPI_QUERY_DRIVERSTATUS_Receiver::onReceive()");
            Intent itResult = new Intent(ACTION_QUERY_DRIVERSTATUS_RESULT);
            itResult.putExtra("driver_ready", mDriverIsReady);
            itResult.putExtra("enabled", getDriverEnabled());
            sendBroadcast(itResult);
        }
    };
    private final BroadcastReceiver m3rdAPI_QUERY_EQUALIZER_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "m3rdAPI_QUERY_EQUALIZER_Receiver::onReceive()");
            Intent itResult = new Intent(ACTION_QUERY_EQUALIZER_RESULT);

            String mode = getAudioOutputRouting(getSharedPreferences(
                            ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE));
            SharedPreferences preferences = getSharedPreferences(
                        ViPER4Android.SHARED_PREFERENCES_BASENAME + "." + mode, 0);
            boolean mEqEnabled = preferences.getBoolean(
                        "viper4android.headphonefx.fireq.enable", false);
            itResult.putExtra("equalizer_enabled", mEqEnabled);
            itResult.putExtra("equalizer_bandcount", 10);
            float[] mEqBands = {
                31.0f, 62.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f
            };
            itResult.putExtra("equalizer_bandfreq", mEqBands);
            sendBroadcast(itResult);
        }
    };
    private final BroadcastReceiver m3rdAPI_TAKEOVER_EFFECT_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "m3rdAPI_TAKEOVER_EFFECT_Receiver::onReceive()");
            Intent itResult = new Intent(ACTION_TAKEOVER_EFFECT_RESULT);

            if (!intent.hasExtra("token")) {
                Log.i("ViPER4Android", "m3rdAPI_TAKEOVER_EFFECT_Receiver, no token found");
                itResult.putExtra("granted", false);
                sendBroadcast(itResult);
            } else {
                int mToken = intent.getIntExtra("token", 0);
                if (mToken == 0) {
                    Log.i("ViPER4Android", "m3rdAPI_TAKEOVER_EFFECT_Receiver, invalid token found");
                    itResult.putExtra("granted", false);
                    sendBroadcast(itResult);
                } else {
                    mWorkingWith3rd = true;
                    Log.i("ViPER4Android", "m3rdAPI_TAKEOVER_EFFECT_Receiver, token = " + mToken);
                    itResult.putExtra("granted", true);
                    sendBroadcast(itResult);
                }
            }
        }
    };

    private final BroadcastReceiver m3rdAPI_RELEASE_EFFECT_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "m3rdAPI_RELEASE_EFFECT_Receiver::onReceive()");
            mWorkingWith3rd = false;

            if (!intent.hasExtra("token")) updateSystem(false);
            else {
                int mToken = intent.getIntExtra("token", 0);
                Log.i("ViPER4Android", "m3rdAPI_RELEASE_EFFECT_Receiver, token = " + mToken);
                updateSystem(false);
            }
        }
    };
    private final BroadcastReceiver m3rdAPI_SET_ENABLED_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "m3rdAPI_SET_ENABLED_Receiver::onReceive()");

            if (!mWorkingWith3rd)
                return;
            if (!intent.hasExtra("token")) {
                Log.i("ViPER4Android",
                                "m3rdAPI_SET_ENABLED_Receiver, no token found");
            } else {
                int mToken = intent.getIntExtra("token", 0);
                if (mToken == 0) {
                    Log.i("ViPER4Android", "m3rdAPI_SET_ENABLED_Receiver, invalid token found");
                } else {
                    if (!intent.hasExtra("enabled"))
                        return;
                    m3rdEnabled = intent.getBooleanExtra("enabled", false);
                    Log.i("ViPER4Android",
                                    "m3rdAPI_SET_ENABLED_Receiver, token = "
                                                    + mToken + ", enabled = "
                                                    + m3rdEnabled);
                    updateSystem(false);
                }
            }
        }
    };
    private final BroadcastReceiver m3rdAPI_SET_EQUALIZER_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android",
                            "m3rdAPI_SET_EQUALIZER_Receiver::onReceive()");

            if (!mWorkingWith3rd)
                return;
            if (!intent.hasExtra("token")) {
                Log.i("ViPER4Android",
                                "m3rdAPI_SET_EQUALIZER_Receiver, no token found");
            } else {
                int mToken = intent.getIntExtra("token", 0);
                if (mToken == 0) {
                    Log.i("ViPER4Android",
                                    "m3rdAPI_SET_EQUALIZER_Receiver, invalid token found");
                    return;
                } else {
                    Log.i("ViPER4Android", "m3rdAPI_SET_EQUALIZER_Receiver, token = " + mToken);
                    if (intent.hasExtra("enabled")) {
                        m3rdEqualizerEnabled = intent.getBooleanExtra("enabled", m3rdEqualizerEnabled);
                        Log.i("ViPER4Android",
                                        "m3rdAPI_SET_EQUALIZER_Receiver, enable equalizer = " + m3rdEqualizerEnabled);
                    }
                    if (intent.hasExtra("bandcount") && intent.hasExtra("bandvalues")) {
                        int mBandCount = intent.getIntExtra("bandcount", 0);
                        float[] mBandValues = intent.getFloatArrayExtra("bandvalues");
                        if (mBandCount != 10 || mBandValues == null) {
                            Log.i("ViPER4Android", "m3rdAPI_SET_EQUALIZER_Receiver, invalid band parameters");
                            return;
                        }
                        Log.i("ViPER4Android",
                                        "m3rdAPI_SET_EQUALIZER_Receiver, got new eq band values");
                        if (m3rdEqualizerLevels == null)
                            m3rdEqualizerLevels = new float[10];
                        System.arraycopy(mBandValues, 0, m3rdEqualizerLevels,
                                        0, mBandCount);
                    }
                }
                updateSystem(false);
            }
        }
    };
    /*******************************/

    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "mAudioSessionReceiver::onReceive()");

            SharedPreferences prefSettings = getSharedPreferences(
                    ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);
            String mCompatibleMode = prefSettings.getString("viper4android.settings.compatiblemode", "global");
            boolean mFXInLocalMode;
            mFXInLocalMode = !mCompatibleMode.equals("global");

            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            if (sessionId == 0) {
                Log.i("ViPER4Android", "Global output mixer session control received! ");
                return;
            }

            if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                Log.i("ViPER4Android", String.format("New audio session: %d", sessionId));
                if (!mFXInLocalMode) {
                    Log.i("ViPER4Android", "Only global effect allowed.");
                    return;
                }
                if (mV4AMutex.acquire()) {
                    if (mGeneralFXList.indexOfKey(sessionId) < 0) {
                        Log.i("ViPER4Android", "Creating local V4ADSPModule ...");
                        V4ADSPModule v4aNewDSPModule = new V4ADSPModule(ID_V4A_GENERAL_FX, sessionId);
                        if (v4aNewDSPModule.mInstance == null) {
                            Log.e("ViPER4Android", "Failed to load v4a driver.");
                            v4aNewDSPModule.release();
                        } else mGeneralFXList.put(sessionId, v4aNewDSPModule);
                    }
                    mV4AMutex.release();
                } else
                    Log.i("ViPER4Android", "Semaphore accquire failed.");
            }

            if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
                Log.i("ViPER4Android", String.format("Audio session removed: %d", sessionId));
                if (mV4AMutex.acquire()) {
                    if (mGeneralFXList.indexOfKey(sessionId) >= 0) {
                        V4ADSPModule v4aRemove = mGeneralFXList.get(sessionId);
                        mGeneralFXList.remove(sessionId);
                        if (v4aRemove != null)
                            v4aRemove.release();
                    }
                    mV4AMutex.release();
                } else
                    Log.i("ViPER4Android", "Semaphore accquire failed.");
            }

            updateSystem(false);
        }
    };

    private final BroadcastReceiver mPreferenceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "mPreferenceUpdateReceiver::onReceive()");
            updateSystem(false);
        }
    };

    private final BroadcastReceiver mShowNotifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "mShowNotifyReceiver::onReceive()");

            String mode = getAudioOutputRouting(getSharedPreferences(
                            ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE));
            if (mode.equalsIgnoreCase("headset"))
                showNotification(getString(getResources().getIdentifier(
                                "text_headset", "string",
                                getApplicationInfo().packageName)));
            else if (mode.equalsIgnoreCase("bluetooth"))
                showNotification(getString(getResources().getIdentifier(
                                "text_bluetooth", "string",
                                getApplicationInfo().packageName)));
            else if (mode.equalsIgnoreCase("usb"))
                showNotification(getString(getResources().getIdentifier(
                                "text_usb", "string",
                                getApplicationInfo().packageName)));
            else
                showNotification(getString(getResources().getIdentifier(
                                "text_speaker", "string",
                                getApplicationInfo().packageName)));
        }
    };

    private final BroadcastReceiver mCancelNotifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("ViPER4Android", "mCancelNotifyReceiver::onReceive()");
            cancelNotification();
        }
    };

    private final BroadcastReceiver mScreenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.i("ViPER4Android", "mScreenOnReceiver::onReceive()");
            /* Nothing to do here, for now */
        }
    };

    private final BroadcastReceiver mRoutingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.i("ViPER4Android", "mRoutingReceiver::onReceive()");

            final String action = intent.getAction();
            final boolean prevUseHeadset = mUseHeadset;
            final boolean prevUseBluetooth = mUseBluetooth;
            final boolean prevUseUSB = mUseUSB;

            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                mUseHeadset = intent.getIntExtra("state", 0) == 1;
            } else if (action.equals(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE,
                        BluetoothA2dp.STATE_DISCONNECTED);
                mUseBluetooth = state == BluetoothA2dp.STATE_CONNECTED;
            } else {
                if (Build.VERSION.SDK_INT >= 16) {
                    // Equals Intent.ACTION_ANALOG_AUDIO_DOCK_PLUG
                    if (action.equals("android.intent.action.ANALOG_AUDIO_DOCK_PLUG"))
                        mUseUSB = intent.getIntExtra("state", 0) == 1;
                }
            }

            Log.i("ViPER4Android", "Headset=" + mUseHeadset + ", Bluetooth="
                    + mUseBluetooth + ", USB=" + mUseUSB);
            if (prevUseHeadset != mUseHeadset
                    || prevUseBluetooth != mUseBluetooth
                    || prevUseUSB != mUseUSB) {
                /* Audio output method changed, so we flush buffer */
                updateSystem(true);
            }
        }
    };

    private void showNotification(String mFXType) {
        SharedPreferences preferences = getSharedPreferences(
                        ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);
        boolean enableNotify = preferences.getBoolean(
                        "viper4android.settings.show_notify_icon", false);
        if (!enableNotify) {
            Log.i("ViPER4Android", "showNotification(): show_notify = false");
            return;
        }

        int mIconID = getResources().getIdentifier("icon", "drawable",
                        getApplicationInfo().packageName);
        String mNotifyText = "ViPER4Android FX " + mFXType;
        CharSequence contentTitle = "ViPER4Android FX", contentText = mFXType;
        Intent notificationIntent = new Intent(this, ViPER4Android.class);
        PendingIntent contentItent = PendingIntent.getActivity(
                this, 0, notificationIntent, 0);

        if (contentItent != null) {
            Notification v4aNotify = new Notification.Builder(this)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setDefaults(0)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(mIconID)
                    .setTicker(mNotifyText)
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setContentIntent(contentItent)
                    .build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (notificationManager != null)
                notificationManager.notify(0x1234, v4aNotify);
        }
    }

    private void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null)
            notificationManager.cancel(0x1234);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("ViPER4Android", "Query ViPER4Android engine ...");
        Utils.AudioEffectUtils aeuUtils = new Utils().new AudioEffectUtils();
        if (!aeuUtils.isViPER4AndroidEngineFound()) {
            Log.i("ViPER4Android", "ViPER4Android engine not found, create empty service");
            mDriverIsReady = false;
            return;
        } else {
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo;
            String apkVersion;
            try {
                int[] iaDrvVer = aeuUtils.getViPER4AndroidEngineVersion();
                String mDriverVersion = iaDrvVer[0] + "." + iaDrvVer[1] + "." + iaDrvVer[2] + "." + iaDrvVer[3];
                packageInfo = pm.getPackageInfo(getPackageName(), 0);
                apkVersion = packageInfo.versionName;
                if (!apkVersion.equalsIgnoreCase(mDriverVersion)) {
                    Log.i("ViPER4Android", "ViPER4Android engine is not compatible with service");
                    mDriverIsReady = false;
                    return;
                }
            }catch (NameNotFoundException e) {
                Log.i("ViPER4Android", "Cannot find ViPER4Android's apk [weird]");
                mDriverIsReady = false;
                return;
            }
        }
        mDriverIsReady = true;

        Context context = getApplicationContext();
        AudioManager mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if (mAudioManager != null) {
            mUseBluetooth = mAudioManager.isBluetoothA2dpOn();
            if (mUseBluetooth) {
                Log.i("ViPER4Android", "Current is a2dp mode [bluetooth]");
                mUseHeadset = false;
                mUseUSB = false;
            } else {
                mUseHeadset = mAudioManager.isWiredHeadsetOn();
                if (mUseHeadset) {
                    Log.i("ViPER4Android", "Current is headset mode");
                    mUseUSB = false;
                } else {
                    Log.i("ViPER4Android", "Current is speaker mode");
                    mUseUSB = false;
                }
            }
        }
        Log.i("ViPER4Android", "Get current mode from system [" + getAudioOutputRouting(getSharedPreferences(
                        ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE)) + "]");

        if (mGeneralFX != null) {
            Log.e("ViPER4Android", "onCreate, mGeneralFX != null");
            mGeneralFX.release();
            mGeneralFX = null;
        }

        SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", 0);
        boolean mDriverConfigured = prefSettings.getBoolean("viper4android.settings.driverconfigured", false);
        if (!mDriverConfigured) {
            Editor editPrefs = prefSettings.edit();
            if (editPrefs != null) {
                editPrefs.putBoolean("viper4android.settings.driverconfigured", true);
                editPrefs.commit();
            }
        }
        String mCompatibleMode = prefSettings.getString("viper4android.settings.compatiblemode", "global");
        if (mCompatibleMode.equalsIgnoreCase("global")) {
            Log.i("ViPER4Android", "Creating global V4ADSPModule ...");
            if (mGeneralFX == null)
                mGeneralFX = new V4ADSPModule(ID_V4A_GENERAL_FX, DEVICE_GLOBAL_OUTPUT_MIXER);
            if (mGeneralFX.mInstance == null) {
                Log.e("ViPER4Android", "Found v4a driver, but failed to load.");
                mGeneralFX.release();
                mGeneralFX = null;
            }
        }

        if (Build.VERSION.SDK_INT < 18)
            startForeground(ViPER4Android.NOTIFY_FOREGROUND_ID, new Notification());

        IntentFilter audioSessionFilter = new IntentFilter();
        audioSessionFilter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        audioSessionFilter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        registerReceiver(mAudioSessionReceiver, audioSessionFilter);

        final IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenOnReceiver, screenFilter);

        final IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        if (Build.VERSION.SDK_INT >= 16) {
            // Equals Intent.ACTION_ANALOG_AUDIO_DOCK_PLUG
            audioFilter.addAction("android.intent.action.ANALOG_AUDIO_DOCK_PLUG");
        }
        audioFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        audioFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mRoutingReceiver, audioFilter);

        registerReceiver(mPreferenceUpdateReceiver, new IntentFilter(ViPER4Android.ACTION_UPDATE_PREFERENCES));
        registerReceiver(mShowNotifyReceiver, new IntentFilter(ViPER4Android.ACTION_SHOW_NOTIFY));
        registerReceiver(mCancelNotifyReceiver, new IntentFilter(ViPER4Android.ACTION_CANCEL_NOTIFY));

        registerReceiver(m3rdAPI_QUERY_DRIVERSTATUS_Receiver, new IntentFilter(ACTION_QUERY_DRIVERSTATUS));
        registerReceiver(m3rdAPI_QUERY_EQUALIZER_Receiver, new IntentFilter(ACTION_QUERY_EQUALIZER));
        registerReceiver(m3rdAPI_TAKEOVER_EFFECT_Receiver, new IntentFilter(ACTION_TAKEOVER_EFFECT));
        registerReceiver(m3rdAPI_RELEASE_EFFECT_Receiver, new IntentFilter(ACTION_RELEASE_EFFECT));
        registerReceiver(m3rdAPI_SET_ENABLED_Receiver, new IntentFilter(ACTION_SET_ENABLED));
        registerReceiver(m3rdAPI_SET_EQUALIZER_Receiver, new IntentFilter(ACTION_SET_EQUALIZER));

        Log.i("ViPER4Android", "Service launched.");

        updateSystem(true);

        /* First is 15 secs, then 60 secs */
        mediaStatusTimer.schedule(mediaTimerTask, 15000, 60000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (!mDriverIsReady)
            return;

        mediaStatusTimer.cancel();

        if (Build.VERSION.SDK_INT < 18)
            stopForeground(true);

        unregisterReceiver(mAudioSessionReceiver);
        unregisterReceiver(mScreenOnReceiver);
        unregisterReceiver(mRoutingReceiver);
        unregisterReceiver(mPreferenceUpdateReceiver);
        unregisterReceiver(mShowNotifyReceiver);
        unregisterReceiver(mCancelNotifyReceiver);

        unregisterReceiver(m3rdAPI_QUERY_DRIVERSTATUS_Receiver);
        unregisterReceiver(m3rdAPI_QUERY_EQUALIZER_Receiver);
        unregisterReceiver(m3rdAPI_TAKEOVER_EFFECT_Receiver);
        unregisterReceiver(m3rdAPI_RELEASE_EFFECT_Receiver);
        unregisterReceiver(m3rdAPI_SET_ENABLED_Receiver);
        unregisterReceiver(m3rdAPI_SET_EQUALIZER_Receiver);

        cancelNotification();

        if (mGeneralFX != null)
            mGeneralFX.release();
        mGeneralFX = null;

        if (mV4AMutex.acquire()) {
            for (int idx = 0; idx < mGeneralFXList.size(); idx++) {
                Integer sessionId = mGeneralFXList.keyAt(idx);
                V4ADSPModule v4aModule = mGeneralFXList.valueAt(idx);
                if (sessionId < 0 || v4aModule == null) continue;
                v4aModule.release();
            }
            mGeneralFXList.clear();
            mV4AMutex.release();
        }

        Log.i("ViPER4Android", "Service destroyed.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override  
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We should do some driver check in this method, if the driver is abnormal, we need to reload it

        Log.i("ViPER4Android", "Service::onStartCommand [Begin check driver]");

        if (!mDriverIsReady) {
            Log.e("ViPER4Android", "Service::onStartCommand [V4A Engine not found]");
            return super.onStartCommand(intent, flags, startId);
        }

        SharedPreferences prefSettings = getSharedPreferences(ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", 0);
        String mCompatibleMode = prefSettings.getString("viper4android.settings.compatiblemode", "global");
        if (!mCompatibleMode.equalsIgnoreCase("global")) {
            Log.i("ViPER4Android", "Service::onStartCommand [V4A is local effect mode]");
            return super.onStartCommand(intent, flags, startId);
        }

        if (mGeneralFX == null) {
            // Create engine instance
            Log.i("ViPER4Android", "Service::onStartCommand [Creating global V4ADSPModule ...]");
            mGeneralFX = new V4ADSPModule(ID_V4A_GENERAL_FX, DEVICE_GLOBAL_OUTPUT_MIXER);
            if (mGeneralFX.mInstance == null) {
                // If we reach here, it means android refuse to load v4a driver.
                // There are only two cases:
                //   1. The android system not totally booted or media server crashed.
                //   2. The v4a driver installed not compitable with this android.
                Log.e("ViPER4Android", "Service::onStartCommand [Found v4a driver, but failed to load]");
                mGeneralFX.release();
                mGeneralFX = null;
                return super.onStartCommand(intent, flags, startId);
            }

            // Engine instance created, update parameters
            Log.i("ViPER4Android", "Service::onStartCommand [V4ADSPModule created]");
            updateSystem(true);  // After all parameters commited, please reset all effects
            return super.onStartCommand(intent, flags, startId);
        }

        if (mGeneralFX.mInstance == null) {
            // We shouldn't go here, but ...
            // Recreate engine instance
            mGeneralFX.release();
            Log.i("ViPER4Android", "Service::onStartCommand [Recreating global V4ADSPModule ...]");
            mGeneralFX = new V4ADSPModule(ID_V4A_GENERAL_FX, DEVICE_GLOBAL_OUTPUT_MIXER);
            if (mGeneralFX.mInstance == null) {
                // If we reach here, it means android refuse to load v4a driver.
                // There are only two cases:
                //   1. The android system not totally booted or media server crashed.
                //   2. The v4a driver installed not compitable with this android.
                Log.e("ViPER4Android", "Service::onStartCommand [Found v4a driver, but failed to load]");
                mGeneralFX.release();
                mGeneralFX = null;
                return super.onStartCommand(intent, flags, startId);
            }

            // Engine instance created, update parameters
            Log.i("ViPER4Android", "Service::onStartCommand [V4ADSPModule created]");
            updateSystem(true);  // After all parameters commited, please reset all effects
            return super.onStartCommand(intent, flags, startId);
        }

        if (!getDriverUsable()) {
            // V4A driver is malfunction, but what caused this?
            //   1. Low ram available.
            //   2. Android audio hal bug.
            //   3. Media server crashed.

            // Recreate engine instance
            mGeneralFX.release();
            Log.i("ViPER4Android", "Service::onStartCommand [Recreating global V4ADSPModule ...]");
            mGeneralFX = new V4ADSPModule(ID_V4A_GENERAL_FX, DEVICE_GLOBAL_OUTPUT_MIXER);
            if (mGeneralFX.mInstance == null) {
                // If we reach here, it means android refuse to load v4a driver.
                // There are only two cases:
                //   1. The android system not totally booted or media server crashed.
                //   2. The v4a driver installed not compitable with this android.
                Log.e("ViPER4Android", "Service::onStartCommand [Found v4a driver, but failed to load]");
                mGeneralFX.release();
                mGeneralFX = null;
                return super.onStartCommand(intent, flags, startId);
            }

            // Engine instance created, update parameters
            Log.i("ViPER4Android", "Service::onStartCommand [V4ADSPModule created]");
            updateSystem(true);  // After all parameters commited, please reset all effects
            return super.onStartCommand(intent, flags, startId);
        }

        Log.i("ViPER4Android", "Service::onStartCommand [Everything is ok]");

        return super.onStartCommand(intent, flags, startId);
    }

    public void setEqualizerLevels(float[] levels) {
        mOverriddenEqualizerLevels = levels;
        updateSystem(false);
    }

    public static String getAudioOutputRouting(SharedPreferences prefSettings) {
        String mLockedEffect = prefSettings.getString("viper4android.settings.lock_effect", "none");
        if (mLockedEffect.equalsIgnoreCase("none")) {
            if (mUseBluetooth)
                return "bluetooth";
            if (mUseHeadset)
                return "headset";
            if (mUseUSB)
                return "usb";
            return "speaker";
        }
        return mLockedEffect;
    }

    public boolean getDriverIsReady() {
        return mDriverIsReady;
    }

    public boolean getDriverLoaded() {
        if (!mDriverIsReady) return false;
        if (mGeneralFX == null) return false;
        if (mGeneralFX.mInstance == null) return false;
        return true;
    }

    public void startStatusUpdating() {
        if (mGeneralFX != null && mDriverIsReady)
            mGeneralFX.setParameter_px4_vx4x1(PARAM_SET_UPDATE_STATUS, 1);
    }

    public void stopStatusUpdating() {
        if (mGeneralFX != null && mDriverIsReady)
            mGeneralFX.setParameter_px4_vx4x1(PARAM_SET_UPDATE_STATUS, 0);
    }

    public boolean getDriverNEON() {
        boolean mResult = false;
        if (mGeneralFX != null && mDriverIsReady) {
            if (mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_NEONENABLED) == 1)
                mResult = true;
        }
        return mResult;
    }

    public boolean getDriverEnabled() {
        boolean mResult = false;
        if (mGeneralFX != null && mDriverIsReady) {
            if (mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_ENABLED) == 1)
                mResult = true;
        }
        return mResult;
    }

    public boolean getDriverUsable() {
        boolean mResult = false;
        if (mGeneralFX != null && mDriverIsReady) {
            if (mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_CONFIGURE) == 1)
                mResult = true;
        }
        return mResult;
    }

    public boolean getDriverProcess() {
        boolean mResult = false;
        if (mGeneralFX != null && mDriverIsReady) {
            if (mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_STREAMING) == 1)
                mResult = true;
        }
        return mResult;
    }

    public int getDriverEffectType() {
        int result = V4A_FX_TYPE_NONE;
        if (mGeneralFX != null && mDriverIsReady)
            result = mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_EFFECT_TYPE);
        return result;
    }

    public int getDriverSamplingRate() {
        int result = 0;
        if (mGeneralFX != null && mDriverIsReady)
            result = mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_SAMPLINGRATE);
        return result;
    }

    public int getDriverChannels() {
        int result = 0;
        if (mGeneralFX != null && mDriverIsReady)
            result = mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_CHANNELS);
        return result;
    }

    public boolean getConvolverUsable() {
        boolean mResult = false;
        if (mGeneralFX != null && mDriverIsReady) {
            if (mGeneralFX.getParameter_px4_vx4x1(PARAM_GET_CONVUSABLE) == 1)
                mResult = true;
        }
        return mResult;
    }

    protected void setV4AEqualizerBandLevel(int idx, int level, boolean hpfx, V4ADSPModule dsp) {
        if (dsp == null || !mDriverIsReady)
            return;
        if (hpfx)
            dsp.setParameter_px4_vx4x2(PARAM_HPFX_FIREQ_BANDLEVEL, idx, level);
        else
            dsp.setParameter_px4_vx4x2(PARAM_SPKFX_FIREQ_BANDLEVEL, idx, level);
    }

    protected void updateSystem(boolean requireReset) {
        String mode = getAudioOutputRouting(getSharedPreferences(
                        ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE));
        SharedPreferences preferences = getSharedPreferences(
                        ViPER4Android.SHARED_PREFERENCES_BASENAME + "." + mode, 0);
        Log.i("ViPER4Android", "Begin system update(" + mode + ")");

        int mFXType = V4A_FX_TYPE_NONE;
        if (mode.equalsIgnoreCase("headset")
                    || mode.equalsIgnoreCase("bluetooth")
                    || mode.equalsIgnoreCase("usb"))
            mFXType = V4A_FX_TYPE_HEADPHONE;
        else if (mode.equalsIgnoreCase("speaker"))
            mFXType = V4A_FX_TYPE_SPEAKER;

        if (!mode.equalsIgnoreCase(mPreviousMode)) {
            mPreviousMode = mode;
            if (mode.equalsIgnoreCase("headset"))
                showNotification(getString(getResources().getIdentifier(
                        "text_headset", "string", getApplicationInfo().packageName)));
            else if (mode.equalsIgnoreCase("bluetooth"))
                showNotification(getString(getResources().getIdentifier(
                        "text_bluetooth", "string", getApplicationInfo().packageName)));
            else if (mode.equalsIgnoreCase("usb"))
                showNotification(getString(getResources().getIdentifier(
                        "text_usb", "string", getApplicationInfo().packageName)));
            else showNotification(getString(getResources().getIdentifier(
                        "text_speaker", "string", getApplicationInfo().packageName)));
        }

        SharedPreferences prefSettings = getSharedPreferences(
                ViPER4Android.SHARED_PREFERENCES_BASENAME + ".settings", MODE_PRIVATE);

        String mCompatibleMode = prefSettings.getString("viper4android.settings.compatiblemode", "global");
        boolean mFXInLocalMode;
        mFXInLocalMode = !mCompatibleMode.equals("global");

        Log.i("ViPER4Android", "<+++++++++++++++ Update global effect +++++++++++++++>");
        updateSystem_Global(preferences, mFXType, requireReset, mFXInLocalMode);
        Log.i("ViPER4Android", "<++++++++++++++++++++++++++++++++++++++++++++++++++++>");

        Log.i("ViPER4Android", "<+++++++++++++++ Update local effect +++++++++++++++>");
        updateSystem_Local(preferences, mFXType, requireReset, mFXInLocalMode);
        Log.i("ViPER4Android", "<+++++++++++++++++++++++++++++++++++++++++++++++++++>");
    }

    protected void updateSystem_Global(SharedPreferences preferences, int mFXType, boolean requireReset, boolean mLocalFX) {
        if (mGeneralFX == null || mGeneralFX.mInstance == null || !mDriverIsReady) {
            Log.i("ViPER4Android", "updateSystem(): Effects is invalid!");
            return;
        }

        try {
            if (!mGeneralFX.mInstance.hasControl()) {
                Log.i("ViPER4Android", "The effect is controlling by system now");
                return;
            }
        } catch (Exception e) {
            Log.i("ViPER4Android", "updateSystem_Global(), Exception = " + e.getMessage());
            return;
        }

        if (mLocalFX)
            updateSystem_Module(preferences, mFXType, mGeneralFX, requireReset, true);
        else
            updateSystem_Module(preferences, mFXType, mGeneralFX, requireReset, false);
    }

    protected void updateSystem_Local(SharedPreferences preferences,
                    int mFXType, boolean requireReset, boolean mLocalFX) {
        if (mV4AMutex.acquire()) {
            List<Integer> v4aUnderControl = new ArrayList<Integer>();
            for (int idx = 0; idx < mGeneralFXList.size(); idx++) {
                Integer sessionId = mGeneralFXList.keyAt(idx);
                V4ADSPModule v4aModule = mGeneralFXList.valueAt(idx);
                if (sessionId < 0 || v4aModule == null)
                    continue;
                try {
                    if (!mLocalFX)
                        updateSystem_Module(preferences, mFXType, v4aModule, requireReset, true);
                    else
                        updateSystem_Module(preferences, mFXType, v4aModule, requireReset, false);
                } catch (Exception e) {
                    Log.i("ViPER4Android",
                                    String.format("Trouble trying to manage session %d, removing...", sessionId), e);
                    v4aUnderControl.add(sessionId);
                }
            }
            for (Integer mV4aUnderControl : v4aUnderControl)
                mGeneralFXList.remove(mV4aUnderControl);

            mV4AMutex.release();
        } else
            Log.i("ViPER4Android", "Semaphore accquire failed.");
    }

    protected void updateSystem_Module(SharedPreferences preferences,
                    int mFXType, V4ADSPModule v4aModule, boolean requireReset,
                    boolean mMasterSwitchOff) {
        Log.i("ViPER4Android", "updateSystem(): Commiting effects type");
        v4aModule.setParameter_px4_vx4x1(PARAM_FX_TYPE_SWITCH, mFXType);

        /******************************************** Headphone FX ********************************************/
        if (mFXType == V4A_FX_TYPE_HEADPHONE) {
            Log.i("ViPER4Android", "updateSystem(): Commiting headphone-fx parameters");

            /* FIR Equalizer */
            Log.i("ViPER4Android", "updateSystem(): Updating FIR Equalizer.");
            if (!mWorkingWith3rd) {
                if (mOverriddenEqualizerLevels != null) {
                    for (int i = 0; i < mOverriddenEqualizerLevels.length; i++)
                        setV4AEqualizerBandLevel(
                                i,Math.round(mOverriddenEqualizerLevels[i] * 100),
                                true, v4aModule);
                } else {
                    String[] levels = preferences.getString(
                            "viper4android.headphonefx.fireq.custom",
                            "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;")
                            .split(";");
                for (short i = 0; i < levels.length; i++)
                        setV4AEqualizerBandLevel(i, Math.round(Float
                                .valueOf(levels[i]) * 100), true,
                                v4aModule);
                }
                if (preferences.getBoolean("viper4android.headphonefx.fireq.enable", false))
                    v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_FIREQ_PROCESS_ENABLED, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_FIREQ_PROCESS_ENABLED, 0);
            } else {
                if (m3rdEqualizerLevels != null) {
                    for (int i = 0; i < m3rdEqualizerLevels.length; i++)
                        setV4AEqualizerBandLevel(
                                i, Math.round(m3rdEqualizerLevels[i] * 100), true, v4aModule);
                }
                if (m3rdEqualizerEnabled)
                    v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_FIREQ_PROCESS_ENABLED, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_FIREQ_PROCESS_ENABLED, 0);
            }

            /* Convolver */
            Log.i("ViPER4Android", "updateSystem(): Updating Convolver.");
            String convolverIrFileName = preferences.getString(
                            "viper4android.headphonefx.convolver.kernel", "");
            v4aModule.setConvIRFile(convolverIrFileName, false);
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_CONV_CROSSCHANNEL,Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.convolver.crosschannel", "0")));
            if (preferences.getBoolean("viper4android.headphonefx.convolver.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_CONV_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_CONV_PROCESS_ENABLED, 0);

            /* Colorful Music (ViPER's Headphone 360) */
            Log.i("ViPER4Android", "updateSystem(): Updating Field Surround (Colorful Music).");
            String[] colorfulMusic = preferences.getString(
                    "viper4android.headphonefx.colorfulmusic.coeffs", "120;200").split(";");
            if (colorfulMusic.length == 2) {
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_COLM_WIDENING, Integer.valueOf(colorfulMusic[0]));
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_COLM_DEPTH, Integer.valueOf(colorfulMusic[1]));
            }
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_COLM_MIDIMAGE, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.colorfulmusic.midimage", "150")));
            if (preferences.getBoolean("viper4android.headphonefx.colorfulmusic.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_COLM_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_COLM_PROCESS_ENABLED, 0);

            /* Diff Surround */
            Log.i("ViPER4Android", "updateSystem(): Updating Diff Surround.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_DIFFSURR_DELAYTIME, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.diffsurr.delay", "500")));
            if (preferences.getBoolean("viper4android.headphonefx.diffsurr.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_DIFFSURR_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_DIFFSURR_PROCESS_ENABLED, 0);

            /* ViPER's Headphone Surround Engine + */
            Log.i("ViPER4Android", "updateSystem(): Updating ViPER's Headphone Surround Engine +.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VHE_EFFECT_LEVEL, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.vhs.qual", "0")));
            if (preferences.getBoolean("viper4android.headphonefx.vhs.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VHE_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VHE_PROCESS_ENABLED, 0);

            /* ViPER's Reverberation */
            Log.i("ViPER4Android", "updateSystem(): Updating Reverberation.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_ROOMSIZE, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.roomsize", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_WIDTH, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.roomwidth", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_DAMP, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.damp", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_WET, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.wet", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_DRY, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.dry", "50")));
            if (preferences.getBoolean("viper4android.headphonefx.reverb.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_REVB_PROCESS_ENABLED, 0);

            /* Playback Auto Gain Control */
            Log.i("ViPER4Android", "updateSystem(): Updating Playback AGC.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_AGC_RATIO, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.playbackgain.ratio", "50")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_AGC_VOLUME, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.playbackgain.volume", "80")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_AGC_MAXSCALER, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.playbackgain.maxscaler", "400")));
            if (preferences.getBoolean("viper4android.headphonefx.playbackgain.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_AGC_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_AGC_PROCESS_ENABLED, 0);

            /* Dynamic System */
            Log.i("ViPER4Android", "updateSystem(): Updating Dynamic System.");
            String[] dynamicSystem = preferences.getString(
                            "viper4android.headphonefx.dynamicsystem.coeffs",
                            "100;5600;40;40;50;50").split(";");
            if (dynamicSystem.length == 6) {
                v4aModule.setParameter_px4_vx4x2(PARAM_HPFX_DYNSYS_XCOEFFS,
                                Integer.valueOf(dynamicSystem[0]),
                                Integer.valueOf(dynamicSystem[1]));
                v4aModule.setParameter_px4_vx4x2(PARAM_HPFX_DYNSYS_YCOEFFS,
                                Integer.valueOf(dynamicSystem[2]),
                                Integer.valueOf(dynamicSystem[3]));
                v4aModule.setParameter_px4_vx4x2(PARAM_HPFX_DYNSYS_SIDEGAIN,
                                Integer.valueOf(dynamicSystem[4]),
                                Integer.valueOf(dynamicSystem[5]));
            }
            int mBass = Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.dynamicsystem.bass", "0"));
            mBass = mBass * 20 + 100;
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_DYNSYS_BASSGAIN, mBass);
            if (preferences.getBoolean("viper4android.headphonefx.dynamicsystem.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_DYNSYS_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_DYNSYS_PROCESS_ENABLED, 0);

            /* Fidelity Control */
            Log.i("ViPER4Android", "updateSystem(): Updating Fidelity Control.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERBASS_MODE, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.fidelity.bass.mode", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERBASS_SPEAKER, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.fidelity.bass.freq", "40")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERBASS_BASSGAIN, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.fidelity.bass.gain", "50")));
            if (preferences.getBoolean("viper4android.headphonefx.fidelity.bass.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERBASS_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERBASS_PROCESS_ENABLED, 0);
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERCLARITY_MODE, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.fidelity.clarity.mode", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERCLARITY_CLARITY, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.fidelity.clarity.gain", "50")));
            if (preferences.getBoolean("viper4android.headphonefx.fidelity.clarity.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERCLARITY_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_VIPERCLARITY_PROCESS_ENABLED, 0);

            /* Cure System */
            Log.i("ViPER4Android", "updateSystem(): Updating Cure System.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_CURE_CROSSFEED, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.cure.crossfeed", "0")));
            if (preferences.getBoolean("viper4android.headphonefx.cure.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_CURE_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_CURE_PROCESS_ENABLED, 0);

            /* Tube Simulator */
            Log.i("ViPER4Android", "updateSystem(): Updating Tube Simulator.");
            if (preferences.getBoolean("viper4android.headphonefx.tube.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_TUBE_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_TUBE_PROCESS_ENABLED, 0);

            /* Speaker Optimization */
            Log.i("ViPER4Android", "updateSystem(): Shutting down speaker optimizer.");
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_CORR_PROCESS_ENABLED, 0);

            /* Limiter */
            Log.i("ViPER4Android", "updateSystem(): Updating Limiter.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_OUTPUT_VOLUME, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.outvol", "100")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_OUTPUT_PAN, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.channelpan", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_LIMITER_THRESHOLD, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.limiter", "100")));

            /* Master Switch */
            if (!mWorkingWith3rd) {
                boolean mForceEnable = preferences.getBoolean(
                        "viper4android.global.forceenable.enable", false);
                if (mForceEnable)
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_FORCEENABLE_STATUS, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_FORCEENABLE_STATUS, 0);

                boolean mMasterControl = preferences.getBoolean(
                            "viper4android.headphonefx.enable", false);
                if (mMasterSwitchOff)
                    mMasterControl = false;
                if (mMasterControl)
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 0);
                v4aModule.mInstance.setEnabled(mMasterControl);
            } else {
                if (m3rdEnabled) {
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 1);
                    v4aModule.mInstance.setEnabled(true);
                } else {
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 0);
                    v4aModule.mInstance.setEnabled(false);
                }
            }
        }
        /******************************************************************************************************/
        /********************************************* Speaker FX *********************************************/
        else if (mFXType == V4A_FX_TYPE_SPEAKER) {
            Log.i("ViPER4Android", "updateSystem(): Commiting speaker-fx parameters");

            /* FIR Equalizer */
            Log.i("ViPER4Android", "updateSystem(): Updating FIR Equalizer.");
            if (!mWorkingWith3rd) {
                if (mOverriddenEqualizerLevels != null) {
                    for (int i = 0; i < mOverriddenEqualizerLevels.length; i++)
                        setV4AEqualizerBandLevel(
                                i, Math.round(mOverriddenEqualizerLevels[i] * 100),
                                false, v4aModule);
                } else {
                    String[] levels = preferences.getString(
                            "viper4android.headphonefx.fireq.custom",
                            "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;")
                            .split(";");
                    for (short i = 0; i < levels.length; i++)
                        setV4AEqualizerBandLevel(i, Math.round(Float
                                .valueOf(levels[i]) * 100), false, v4aModule);
                }
                if (preferences.getBoolean("viper4android.headphonefx.fireq.enable", false))
                    v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_FIREQ_PROCESS_ENABLED, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_FIREQ_PROCESS_ENABLED, 0);
            } else {
                if (m3rdEqualizerLevels != null) {
                    for (int i = 0; i < m3rdEqualizerLevels.length; i++)
                        setV4AEqualizerBandLevel(
                                i, Math.round(m3rdEqualizerLevels[i] * 100),
                                false, v4aModule);
                }
                if (m3rdEqualizerEnabled)
                    v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_FIREQ_PROCESS_ENABLED, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_FIREQ_PROCESS_ENABLED, 0);
            }

            /* ViPER's Reverberation */
            Log.i("ViPER4Android", "updateSystem(): Updating Reverberation.");
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_ROOMSIZE, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.roomsize", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_WIDTH, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.roomwidth", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_DAMP, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.damp", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_WET, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.wet", "0")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_DRY, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.reverb.dry", "50")));
            if (preferences.getBoolean("viper4android.headphonefx.reverb.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_REVB_PROCESS_ENABLED, 0);

            /* Convolver */
            Log.i("ViPER4Android", "updateSystem(): Updating Convolver.");
            String convolverIrFileName = preferences.getString("viper4android.headphonefx.convolver.kernel", "");
            v4aModule.setConvIRFile(convolverIrFileName, true);
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_CONV_CROSSCHANNEL,Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.convolver.crosschannel", "0")));
            if (preferences.getBoolean("viper4android.headphonefx.convolver.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_CONV_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_CONV_PROCESS_ENABLED, 0);

            /* Tube Simulator */
            Log.i("ViPER4Android", "updateSystem(): Shutting down tube simulator.");
            v4aModule.setParameter_px4_vx4x1(PARAM_HPFX_TUBE_PROCESS_ENABLED, 0);

            /* Speaker Optimization */
            Log.i("ViPER4Android", "updateSystem(): Updating Speaker Optimizer.");
            if (preferences.getBoolean("viper4android.speakerfx.spkopt.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_CORR_PROCESS_ENABLED, 1);
            else
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_CORR_PROCESS_ENABLED, 0);

            /* eXtraLoud */
            Log.i("ViPER4Android", "updateSystem(): Updating eXtraLoud.");
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_AGC_RATIO, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.playbackgain.ratio", "50")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_AGC_VOLUME, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.playbackgain.volume", "80")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_AGC_MAXSCALER, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.playbackgain.maxscaler", "400")));
            if (preferences.getBoolean("viper4android.headphonefx.playbackgain.enable", false))
                v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_AGC_PROCESS_ENABLED, 1);
            else v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_AGC_PROCESS_ENABLED, 0);

            /* Limiter */
            Log.i("ViPER4Android", "updateSystem(): Updating Limiter.");
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_OUTPUT_VOLUME, Integer.valueOf(
                    preferences.getString("viper4android.headphonefx.outvol", "100")));
            v4aModule.setParameter_px4_vx4x1(PARAM_SPKFX_LIMITER_THRESHOLD, Integer.valueOf(
                    preferences.getString("viper4android.speakerfx.limiter", "100")));

            /* Master Switch */
            if (!mWorkingWith3rd) {
                boolean mForceEnable = preferences.getBoolean(
                        "viper4android.global.forceenable.enable", false);
                if (mForceEnable)
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_FORCEENABLE_STATUS, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_FORCEENABLE_STATUS, 0);

                boolean mMasterControl = preferences.getBoolean(
                        "viper4android.speakerfx.enable", false);
                if (mMasterSwitchOff)
                    mMasterControl = false;
                if (mMasterControl)
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 1);
                else
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 0);
                v4aModule.mInstance.setEnabled(mMasterControl);
            } else {
                if (m3rdEnabled) {
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 1);
                    v4aModule.mInstance.setEnabled(true);
                } else {
                    v4aModule.setParameter_px4_vx4x1(PARAM_SET_DOPROCESS_STATUS, 0);
                    v4aModule.mInstance.setEnabled(false);
                }
            }
        }
        /******************************************************************************************************/

        /* Reset */
        if (requireReset)
            v4aModule.setParameter_px4_vx4x1(PARAM_SET_RESET_STATUS, 1);
        /*********/

        Log.i("ViPER4Android", "System updated.");
    }
}
