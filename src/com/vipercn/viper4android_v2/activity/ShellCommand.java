package com.vipercn.viper4android_v2.activity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import android.os.SystemClock;
import android.util.Log;

public class ShellCommand {
    private static Process m_psShellProcess = null;
    private static DataOutputStream m_doShellStdIn = null;
    private static DataInputStream m_diShellStdOut = null;
    private static DataInputStream m_diShellStdErr = null;
    private static boolean m_bShellOpened = false;

    private static String byteToString(byte[] byteArray) {
        if (byteArray == null)
            return null;
        try {
            String mResult = new String(byteArray, "ASCII");
            mResult = String.copyValueOf(mResult.toCharArray(), 0,
                    byteArray.length);
            return mResult;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private static String[] byteArrayToStringArray(byte[] byteArray,
            int mDataLength) {
        if (byteArray == null)
            return null;
        if (mDataLength <= 0)
            return null;
        if (mDataLength > byteArray.length)
            return null;

        // Replace all invisible chars to '.'
        for (int i = 0; i < mDataLength; i++) {
            if ((byteArray[i] == 0x0D) || (byteArray[i] == 0x0A)) {
                byteArray[i] = 0;
                continue;
            }
            if (byteArray[i] < 0x20)
                byteArray[i] = 0x2E;
            if (byteArray[i] > 0x7E)
                byteArray[i] = 0x2E;
        }

        // Split and convert to string
        List<String> lstString = new ArrayList<String>();
        for (int i = 0; i < mDataLength; i++) {
            if (byteArray[i] == 0)
                continue;
            int mBlockLength = -1;
            for (int j = i + 1; j < mDataLength; j++) {
                if (byteArray[j] == 0) {
                    mBlockLength = j - i;
                    break;
                }
            }
            if (mBlockLength == -1)
                mBlockLength = mDataLength - i;
            byte[] baBlockData = new byte[mBlockLength];
            System.arraycopy(byteArray, i, baBlockData, 0, mBlockLength);
            lstString.add(byteToString(baBlockData));
            i += mBlockLength;
        }

        if (lstString.size() <= 0)
            return null;
        String[] mResult = new String[lstString.size()];
        lstString.toArray(mResult);
        return mResult;
    }

    private static String[] getStdOut() {
        if (m_diShellStdOut == null)
            return null;
        try {
            if (m_diShellStdOut.available() <= 0)
                return null;
        } catch (IOException ioe) {
            return null;
        }

        byte[] mDataOut = null;
        int mDataLength = 0;
        try {
            while (m_diShellStdOut.available() > 0) {
                byte[] baData = new byte[1024];
                int mReadCount = m_diShellStdOut.read(baData);
                if (mReadCount == -1)
                    break;
                // Realloc
                {
                    int mCurrentSize = 0;
                    if (mDataOut != null)
                        mCurrentSize = mDataOut.length;
                    byte[] newDataOut = new byte[mCurrentSize + mReadCount];
                    if (mDataOut != null)
                        System.arraycopy(mDataOut, 0, newDataOut, 0,
                                mCurrentSize);
                    System.arraycopy(baData, 0, newDataOut, mCurrentSize,
                            mReadCount);
                    mDataOut = newDataOut;
                    mDataLength += mReadCount;
                }
            }
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand",
                    "IOException, msg = " + ioe.getMessage());
        }
        Log.i("ViPER4Android_ShellCommand", "Standard output read "
                + mDataLength + " bytes");

        return byteArrayToStringArray(mDataOut, mDataLength);
    }

    private static String[] getStdErr() {
        if (m_diShellStdErr == null)
            return null;
        try {
            if (m_diShellStdErr.available() <= 0)
                return null;
        } catch (IOException ioe) {
            return null;
        }

        byte[] mDataOut = null;
        int mDataLength = 0;
        try {
            while (m_diShellStdErr.available() > 0) {
                byte[] mData = new byte[1024];
                int mReadCount = m_diShellStdErr.read(mData);
                if (mReadCount == -1)
                    break;
                // Realloc
                {
                    int mCurrentSize = 0;
                    if (mDataOut != null)
                        mCurrentSize = mDataOut.length;
                    byte[] baNewDataOut = new byte[mCurrentSize + mReadCount];
                    if (mDataOut != null)
                        System.arraycopy(mDataOut, 0, baNewDataOut, 0,
                                mCurrentSize);
                    System.arraycopy(mData, 0, baNewDataOut, mCurrentSize,
                            mReadCount);
                    mDataOut = baNewDataOut;
                    mDataLength += mReadCount;
                }
            }
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand",
                    "IOException, msg = " + ioe.getMessage());
        }
        Log.i("ViPER4Android_ShellCommand", "Standard error read "
                + mDataLength + " bytes");

        return byteArrayToStringArray(mDataOut, mDataLength);
    }

