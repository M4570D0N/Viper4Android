package com.vipercn.viper4android_v2.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.CommandCapture;

public class StaticEnvironment {
    private static boolean m_bEnvironmentInited = false;

    private static boolean m_bVBoXPrepared = false;
    private static String m_szVBoXPath = "";

    private static String m_szExternalStoragePath = "";
    private static String m_szV4AESRoot = "";
    private static String m_szV4AESKernel = "";
    private static String m_szV4AESProfile = "";

    private static boolean m_bDriverInited = false;
    private static boolean m_bDriverIsOK = false;
    private static String m_szDriverVersion = "";

    private static boolean installVBox(Context ctx) {
        Log.i("ViPER4Android", "Installing vbox ...");

        // Copy vbox to local first
        Log.i("ViPER4Android", "Extracting vbox to local");
        if (!Utils.copyAssetsToLocal(ctx, "vbox", "vbox")) {
            Log.i("ViPER4Android", "Can not copy vbox to local dir");
            return false;
        }

        // Get vbox path
        String vBoxPath = Utils.getBasePath(ctx);
        if (vBoxPath.equals("")) {
            Log.i("ViPER4Android", "GetBasePath() failed");
            return false;
        }
        if (vBoxPath.endsWith("/"))
            vBoxPath = vBoxPath + "vbox";
        else
            vBoxPath = vBoxPath + "/vbox";
        Log.i("ViPER4Android", "vbox path = " + vBoxPath);

        boolean vBoxInstalled = false;

        // Try toolbox first
        Log.i("ViPER4Android", "Now install vbox with viper's method");
        {
            if (ShellCommand.openRootShell(true)) {
                /* Toolbox is a basic util for android system */
                /*
                 * Considering the fragmentation of android, cp and dd commands
                 * may not included in all roms
                 */
                /*
                 * But cat and chmod should included, otherwise android may
                 * malfunction
                 */
                /*
                 * NOTICE: toolbox has no command stdout echo, so I just wait
                 * 0.5 secs for executing the command
                 */
                boolean mResult = true;
                mResult = ShellCommand.sendShellCommand("toolbox cat "
                        + vBoxPath + " > /data/vbox", 0.5f);
                Log.i("ViPER4Android",
                        "Command return = " + ShellCommand.setLastReturnValue());
                mResult &= ShellCommand.sendShellCommand(
                        "toolbox chmod 777 /data/vbox", 0.5f);
                Log.i("ViPER4Android",
                        "Command return = " + ShellCommand.setLastReturnValue());
                /* I think the best way to check whether vbox was installed is
                 * execute it and check the exit value
                 */

                /* vbox has command stdout echo ( except dd and cat ) , so we
                 * can wait infinite here
                 */
                mResult &= ShellCommand.sendShellCommand("/data/vbox", 1.0f);
                int vBoxExitValue = ShellCommand.setLastReturnValue();
                /* If the shell failed to execute vbox, the return value will never equal 0 */
                if (mResult && (vBoxExitValue == 0)) {
                    Log.i("ViPER4Android", "Good, vbox installed");
                    vBoxInstalled = true;
                } else {
                    Log.i("ViPER4Android", "Bad, vbox install failed");
                    vBoxInstalled = false;
                }
                ShellCommand.closeShell();
            } else {
                Log.i("ViPER4Android", "Can't open root shell");
            }
        }
        if (vBoxInstalled)
            return true;

        // Try roottools
        Log.i("ViPER4Android", "Now install vbox with roottools");
        {
            RootTools.useRoot = true;
            RootTools.debugMode = true;

            if (!RootTools.isRootAvailable())
                return false;
            if (!RootTools.isAccessGiven())
                return false;

            if (!RootTools.copyFile(vBoxPath, "/data/vbox", false, false)) {
                try {
                    RootTools.closeAllShells();
                } catch (IOException e) {
                }
                Log.i("ViPER4Android", "Bad, vbox install failed");
                return false;
            }

            boolean bError = false;
            CommandCapture ccSetPermission = new CommandCapture(0,
                    "toolbox chmod 777 /data/vbox");
            try {
                RootTools.getShell(true).add(ccSetPermission).waitForFinish();
            } catch (InterruptedException e) {
                bError = true;
            } catch (IOException e) {
                bError = true;
            } catch (TimeoutException e) {
                bError = true;
            } catch (RootDeniedException e) {
                bError = true;
            }

            try {
                RootTools.closeAllShells();
            } catch (IOException e) {
            }

            if (bError) {
                Log.i("ViPER4Android", "Bad, vbox install failed");
                return false;
            }
        }
        Log.i("ViPER4Android", "Good, vbox installed");

        return true;
    }

