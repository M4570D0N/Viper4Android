package com.vipercn.viper4android_v2.activity;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.audiofx.AudioEffect;
import android.os.Environment;
import android.util.Log;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.execution.CommandCapture;
import com.vipercn.viper4android_v2.service.ViPER4AndroidService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;

public class Utils {
    public class AudioEffectUtils {
        private AudioEffect.Descriptor[] mAudioEffectList;
        private boolean mHasViPER4AndroidEngine;
        private int[] mV4AEngineVersion = new int[4];

        public AudioEffectUtils() {
            try {
                mAudioEffectList = AudioEffect.queryEffects();
            } catch (Exception e) {
                mAudioEffectList = null;
                mHasViPER4AndroidEngine = false;
                mV4AEngineVersion[0] = 0;
                mV4AEngineVersion[1] = 0;
                mV4AEngineVersion[2] = 0;
                mV4AEngineVersion[3] = 0;
                Log.e("ViPER4Android_Utils", "Failed to query audio effects");
                return;
            }
            if (mAudioEffectList == null) {
                mHasViPER4AndroidEngine = false;
                mV4AEngineVersion[0] = 0;
                mV4AEngineVersion[1] = 0;
                mV4AEngineVersion[2] = 0;
                mV4AEngineVersion[3] = 0;
                Log.e("ViPER4Android_Utils", "Failed to query audio effects");
                return;
            }

            AudioEffect.Descriptor mViper4AndroidEngine = null;
            Log.i("ViPER4Android_Utils", "Found " + mAudioEffectList.length + " effects");
            for (int i = 0; i < mAudioEffectList.length; i++) {
                if (mAudioEffectList[i] == null) continue;
                try {
                    AudioEffect.Descriptor aeEffect = mAudioEffectList[i];
                    Log.i("ViPER4Android_Utils", "[" + (i + 1) + "], " + aeEffect.name + ", " + aeEffect.implementor);
                    if (aeEffect.uuid.equals(ViPER4AndroidService.ID_V4A_GENERAL_FX)) {
                        Log.i("ViPER4Android_Utils", "Perfect, found ViPER4Android engine at " + (i + 1));
                        mViper4AndroidEngine = aeEffect;
                    }
                } catch (Exception e) {
                }
            }

            if (mViper4AndroidEngine == null) {
                Log.i("ViPER4Android_Utils", "ViPER4Android engine not found");
                mHasViPER4AndroidEngine = false;
                mV4AEngineVersion[0] = 0;
                mV4AEngineVersion[1] = 0;
                mV4AEngineVersion[2] = 0;
                mV4AEngineVersion[3] = 0;
                return;
            }

            // Extract engine version
            try {
                String v4aVersionLine = mViper4AndroidEngine.name;
                if (v4aVersionLine.contains("[") && v4aVersionLine.contains("]")) {
                    if (v4aVersionLine.length() >= 23) {
                        // v4aVersionLine should be "ViPER4Android [A.B.C.D]"
                        v4aVersionLine = v4aVersionLine.substring(15);
                        while (v4aVersionLine.endsWith("]"))
                            v4aVersionLine = v4aVersionLine.substring(0, v4aVersionLine.length() - 1);
                        // v4aVersionLine should be "A.B.C.D"
                        String [] mVerBlocks = v4aVersionLine.split("\\.");
                        if (mVerBlocks.length == 4) {
                            mV4AEngineVersion[0] = Integer.parseInt(mVerBlocks[0]);
                            mV4AEngineVersion[1] = Integer.parseInt(mVerBlocks[1]);
                            mV4AEngineVersion[2] = Integer.parseInt(mVerBlocks[2]);
                            mV4AEngineVersion[3] = Integer.parseInt(mVerBlocks[3]);
                        }
                        Log.i("ViPER4Android_Utils", "The version of ViPER4Android engine is " +
                                mV4AEngineVersion[0] + "." +
                                mV4AEngineVersion[1] + "." +
                                mV4AEngineVersion[2] + "." +
                                mV4AEngineVersion[3]);
                        mHasViPER4AndroidEngine = true;
                        return;
                    }
                }
            } catch (Exception e) {}

            Log.e("ViPER4Android_Utils", "Cannot extract ViPER4Android engine version");
            mHasViPER4AndroidEngine = false;
            mV4AEngineVersion[0] = 0;
            mV4AEngineVersion[1] = 0;
            mV4AEngineVersion[2] = 0;
            mV4AEngineVersion[3] = 0;
        }

        public AudioEffect.Descriptor[] GetAudioEffectList() {
            return mAudioEffectList;
        }

        public boolean isViPER4AndroidEngineFound() {
            return mHasViPER4AndroidEngine;
        }

        public int[] getViPER4AndroidEngineVersion() {
            return mV4AEngineVersion;
        }
    }

    public static class CpuInfo {
        private boolean mCPUHasNEON;
        private boolean mCPUHasVFP;

