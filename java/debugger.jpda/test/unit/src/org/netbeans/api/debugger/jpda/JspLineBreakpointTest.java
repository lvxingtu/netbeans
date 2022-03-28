/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.api.debugger.jpda;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.jpda.event.JPDABreakpointEvent;
import org.netbeans.api.debugger.jpda.event.JPDABreakpointListener;
import org.netbeans.junit.NbTestCase;


/**
 * Tests JSP line breakpoints at various circumstances.
 *
 * The tests use test java application (JspLineBreakpointApp.java) created 
 * from servlet generated by Tomcat's JSP compiler. The java app has all necessary 
 * lines left on the same place as original servlet. In addition SMAP file (JspLineBreakpointApp.txt)
 * is attached to java app (class file) to support mapping from lines in java source 
 * to lines in JSP, from which the servlet was translated. The JSP pages are placed in
 * org/netbeans/api/debugger/jpda/testapps/resources directory.
 *
 * @author Libor Kotouc
 */
public class JspLineBreakpointTest extends NbTestCase {

    private static final String SOURCE_NAME = "included.jsp";
    private static final String SOURCE_PATH_FIRST = "d/" + SOURCE_NAME;
    private static final String SOURCE_PATH_SECOND = SOURCE_NAME;
    private static final String CLASS_NAME = "org.netbeans.api.debugger.jpda.testapps.*";
    private static final int LINE_NUMBER = 2;
    private static final String STRATUM = "JSP";
    
    private final String SRC_ROOT = System.getProperty ("test.dir.src");
    
    private JPDASupport support;
    private String testAppCLAZ = null;
    private String testAppSMAP = null;
    
    public JspLineBreakpointTest (String s) {
        super (s);
    }
    
    public static Test suite() {
        return JPDASupport.createTestSuite(JspLineBreakpointTest.class);
    }
    
    protected void setUp () {
        URL clazURL = getClass ().getResource ("testapps/JspLineBreakpointApp.class");
        assertNotNull (clazURL);
        testAppCLAZ = clazURL.getPath();
        URL smapURL = getClass ().getResource ("testapps/JspLineBreakpointApp.txt");
        assertNotNull (smapURL);
        testAppSMAP = smapURL.getPath();
    }
    
    /**
     * Tests debugger's ability to make difference between different JSP pages
     * with the same name while getting the locations during class-loaded event.
     *
     * 1. The user creates JSP (index.jsp, include.jsp, d/include.jsp) and 
     * 2. statically includes d/include.jsp (as 1st) and include.jsp (as 2nd) into index.jsp.
     * 3. Then bp is set in include.jsp (line 2).
     * 
     * Debugger should stop _only_ in the include.jsp. If debugger stopped in the first JSP
     * (d/include.jsp), then assertion violation would arise because of source path
     * equality test.
     */
    public void testBreakpointUnambiguity () throws Exception {
        try {
            //install SDE extension to class file
            runSDEInstaller(testAppCLAZ, testAppSMAP);

            //String URL = getClass().getResource("testapps/resources/included.jsp").toString();
            String URL = "file:"+SRC_ROOT+"/org/netbeans/api/debugger/jpda/testapps/resources/included.jsp";
            LineBreakpoint lb = LineBreakpoint.create(URL, LINE_NUMBER);
            lb.setStratum(STRATUM); // NOI18N
            lb.setSourceName(SOURCE_NAME);
            lb.setSourcePath(SOURCE_PATH_SECOND);
            lb.setPreferredClassName(CLASS_NAME);

            DebuggerManager dm = DebuggerManager.getDebuggerManager ();
            dm.addBreakpoint (lb);

            support = JPDASupport.attach (
                "org.netbeans.api.debugger.jpda.testapps.JspLineBreakpointApp"
            );
            JPDADebugger debugger = support.getDebugger();

            support.waitState (JPDADebugger.STATE_STOPPED);  // breakpoint hit
            assertNotNull(debugger.getCurrentCallStackFrame());
            assertEquals(
                "Debugger stopped at wrong file", 
                lb.getSourcePath(), 
                debugger.getCurrentCallStackFrame().getSourcePath(STRATUM)
            );

            dm.removeBreakpoint (lb);
        } finally {
            if (support != null) support.doFinish ();
        }
    }

