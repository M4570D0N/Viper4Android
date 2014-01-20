package com.vipercn.viper4android_v2.activity;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.stericson.RootTools.*;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.CommandCapture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class StaticEnvironment {

    private static boolean sEnvironmentInitialized;
    private static boolean sVboxPrepared;
    private static String sVboxExecPath = "";
    private static String sExternalStoragePath = "";
    private static String sV4aRoot = "";
    private static String sV4aKernelPath = "";
    private static String sV4aProfilePath = "";

    private static boolean installVBox(Context ctx) {
        Log.i("ViPER4Android", "Installing vbox ...");

        // Copy vbox to local first
        Log.i("ViPER4Android", "Extracting VBox to local");
        if (!Utils.copyAssetsToLocal(ctx, "vbox", "vbox")) {
            Log.i("ViPER4Android", "Can not copy VBox to local dir");
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
        Log.i("ViPER4Android", "Now install VBox with viper's method [toolbox]");
        {
            if (ShellCommand.openRootShell(true)) {
                /* Toolbox is a basic util for android system */
                /* Considering the fragmentation of android, cp and dd commands may not included in all roms */
                /* But cat and chmod should included, otherwise android may malfunction */
                /* NOTICE: toolbox has no command stdout echo, so I just wait 0.5 secs for executing the command */
                boolean mResult = true;
                mResult &= ShellCommand.sendShellCommand("toolbox cat " + vBoxPath + " > /data/vbox", 0.5f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                mResult &= ShellCommand.sendShellCommand("toolbox chmod 777 /data/vbox", 0.5f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                /* I think the best way to check whether vbox was installed is
                 * execute it and check the exit value
                 */
                /* vbox has command stdout echo (except dd and cat), so we can wait infinite here */
                mResult &= ShellCommand.sendShellCommand("/data/vbox", 1.0f);
                int vBoxExitValue = ShellCommand.getLastReturnValue();
                /* If the shell failed to execute vbox, the return value will never equal 0 */
                if (mResult && vBoxExitValue == 0) {
                    Log.i("ViPER4Android", "Good, VBox installed");
                    vBoxInstalled = true;
                } else {
                    Log.i("ViPER4Android", "Bad, VBox install failed");
                    vBoxInstalled = false;
                }
                ShellCommand.closeShell();
            } else {
                Log.i("ViPER4Android", "Can't open root shell");
            }
        }
        if (vBoxInstalled)
            return true;

        // Try busybox
        Log.i("ViPER4Android", "Now install vbox with viper's method [busybox]");
        {
            if (ShellCommand.openRootShell(true)) {
                boolean mResult = true;
                mResult &= ShellCommand.sendShellCommand("busybox cat " + vBoxPath + " > /data/vbox", 0.5f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                mResult &= ShellCommand.sendShellCommand("busybox chmod 777 /data/vbox", 0.5f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                /* I think the best way to check wether vbox was installed is execute it and check the exit value */
                mResult &= ShellCommand.sendShellCommand("/data/vbox", 1.0f);  /* vbox has command stdout echo (except dd and cat), so we can wait infinite here */
                int vBoxExitValue = ShellCommand.getLastReturnValue();  /* If the shell failed to execute vbox, the return value will never equal 0 */
                if (mResult && vBoxExitValue == 0) {
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

        // Try direct
        Log.i("ViPER4Android", "Now install vbox with viper's method [direct]");
        {
            if (ShellCommand.openRootShell(true)) {
                boolean mResult = true;
                mResult &= ShellCommand.sendShellCommand("cat " + vBoxPath + " > /data/vbox", 0.5f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                mResult &= ShellCommand.sendShellCommand("chmod 777 /data/vbox", 0.5f);
                Log.i("ViPER4Android", "Command return = " + ShellCommand.getLastReturnValue());
                /* I think the best way to check wether vbox was installed is execute it and check the exit value */
                mResult &= ShellCommand.sendShellCommand("/data/vbox", 1.0f);  /* vbox has command stdout echo (except dd and cat), so we can wait infinite here */
                int vBoxExitValue = ShellCommand.getLastReturnValue();  /* If the shell failed to execute vbox, the return value will never equal 0 */
                if (mResult && vBoxExitValue == 0) {
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

        // Try RootTools
        Log.i("ViPER4Android", "Now install VBox with RootTools");
        {
            RootTools.useRoot = true;
            RootTools.debugMode = true;

            if (!RootTools.isRootAvailable()) return false;
            if (!RootTools.isAccessGiven()) return false;

            if (!RootTools.copyFile(vBoxPath, "/data/vbox", false, false)) {
                try {
                    RootTools.closeAllShells();
                } catch (IOException e) {
                }
                Log.i("ViPER4Android", "Bad, vbox install failed");
                return false;
            }

            boolean error = false;
            CommandCapture ccSetPermission = new CommandCapture(0,
                    "toolbox chmod 777 /data/vbox",
                    "busybox chmod 777 /data/vbox",
                    "chmod 777 /data/vbox");
            try {
                RootTools.getShell(true).add(ccSetPermission).waitForFinish();
            } catch (InterruptedException e) {
                error = true;
            } catch (IOException e) {
                error = true;
            } catch (TimeoutException e) {
                error = true;
            } catch (RootDeniedException e) {
                error = true;
            }

            try {
                RootTools.closeAllShells();
            } catch (IOException e) {
            }

            if (error) {
                Log.i("ViPER4Android", "Bad, vbox install failed");
                return false;
            }
        }

        // Now lets check vbox
        if (ShellCommand.executeWithoutShell("/data/vbox", null) == 0) {
            Log.i("ViPER4Android", "Good, vbox installed");
            return true;
        } else {
            Log.i("ViPER4Android", "Bad, vbox install failed");
            return false;
        }
    }

    private static void proceedVBox(Context ctx) {
        // Check vbox
        Log.i("ViPER4Android", "Checking vbox");
        if (ShellCommand.executeWithoutShell("/data/vbox", null) == 0) {
            Log.i("ViPER4Android", "Good, vbox is ok");
            sVboxPrepared = true;
            sVboxExecPath = "/data/vbox";
            return;
        }

        // Install vbox
        if (installVBox(ctx)) {
            ShellCommand.closeShell();
            sVboxPrepared = true;
            sVboxExecPath = "/data/vbox";
            return;
        }
        ShellCommand.closeShell();
        sVboxPrepared = false;
        sVboxExecPath = "";
    }

    private static boolean checkWritable(String mFileName) {
        try {
            byte[] mBlank = new byte[16];
            FileOutputStream fosOutput = new FileOutputStream(mFileName);
            fosOutput.write(mBlank);
            fosOutput.flush();
            fosOutput.close();
            new File(mFileName).delete();
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
                sExternalStoragePath = mExternalStoragePathName;
            } else {
                sExternalStoragePath = mExternalStoragePathName + "/";
            }
            sV4aRoot = sExternalStoragePath + "ViPER4Android/";
            sV4aKernelPath = sV4aRoot + "Kernel/";
            sV4aProfilePath = sV4aRoot + "Profile/";
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
                    sExternalStoragePath = mExternalStoragePathName;
                else
                    sExternalStoragePath = mExternalStoragePathName + "/";
                sV4aRoot = sExternalStoragePath + "ViPER4Android/";
                sV4aKernelPath = sV4aRoot + "Kernel/";
                sV4aProfilePath = sV4aRoot + "Profile/";
                Log.i("ViPER4Android", "External storage path = "
                        + sExternalStoragePath);
                Log.i("ViPER4Android", "ViPER4Android root path = "
                        + sV4aRoot);
                Log.i("ViPER4Android", "ViPER4Android kernel path = "
                        + sV4aKernelPath);
                Log.i("ViPER4Android", "ViPER4Android profile path = "
                        + sV4aProfilePath);
                return;
            }
            if (pathFromSDKIsWorking) {
                if (mExternalStoragePathName.endsWith("/"))
                    sExternalStoragePath = mExternalStoragePathName;
                else
                    sExternalStoragePath = mExternalStoragePathName + "/";
                sV4aRoot = sExternalStoragePath + "ViPER4Android/";
                sV4aKernelPath = sV4aRoot + "Kernel/";
                sV4aProfilePath = sV4aRoot + "Profile/";
                Log.i("ViPER4Android", "External storage path = "
                        + sExternalStoragePath);
                Log.i("ViPER4Android", "ViPER4Android root path = "
                        + sV4aRoot);
                Log.i("ViPER4Android", "ViPER4Android kernel path = "
                        + sV4aKernelPath);
                Log.i("ViPER4Android", "ViPER4Android profile path = "
                        + sV4aProfilePath);
                return;
            }

            Log.i("ViPER4Android",
                    "Really terrible thing, external storage detection failed, v4a may malfunctioned");
            if (mExternalStoragePathName.endsWith("/"))
                sExternalStoragePath = mExternalStoragePathName;
            else
                sExternalStoragePath = mExternalStoragePathName + "/";
            sV4aRoot = sExternalStoragePath + "ViPER4Android/";
            sV4aKernelPath = sV4aRoot + "Kernel/";
            sV4aProfilePath = sV4aRoot + "Profile/";
        }
    }

    public static boolean isEnvironmentInitialized() {
        return sEnvironmentInitialized;
    }

    public static void initEnvironment(Context ctx) {
        if (sEnvironmentInitialized)
            return;
        try {
            proceedVBox(ctx);
        } catch (Exception e) {
            sVboxPrepared = false;
            sVboxExecPath = "";
        }
        try {
            proceedExternalStoragePath();
        } catch (Exception e) {
            String mExternalStoragePathName = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (Build.VERSION.SDK_INT >= 18) {
                if (mExternalStoragePathName.endsWith("/emulated/0") || mExternalStoragePathName.endsWith("/emulated/0/"))
                    mExternalStoragePathName = mExternalStoragePathName.replace("/emulated/0", "/emulated/legacy");
            }
            if (mExternalStoragePathName.endsWith("/")) sExternalStoragePath = mExternalStoragePathName;
            else sExternalStoragePath = mExternalStoragePathName + "/";
            sV4aRoot = sExternalStoragePath + "ViPER4Android/";
            sV4aKernelPath = sV4aRoot + "Kernel/";
            sV4aProfilePath = sV4aRoot + "Profile/";
        }
        sEnvironmentInitialized = true;
    }

    public static boolean getVBoxUsable() {
        return sVboxPrepared;
    }

    public static String getVBoxExecutablePath() {
        return sVboxExecPath;
    }

    public static String getESPath() {
        return sExternalStoragePath;
    }

    public static String getV4ARootPath() {
        return sV4aRoot;
    }

    public static String getV4AKernelPath() {
        return sV4aKernelPath;
    }

    public static String getV4AProfilePath() {
        return sV4aProfilePath;
    }
}
