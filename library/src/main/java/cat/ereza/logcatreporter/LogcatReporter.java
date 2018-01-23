/*
 * Copyright 2016-2017 Eduard Ereza Martínez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cat.ereza.logcatreporter;

import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class LogcatReporter {

    private static final String TAG = "LogcatReporter";

    private static final int DEFAULT_WAIT_TIME_IN_MILLIS = 500;
    private static final int DEFAULT_LINE_COUNT = 1000;

    private static ArrayList<String> tags;

    private static int lineCount;

    public static void install() {
        install(DEFAULT_LINE_COUNT, DEFAULT_WAIT_TIME_IN_MILLIS, null);
    }

    public static void install(ArrayList<String> tags) {
        install(DEFAULT_LINE_COUNT, DEFAULT_WAIT_TIME_IN_MILLIS, tags);
    }

    public static void install(int lineCount) {
        install(lineCount, DEFAULT_WAIT_TIME_IN_MILLIS, null);
    }

    public static void install(int lineCount, ArrayList<String> tags) {
        install(lineCount, DEFAULT_WAIT_TIME_IN_MILLIS, tags);
    }

    public static void install(final int lineCount, final int waitTimeInMillis, ArrayList<String> tags) {

        LogcatReporter.tags = tags;

        if(tags != null && tags.size() > 0) {
            tags.add(TAG);
            tags.add("AndroidRuntime");
        }

        LogcatReporter.lineCount = lineCount;

        clearLogCat();

        final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.i(TAG, "Crash detected, sending Logcat to Crashlytics!");
                logLogcat();
                try {
                    //Sleep for a moment, try to let the Crashlytics log service catch up...
                    Thread.sleep(waitTimeInMillis);
                } catch (Throwable t) {
                    Log.e(TAG, "The reporting thread was interrupted, the log may be incomplete!");
                }
                //Let Crashlytics handle everything
                originalHandler.uncaughtException(thread, ex);
            }
        });

        Log.i(TAG, "LogcatReporter has been installed");
    }

    public static void clearLogCat() {
        try {
            Runtime.getRuntime().exec("logcat -c");
            Log.i(TAG, "Logs have been cleared.");
        } catch (Throwable t) {
            Log.e(TAG, "Could not clear logs, in case of crash, the logs may contain more info from past executions.");
        }
    }

    public static void reportExceptionWithLogcat(Throwable t) {
        logLogcat();
        Crashlytics.logException(t);
    }

    private static void logLogcat() {
        //Get the log (try at least)
        try {

            String command = "logcat -t " + lineCount + " -v threadtime";
            String regexTags = "";

            if(tags != null && tags.size() > 0) {
                //regexTags += " | grep -o -P '";
                for (int i = 0; i < tags.size(); i++) {
                    regexTags += ".*" + tags.get(i) + "[\\s]*:.*|";
                }

                regexTags = regexTags.substring(0, regexTags.length()-1);
                //regexTags += "'";
            }

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if(regexTags== "" || line.startsWith("-") || line.matches(regexTags))
                    Crashlytics.log("|| " + line);
            }
        } catch (Throwable t) {
            Crashlytics.log("(No log available, an error occurred while getting it)");
        }
    }
}