        // Lets read /proc/cpuinfo in java
        private boolean readCPUInfo() {
            String mCPUInfoFile = "/proc/cpuinfo";
            FileReader cpuReader = null;
            BufferedReader bufferReader = null;

            mCPUHasNEON = false;
            mCPUHasVFP = false;

            // Find "Features" line, extract neon and vfp
            try {
                cpuReader = new FileReader(mCPUInfoFile);
                bufferReader = new BufferedReader(cpuReader);
                while (true) {
                    String line = bufferReader.readLine();
                    if (line == null) break;
                    line = line.trim();
                    if (line.startsWith("Features")) {
                        Log.i("ViPER4Android_Utils", "CpuInfo[java] = <" + line + ">");
                        StringTokenizer stBlock = new StringTokenizer(line);
                        while (stBlock.hasMoreElements()) {
                            String mFeature = stBlock.nextToken();
                            if (mFeature != null) {
                                if (mFeature.equalsIgnoreCase("neon")) mCPUHasNEON = true;
                                else if (mFeature.equalsIgnoreCase("vfp")) mCPUHasVFP = true;
                            }
                        }
                    }
                }
                bufferReader.close();
                cpuReader.close();

                Log.i("ViPER4Android_Utils", "CpuInfo[java] = NEON:"
                        + mCPUHasNEON + ", VFP:" + mCPUHasVFP);
                return !(!mCPUHasNEON && !mCPUHasVFP);
            } catch (IOException e) {
                try {
                    if (bufferReader != null)
                        bufferReader.close();
                    if (cpuReader != null)
                        cpuReader.close();
                    return false;
                } catch (Exception ex) {
                    return false;
                }
            }
        }

        // Lets read /proc/cpuinfo in jni
        private void readCPUInfoJni() {
            mCPUHasNEON = V4AJniInterface.isCPUSupportNEON();
            mCPUHasVFP = V4AJniInterface.isCPUSupportVFP();
        }

        // Buffered result
        public CpuInfo() {
            mCPUHasNEON = false;
            mCPUHasVFP = false;
            if (!readCPUInfo())
                readCPUInfoJni();
        }

        public boolean hasNEON() {
            return mCPUHasNEON;
        }

        public boolean hasVFP() {
            return mCPUHasVFP;
        }
    }

    // Check if the specified file exists.
    public static boolean fileExists(String filename) {
        boolean mExist = new File(filename).exists();
        if (!mExist) {
            if (!StaticEnvironment.getVBoxUsable()) {
                RootTools.useRoot = true;
                RootTools.debugMode = true;
                mExist = RootTools.exists(filename);
            } else {
                String VBoX = StaticEnvironment.getVBoxExecutablePath();
                if (ShellCommand.executeWithoutShell(VBoX + " exists "
                        + filename, null) == 0)
                    mExist = true;
            }
        }
        return mExist;
    }

    // Get a file length
    public static long getFileLength(String mFileName) {
        try {
            return new File(mFileName).length();
        } catch (Exception e) {
            return 0;
        }
    }

    // Download a file from Internet
    public static boolean downloadFile(String mURL, String mFileName, String mStorePath) {
        try {
            URL url = new URL(mURL);
            URLConnection connection = url.openConnection();
            connection.connect();
            InputStream stream = connection.getInputStream();
            if (connection.getContentLength() <= 0) return false;
            if (stream == null) return false;
            FileOutputStream fos = new FileOutputStream(mStorePath + mFileName);

            byte[] buf = new byte[1024];
            do {
                int numRead = stream.read(buf);
                if (numRead == -1)
                    break;
                fos.write(buf, 0, numRead);
            } while (true);
            stream.close();
            fos.close();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Check a file with checksum
    public static boolean fileChecksum(String filePathName, String mCheckSum) {
        long checkSum = 0;

        try {
            FileInputStream fis = new FileInputStream(filePathName);
            byte[] buf = new byte[1024];
            do {
                int numRead = fis.read(buf);
                if (numRead == -1)
                    break;
                for (int idx = 0; idx < numRead; idx++)
                    checkSum = checkSum + (long) buf[idx];
            } while (true);
            fis.close();
            String mNewChecksum = Long.toString(checkSum);
            return mCheckSum.equals(mNewChecksum);
        } catch (Exception e) {
            return false;
        }
    }

    // Read file list from path
    public static void getFileNameList(File path, String fileExt, ArrayList<String> fileList) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (null == files)
                return;
            for (File file : files) getFileNameList(file, fileExt, fileList);
        } else {
            String filePath = path.getAbsolutePath();
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
            if (fileName.toLowerCase(Locale.US).endsWith(fileExt))
                fileList.add(fileName);
        }
    }

    // Get profile name from a file
    public static String getProfileName(String mProfileFileName) {
        try {
            FileInputStream fisInput = new FileInputStream(mProfileFileName);
            InputStreamReader isrInput = new InputStreamReader(fisInput, "UTF-8");
            BufferedReader bufferInput = new BufferedReader(isrInput);
            String mProfileName = "";
            while (true) {
                String mLine = bufferInput.readLine();
                if (mLine == null) break;
                if (mLine.startsWith("#")) continue;

                String[] mChunks = mLine.split("=");
                if (mChunks.length != 2) continue;
                if (mChunks[0].trim().equalsIgnoreCase("profile_name")) {
                    mProfileName = mChunks[1];
                    break;
                }
            }
            bufferInput.close();
            isrInput.close();
            fisInput.close();

            return mProfileName;
        } catch (Exception e) {
            return "";
        }
    }

    // Get profile name list
    public static ArrayList<String> getProfileList(String mProfileDir) {
        try {
            File fProfileDirHandle = new File(mProfileDir);
            ArrayList<String> profileList = new ArrayList<String>();
            getFileNameList(fProfileDirHandle, ".prf", profileList);

            ArrayList<String> mProfileNameList = new ArrayList<String>();
            for (String mProfileList : profileList) {
                String mFileName = mProfileDir + mProfileList;
                String mName = getProfileName(mFileName);
                mProfileNameList.add(mName.trim());
            }

            return mProfileNameList;
        } catch (Exception e) {
            return new ArrayList<String>();
        }
    }