    private static void proceedVBox(Context ctx) {
        // Check vbox
        Log.i("ViPER4Android", "Checking vbox");
        if (ShellCommand.executeWithoutShell("/data/vbox", null) == 0) {
            Log.i("ViPER4Android", "Good, vbox is ok");
            m_bVBoXPrepared = true;
            m_szVBoXPath = "/data/vbox";
            return;
        }

        // Install vbox
        if (installVBox(ctx)) {
            ShellCommand.closeShell();
            m_bVBoXPrepared = true;
            m_szVBoXPath = "/data/vbox";
            return;
        }
        ShellCommand.closeShell();
        m_bVBoXPrepared = false;
        m_szVBoXPath = "";
    }

    private static boolean checkWritable(String szFileName) {
        try {
            byte[] baBlank = new byte[16];
            FileOutputStream fosOutput = new FileOutputStream(szFileName);
            fosOutput.write(baBlank);
            fosOutput.flush();
            fosOutput.close();
            new File(szFileName).delete();
            return true;
        } catch (FileNotFoundException e) {
            Log.i("ViPER4Android",
                    "FileNotFoundException, msg = " + e.getMessage());
            return false;
        } catch (IOException e) {
            Log.i("ViPER4Android", "IOException, msg = " + e.getMessage());
            return false;
        }
    }