    /**
     * Tests debugger's ability to stop in one JSP as many times as this JSP 
     * is included in another page.
     *
     * 1. The user creates JSP (index.jsp, include.jsp) and 
     * 2. statically includes include.jsp twice into index.jsp.
     * 3. Then bp is set in include.jsp (line 2).
     * 
     * Debugger should stop twice in the include.jsp. If debugger didn't stopped 
     * in the include.jsp for the second time, then assertion violation would arise
     * because of testing debugger's state for STOP state.
     */
    public void testBreakpointRepeatability () throws Exception {
        try {
            //install SDE extension to class file
            runSDEInstaller(testAppCLAZ, testAppSMAP);

            //String URL = getClass().getResource("testapps/resources/included.jsp").toString();
            String URL = "file:"+SRC_ROOT+"/org/netbeans/api/debugger/jpda/testapps/resources/included.jsp";
            LineBreakpoint lb = LineBreakpoint.create(URL, LINE_NUMBER);
            lb.setStratum(STRATUM); // NOI18N
            lb.setSourceName(SOURCE_NAME);
            lb.setSourcePath(SOURCE_PATH_SECOND);
            lb.setPreferredClassName(CLASS_NAME);

            DebuggerManager dm = DebuggerManager.getDebuggerManager ();
            dm.addBreakpoint (lb);

            support = JPDASupport.attach (
                "org.netbeans.api.debugger.jpda.testapps.JspLineBreakpointApp"
            );
            JPDADebugger debugger = support.getDebugger();

            support.waitState (JPDADebugger.STATE_STOPPED);  // first breakpoint hit
            support.doContinue ();
            support.waitState (JPDADebugger.STATE_STOPPED);  // second breakpoint hit
            assertTrue(
                "Debugger did not stop at breakpoint for the second time.",
                debugger.getState() == JPDADebugger.STATE_STOPPED
            );
            
            dm.removeBreakpoint (lb);

        } finally {
            if (support != null) support.doFinish ();
        }
    }
    
    private static void runSDEInstaller(String pathToClassFile, String pathToSmapFile) throws IOException {
        SDEInstaller.main(new String[] { pathToClassFile, pathToSmapFile });
    }

//inner classes
    
    private static class SDEInstaller {

        static final String nameSDE = "SourceDebugExtension";

        byte[] orig;
        byte[] sdeAttr;
        byte[] gen;

        int origPos = 0;
        int genPos = 0;

        int sdeIndex;

        private boolean isDebugEnabled() {
            return System.getProperty("sde.SDEInstaller.verbose") != null;
        }

        public static void main(String[] args) throws IOException {
            if (args.length == 2) {
                install(new File(args[0]), new File(args[1]));
            } else if (args.length == 3) {
                install(
                    new File(args[0]),
                    new File(args[1]),
                    new File(args[2]));
            } else {
                System.err.println(
                    "Usage: <command> <input class file> "
                        + "<attribute file> <output class file name>\n"
                        + "<command> <input/output class file> <attribute file>");
            }
        }

        static void install(File inClassFile, File attrFile, File outClassFile)
            throws IOException {
            new SDEInstaller(inClassFile, attrFile, outClassFile);
        }

        static void install(File inOutClassFile, File attrFile)
            throws IOException {
            File tmpFile = new File(inOutClassFile.getPath() + "tmp");
            new SDEInstaller(inOutClassFile, attrFile, tmpFile);
            if (!inOutClassFile.delete()) {
                throw new IOException("inOutClassFile.delete() failed");
            }
            if (!tmpFile.renameTo(inOutClassFile)) {
                throw new IOException("tmpFile.renameTo(inOutClassFile) failed");
            }
        }

        static void install(File classFile, byte[] smap) throws IOException {
            File tmpFile = new File(classFile.getPath() + "tmp");
            new SDEInstaller(classFile, smap, tmpFile);
            if (!classFile.delete()) {
                throw new IOException("classFile.delete() failed");
            }
            if (!tmpFile.renameTo(classFile)) {
                throw new IOException("tmpFile.renameTo(classFile) failed");
            }
        }

        SDEInstaller(File inClassFile, byte[] sdeAttr, File outClassFile)
            throws IOException {
            if (!inClassFile.exists()) {
                throw new FileNotFoundException("no such file: " + inClassFile);
            }

            this.sdeAttr = sdeAttr;
            // get the bytes
            orig = readWhole(inClassFile);
            gen = new byte[orig.length + sdeAttr.length + 100];

            // do it
            addSDE();

            // write result
            FileOutputStream outStream = new FileOutputStream(outClassFile);
            outStream.write(gen, 0, genPos);
            outStream.close();
        }