    // Check whether profile has been exists
    public static boolean checkProfileExists(String mProfileName, String mProfileDir) {
        try {
            File fProfileDirHandle = new File(mProfileDir);
            ArrayList<String> profileList = new ArrayList<String>();
            getFileNameList(fProfileDirHandle, ".prf", profileList);

            boolean mFoundProfile = false;
            for (String mProfileList : profileList) {
                String mFileName = mProfileDir + mProfileList;
                String mName = getProfileName(mFileName);
                if (mProfileName.trim().equalsIgnoreCase(mName.trim())) {
                    mFoundProfile = true;
                    break;
                }
            }

            return mFoundProfile;
        } catch (Exception e) {
            return false;
        }
    }

    // Load profile from file
    public static boolean loadProfile(String mProfileName, String mProfileDir,
            String mPreferenceName, Context ctx) {
        try {
            File fProfileDirHandle = new File(mProfileDir);
            ArrayList<String> profileFileList = new ArrayList<String>();
            getFileNameList(fProfileDirHandle, ".prf", profileFileList);
            String mProfileFileName = "";
            for (String mProfileFileList : profileFileList) {
                String mFileName = mProfileDir + mProfileFileList;
                String mName = getProfileName(mFileName);
                if (mProfileName.trim().equalsIgnoreCase(mName.trim())) {
                    mProfileFileName = mFileName;
                    break;
                }
            }
            if (mProfileFileName.equals("")) return false;

            SharedPreferences preferences = ctx.getSharedPreferences(
                    mPreferenceName, Context.MODE_PRIVATE);
            if (preferences != null) {
                FileInputStream fisInput = new FileInputStream(mProfileFileName);
                InputStreamReader isrInput = new InputStreamReader(fisInput, "UTF-8");
                BufferedReader bufferInput = new BufferedReader(isrInput);
                Editor e = preferences.edit();
                while (true) {
                    String mLine = bufferInput.readLine();
                    if (mLine == null) break;
                    if (mLine.startsWith("#")) continue;

                    String[] mChunks = mLine.split("=");
                    if (mChunks.length != 3) continue;
                    if (mChunks[1].trim().equalsIgnoreCase("boolean")) {
                        String mParameter = mChunks[0];
                        boolean mValue = Boolean.valueOf(mChunks[2]);
                        e.putBoolean(mParameter, mValue);
                    } else if (mChunks[1].trim().equalsIgnoreCase("string")) {
                        String mParameter = mChunks[0];
                        String mValue = mChunks[2];
                        e.putString(mParameter, mValue);
                    } else {
                    }
                }
                e.commit();
                bufferInput.close();
                isrInput.close();
                fisInput.close();

                return true;
            } else
                return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Save profile to file
    public static void saveProfile(String mProfileName, String mProfileDir,
            String mPreferenceName, Context ctx) {
        try {
            SharedPreferences preferences = ctx.getSharedPreferences(
                    mPreferenceName, Context.MODE_PRIVATE);
            if (preferences != null) {
                String mOutFileName = mProfileDir + mProfileName + ".prf";
                if (fileExists(mOutFileName)) new File(mOutFileName).delete();

                FileOutputStream fosOutput = new FileOutputStream(mOutFileName);
                OutputStreamWriter oswOutput = new OutputStreamWriter(fosOutput, "UTF-8");
                BufferedWriter mOutput = new BufferedWriter(oswOutput);

                SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd   hh:mm:ss", Locale.US);
                String mDate = sDateFormat.format(new java.util.Date());

                mOutput.write("# ViPER4Android audio effect profile !\n");
                mOutput.write("# Created " + mDate + "\n\n");
                mOutput.write("profile_name=" + mProfileName + "\n\n");

                String mValue;

                // boolean values
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.enable", false));
                mOutput.write("viper4android.headphonefx.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.speakerfx.enable", false));
                mOutput.write("viper4android.speakerfx.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.speakerfx.spkopt.enable", false));
                mOutput.write("viper4android.speakerfx.spkopt.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.playbackgain.enable", false));
                mOutput.write("viper4android.headphonefx.playbackgain.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.fireq.enable", false));
                mOutput.write("viper4android.headphonefx.fireq.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.convolver.enable", false));
                mOutput.write("viper4android.headphonefx.convolver.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.colorfulmusic.enable", false));
                mOutput.write("viper4android.headphonefx.colorfulmusic.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.diffsurr.enable", false));
                mOutput.write("viper4android.headphonefx.diffsurr.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.vhs.enable", false));
                mOutput.write("viper4android.headphonefx.vhs.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.reverb.enable", false));
                mOutput.write("viper4android.headphonefx.reverb.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.dynamicsystem.enable", false));
                mOutput.write("viper4android.headphonefx.dynamicsystem.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.fidelity.bass.enable", false));
                mOutput.write("viper4android.headphonefx.fidelity.bass.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.fidelity.clarity.enable", false));
                mOutput.write("viper4android.headphonefx.fidelity.clarity.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.cure.enable", false));
                mOutput.write("viper4android.headphonefx.cure.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(preferences.getBoolean("viper4android.headphonefx.tube.enable", false));
                mOutput.write("viper4android.headphonefx.tube.enable=boolean=" + mValue + "\n");

                // string values
                mValue = preferences.getString("viper4android.headphonefx.playbackgain.ratio", "50");
                mOutput.write("viper4android.headphonefx.playbackgain.ratio=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.playbackgain.maxscaler", "400");
                mOutput.write("viper4android.headphonefx.playbackgain.maxscaler=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.playbackgain.volume", "80");
                mOutput.write("viper4android.headphonefx.playbackgain.volume=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fireq", "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;");
                mOutput.write("viper4android.headphonefx.fireq=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fireq.custom", "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;");
                mOutput.write("viper4android.headphonefx.fireq.custom=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.convolver.kernel", "");
                mOutput.write("viper4android.headphonefx.convolver.kernel=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.convolver.crosschannel", "0");
                mOutput.write("viper4android.headphonefx.convolver.crosschannel=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.colorfulmusic.coeffs", "120;200");
                mOutput.write("viper4android.headphonefx.colorfulmusic.coeffs=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.colorfulmusic.midimage", "150");
                mOutput.write("viper4android.headphonefx.colorfulmusic.midimage=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.diffsurr.delay", "500");
                mOutput.write("viper4android.headphonefx.diffsurr.delay=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.vhs.qual", "0");
                mOutput.write("viper4android.headphonefx.vhs.qual=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.reverb.roomsize", "0");
                mOutput.write("viper4android.headphonefx.reverb.roomsize=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.reverb.roomwidth", "0");
                mOutput.write("viper4android.headphonefx.reverb.roomwidth=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.reverb.damp", "0");
                mOutput.write("viper4android.headphonefx.reverb.damp=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.reverb.wet", "0");
                mOutput.write("viper4android.headphonefx.reverb.wet=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.reverb.dry", "50");
                mOutput.write("viper4android.headphonefx.reverb.dry=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.dynamicsystem.coeffs", "100;5600;40;80;50;50");
                mOutput.write("viper4android.headphonefx.dynamicsystem.coeffs=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.dynamicsystem.bass", "0");
                mOutput.write("viper4android.headphonefx.dynamicsystem.bass=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fidelity.bass.mode", "0");
                mOutput.write("viper4android.headphonefx.fidelity.bass.mode=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fidelity.bass.freq", "40");
                mOutput.write("viper4android.headphonefx.fidelity.bass.freq=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fidelity.bass.gain", "50");
                mOutput.write("viper4android.headphonefx.fidelity.bass.gain=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fidelity.clarity.mode", "0");
                mOutput.write("viper4android.headphonefx.fidelity.clarity.mode=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.fidelity.clarity.gain", "50");
                mOutput.write("viper4android.headphonefx.fidelity.clarity.gain=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.cure.crossfeed", "0");
                mOutput.write("viper4android.headphonefx.cure.crossfeed=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.outvol", "100");
                mOutput.write("viper4android.headphonefx.outvol=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.channelpan", "0");
                mOutput.write("viper4android.headphonefx.channelpan=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.headphonefx.limiter", "100");
                mOutput.write("viper4android.headphonefx.limiter=string=" + mValue + "\n");
                mValue = preferences.getString("viper4android.speakerfx.limiter", "100");
                mOutput.write("viper4android.speakerfx.limiter=string=" + mValue + "\n");

                mOutput.flush();
                mOutput.close();
                oswOutput.close();
                fosOutput.close();
            }
        } catch (Exception e) {
        }
    }

    // Modify audio_effects.conf
    public static boolean modifyFXConfig(String mInputFile, String mOutputFile) {
        Log.i("ViPER4Android_Utils", "Editing audio configuration, input = "
                + mInputFile + ", output = " + mOutputFile);
        try {
            long inputFileLength = getFileLength(mInputFile);

            // Create reading and writing stuff
            FileInputStream fisInput = new FileInputStream(mInputFile);
            FileOutputStream fosOutput = new FileOutputStream(mOutputFile);
            InputStreamReader isrInput = new InputStreamReader(fisInput, "ASCII");
            OutputStreamWriter oswOutput = new OutputStreamWriter(fosOutput, "ASCII");
            BufferedReader bufferInput = new BufferedReader(isrInput);
            BufferedWriter bufferOutput = new BufferedWriter(oswOutput);

            // Check whether the file has already modified
            boolean configModified = false;
            bufferInput.mark((int) inputFileLength);
            do {
                String mLine = bufferInput.readLine();
                if (mLine == null)
                    break;
                if (mLine.trim().startsWith("#"))
                    continue;
                /* This is v4a effect uuid */
                if (mLine.toLowerCase(Locale.US).contains(
                        "41d3c987-e6cf-11e3-a88a-11aba5d5c51b")) {
                    Log.i("ViPER4Android_Utils",
                            "Source file has been modified, line = " + mLine);
                    configModified = true;
                    break;
                }
            } while (true);

            boolean bLibraryAppend = false;
            boolean bEffectAppend = false;
            if (configModified) {
                // Already modified, just copy
                bufferInput.reset();
                do {
                    String mLine = bufferInput.readLine();
                    if (mLine == null)
                        break;
                    bufferOutput.write(mLine + "\n");
                } while (true);
                bufferOutput.flush();

                bufferInput.close();
                isrInput.close();
                fisInput.close();
                bufferOutput.close();
                oswOutput.close();
                fosOutput.close();

                return true;
            } else {
                // Lets append v4a library and effect to configuration
                bufferInput.reset();
                do {
                    String mLine = bufferInput.readLine();
                    if (mLine == null)
                        break;
                    if (mLine.trim().equalsIgnoreCase("libraries {")
                            && !bLibraryAppend) {
                        // Append library
                        bufferOutput.write(mLine + "\n");
                        bufferOutput.write("  v4a_fx {\n");
                        bufferOutput.write("    path /system/lib/soundfx/libv4a_fx_ics.so\n");
                        bufferOutput.write("  }\n");
                        bLibraryAppend = true;
                    } else if (mLine.trim().equalsIgnoreCase("effects {") && !bEffectAppend) {
                        // Append effect
                        bufferOutput.write(mLine + "\n");
                        bufferOutput.write("  v4a_standard_fx {\n");
                        bufferOutput.write("    library v4a_fx\n");
                        bufferOutput.write("    uuid 41d3c987-e6cf-11e3-a88a-11aba5d5c51b\n");
                        bufferOutput.write("  }\n");
                        bEffectAppend = true;
                    } else
                        bufferOutput.write(mLine + "\n");
                } while (true);
                bufferOutput.flush();

                bufferInput.close();
                isrInput.close();
                fisInput.close();
                bufferOutput.close();
                oswOutput.close();
                fosOutput.close();

                // Just in case, different config file format in future
                return bLibraryAppend & bEffectAppend;
            }
        } catch (Exception e) {
            Log.i("ViPER4Android_Utils", "Error: " + e.getMessage());
            return false;
        }
    }

    // Get application data path
    public static String getBasePath(Context ctx) {
        Context cont = ctx.getApplicationContext();
        String mBasePath = cont.getFilesDir().getAbsolutePath();
        if (!cont.getFilesDir().exists())
            if (!cont.getFilesDir().mkdirs())
                return "";
        return mBasePath;
    }

    // Check if addon.d folder exists for script installation (Device kernel dependant)
    public static boolean AddondExists() {
        File file = new File("/system/addon.d/");

        return (file.exists() && file.isDirectory());
    }

    // Copy assets to local
    public static boolean copyAssetsToLocal(Context ctx, String mSourceName, String mDstName) {
        String mBasePath = getBasePath(ctx);
        if (mBasePath.equals("")) return false;
        mDstName = mBasePath + "/" + mDstName;

        InputStream myInput;
        OutputStream myOutput;
        String outFileName = mDstName;
        try {
            File hfOutput = new File(mDstName);
            if (hfOutput.exists()) hfOutput.delete();

            myOutput = new FileOutputStream(outFileName);
            myInput = ctx.getAssets().open(mSourceName);
            byte[] tBuffer = new byte[4096]; /* 4K page size */
            int mLength;
            while ((mLength = myInput.read(tBuffer)) > 0)
                myOutput.write(tBuffer, 0, mLength);
            myOutput.flush();
            myInput.close();
            myOutput.close();
        } catch (Exception e) {
            Log.i("ViPER4Android_Utils", "CopyAssetsToLocal() failed, msg = " + e.getMessage());
            return false;
        }

        return true;
    }

    // Uninstall ViPER4Android FX driver
    public static void uninstallDrv_FX() {
        /* When uninstalling the v4a driver, we just delete the driver file (or just uninstall the apk).
         * Android will check all effect drivers before load, so keep v4a in audio_effects.conf is safe.
         */

        if (!StaticEnvironment.getVBoxUsable()) {
            // Lets acquire root first :)
            RootTools.useRoot = true;
            RootTools.debugMode = true;
            if (!RootTools.isRootAvailable()) return;
            if (!RootTools.isAccessGiven()) return;
            // When done, a root shell was opened

            // Then delete the driver
            String mDriverPathName = "/system/lib/soundfx/libv4a_fx_ics.so";
            try {
                RootTools.useRoot = true;
                RootTools.debugMode = true;
                if (RootTools.exists(mDriverPathName)) {
                    RootTools rtTools = new RootTools();
                    rtTools.deleteFileOrDirectory(mDriverPathName, true);
                }
                RootTools.closeAllShells();
            } catch (IOException e) {
            }
        } else {
            String vBoX = StaticEnvironment.getVBoxExecutablePath();
            String mDriverPathName = "/system/lib/soundfx/libv4a_fx_ics.so";
            if (ShellCommand.openRootShell(true)) {
                ShellCommand.sendShellCommand(vBoX + " mount -o remount,rw /system", 5.0f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                ShellCommand.sendShellCommand(vBoX + " rm " + mDriverPathName, 1.0f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                ShellCommand.sendShellCommand(vBoX + " sync", 5.0f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                ShellCommand.sendShellCommand(vBoX + " mount -o remount,ro /system", 5.0f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                ShellCommand.closeShell();
            }
        }
    }

    // Install ViPER4Android FX driver through roottools
    private static boolean installDrv_FX_RootTools(Context ctx, String mDriverName) {

        boolean isAddondSupported = false;

        // Make sure we can use external storage for temp directory
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            return false;

        // Copy driver assets to local
        if (!copyAssetsToLocal(ctx, mDriverName, "libv4a_fx_ics.so"))
            return false;

        //Check if addon.d directory exists and copy script if supported
        if (AddondExists()) {
            copyAssetsToLocal(ctx, "91-v4a.sh", "91-v4a.sh");
            isAddondSupported = true;
        }

        // Lets acquire root first :)
        RootTools.useRoot = true;
        if (!RootTools.isRootAvailable()) return false;
        if (!RootTools.isAccessGiven()) return false;
        // When done, a root shell was opened

        // Check chmod utils
        String mChmod = "";
        if (RootTools.checkUtil("chmod"))
            mChmod = "chmod";
        else {
            if (RootTools.checkUtil("busybox") && RootTools.hasUtil("chmod", "busybox"))
                mChmod = "busybox chmod";
            else {
                if (RootTools.checkUtil("toolbox") && RootTools.hasUtil("chmod", "toolbox"))
                    mChmod = "toolbox chmod";
            }
        }
        if (mChmod == null || mChmod.equals(""))
            return false;

        // Generate temp config file path, thanks to 'ste71m'
        String mSystemConf = StaticEnvironment.getESPath() + "v4a_audio_system.conf";
        String mVendorConf = StaticEnvironment.getESPath() + "v4a_audio_vendor.conf";

        // Check vendor directory
        boolean vendorExists = false;
        if (fileExists("/system/vendor/etc/audio_effects.conf"))
            vendorExists = true;

        // Copy configuration to temp directory
        if (vendorExists) {
            /* Copy to external storage, we dont need remount */
            RootTools.copyFile("/system/etc/audio_effects.conf", mSystemConf, false, false);
            RootTools.copyFile("/system/vendor/etc/audio_effects.conf", mVendorConf, false, false);
        } else {
            /* Copy to external storage, we dont need remount */
            RootTools.copyFile("/system/etc/audio_effects.conf", mSystemConf, false, false);
        }

        // Modifing configuration
        boolean modifyResult = true;
        modifyResult &= modifyFXConfig(mSystemConf, mSystemConf + ".out");
        if (vendorExists) modifyResult &= modifyFXConfig(mVendorConf, mVendorConf + ".out");
        if (!modifyResult) {
            /* Modify the configuration failed, lets cleanup temp file(s) */
            try {
                RootTools rtTools = new RootTools();
                if (vendorExists) {
                    if (!rtTools.deleteFileOrDirectory(mSystemConf, false))
                        new File(mSystemConf).delete();
                    if (!rtTools.deleteFileOrDirectory(mVendorConf, false))
                        new File(mVendorConf).delete();
                    if (!rtTools.deleteFileOrDirectory(mSystemConf + ".out", false))
                        new File(mSystemConf + ".out").delete();
                    if (!rtTools.deleteFileOrDirectory(mVendorConf + ".out", false))
                        new File(mVendorConf + ".out").delete();
                } else {
                    if (!rtTools.deleteFileOrDirectory(mSystemConf, false))
                        new File(mSystemConf).delete();
                    if (!rtTools.deleteFileOrDirectory(mSystemConf + ".out", false))
                        new File(mSystemConf + ".out").delete();
                }
                // Close all shells
                RootTools.closeAllShells();
                return false;
            } catch (Exception e) {
                return false;
            }
        }

        // Copy back to system
        boolean operationSuccess = true;
        String baseDrvPathName = getBasePath(ctx);
        String addondScriptPathName = baseDrvPathName;

        if (baseDrvPathName.endsWith("/")) {
            baseDrvPathName = baseDrvPathName + "libv4a_fx_ics.so";
            if (isAddondSupported) addondScriptPathName = addondScriptPathName + "91-v4a.sh";
        } else {
            baseDrvPathName = baseDrvPathName + "/libv4a_fx_ics.so";
            if (isAddondSupported) addondScriptPathName = addondScriptPathName + "/91-v4a.sh";
        }
        try {
            if (vendorExists) {
                // Copy files
                operationSuccess &= RootTools.remount("/system", "RW");
                if (operationSuccess)
                    operationSuccess &= RootTools.copyFile(baseDrvPathName,
                            "/system/lib/soundfx/libv4a_fx_ics.so", false,
                            false);
                if (operationSuccess)
                    operationSuccess &= RootTools.copyFile(
                            mSystemConf + ".out",
                            "/system/etc/audio_effects.conf", false, false);
                if (operationSuccess)
                    operationSuccess &= RootTools.copyFile(
                            mVendorConf + ".out",
                            "/system/vendor/etc/audio_effects.conf", false,
                            false);
                if (operationSuccess && isAddondSupported)
                    operationSuccess = RootTools.copyFile(
                            addondScriptPathName, "/system/addon.d/91-v4a.sh",
                            false, false);
                // Modify permission
                CommandCapture ccSetPermission = new CommandCapture(0, mChmod
                        + " 644 /system/etc/audio_effects.conf", mChmod
                        + " 644 /system/vendor/etc/audio_effects.conf", mChmod
                        + " 644 /system/lib/soundfx/libv4a_fx_ics.so");
                RootTools.getShell(true).add(ccSetPermission).waitForFinish();

                // Modify permission of addon.d script if applicable
                if (isAddondSupported) {
                    CommandCapture ccSetAddondPermission = new CommandCapture(0, mChmod
                             + " 644 /system/addon.d/91-v4a.sh");
                    RootTools.getShell(true).add(ccSetAddondPermission).waitForFinish();
                }

                RootTools.remount("/system", "RO");
            } else {
                // Copy files
                operationSuccess &= RootTools.remount("/system", "RW");
                if (operationSuccess)
                    operationSuccess &= RootTools.copyFile(baseDrvPathName,
                            "/system/lib/soundfx/libv4a_fx_ics.so", false,
                            false);
                if (operationSuccess)
                    operationSuccess &= RootTools.copyFile(
                            mSystemConf + ".out",
                            "/system/etc/audio_effects.conf", false, false);
                if (operationSuccess && isAddondSupported)
                    operationSuccess = RootTools.copyFile(
                            addondScriptPathName, "/system/addon.d/91-v4a.sh",
                            false, false);

                // Modify permission
                CommandCapture ccSetPermission = new CommandCapture(0, mChmod
                        + " 644 /system/etc/audio_effects.conf", mChmod
                        + " 644 /system/lib/soundfx/libv4a_fx_ics.so");
                RootTools.getShell(true).add(ccSetPermission).waitForFinish();

                // Modify permission of addon.d script if applicable
                if (isAddondSupported) {
                    CommandCapture ccSetAddondPermission = new CommandCapture(0, mChmod
                            + " 644 /system/addon.d/91-v4a.sh");
                    RootTools.getShell(true).add(ccSetAddondPermission).waitForFinish();
                }

                RootTools.remount("/system", "RO");
            }
        } catch (Exception e) {
            operationSuccess = false;
        }

        /* Cleanup temp file(s) and close root shell */
        try {
            RootTools rtTools = new RootTools();
            if (vendorExists) {
                if (!rtTools.deleteFileOrDirectory(mSystemConf, false))
                    new File(mSystemConf).delete();
                if (!rtTools.deleteFileOrDirectory(mVendorConf, false))
                    new File(mVendorConf).delete();
                if (!rtTools.deleteFileOrDirectory(mSystemConf + ".out", false))
                    new File(mSystemConf + ".out").delete();
                if (!rtTools.deleteFileOrDirectory(mVendorConf + ".out", false))
                    new File(mVendorConf + ".out").delete();
            } else {
                if (!rtTools.deleteFileOrDirectory(mSystemConf, false))
                    new File(mSystemConf).delete();
                if (!rtTools.deleteFileOrDirectory(mSystemConf + ".out", false))
                    new File(mSystemConf + ".out").delete();
            }
            // Close all shells
            RootTools.closeAllShells();
        } catch (Exception e) {
            return false;
        }

        return operationSuccess;
    }

    // Install ViPER4Android FX driver through vbox
    private static boolean installDrv_FX_VBoX(Context ctx, String mDriverName) {

        boolean isAddondSupported = false;

        // Make sure we can use external storage for temp directory
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED))
            return false;

        // Copy driver assets to local
        if (!copyAssetsToLocal(ctx, mDriverName, "libv4a_fx_ics.so"))
            return false;

        //Check if addon.d directory exists and copy script if supported
        if (AddondExists()) {
            copyAssetsToLocal(ctx, "91-v4a.sh", "91-v4a.sh");
            isAddondSupported = true;
        }

        // Open root shell
        String vBox = StaticEnvironment.getVBoxExecutablePath();
        if (!ShellCommand.openRootShell(true))
            return false;

        // Generate temp config file path, thanks to 'ste71m'
        String mSystemConf = StaticEnvironment.getESPath() + "v4a_audio_system.conf";
        String mVendorConf = StaticEnvironment.getESPath() + "v4a_audio_vendor.conf";

        // Check vendor directory
        boolean vendorExists = false;
        if (fileExists("/system/vendor/etc/audio_effects.conf"))
            vendorExists = true;

        // Copy configuration to temp directory
        if (vendorExists) {
            /* Copy to external storage */
            ShellCommand.sendShellCommand(vBox
                    + " cp /system/etc/audio_effects.conf " + mSystemConf, 1.0f);
            Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
            ShellCommand.sendShellCommand(vBox
                    + " cp /system/vendor/etc/audio_effects.conf "
                    + mVendorConf, 1.0f);
            Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
        } else {
            /* Copy to external storage */
            ShellCommand.sendShellCommand(vBox
                    + " cp /system/etc/audio_effects.conf " + mSystemConf, 1.0f);
            Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
        }

        // Modifing configuration
        boolean modifyResult = true;
        modifyResult &= modifyFXConfig(mSystemConf, mSystemConf + ".out");
        if (vendorExists)
            modifyResult &= modifyFXConfig(mVendorConf, mVendorConf + ".out");
        if (!modifyResult) {
            /* Modify the configuration failed, lets cleanup temp file(s) */
            if (vendorExists) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
            } else {
                new File(mSystemConf).delete();
                new File(mSystemConf + ".out").delete();
            }
            // Close shell
            ShellCommand.closeShell();
            return false;
        }

        // Copy back to system
        String baseDrvPathName = getBasePath(ctx);
        String addondScriptPathName = baseDrvPathName;

        if (baseDrvPathName.endsWith("/")) {
            baseDrvPathName = baseDrvPathName + "libv4a_fx_ics.so";
            if (isAddondSupported) addondScriptPathName = addondScriptPathName + "91-v4a.sh";
        } else {
            baseDrvPathName = baseDrvPathName + "/libv4a_fx_ics.so";
            if (isAddondSupported) addondScriptPathName = addondScriptPathName + "/91-v4a.sh";
        }
        int mShellCmdReturn; boolean success;
        if (vendorExists) {
            // Copy files
            success = ShellCommand.sendShellCommand(vBox + " mount -o remount,rw /system", 5.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot remount /system");
                ShellCommand.closeShell();
                return false;
            }
            ShellCommand.sendShellCommand(vBox + " rm /system/lib/soundfx/libv4a_fx_ics.so", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            Log.i("ViPER4Android", "Command return = " + mShellCmdReturn);
            success = ShellCommand.sendShellCommand(vBox + " cp " + baseDrvPathName + " /system/lib/soundfx/libv4a_fx_ics.so", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot copy V4A driver to /system");
                ShellCommand.closeShell();
                return false;
            }

            if (isAddondSupported) {
                success = ShellCommand.sendShellCommand(vBox + " cp " + addondScriptPathName + " /system/addon.d/91-v4a.sh", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
                if (!success || (mShellCmdReturn != 0)) {
                    Log.e("ViPER4Android", "Cannot copy addon.d script to /system/addon.d/");
                    // NO RETURN FALSE OR CLOSESHELL - addon.d script failure should not stop v4a from installing
                }
            }

            success = ShellCommand.sendShellCommand(vBox + " cp " + mSystemConf + ".out" + " /system/etc/audio_effects.conf", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot copy audio config to /system");
                ShellCommand.closeShell();
                return false;
            }
            success = ShellCommand.sendShellCommand(vBox + " cp " + mVendorConf + ".out" + " /system/vendor/etc/audio_effects.conf", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot copy audio config to /system/vendor");
                ShellCommand.closeShell();
                return false;
            }
            // Modify permission
            success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/etc/audio_effects.conf", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot change config's permission [/system]");
                ShellCommand.closeShell();
                return false;
            }
            success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/vendor/etc/audio_effects.conf", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot change config's permission [/system/vendor]");
                ShellCommand.closeShell();
                return false;
            }
            if (isAddondSupported) {
                success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/addon.d/91-v4a.sh", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
                if (!success || (mShellCmdReturn != 0)) {
                    Log.e("ViPER4Android", "Cannot change addon.d script permission [/system/addon.d]");
                    // NO RETURN FALSE OR CLOSESHELL - addon.d script failure should not stop v4a from installing
                }
            }
            success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/lib/soundfx/libv4a_fx_ics.so", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mVendorConf).delete();
                new File(mSystemConf + ".out").delete();
                new File(mVendorConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot change driver's permission");
                ShellCommand.closeShell();
                return false;
            }
            ShellCommand.sendShellCommand(vBox + " sync", 5.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            Log.i("ViPER4Android", "Command return = " + mShellCmdReturn);
            ShellCommand.sendShellCommand(vBox + " mount -o remount,ro /system", 5.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            Log.i("ViPER4Android", "Command return = " + mShellCmdReturn);
        } else {
            // Copy files
            success = ShellCommand.sendShellCommand(vBox + " mount -o remount,rw /system", 5.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mSystemConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot remount /system");
                ShellCommand.closeShell();
                return false;
            }
            ShellCommand.sendShellCommand(vBox + " rm /system/lib/soundfx/libv4a_fx_ics.so", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            Log.i("ViPER4Android", "Command return = " + mShellCmdReturn);
            success = ShellCommand.sendShellCommand(vBox + " cp " + baseDrvPathName + " /system/lib/soundfx/libv4a_fx_ics.so", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mSystemConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot copy V4A driver to /system");
                ShellCommand.closeShell();
                return false;
            }
            if (isAddondSupported) {
                success = ShellCommand.sendShellCommand(vBox + " cp " + addondScriptPathName + " /system/addon.d/91-v4a.sh", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
                if (!success || (mShellCmdReturn != 0)) {
                    Log.e("ViPER4Android", "Cannot copy addon.d script to /system/addon.d/");
                    // NO RETURN FALSE OR CLOSESHELL - addon.d script failure should not stop v4a from installing
                }
            }
            success = ShellCommand.sendShellCommand(vBox + " cp " + mSystemConf + ".out" + " /system/etc/audio_effects.conf", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mSystemConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot copy audio config to /system");
                ShellCommand.closeShell();
                return false;
            }
            // Modify permission
            success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/etc/audio_effects.conf", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mSystemConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot change config's permission [/system]");
                ShellCommand.closeShell();
                return false;
            }
            if (isAddondSupported) {
                success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/addon.d/91-v4a.sh", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
                if (!success || (mShellCmdReturn != 0)) {
                    Log.e("ViPER4Android", "Cannot change addon.d script permission [/system/addon.d]");
                    // NO RETURN FALSE OR CLOSESHELL - addon.d script failure should not stop v4a from installing
                }
            }
            success = ShellCommand.sendShellCommand(vBox + " chmod 644 /system/lib/soundfx/libv4a_fx_ics.so", 1.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            if (!success || mShellCmdReturn != 0) {
                new File(mSystemConf).delete();
                new File(mSystemConf + ".out").delete();
                Log.e("ViPER4Android", "Cannot change driver's permission");
                ShellCommand.closeShell();
                return false;
            }
            ShellCommand.sendShellCommand(vBox + " sync", 5.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
            Log.i("ViPER4Android", "Command return = " + mShellCmdReturn);
           	ShellCommand.sendShellCommand(vBox + " mount -o remount,ro /system", 5.0f); mShellCmdReturn = ShellCommand.getLastReturnValue();
           	Log.i("ViPER4Android", "Command return = " + mShellCmdReturn);
        }

        /* Cleanup temp file(s) and close root shell */
        if (vendorExists) {
            new File(mSystemConf).delete();
            new File(mVendorConf).delete();
            new File(mSystemConf + ".out").delete();
            new File(mVendorConf + ".out").delete();
        } else {
            new File(mSystemConf).delete();
            new File(mSystemConf + ".out").delete();
        }
        // Close shell
        ShellCommand.closeShell();

        return fileExists("/system/lib/soundfx/libv4a_fx_ics.so");
    }

    // Install ViPER4Android FX driver
    public static boolean installDrv_FX(Context ctx, String mDriverName) {
        if (!StaticEnvironment.getVBoxUsable())
            return installDrv_FX_RootTools(ctx, mDriverName);
        else
            return installDrv_FX_VBoX(ctx, mDriverName);
    }

    /**
     * Restart the activity smoothly
     *
     * @param activity
     */
    public static void restartActivity(final Activity activity) {
        if (activity == null)
            return;
        final int enter_anim = android.R.anim.fade_in;
        final int exit_anim = android.R.anim.fade_out;
        activity.overridePendingTransition(enter_anim, exit_anim);
        activity.finish();
        activity.overridePendingTransition(enter_anim, exit_anim);
        activity.startActivity(activity.getIntent());
    }
}
