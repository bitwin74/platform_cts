/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.atrace.cts;

import com.android.cts.tradefed.build.CtsBuildHelper;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test to check that atrace is usable, to enable usage of systrace.
 */
public class AtraceHostTest extends DeviceTestCase implements IBuildReceiver {
    private static final String TEST_APK = "CtsAtraceTestApp.apk";
    private static final String TEST_PKG = "com.android.cts.atracetestapp";

    private interface FtraceEntryCallback {
        void onTraceEntry(String threadName, int pid, int tid, String eventType, String args);
        void onFinished();
    }

    /**
     * Helper for parsing ftrace data.
     * Regexs copied from (and should be kept in sync with) ftrace importer in catapult.
     */
    private static class FtraceParser {
        // Matches the trace record in 3.2 and later with the print-tgid option:
        //          <idle>-0    0 [001] d...  1.23: sched_switch
        private static final Pattern sLineWithTgid = Pattern.compile(
                "^\\s*(.+)-(\\d+)\\s+\\(\\s*(\\d+|-+)\\)\\s\\[(\\d+)\\]"
                + "\\s+[dX.][N.][Hhs.][0-9a-f.]"
                + "\\s+(\\d+\\.\\d+):\\s+(\\S+):\\s(.*)");

        // Matches the default trace record in 3.2 and later (includes irq-info):
        //          <idle>-0     [001] d...  1.23: sched_switch
        private static final Pattern sLineWithIrqInfo = Pattern.compile(
                "^\\s*(.+)-(\\d+)\\s+\\[(\\d+)\\]"
                + "\\s+[dX.][N.][Hhs.][0-9a-f.]"
                + "\\s+(\\d+\\.\\d+):\\s+(\\S+):\\s(.*)$");

        // Matches the default trace record pre-3.2:
        //          <idle>-0     [001]  1.23: sched_switch
        private static final Pattern sLineLegacy = Pattern.compile(
                "^\\s*(.+)-(\\d+)\\s+\\[(\\d+)\\]\\s*(\\d+\\.\\d+):\\s+(\\S+):\\s(.*)");
        private static void parseLine(String line, FtraceEntryCallback callback) {
            Matcher m = sLineWithTgid.matcher(line);
            if (m.matches()) {
                callback.onTraceEntry(
                        /*threadname*/ m.group(1),
                        /*pid*/ m.group(3).startsWith("-") ? -1 : Integer.parseInt(m.group(3)),
                        /*tid*/ Integer.parseInt(m.group(2)),
                        /*eventName*/ m.group(6),
                        /*details*/ m.group(7));
                return;
            }

            m = sLineWithIrqInfo.matcher(line);
            if (m.matches()) {
                callback.onTraceEntry(
                        /*threadname*/ m.group(1),
                        /*pid*/ -1,
                        /*tid*/ Integer.parseInt(m.group(2)),
                        /*eventName*/ m.group(5),
                        /*details*/ m.group(6));
                return;
            }

            m = sLineLegacy.matcher(line);
            if (m.matches()) {
                callback.onTraceEntry(
                        /*threadname*/ m.group(1),
                        /*pid*/ -1,
                        /*tid*/ Integer.parseInt(m.group(2)),
                        /*eventName*/ m.group(5),
                        /*details*/ m.group(6));
                return;
            }
            System.err.println("line doesn't match: " + line);
        }

        private static void parse(Reader reader, FtraceEntryCallback callback) throws Exception {
            try {
                BufferedReader bufferedReader = new BufferedReader(reader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    FtraceParser.parseLine(line, callback);
                }
            } finally {
                callback.onFinished();
            }
        }
    }