    private static void proceedExternalStoragePath() {
        // Get path
        String mExternalStoragePathName = Environment
                .getExternalStorageDirectory().getAbsolutePath();

        // Check writable
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            if (Build.VERSION.SDK_INT >= 18) {
                if (mExternalStoragePathName.endsWith("/emulated/0")
                        || mExternalStoragePathName.endsWith("/emulated/0/"))
                    mExternalStoragePathName = mExternalStoragePathName
                            .replace("/emulated/0", "/emulated/legacy");
            }
            if (mExternalStoragePathName.endsWith("/")) {
                m_szExternalStoragePath = mExternalStoragePathName;
            } else {
                m_szExternalStoragePath = mExternalStoragePathName + "/";
            }
            m_szV4AESRoot = m_szExternalStoragePath + "ViPER4Android/";
            m_szV4AESKernel = m_szV4AESRoot + "Kernel/";
            m_szV4AESProfile = m_szV4AESRoot + "Profile/";
        } else {
            boolean pathFromSDKIsWorking = false;
            boolean pathFromLegacyIsWorking = false;
            String mExtPath;
            {
                if (mExternalStoragePathName.endsWith("/"))
                    mExtPath = mExternalStoragePathName;
                else
                    mExtPath = mExternalStoragePathName + "/";
                mExtPath = mExtPath + "v4a_test_file";
                Log.i("ViPER4Android",
                        "Now checking for external storage writable, file = "
                                + mExtPath);
                if (checkWritable(mExtPath))
                    pathFromSDKIsWorking = true;
            }
            {
                if (mExternalStoragePathName.endsWith("/"))
                    mExtPath = mExternalStoragePathName;
                else
                    mExtPath = mExternalStoragePathName + "/";
                if (mExtPath.endsWith("/emulated/0/")) {
                    mExtPath = mExtPath.replace("/emulated/0/",
                            "/emulated/legacy/");
                    mExtPath = mExtPath + "v4a_test_file";
                    Log.i("ViPER4Android",
                            "Now checking for external storage writable, file = "
                                    + mExtPath);
                    if (checkWritable(mExtPath))
                        pathFromLegacyIsWorking = true;
                }
            }

            if (pathFromLegacyIsWorking) {
                mExternalStoragePathName = mExternalStoragePathName.replace(
                        "/emulated/0", "/emulated/legacy");
                if (mExternalStoragePathName.endsWith("/"))
                    m_szExternalStoragePath = mExternalStoragePathName;
                else
                    m_szExternalStoragePath = mExternalStoragePathName + "/";
                m_szV4AESRoot = m_szExternalStoragePath + "ViPER4Android/";
                m_szV4AESKernel = m_szV4AESRoot + "Kernel/";
                m_szV4AESProfile = m_szV4AESRoot + "Profile/";
                Log.i("ViPER4Android", "External storage path = "
                        + m_szExternalStoragePath);
                Log.i("ViPER4Android", "ViPER4Android root path = "
                        + m_szV4AESRoot);
                Log.i("ViPER4Android", "ViPER4Android kernel path = "
                        + m_szV4AESKernel);
                Log.i("ViPER4Android", "ViPER4Android profile path = "
                        + m_szV4AESProfile);
                return;
            }
            if (pathFromSDKIsWorking) {
                if (mExternalStoragePathName.endsWith("/"))
                    m_szExternalStoragePath = mExternalStoragePathName;
                else
                    m_szExternalStoragePath = mExternalStoragePathName + "/";
                m_szV4AESRoot = m_szExternalStoragePath + "ViPER4Android/";
                m_szV4AESKernel = m_szV4AESRoot + "Kernel/";
                m_szV4AESProfile = m_szV4AESRoot + "Profile/";
                Log.i("ViPER4Android", "External storage path = "
                        + m_szExternalStoragePath);
                Log.i("ViPER4Android", "ViPER4Android root path = "
                        + m_szV4AESRoot);
                Log.i("ViPER4Android", "ViPER4Android kernel path = "
                        + m_szV4AESKernel);
                Log.i("ViPER4Android", "ViPER4Android profile path = "
                        + m_szV4AESProfile);
                return;
            }

            Log.i("ViPER4Android",
                    "Really terrible thing, external storage detection failed, v4a may malfunctioned");
            if (mExternalStoragePathName.endsWith("/"))
                m_szExternalStoragePath = mExternalStoragePathName;
            else
                m_szExternalStoragePath = mExternalStoragePathName + "/";
            m_szV4AESRoot = m_szExternalStoragePath + "ViPER4Android/";
            m_szV4AESKernel = m_szV4AESRoot + "Kernel/";
            m_szV4AESProfile = m_szV4AESRoot + "Profile/";
        }
    }

    public static boolean isEnvironmentInited() {
        return m_bEnvironmentInited;
    }

    public static void initEnvironment(Context ctx) {
        if (m_bEnvironmentInited)
            return;
        proceedVBox(ctx);
        proceedExternalStoragePath();
        m_bEnvironmentInited = true;
    }

    public static boolean getVBoxUsable() {
        return m_bVBoXPrepared;
    }

    public static String getVBoxExecutablePath() {
        return m_szVBoXPath;
    }

    public static String getESPath() {
        return m_szExternalStoragePath;
    }

    public static String getV4ARootPath() {
        
        return m_szV4AESRoot;
    }

    public static String getV4AKernelPath() {
        return m_szV4AESKernel;
    }

    public static String getV4AProfilePath() {
        return m_szV4AESProfile;
    }

    /**********
     * Because android audio effect engine is native, so we use static method to
     * record status
     **********/
    public static void setDriverStatus(boolean mDriverIsOK,
            String mDriverVersion) {
        Log.i("ViPER4Android", "Got driver status");
        Log.i("ViPER4Android", "Static old = [" + m_bDriverIsOK + ", "
                + m_szDriverVersion + "]");
        Log.i("ViPER4Android", "Static new = [" + mDriverIsOK + ", "
                + mDriverVersion + "]");
        Log.i("ViPER4Android", "Current status = " + m_bDriverInited);
        if (m_bDriverInited) {
            if (!m_bDriverIsOK && mDriverIsOK)
                m_bDriverIsOK = true;
            if (m_szDriverVersion.equals("")
                    || m_szDriverVersion.equals("0.0.0.0"))
                m_szDriverVersion = mDriverVersion;
            return;
        }
        m_bDriverIsOK = mDriverIsOK;
        m_szDriverVersion = mDriverVersion;
        m_bDriverInited = true;
    }

    public static boolean driverInited() {
        return m_bDriverInited;
    }

    public static boolean driverIsUsable() {
        return m_bDriverIsOK;
    }

    public static String driverVersion() {
        if (m_szDriverVersion.equals(""))
            return "0.0.0.0";
        return m_szDriverVersion;
    }
    /*************************************************************************************************************/
}