    private static void clearStdOutAndErr() {
        if (m_diShellStdOut != null) {
            Log.i("ViPER4Android_ShellCommand", "Flushing standard output ...");
            try {
                while (m_diShellStdOut.available() > 0) {
                    if (m_diShellStdOut.read() == -1)
                        break;
                }
            } catch (IOException e) {
            }
        }
        if (m_diShellStdErr != null) {
            Log.i("ViPER4Android_ShellCommand", "Flushing standard error ...");
            try {
                while (m_diShellStdErr.available() > 0) {
                    if (m_diShellStdErr.read() == -1)
                        break;
                }
            } catch (IOException e) {
            }
        }
    }

    public static boolean openSysShell(boolean reopen) {
        Log.i("ViPER4Android_ShellCommand", "Open shell, reopen = " + reopen);

        if (m_bShellOpened && !reopen) {
            Log.i("ViPER4Android_ShellCommand", "Shell already opened");
            return true;
        } else if (m_bShellOpened) {
            Log.i("ViPER4Android_ShellCommand", "Close current shell");
            closeShell();
        }

        try {
            Log.i("ViPER4Android_ShellCommand", "Starting system shell");
            m_psShellProcess = Runtime.getRuntime().exec("sh"); /*
                                                                 * Maybe we
                                                                 * should parse
                                                                 * init.rc to
                                                                 * find out the
                                                                 * $PATH
                                                                 */
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand",
                    "Start system shell failed, msg = " + ioe.getMessage());
            m_psShellProcess = null;
            m_doShellStdIn = null;
            m_diShellStdOut = null;
            m_diShellStdErr = null;
            m_bShellOpened = false;
            return false;
        }