    private CtsBuildHelper mCtsBuild;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = CtsBuildHelper.createBuildHelper(buildInfo);
    }

    // Collection of all userspace tags, and 'sched'
    private static final List<String> sRequiredCategoriesList = Arrays.asList(
            "sched",
            "gfx",
            "input",
            "view",
            "webview",
            "wm",
            "am",
            "sm",
            "audio",
            "video",
            "camera",
            "hal",
            "app",
            "res",
            "dalvik",
            "rs",
            "bionic",
            "power"
    );

    /**
     * Tests that atrace exists and is runnable with no args
     */
    public void testSimpleRun() throws Exception {
        String output = getDevice().executeShellCommand("atrace");
        String[] lines = output.split("\\r?\\n");

        // check for expected stdout
        assertEquals("capturing trace... done", lines[0]);
        assertEquals("TRACE:", lines[1]);

        // commented trace marker starts here
        assertEquals("# tracer: nop", lines[2]);
    }

    /**
     * Tests the output of "atrace --list_categories" to ensure required categories exist.
     */
    public void testCategories() throws Exception {
        String output = getDevice().executeShellCommand("atrace --list_categories");
        String[] categories = output.split("\\r?\\n");

        Set<String> requiredCategories = new HashSet<String>(sRequiredCategoriesList);

        for (String category : categories) {
            int dashIndex = category.indexOf("-");

            assertTrue(dashIndex > 1); // must match category output format
            category = category.substring(0, dashIndex).trim();

            requiredCategories.remove(category);
        }

        if (!requiredCategories.isEmpty()) {
            for (String missingCategory : requiredCategories) {
                System.err.println("missing category: " + missingCategory);
            }
            fail("Expected categories missing from atrace");
        }
    }

    /**
     * Tests that atrace captures app launch, including app level tracing
     */
    public void testTracingContent() throws Exception {
        String atraceOutput = null;
        try {
            // cleanup test apps that might be installed from previous partial test run
            getDevice().uninstallPackage(TEST_PKG);

            // install the test app
            File testAppFile = mCtsBuild.getTestApp(TEST_APK);
            String installResult = getDevice().installPackage(testAppFile, false);
            assertNull(
                    String.format("failed to install atrace test app. Reason: %s", installResult),
                    installResult);

            // capture a launch of the app with async tracing
            String atraceArgs = "-a " + TEST_PKG + " -c -b 16000 view"; // TODO: zipping
            getDevice().executeShellCommand("atrace --async_stop " + atraceArgs);
            getDevice().executeShellCommand("atrace --async_start " + atraceArgs);
            getDevice().executeShellCommand("am start " + TEST_PKG);
            getDevice().executeShellCommand("sleep 1");
            atraceOutput = getDevice().executeShellCommand("atrace --async_stop " + atraceArgs);
        } finally {
            assertNotNull("unable to capture atrace output", atraceOutput);
            getDevice().uninstallPackage(TEST_PKG);
        }


        // now parse the trace data (see external/chromium-trace/systrace.py)
        final String MARKER = "TRACE:";
        int dataStart = atraceOutput.indexOf(MARKER);
        assertTrue(dataStart >= 0);
        String traceData = atraceOutput.substring(dataStart + MARKER.length());

        FtraceEntryCallback callback = new FtraceEntryCallback() {
            private int matches = 0;
            private int nextSectionIndex = 0;
            private int appPid = -1;

            // list of tags expected to be seen on app launch, in order.
            private final String[] requiredSectionList = {
                    "traceable-app-test-section",
                    "inflate",
                    "Choreographer#doFrame",
                    "traversal",
                    "measure",
                    "layout",
                    "draw",
                    "Record View#draw()"
            };

            @Override
            public void onTraceEntry(String truncatedThreadName, int pid, int tid,
                    String eventName, String details) {
                if (!"tracing_mark_write".equals(eventName)) {
                    // not userspace trace, ignore
                    return;
                }

                matches++;
                assertNotNull(truncatedThreadName);
                assertTrue(tid > 0);
                if (TEST_PKG.endsWith(truncatedThreadName)) {
                    matches++;

                    if (pid >= 0) {
                        // verify pid, if present
                        if (appPid == -1) {
                            appPid = pid;
                        } else {
                            assertEquals(appPid, pid);
                        }
                    }

                    if (nextSectionIndex < requiredSectionList.length
                            && details != null
                            && details.startsWith("B|")
                            && details.endsWith("|" + requiredSectionList[nextSectionIndex])) {
                        nextSectionIndex++;
                    }
                }
            }

            @Override
            public void onFinished() {
                assertTrue("Unable to parse any userspace sections from atrace output",
                        matches != 0);
                assertEquals("Didn't see required list of traced sections, in order",
                        requiredSectionList.length, nextSectionIndex);
            }
        };

        FtraceParser.parse(new StringReader(traceData), callback);
    }
}