        SDEInstaller(File inClassFile, File attrFile, File outClassFile)
            throws IOException {
            this(inClassFile, readWhole(attrFile), outClassFile);
        }

        static byte[] readWhole(File input) throws IOException {
            FileInputStream inStream = new FileInputStream(input);
            int len = (int)input.length();
            byte[] bytes = new byte[len];
            if (inStream.read(bytes, 0, len) != len) {
                throw new IOException("expected size: " + len);
            }
            inStream.close();
            return bytes;
        }

        void addSDE() throws UnsupportedEncodingException, IOException {
            int i;
            copy(4 + 2 + 2); // magic min/maj version
            int constantPoolCountPos = genPos;
            int constantPoolCount = readU2();
            if (isDebugEnabled())
                System.out.println("constant pool count: " + constantPoolCount);
            writeU2(constantPoolCount);

            // copy old constant pool return index of SDE symbol, if found
            sdeIndex = copyConstantPool(constantPoolCount);
            if (sdeIndex < 0) {
                // if "SourceDebugExtension" symbol not there add it
                writeUtf8ForSDE();

                // increment the countantPoolCount
                sdeIndex = constantPoolCount;
                ++constantPoolCount;
                randomAccessWriteU2(constantPoolCountPos, constantPoolCount);

                if (isDebugEnabled())
                    System.out.println("SourceDebugExtension not found, installed at: " + sdeIndex);
            } else {
                if (isDebugEnabled())
                    System.out.println("SourceDebugExtension found at: " + sdeIndex);
            }
            copy(2 + 2 + 2); // access, this, super
            int interfaceCount = readU2();
            writeU2(interfaceCount);
            if (isDebugEnabled())
                System.out.println("interfaceCount: " + interfaceCount);
            copy(interfaceCount * 2);
            copyMembers(); // fields
            copyMembers(); // methods
            int attrCountPos = genPos;
            int attrCount = readU2();
            writeU2(attrCount);
            if (isDebugEnabled())
                System.out.println("class attrCount: " + attrCount);
            // copy the class attributes, return true if SDE attr found (not copied)
            if (!copyAttrs(attrCount)) {
                // we will be adding SDE and it isn't already counted
                ++attrCount;
                randomAccessWriteU2(attrCountPos, attrCount);
                if (isDebugEnabled())
                    System.out.println("class attrCount incremented");
            }
            writeAttrForSDE(sdeIndex);
        }

        void copyMembers() {
            int count = readU2();
            writeU2(count);
            if (isDebugEnabled())
                System.out.println("members count: " + count);
            for (int i = 0; i < count; ++i) {
                copy(6); // access, name, descriptor
                int attrCount = readU2();
                writeU2(attrCount);
                if (isDebugEnabled())
                    System.out.println("member attr count: " + attrCount);
                copyAttrs(attrCount);
            }
        }

        boolean copyAttrs(int attrCount) {
            boolean sdeFound = false;
            for (int i = 0; i < attrCount; ++i) {
                int nameIndex = readU2();
                // don't write old SDE
                if (nameIndex == sdeIndex) {
                    sdeFound = true;
                    if (isDebugEnabled())
                        System.out.println("SDE attr found");
                } else {
                    writeU2(nameIndex); // name
                    int len = readU4();
                    writeU4(len);
                    copy(len);
                    if (isDebugEnabled())
                        System.out.println("attr len: " + len);
                }
            }
            return sdeFound;
        }

        void writeAttrForSDE(int index) {
            writeU2(index);
            writeU4(sdeAttr.length);
            for (int i = 0; i < sdeAttr.length; ++i) {
                writeU1(sdeAttr[i]);
            }
        }

        void randomAccessWriteU2(int pos, int val) {
            int savePos = genPos;
            genPos = pos;
            writeU2(val);
            genPos = savePos;
        }

        int readU1() {
            return ((int)orig[origPos++]) & 0xFF;
        }

        int readU2() {
            int res = readU1();
            return (res << 8) + readU1();
        }

        int readU4() {
            int res = readU2();
            return (res << 16) + readU2();
        }

        void writeU1(int val) {
            gen[genPos++] = (byte)val;
        }

