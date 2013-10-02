package com.vipercn.viper4android.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.stericson.RootTools.RootTools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;


public class Utils {
    protected static final String TAG = "ViPER4Android";

    // Check if the specified file exists.
    public static boolean fileExists(String filename) {
        boolean mExists = new File(filename).exists();
        if (!mExists) {
            RootTools.useRoot = true;
            mExists = RootTools.exists(filename);
        }
        return mExists;
    }

    // Get a file length
    public static long getFileLength(String mFileName) {
        try {
            return new File(mFileName).length();
        } catch (Exception e) {
            return 0;
        }
    }

    // Download a file from internet
    public static boolean downloadFile(String mURL, String mFileName, String mStorePath) {
        try {
            URL url = new URL(mURL);
            URLConnection urlConnection = url.openConnection();
            urlConnection.connect();
            InputStream is = urlConnection.getInputStream();
            if (urlConnection.getContentLength() <= 0) return false;
            if (is == null) return false;
            FileOutputStream fos = new FileOutputStream(mStorePath + mFileName);

            byte buf[] = new byte[1024];
            do {
                int numread = is.read(buf);
                if (numread == -1) break;
                fos.write(buf, 0, numread);
            } while (true);
            is.close();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Check a file with checksum
    public static boolean fileChecksum(String mFilePathName, String mChecksum) {
        long lChecksum = 0;

        try {
            FileInputStream fis = new FileInputStream(mFilePathName);
            byte buf[] = new byte[1024];
            do {
                int numread = fis.read(buf);
                if (numread == -1) break;
                for (int idx = 0; idx < numread; idx++)
                    lChecksum = lChecksum + (long) (buf[idx]);
            } while (true);
            fis.close();
            String mNewChecksum = Long.toString(lChecksum);
            if (mChecksum.equals(mNewChecksum)) return true;
            else return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Read file list from path
    public static void getFileNameList(File path, String fileExt, ArrayList<String> fileList) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (null == files) return;
            for (File file : files) getFileNameList(file, fileExt, fileList);
        } else {
            String filePath = path.getAbsolutePath();
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
            if (fileName.toLowerCase().endsWith(fileExt))
                fileList.add(fileName);
        }
    }

    // Get profile name from a file
    public static String getProfileName(String mProfileFileName) {
        try {
            FileInputStream fisInput = new FileInputStream(mProfileFileName);
            InputStreamReader isrInput = new InputStreamReader(fisInput, "UTF-8");
            BufferedReader brInput = new BufferedReader(isrInput);
            String mProfileName = "";
            while (true) {
                String mLine = brInput.readLine();
                if (mLine == null) break;
                if (mLine.startsWith("#")) continue;

                String mChunks[] = mLine.split("=");
                if (mChunks.length != 2) continue;
                if (mChunks[0].trim().toLowerCase().equals("profile_name")) {
                    mProfileName = mChunks[1];
                    break;
                }
            }
            brInput.close();
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
            File mProfileDirHandle = new File(mProfileDir);
            ArrayList<String> mProfileList = new ArrayList<String>();
            getFileNameList(mProfileDirHandle, ".prf", mProfileList);

            ArrayList<String> mProfileNameList = new ArrayList<String>();
            for (String mProfList : mProfileList) {
                String mFileName = mProfileDir + mProfList;
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
            File mProfileDirHandle = new File(mProfileDir);
            ArrayList<String> mProfileList = new ArrayList<String>();
            getFileNameList(mProfileDirHandle, ".prf", mProfileList);

            boolean bFoundProfile = false;
            for (String profileList : mProfileList) {
                String mFileName = mProfileDir + profileList;
                String mName = getProfileName(mFileName);
                if (mProfileName.trim().toUpperCase().equals(mName.trim().toUpperCase())) {
                    bFoundProfile = true;
                    break;
                }
            }

            return bFoundProfile;
        } catch (Exception e) {
            return false;
        }
    }

    // Load profile from file
    public static boolean loadProfile(String mProfileName, String mProfileDir, String mPreferenceName, Context ctx) {
        try {
            File mProfileDirHandle = new File(mProfileDir);
            ArrayList<String> mProfileFileList = new ArrayList<String>();
            getFileNameList(mProfileDirHandle, ".prf", mProfileFileList);
            String mProfileFileName = "";
            for (String profileFileList : mProfileFileList) {
                String mFileName = mProfileDir + profileFileList;
                String mName = getProfileName(mFileName);
                if (mProfileName.trim().toUpperCase().equals(mName.trim().toUpperCase())) {
                    mProfileFileName = mFileName;
                    break;
                }
            }
            if (mProfileFileName.equals("")) return false;

            SharedPreferences prefs = ctx.getSharedPreferences(mPreferenceName, Context.MODE_PRIVATE);
            if (prefs != null) {
                FileInputStream fisInput = new FileInputStream(mProfileFileName);
                InputStreamReader isrInput = new InputStreamReader(fisInput, "UTF-8");
                BufferedReader brInput = new BufferedReader(isrInput);
                Editor e = prefs.edit();
                while (true) {
                    String mLine = brInput.readLine();
                    if (mLine == null) break;
                    if (mLine.startsWith("#")) continue;

                    String mChunks[] = mLine.split("=");
                    if (mChunks.length != 3) continue;
                    if (mChunks[1].trim().toLowerCase().equals("boolean")) {
                        String mParameter = mChunks[0];
                        boolean bValue = Boolean.valueOf(mChunks[2]);
                        e.putBoolean(mParameter, bValue);
                    } else if (mChunks[1].trim().toLowerCase().equals("string")) {
                        String mParameter = mChunks[0];
                        String mValue = mChunks[2];
                        e.putString(mParameter, mValue);
                    } else continue;
                }
                e.commit();
                brInput.close();
                isrInput.close();
                fisInput.close();

                return true;
            } else return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Save profile to file
    public static void saveProfile(String mProfileName, String mProfileDir, String mPreferenceName, Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(mPreferenceName, Context.MODE_PRIVATE);
            if (prefs != null) {
                String mOutFileName = mProfileDir + mProfileName + ".prf";
                if (fileExists(mOutFileName)) new File(mOutFileName).delete();

                FileOutputStream fosOutput = new FileOutputStream(mOutFileName);
                OutputStreamWriter oswOutput = new OutputStreamWriter(fosOutput, "UTF-8");
                BufferedWriter bwOutput = new BufferedWriter(oswOutput);

                SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd   hh:mm:ss");
                String mDate = sDateFormat.format(new Date());

                bwOutput.write("# ViPER4Android audio effect profile !\n");
                bwOutput.write("# Created " + mDate + "\n\n");
                bwOutput.write("profile_name=" + mProfileName + "\n\n");

                String mValue;
                mValue = "";

                // boolean values
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.enable", false));
                bwOutput.write("viper4android.headphonefx.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.speakerfx.enable", false));
                bwOutput.write("viper4android.speakerfx.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.playbackgain.enable", false));
                bwOutput.write("viper4android.headphonefx.playbackgain.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.fireq.enable", false));
                bwOutput.write("viper4android.headphonefx.fireq.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.convolver.enable", false));
                bwOutput.write("viper4android.headphonefx.convolver.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.colorfulmusic.enable", false));
                bwOutput.write("viper4android.headphonefx.colorfulmusic.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.diffsurr.enable", false));
                bwOutput.write("viper4android.headphonefx.diffsurr.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.vhs.enable", false));
                bwOutput.write("viper4android.headphonefx.vhs.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.reverb.enable", false));
                bwOutput.write("viper4android.headphonefx.reverb.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.dynamicsystem.enable", false));
                bwOutput.write("viper4android.headphonefx.dynamicsystem.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.dynamicsystem.tube", false));
                bwOutput.write("viper4android.headphonefx.dynamicsystem.tube=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.fidelity.bass.enable", false));
                bwOutput.write("viper4android.headphonefx.fidelity.bass.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.fidelity.clarity.enable", false));
                bwOutput.write("viper4android.headphonefx.fidelity.clarity.enable=boolean=" + mValue + "\n");
                mValue = String.valueOf(prefs.getBoolean("viper4android.headphonefx.cure.enable", false));
                bwOutput.write("viper4android.headphonefx.cure.enable=boolean=" + mValue + "\n");

                // string values
                mValue = prefs.getString("viper4android.headphonefx.playbackgain.ratio", "50");
                bwOutput.write("viper4android.headphonefx.playbackgain.ratio=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.playbackgain.maxscaler", "400");
                bwOutput.write("viper4android.headphonefx.playbackgain.maxscaler=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.playbackgain.volume", "80");
                bwOutput.write("viper4android.headphonefx.playbackgain.volume=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fireq", "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;");
                bwOutput.write("viper4android.headphonefx.fireq=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fireq.custom", "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;");
                bwOutput.write("viper4android.headphonefx.fireq.custom=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.convolver.kernel", "");
                bwOutput.write("viper4android.headphonefx.convolver.kernel=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.colorfulmusic.coeffs", "120;200");
                bwOutput.write("viper4android.headphonefx.colorfulmusic.coeffs=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.colorfulmusic.midimage", "150");
                bwOutput.write("viper4android.headphonefx.colorfulmusic.midimage=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.diffsurr.delay", "500");
                bwOutput.write("viper4android.headphonefx.diffsurr.delay=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.vhs.qual", "0");
                bwOutput.write("viper4android.headphonefx.vhs.qual=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.reverb.roomsize", "0");
                bwOutput.write("viper4android.headphonefx.reverb.roomsize=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.reverb.roomwidth", "0");
                bwOutput.write("viper4android.headphonefx.reverb.roomwidth=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.reverb.damp", "0");
                bwOutput.write("viper4android.headphonefx.reverb.damp=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.reverb.wet", "0");
                bwOutput.write("viper4android.headphonefx.reverb.wet=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.reverb.dry", "50");
                bwOutput.write("viper4android.headphonefx.reverb.dry=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.dynamicsystem.coeffs", "100;5600;40;80;50;50");
                bwOutput.write("viper4android.headphonefx.dynamicsystem.coeffs=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.dynamicsystem.bass", "0");
                bwOutput.write("viper4android.headphonefx.dynamicsystem.bass=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fidelity.bass.mode", "0");
                bwOutput.write("viper4android.headphonefx.fidelity.bass.mode=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fidelity.bass.freq", "40");
                bwOutput.write("viper4android.headphonefx.fidelity.bass.freq=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fidelity.bass.gain", "50");
                bwOutput.write("viper4android.headphonefx.fidelity.bass.gain=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fidelity.clarity.mode", "0");
                bwOutput.write("viper4android.headphonefx.fidelity.clarity.mode=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.fidelity.clarity.gain", "50");
                bwOutput.write("viper4android.headphonefx.fidelity.clarity.gain=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.cure.crossfeed", "0");
                bwOutput.write("viper4android.headphonefx.cure.crossfeed=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.outvol", "100");
                bwOutput.write("viper4android.headphonefx.outvol=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.channelpan", "0");
                bwOutput.write("viper4android.headphonefx.channelpan=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.headphonefx.limiter", "100");
                bwOutput.write("viper4android.headphonefx.limiter=string=" + mValue + "\n");
                mValue = prefs.getString("viper4android.speakerfx.limiter", "100");
                bwOutput.write("viper4android.speakerfx.limiter=string=" + mValue + "\n");

                bwOutput.flush();
                bwOutput.close();
                oswOutput.close();
                fosOutput.close();
            }
        } catch (Exception e) {
            return;
        }
    }

    // Run root command
    public static boolean runRootCommand(String mCommand) {
        Process mProcess = null;
        DataOutputStream mOutStream = null;
        try {
            mProcess = Runtime.getRuntime().exec("su");
            mOutStream = new DataOutputStream(mProcess.getOutputStream());
            mOutStream.writeBytes(mCommand + "\n");
            mOutStream.flush();
            Thread.sleep(100);
            mOutStream.writeBytes("exit\n");
            mOutStream.flush();
            mProcess.waitFor();
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (mOutStream != null) mOutStream.close();
                if (mProcess != null) mProcess.destroy();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    // Run root commands
    public static boolean runRootCommand(String[] mCommand, int nSleepTime) {
        Process mProcess = null;
        DataOutputStream mOutStream = null;
        try {
            mProcess = Runtime.getRuntime().exec("su");
            mOutStream = new DataOutputStream(mProcess.getOutputStream());
            for (String command : mCommand) {
                mOutStream.writeBytes(command + "\n");
                mOutStream.flush();
                Thread.sleep(nSleepTime);
            }
            mOutStream.writeBytes("exit\n");
            mOutStream.flush();
            mProcess.waitFor();
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (mOutStream != null) mOutStream.close();
                if (mProcess != null) mProcess.destroy();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    // Acquire root permission
    public static boolean acquireRoot() {
        RootTools.useRoot = true;
        if (!RootTools.isRootAvailable()) return false;
        if (!RootTools.isAccessGiven()) return false;
        return true;
    }

    // Find toolbox
    public static String getToolbox(String mName) {
        try {
            Log.i("ViPER4Android_Utils", "Searching toolbox " + mName + " ...");
            if (fileExists("/system/bin/" + mName)) {
                Log.i("ViPER4Android_Utils", "Found /system/bin/" + mName);
                return "/system/bin/" + mName;
            } else if (fileExists("/system/xbin/" + mName)) {
                Log.i("ViPER4Android_Utils", "Found /system/xbin/" + mName);
                return "/system/xbin/" + mName;
            } else if (fileExists("/bin/" + mName)) {
                Log.i("ViPER4Android_Utils", "Found /bin/" + mName);
                return "/bin/" + mName;
            } else if (fileExists("/xbin/" + mName)) {
                Log.i("ViPER4Android_Utils", "Found /xbin/" + mName);
                return "/xbin/" + mName;
            } else if (fileExists("/sbin/" + mName)) {
                Log.i("ViPER4Android_Utils", "Found /sbin/" + mName);
                return "/sbin/" + mName;
            } else {
                Log.i("ViPER4Android_Utils", "Toolbox " + mName + " not found!");
                return "";
            }
        } catch (Exception e) {
            Log.i("ViPER4Android_Utils", "Error: " + e.getMessage());
            return "";
        }
    }

    // Perform toolbox test
    public static boolean performToolboxTest(String mToolbox, boolean bUseCopyCmd) {
        String mToolboxPath = getToolbox(mToolbox);
        if (mToolboxPath == null) return false;
        if (mToolboxPath.equals("")) return false;

        Log.i("ViPER4Android_Utils", "Performing toolbox test, toolbox = " + mToolboxPath);

        Random rndMachine = new Random();
        String mTestFilename = "test_" + rndMachine.nextInt(65536) + ".rnd";
        String mSysTestFilename = "/system/" + mTestFilename;
        String mDataTestFilename = "/data/" + mTestFilename;

        String mCommandList[] = new String[7];
        mCommandList[0] = mToolboxPath + " mount -o remount,rw /system /system";
        mCommandList[1] = mToolboxPath + " echo test > " + mSysTestFilename;
        mCommandList[2] = mToolboxPath + " sync";

        if (bUseCopyCmd)
            mCommandList[3] = mToolboxPath + " cp " + mSysTestFilename + " " + mDataTestFilename;
        else
            mCommandList[3] = mToolboxPath + " dd if=" + mSysTestFilename + " of=" + mDataTestFilename;

        mCommandList[4] = mToolboxPath + " rm " + mSysTestFilename;
        mCommandList[5] = mToolboxPath + " sync";
        mCommandList[6] = mToolboxPath + " mount -o remount,ro /system /system";
        runRootCommand(mCommandList, 200);

        if (fileExists(mSysTestFilename)) {
            Log.i("ViPER4Android_Utils", "Toolbox \"" + mToolboxPath + "\" test failed, remove function failure.");
            return false;
        }
        if (!fileExists(mDataTestFilename)) {
            Log.i("ViPER4Android_Utils", "Toolbox \"" + mToolboxPath + "\" test failed, mount or echo or cp function failure.");
            return false;
        }

        runRootCommand(mToolboxPath + " rm " + mDataTestFilename);
        Log.i("ViPER4Android_Utils", "Toolbox \"" + mToolboxPath + "\" test succeed.");

        return true;
    }

    // Get toolbox pathname in preference
    public static String getSavedToolbox(String mPreferenceName, Context ctx) {
        SharedPreferences preferences = ctx.getSharedPreferences(mPreferenceName, Context.MODE_PRIVATE);
        if (preferences == null) return "";
        String mToolbox = preferences.getString("viper4android.settings.toolbox", "none");
        if (mToolbox == null) return "";
        if (mToolbox.equals("")) return "";
        if (mToolbox.equals("none")) return "";
        return mToolbox;
    }

    // Make a copy command line
    public static String makeCopyCmdLine(String mPreferenceName, Context ctx, String mSrcFile, String mDstFile) {
        String mToolbox = getSavedToolbox(mPreferenceName, ctx);
        if (mToolbox.equals("")) return "";

        SharedPreferences preferences = ctx.getSharedPreferences(mPreferenceName, Context.MODE_PRIVATE);
        if (preferences == null) return "";
        String mCopy = preferences.getString("viper4android.settings.copycmd", "dd");
        if (mCopy == null) return "";
        if (mCopy.equals("")) return "";

        if (mCopy.equalsIgnoreCase("dd")) {
            return mToolbox + " dd if=" + mSrcFile + " of=" + mDstFile;
        } else if (mCopy.equalsIgnoreCase("cp")) {
            return mToolbox + " cp " + mSrcFile + " " + mDstFile;
        } else return "";
    }

    // Create a signal in /data/
    public static boolean createSignal(String mSignal, String mPreferenceName, Context ctx) {
        String mToolbox = getSavedToolbox(mPreferenceName, ctx);
        if (mToolbox.equals("")) return false;

        String mTouch = mToolbox + " touch";
        String mChmod = mToolbox + " chmod";
        String mSync = mToolbox + " sync";

        String mDestFile = "/data/" + mSignal;
        String mCommand[] = new String[3];
        mCommand[0] = mTouch + " " + mDestFile;
        mCommand[1] = mChmod + " 777 " + mDestFile;
        mCommand[2] = mSync;
        runRootCommand(mCommand, 100);

        return fileExists(mDestFile);
    }

    // Delete a singal in /data/
    public static boolean deleteSignal(String mSignal, String mPreferenceName, Context ctx) {
        String mToolbox = getSavedToolbox(mPreferenceName, ctx);
        if (mToolbox.equals("")) return false;

        String mRemove = mToolbox + " rm";
        String mSync = mToolbox + " sync";

        String mDestFile = "/data/" + mSignal;
        if (!fileExists(mDestFile)) return true;

        String mCommand[] = new String[2];
        mCommand[0] = mRemove + " " + mDestFile;
        mCommand[1] = mSync;
        runRootCommand(mCommand, 100);

        if (fileExists(szDestFile)) return false;
        return true;
    }

    // Check whether a signal is existed
    public static boolean checkSignal(String mSignal) {
        String mDestFile = "/data/" + mSignal;
        return fileExists(mDestFile);
    }

    // Modify audio_effects.conf
    public static boolean modifyFXConfig(String mInputFile, String mOutputFile) {
        Log.i("ViPER4Android_Utils", "Editing audio configuration, input = " + mInputFile + ", output = " + mOutputFile);
        try {
            long lInputFileLength = getFileLength(mInputFile);

            FileInputStream fisInput = new FileInputStream(mInputFile);
            FileOutputStream fosOutput = new FileOutputStream(mOutputFile);
            InputStreamReader isrInput = new InputStreamReader(fisInput, "ASCII");
            OutputStreamWriter oswOutput = new OutputStreamWriter(fosOutput, "ASCII");
            BufferedReader brInput = new BufferedReader(isrInput);
            BufferedWriter bwOutput = new BufferedWriter(oswOutput);

            boolean bConfigModified = false;
            brInput.mark((int) lInputFileLength);
            do {
                String mLine = brInput.readLine();
                if (mLine == null) break;
                if (mLine.contains("41d3c987-e6cf-11e3-a88a-11aba5d5c51b")) {
                    Log.i("ViPER4Android_Utils", "Source file has been modified, line = " + mLine);
                    bConfigModified = true;
                    break;
                }
            } while (true);

            if (bConfigModified) {
                brInput.reset();
                do {
                    String mLine = brInput.readLine();
                    if (mLine == null) break;
                    bwOutput.write(mLine + "\n");
                } while (true);
                bwOutput.flush();

                brInput.close();
                isrInput.close();
                bwOutput.close();
                oswOutput.close();

                return true;
            } else {
                brInput.reset();
                do {
                    String mLine = brInput.readLine();
                    if (mLine == null) break;
                    if (mLine.trim().equals("libraries {")) {
                        bwOutput.write(mLine + "\n");
                        bwOutput.write("  v4a_fx {\n");
                        bwOutput.write("    path /system/lib/soundfx/libv4a_fx_ics.so\n");
                        bwOutput.write("  }\n");
                    } else if (mLine.trim().equals("effects {")) {
                        bwOutput.write(mLine + "\n");
                        bwOutput.write("  v4a_standard_fx {\n");
                        bwOutput.write("    library v4a_fx\n");
                        bwOutput.write("    uuid 41d3c987-e6cf-11e3-a88a-11aba5d5c51b\n");
                        bwOutput.write("  }\n");
                    } else bwOutput.write(mLine + "\n");
                } while (true);
                bwOutput.flush();

                brInput.close();
                isrInput.close();
                bwOutput.close();
                oswOutput.close();

                return true;
            }
        } catch (Exception e) {
            Log.i("ViPER4Android_Utils", "Error: " + e.getMessage());
            return false;
        }
    }

    // Uninstall ViPER4Android FX driver
    public static void uninstallDrv_FX(String mPreferenceName, Context ctx) {
        String mToolbox = getSavedToolbox(mPreferenceName, ctx);
        if (mToolbox.equals("")) return;

        String mMount = mToolbox + " mount";
        String mRemove = mToolbox + " rm";
        String mSync = mToolbox + " sync";

        String mCmdLine[] = new String[4];
        mCmdLine[0] = mMount + " -o remount,rw /system /system";
        mCmdLine[1] = mRemove + " /system/lib/soundfx/libv4a_fx_ics.so";
        mCmdLine[2] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
        mCmdLine[3] = mMount + " -o remount,ro /system /system";
        runRootCommand(mCmdLine, 200);
    }

    // Get application data path
    private static String getBasePath(Context ctx) {
        Context cont = ctx.getApplicationContext();
        String mBasePath = null;
        if (cont != null) {
            mBasePath = cont.getFilesDir().toString();
        }
        if (!cont.getFilesDir().exists())
            if (!cont.getFilesDir().mkdir()) return "";
        return mBasePath;
    }

    // Copy assets to local
    private static boolean copyAssetsToLocal(Context ctx, String mSourceName, String mDstName) {
        String mBasePath = getBasePath(ctx);
        if (mBasePath.equals("")) return false;
        mDstName = mBasePath + "/" + mDstName;

        InputStream myInput = null;
        OutputStream myOutput = null;
        String outFileName = mDstName;
        try {
            File hfOutput = new File(mDstName);
            if (hfOutput.exists()) hfOutput.delete();

            myOutput = new FileOutputStream(outFileName);
            myInput = ctx.getAssets().open(mSourceName);
            byte[] tBuffer = new byte[1024];
            int nLength = 0;
            while ((nLength = myInput.read(tBuffer)) > 0)
                myOutput.write(tBuffer, 0, nLength);
            myOutput.flush();
            myInput.close();
            myInput = null;
            myOutput.close();
            myOutput = null;
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    // Generate a command line to copy a file
    public static String makeCopyCmdLine(String mDD, String mCopy, String mSource, String mDest) {
        if (mCopy.equals(""))
            return mDD + " if=" + mSource + " of=" + mDest;
        else return mCopy + " " + mSource + " " + mDest;
    }

    // Install ViPER4Android FX driver
    public static boolean installDrv_FX(String mPreferenceName, Context ctx, String mDriverName) {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            return false;

        // Copy driver assets to local
        if (!copyAssetsToLocal(ctx, mDriverName, "libv4a_fx_ics.so"))
            return false;

        String mToolbox = getSavedToolbox(mPreferenceName, ctx);
        if (mToolbox.equals("")) return false;

        // Prepare commands
        String mMount = mToolbox + " mount";
        String mRemove = mToolbox + " rm";
        String mChmod = mToolbox + " chmod";
        String mSync = mToolbox + " sync";

        // Generate temp config file path, thanks to 'ste71m'
        String mExternalStoragePathName = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (Build.VERSION.SDK_INT >= 18) {
            if (mExternalStoragePathName.endsWith("/emulated/0"))
                mExternalStoragePathName = mExternalStoragePathName.replace("/emulated/0", "/emulated/legacy");
        }
        String mSystemConf = mExternalStoragePathName + "/v4a_audio_system.conf";
        String mVendorConf = mExternalStoragePathName + "/v4a_audio_vendor.conf";

        // Check vendor directory
        boolean vendorExists = false;
        if (fileExists("/system/vendor/etc/audio_effects.conf"))
            vendorExists = true;

        // Copy configuration to external storage
        if (vendorExists) {
            String mCopyConfToSD[] = new String[3];
            mCopyConfToSD[0] = makeCopyCmdLine(mPreferenceName, ctx, "/system/etc/audio_effects.conf", mSystemConf);
            mCopyConfToSD[1] = makeCopyCmdLine(mPreferenceName, ctx, "/system/vendor/etc/audio_effects.conf", mVendorConf);
            mCopyConfToSD[2] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            runRootCommand(mCopyConfToSD, 100);
        } else {
            String mCopyConfToSD[] = new String[2];
            mCopyConfToSD[0] = makeCopyCmdLine(mPreferenceName, ctx, "/system/etc/audio_effects.conf", mSystemConf);
            mCopyConfToSD[1] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            runRootCommand(mCopyConfToSD, 100);
        }

        // Modifing configuration
        boolean modifyResult = true;
        modifyResult &= modifyFXConfig(mSystemConf, mSystemConf + ".out");
        if (vendorExists)
            modifyResult &= modifyFXConfig(mVendorConf, mVendorConf + ".out");
        if (!modifyResult) {
            if (vendorExists) {
                String mRemoveTmpFiles[] = new String[5];
                mRemoveTmpFiles[0] = mRemove + " " + mSystemConf;
                mRemoveTmpFiles[1] = mRemove + " " + mSystemConf + ".out";
                mRemoveTmpFiles[2] = mRemove + " " + mVendorConf;
                mRemoveTmpFiles[3] = mRemove + " " + mVendorConf + ".out";
                mRemoveTmpFiles[4] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
                runRootCommand(mRemoveTmpFiles, 100);
            } else {
                String mRemoveTmpFiles[] = new String[3];
                mRemoveTmpFiles[0] = mRemove + " " + mSystemConf;
                mRemoveTmpFiles[1] = mRemove + " " + mSystemConf + ".out";
                mRemoveTmpFiles[2] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
                runRootCommand(mRemoveTmpFiles, 100);
            }
            return false;
        }

        // Copy back to system
        if (vendorExists) {
            String mBaseDrvPathName = getBasePath(ctx) + "/" + "libv4a_fx_ics.so";
            String mCopyToSystem[] = new String[10];
            mCopyToSystem[0] = mMount + " -o remount,rw /system /system";
            mCopyToSystem[1] = makeCopyCmdLine(mPreferenceName, ctx, mSystemConf + ".out", "/system/etc/audio_effects.conf");
            mCopyToSystem[2] = makeCopyCmdLine(mPreferenceName, ctx, mVendorConf + ".out", "/system/vendor/etc/audio_effects.conf");
            mCopyToSystem[3] = makeCopyCmdLine(mPreferenceName, ctx, mBaseDrvPathName, "/system/lib/soundfx/libv4a_fx_ics.so");
            mCopyToSystem[4] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            mCopyToSystem[5] = mChmod + " 644 /system/etc/audio_effects.conf";
            mCopyToSystem[6] = mChmod + " 644 /system/vendor/etc/audio_effects.conf";
            mCopyToSystem[7] = mChmod + " 644 /system/lib/soundfx/libv4a_fx_ics.so";
            mCopyToSystem[8] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            mCopyToSystem[9] = mMount + " -o remount,ro /system /system";
            runRootCommand(mCopyToSystem, 200);
        } else {
            String mBaseDrvPathName = getBasePath(ctx) + "/" + "libv4a_fx_ics.so";
            String mCopyToSystem[] = new String[8];
            mCopyToSystem[0] = mMount + " -o remount,rw /system /system";
            mCopyToSystem[1] = makeCopyCmdLine(mPreferenceName, ctx, mSystemConf + ".out", "/system/etc/audio_effects.conf");
            mCopyToSystem[2] = makeCopyCmdLine(mPreferenceName, ctx, mBaseDrvPathName, "/system/lib/soundfx/libv4a_fx_ics.so");
            mCopyToSystem[3] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            mCopyToSystem[4] = mChmod + " 644 /system/etc/audio_effects.conf";
            mCopyToSystem[5] = mChmod + " 644 /system/lib/soundfx/libv4a_fx_ics.so";
            mCopyToSystem[6] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            mCopyToSystem[7] = mMount + " -o remount,ro /system /system";
            runRootCommand(mCopyToSystem, 200);
        }

        // Delete temp file
        if (vendorExists) {
            String mRemoveTmpFiles[] = new String[5];
            mRemoveTmpFiles[0] = mRemove + " " + mSystemConf;
            mRemoveTmpFiles[1] = mRemove + " " + mSystemConf + ".out";
            mRemoveTmpFiles[2] = mRemove + " " + mVendorConf;
            mRemoveTmpFiles[3] = mRemove + " " + mVendorConf + ".out";
            mRemoveTmpFiles[4] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            runRootCommand(mRemoveTmpFiles, 100);
        } else {
            String mRemoveTmpFiles[] = new String[3];
            mRemoveTmpFiles[0] = mRemove + " " + mSystemConf;
            mRemoveTmpFiles[1] = mRemove + " " + mSystemConf + ".out";
            mRemoveTmpFiles[2] = mSync;  /* FIXME: do i need a 'sync' to flush io buffer ? */
            runRootCommand(mRemoveTmpFiles, 100);
        }

        // Check installation
        if (!fileExists("/system/lib/soundfx/libv4a_fx_ics.so"))
            return false;
        return true;
    }
}