        Log.i("ViPER4Android_ShellCommand",
                "Fetching shell stdin, stdout and stderr");
        m_doShellStdIn = new DataOutputStream(
                m_psShellProcess.getOutputStream());
        m_diShellStdOut = new DataInputStream(m_psShellProcess.getInputStream());
        m_diShellStdErr = new DataInputStream(m_psShellProcess.getErrorStream());
        try {
            Log.i("ViPER4Android_ShellCommand",
                    "Performing shell banner and query id, timeout = 20 secs");
            m_doShellStdIn.writeBytes("echo \"Enter ViPER's System Shell\"\n");
            m_doShellStdIn.writeBytes("id\n");
            m_doShellStdIn.flush();

            boolean gotResult = false;
            for (int mWaitCount = 0; mWaitCount < 200; mWaitCount++) {
                String[] mStdOut = getStdOut();
                if (mStdOut != null)
                    gotResult = true;
                String[] mStdErr = getStdErr();
                if (mStdErr != null)
                    gotResult = true;
                if (gotResult) {
                    if (mStdOut != null)
                        for (String stdOut : mStdOut)
                            Log.i("ViPER4Android_ShellCommand", "stdout: "
                                    + stdOut);
                    if (mStdErr != null)
                        for (String stdErr : mStdErr)
                            Log.i("ViPER4Android_ShellCommand", "stderr: "
                                    + stdErr);
                    break;
                }

                SystemClock.sleep(100);
                Log.i("ViPER4Android_ShellCommand", ((mWaitCount + 1) * 100)
                        + " ms waited, still no result");
            }

            if (!gotResult) {
                Log.i("ViPER4Android_ShellCommand", "Wait system shell timeout");
                closeShell();
                return false;
            }
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand",
                    "IOException, msg = " + ioe.getMessage());
            closeShell();
            return false;
        }

        clearStdOutAndErr();
        m_bShellOpened = true;

        return true;
    }

    public static boolean openRootShell(boolean reopen) {
        Log.i("ViPER4Android_ShellCommand", "Open shell, reopen = " + reopen);

        if (m_bShellOpened && !reopen) {
            Log.i("ViPER4Android_ShellCommand", "Shell already opened");
            return true;
        } else if (m_bShellOpened) {
            Log.i("ViPER4Android_ShellCommand", "Close current shell");
            closeShell();
        }

        try {
            Log.i("ViPER4Android_ShellCommand", "Starting su shell");
            /* Maybe we should parse init.rc to find out the $PATH */
            m_psShellProcess = Runtime.getRuntime().exec("su"); 

        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand", "Start su shell failed, msg = " + ioe.getMessage());
            m_psShellProcess = null;
            m_doShellStdIn = null;
            m_diShellStdOut = null;
            m_diShellStdErr = null;
            m_bShellOpened = false;
            return false;
        }

        Log.i("ViPER4Android_ShellCommand", "Fetching shell stdin, stdout and stderr");
        m_doShellStdIn = new DataOutputStream(m_psShellProcess.getOutputStream());
        m_diShellStdOut = new DataInputStream(m_psShellProcess.getInputStream());
        m_diShellStdErr = new DataInputStream(m_psShellProcess.getErrorStream());
        try {
            Log.i("ViPER4Android_ShellCommand", "Performing shell banner and query id, timeout = 20 secs");
            m_doShellStdIn.writeBytes("echo \"Enter ViPER's Root Shell\"\n");
            m_doShellStdIn.writeBytes("id\n");
            m_doShellStdIn.flush();

            boolean gotResult = false, mAccessGiven = false;
            for (int mWaitCount = 0; mWaitCount < 200; mWaitCount++) {
                String[] mStdOut = getStdOut();
                if (mStdOut != null) {
                    for (String stdOut : mStdOut) {
                        Log.i("ViPER4Android_ShellCommand", "stdout: " + stdOut);
                        if (stdOut.contains("uid")) {
                            Log.i("ViPER4Android_ShellCommand", "Got result");
                            if (stdOut.contains("uid=0")) {
                                gotResult = true;
                                mAccessGiven = true;
                                break;
                            } else {
                                gotResult = true;
                                mAccessGiven = false;
                                break;
                            }
                        }
                    }
                }
                String[] mStdErr = getStdErr();
                if (mStdErr != null) {
                    for (String stdErr : mStdErr) {
                        Log.i("ViPER4Android_ShellCommand", "stderr: " + stdErr);
                        if (stdErr.contains("uid")) {
                            Log.i("ViPER4Android_ShellCommand", "Got result");
                            if (stdErr.contains("uid=0")) {
                                gotResult = true;
                                mAccessGiven = true;
                                break;
                            } else {
                                gotResult = true;
                                mAccessGiven = false;
                                break;
                            }
                        }
                    }
                }
                if (gotResult)
                    break;

                SystemClock.sleep(100);
                Log.i("ViPER4Android_ShellCommand", ((mWaitCount + 1) * 100) + " ms waited, still no result");
            }

            if (gotResult && !mAccessGiven) {
                Log.i("ViPER4Android_ShellCommand", "Acquire root permission failed");
                closeShell();
                return false;
            }
            if (!gotResult) {
                Log.i("ViPER4Android_ShellCommand", "Acquire root permission timeout");
                closeShell();
                return false;
            }
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand", "IOException, msg = " + ioe.getMessage());
            closeShell();
            return false;
        }

        clearStdOutAndErr();
        m_bShellOpened = true;

        return true;
    }

    public static void closeShell() {
        if (m_doShellStdIn != null) {
            Log.i("ViPER4Android_ShellCommand", "Closing shell stdandard input");
            try {
                m_doShellStdIn.writeBytes("exit\n");
                m_doShellStdIn.flush();
                m_doShellStdIn.close();
            } catch (IOException ioe) {
                Log.i("ViPER4Android_ShellCommand",
                        "Close shell stdandard input failed, msg = " + ioe.getMessage());
            }
            m_doShellStdIn = null;
        }
        clearStdOutAndErr();
        if (m_diShellStdOut != null) {
            Log.i("ViPER4Android_ShellCommand",
                    "Closing shell stdandard output");
            try {
                m_diShellStdOut.close();
            } catch (IOException ioe) {
                Log.i("ViPER4Android_ShellCommand", "Close shell stdandard output failed, msg = " + ioe.getMessage());
            }
            m_diShellStdOut = null;
        }
        if (m_diShellStdErr != null) {
            Log.i("ViPER4Android_ShellCommand", "Closing shell stdandard error");
            try {
                m_diShellStdErr.close();
            } catch (IOException ioe) {
                Log.i("ViPER4Android_ShellCommand",
                        "Close shell stdandard error failed, msg = " + ioe.getMessage());
            }
            m_diShellStdErr = null;
        }
        if (m_psShellProcess != null) {
            try {
                Log.i("ViPER4Android_ShellCommand", "Waiting for shell exit");
                m_psShellProcess.waitFor();
            } catch (InterruptedException ie) {
                Log.i("ViPER4Android_ShellCommand",
                        "Wait for shell exit failed, msg = " + ie.getMessage());
            }
            Log.i("ViPER4Android_ShellCommand", "Closing shell");
            m_psShellProcess.destroy();
            m_psShellProcess = null;
        }

        m_bShellOpened = false;
        Log.i("ViPER4Android_ShellCommand", "Shell closed");
    }

    public static boolean sendShellCommand(String szCommand,
            float nMaxWaitSeconds) {
        Log.i("ViPER4Android_ShellCommand", "Sending shell \"" + szCommand
                + "\", wait " + nMaxWaitSeconds + " seconds");

        if (!m_bShellOpened)
            return false;
        if (m_doShellStdIn == null)
            return false;
        if (m_diShellStdOut == null)
            return false;
        if (m_diShellStdErr == null)
            return false;

        clearStdOutAndErr();
        try {
            int mOldCount;
            try {
                mOldCount = m_diShellStdOut.available() + m_diShellStdErr.available();
            } catch (IOException ioe) {
                mOldCount = 0;
            }
            m_doShellStdIn.writeBytes(szCommand + "\n");
            m_doShellStdIn.flush();
            for (int mWaitCount = 0; mWaitCount <= Math.round(nMaxWaitSeconds * 10); mWaitCount++) {
                int mCurrCount;
                try {
                    mCurrCount = m_diShellStdOut.available() + m_diShellStdErr.available();
                } catch (IOException ioe) {
                    mCurrCount = 0;
                }
                Log.i("ViPER4Android_ShellCommand",
                        "Waiting for command return, idx = " + mWaitCount
                                + ", old = " + mOldCount + ", curr = "
                                + mCurrCount);
                if (mCurrCount != mOldCount) {
                    Log.i("ViPER4Android_ShellCommand", "Command returned");
                    break;
                }
                SystemClock.sleep(100);
            }
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand", "Send shell failed, msg = "
                    + ioe.getMessage());
            return false;
        }

        String[] mStdOut = getStdOut();
        if (mStdOut != null) {
            for (String stdOut : mStdOut) Log.i("ViPER4Android_ShellCommand(stdout)", stdOut);
        }
        String[] mStdErr = getStdErr();
        if (mStdErr != null) {
            for (String stdErr : mStdErr) Log.i("ViPER4Android_ShellCommand(stderr)", stdErr);
        }

        return true;
    }

    public static boolean sendShellCommandPreserveOut(String mCommand,
            float maxWaitSeconds) {
        Log.i("ViPER4Android_ShellCommand", "Sending shell \"" + mCommand
                + "\", wait " + maxWaitSeconds + " seconds");

        if (!m_bShellOpened)
            return false;
        if (m_doShellStdIn == null)
            return false;
        if (m_diShellStdOut == null)
            return false;
        if (m_diShellStdErr == null)
            return false;

        clearStdOutAndErr();
        try {
            int mOldCount;
            try {
                mOldCount = m_diShellStdOut.available() + m_diShellStdErr.available();
            } catch (IOException ioe) {
                mOldCount = 0;
            }
            m_doShellStdIn.writeBytes(mCommand + "\n");
            m_doShellStdIn.flush();
            for (int mWaitCount = 0; mWaitCount <= Math.round(maxWaitSeconds * 10); mWaitCount++) {
                int mCurrCount;
                try {
                    mCurrCount = m_diShellStdOut.available() + m_diShellStdErr.available();
                } catch (IOException ioe) {
                    mCurrCount = 0;
                }
                Log.i("ViPER4Android_ShellCommand",
                        "Waiting for command return, idx = " + mWaitCount
                                + ", old = " + mOldCount + ", curr = "
                                + mCurrCount);
                if (mCurrCount != mOldCount) {
                    Log.i("ViPER4Android_ShellCommand", "Command returned");
                    break;
                }
                SystemClock.sleep(100);
            }
        } catch (IOException ioe) {
            Log.i("ViPER4Android_ShellCommand", "Send shell failed, msg = " + ioe.getMessage());
            return false;
        }

        return true;
    }

    public static int setLastReturnValue() {
        clearStdOutAndErr();
        /* Lets print the last command's exit value to stdout */
        if (!sendShellCommandPreserveOut("echo $?", 1.0f))
            return -65536;
        String[] mStdOut = getStdOut();
        if (mStdOut != null) {
            int returnValue = -65536;
            for (String stdOut : mStdOut) {
                try {
                    returnValue = Integer.parseInt(stdOut.trim());
                } catch (NumberFormatException nfe) {
                }
            }
            return returnValue;
        } else
            return -65536;
    }

    public static int executeWithoutShell(String mExecutable, File directory) {
        if (mExecutable == null)
            return -65536;
        if (mExecutable.equals(""))
            return -65536;

        Log.i("ViPER4Android_ShellCommand", "Executing " + mExecutable
                + " ...");
        int exitValue = -65536;
        try {
            Process mProcess = Runtime.getRuntime().exec(mExecutable, null,
                    directory);
            mProcess.waitFor();
            exitValue = mProcess.exitValue();
            mProcess.destroy();
        } catch (IOException e) {
            Log.i("ViPER4Android_ShellCommand",
                    "IOException, msg = " + e.getMessage());
            return exitValue;
        } catch (InterruptedException e) {
            Log.i("ViPER4Android_ShellCommand", "InterruptedException, msg = "
                    + e.getMessage());
            return exitValue;
        }
        Log.i("ViPER4Android_ShellCommand", "Program " + mExecutable
                + " returned " + exitValue);

        return exitValue;
    }

    public static int rootExecuteWithoutShell(String mExecutable,
            File directory) {
        if (mExecutable == null)
            return -65536;
        if (mExecutable.equals(""))
            return -65536;

        Log.i("ViPER4Android_ShellCommand", "Root executing " + mExecutable
                + " ...");
        int exitValue = -65536;
        try {
            Process mProcess = Runtime.getRuntime()
                    .exec(new String[] { "su", "-c", mExecutable }, null,
                            directory);
            mProcess.waitFor();
            exitValue = mProcess.exitValue();
            mProcess.destroy();
        } catch (IOException e) {
            Log.i("ViPER4Android_ShellCommand",
                    "IOException, msg = " + e.getMessage());
            return exitValue;
        } catch (InterruptedException e) {
            Log.i("ViPER4Android_ShellCommand", "InterruptedException, msg = "
                    + e.getMessage());
            return exitValue;
        }
        Log.i("ViPER4Android_ShellCommand", "Program " + mExecutable
                + " returned " + exitValue);

        return exitValue;
    }
}