        void writeU2(int val) {
            writeU1(val >> 8);
            writeU1(val & 0xFF);
        }

        void writeU4(int val) {
            writeU2(val >> 16);
            writeU2(val & 0xFFFF);
        }

        void copy(int count) {
            for (int i = 0; i < count; ++i) {
                gen[genPos++] = orig[origPos++];
            }
        }

        byte[] readBytes(int count) {
            byte[] bytes = new byte[count];
            for (int i = 0; i < count; ++i) {
                bytes[i] = orig[origPos++];
            }
            return bytes;
        }

        void writeBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; ++i) {
                gen[genPos++] = bytes[i];
            }
        }

        int copyConstantPool(int constantPoolCount)
            throws UnsupportedEncodingException, IOException {
            int sdeIndex = -1;
            // copy const pool index zero not in class file
            for (int i = 1; i < constantPoolCount; ++i) {
                int tag = readU1();
                writeU1(tag);
                switch (tag) {
                    case 7 : // Class
                    case 8 : // String
                        if (isDebugEnabled())
                            System.out.println(i + " copying 2 bytes");
                        copy(2);
                        break;
                    case 9 : // Field
                    case 10 : // Method
                    case 11 : // InterfaceMethod
                    case 3 : // Integer
                    case 4 : // Float
                    case 12 : // NameAndType
                        if (isDebugEnabled())
                            System.out.println(i + " copying 4 bytes");
                        copy(4);
                        break;
                    case 5 : // Long
                    case 6 : // Double
                        if (isDebugEnabled())
                            System.out.println(i + " copying 8 bytes");
                        copy(8);
                        i++;
                        break;
                    case 1 : // Utf8
                        int len = readU2();
                        writeU2(len);
                        byte[] utf8 = readBytes(len);
                        String str = new String(utf8, StandardCharsets.UTF_8);
                        if (isDebugEnabled())
                            System.out.println(i + " read class attr -- '" + str + "'");
                        if (str.equals(nameSDE)) {
                            sdeIndex = i;
                        }
                        writeBytes(utf8);
                        break;
                    default :
                        throw new IOException("unexpected tag: " + tag);
                }
            }
            return sdeIndex;
        }

        void writeUtf8ForSDE() {
            int len = nameSDE.length();
            writeU1(1); // Utf8 tag
            writeU2(len);
            for (int i = 0; i < len; ++i) {
                writeU1(nameSDE.charAt(i));
            }
        }
    }
    
    private class TestBreakpointListener implements JPDABreakpointListener {

        private LineBreakpoint  lineBreakpoint;
        private int             conditionResult;

        private JPDABreakpointEvent event;
        private AssertionError      failure;

        public TestBreakpointListener (LineBreakpoint lineBreakpoint) {
            this (lineBreakpoint, JPDABreakpointEvent.CONDITION_NONE);
        }

        public TestBreakpointListener (
            LineBreakpoint lineBreakpoint, 
            int conditionResult
        ) {
            this.lineBreakpoint = lineBreakpoint;
            this.conditionResult = conditionResult;
        }

        public void breakpointReached (JPDABreakpointEvent event) {
            try {
                checkEvent (event);
            } catch (AssertionError e) {
                failure = e;
            } catch (Throwable e) {
                failure = new AssertionError (e);
            }
        }

        private void checkEvent (JPDABreakpointEvent event) {
            this.event = event;
            assertEquals (
                "Breakpoint event: Wrong source breakpoint", 
                lineBreakpoint, 
                event.getSource ()
            );
            assertNotNull (
                "Breakpoint event: Context thread is null", 
                event.getThread ()
            );

            int result = event.getConditionResult ();
            if ( result == JPDABreakpointEvent.CONDITION_FAILED && 
                 conditionResult != JPDABreakpointEvent.CONDITION_FAILED
            )
                failure = new AssertionError (event.getConditionException ());
            else 
            if (result != conditionResult)
                failure = new AssertionError (
                    "Unexpected breakpoint condition result: " + result
                );
        }

        public void checkResult () {
            if (event == null) {
                CallStackFrame f = support.getDebugger ().
                    getCurrentCallStackFrame ();
                int ln = -1;
                if (f != null) {
                    ln = f.getLineNumber (null);
                }
                throw new AssertionError (
                    "Breakpoint was not hit (listener was not notified) " + ln
                );
            }
            if (failure != null) throw failure;
        }
    }
    
}
