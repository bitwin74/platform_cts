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

// Don't edit this file!  It is auto-generated by frameworks/rs/api/generate.sh.

package android.renderscript.cts;

import android.renderscript.Allocation;
import android.renderscript.RSRuntimeException;
import android.renderscript.Element;

public class TestErf extends RSBaseCompute {

    private ScriptC_TestErf script;
    private ScriptC_TestErfRelaxed scriptRelaxed;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        script = new ScriptC_TestErf(mRS);
        scriptRelaxed = new ScriptC_TestErfRelaxed(mRS);
    }

    public class ArgumentsFloatFloat {
        public float inV;
        public Target.Floaty out;
    }

    private void checkErfFloatFloat() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 1, 0xa3951fb8l, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            script.forEach_testErfFloatFloat(inV, out);
            verifyResultsErfFloatFloat(inV, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloatFloat: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 1), INPUTSIZE);
            scriptRelaxed.forEach_testErfFloatFloat(inV, out);
            verifyResultsErfFloatFloat(inV, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloatFloat: " + e.toString());
        }
    }

    private void verifyResultsErfFloatFloat(Allocation inV, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 1];
        inV.copyTo(arrayInV);
        float[] arrayOut = new float[INPUTSIZE * 1];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 1 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloat args = new ArgumentsFloatFloat();
                args.inV = arrayInV[i];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeErf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 1 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    appendVariableToMessage(message, args.inV);
                    message.append("\n");
                    message.append("Expected output out: ");
                    appendVariableToMessage(message, args.out);
                    message.append("\n");
                    message.append("Actual   output out: ");
                    appendVariableToMessage(message, arrayOut[i * 1 + j]);
                    if (!args.out.couldBe(arrayOut[i * 1 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkErfFloatFloat" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkErfFloat2Float2() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 2, 0x448c58dcl, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            script.forEach_testErfFloat2Float2(inV, out);
            verifyResultsErfFloat2Float2(inV, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloat2Float2: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 2), INPUTSIZE);
            scriptRelaxed.forEach_testErfFloat2Float2(inV, out);
            verifyResultsErfFloat2Float2(inV, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloat2Float2: " + e.toString());
        }
    }

    private void verifyResultsErfFloat2Float2(Allocation inV, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 2];
        inV.copyTo(arrayInV);
        float[] arrayOut = new float[INPUTSIZE * 2];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 2 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloat args = new ArgumentsFloatFloat();
                args.inV = arrayInV[i * 2 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeErf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 2 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    appendVariableToMessage(message, args.inV);
                    message.append("\n");
                    message.append("Expected output out: ");
                    appendVariableToMessage(message, args.out);
                    message.append("\n");
                    message.append("Actual   output out: ");
                    appendVariableToMessage(message, arrayOut[i * 2 + j]);
                    if (!args.out.couldBe(arrayOut[i * 2 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkErfFloat2Float2" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkErfFloat3Float3() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 3, 0x3aa779bal, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            script.forEach_testErfFloat3Float3(inV, out);
            verifyResultsErfFloat3Float3(inV, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloat3Float3: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 3), INPUTSIZE);
            scriptRelaxed.forEach_testErfFloat3Float3(inV, out);
            verifyResultsErfFloat3Float3(inV, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloat3Float3: " + e.toString());
        }
    }

    private void verifyResultsErfFloat3Float3(Allocation inV, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 4];
        inV.copyTo(arrayInV);
        float[] arrayOut = new float[INPUTSIZE * 4];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 3 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloat args = new ArgumentsFloatFloat();
                args.inV = arrayInV[i * 4 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeErf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    appendVariableToMessage(message, args.inV);
                    message.append("\n");
                    message.append("Expected output out: ");
                    appendVariableToMessage(message, args.out);
                    message.append("\n");
                    message.append("Actual   output out: ");
                    appendVariableToMessage(message, arrayOut[i * 4 + j]);
                    if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkErfFloat3Float3" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    private void checkErfFloat4Float4() {
        Allocation inV = createRandomAllocation(mRS, Element.DataType.FLOAT_32, 4, 0x30c29a98l, false);
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            script.forEach_testErfFloat4Float4(inV, out);
            verifyResultsErfFloat4Float4(inV, out, false);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloat4Float4: " + e.toString());
        }
        try {
            Allocation out = Allocation.createSized(mRS, getElement(mRS, Element.DataType.FLOAT_32, 4), INPUTSIZE);
            scriptRelaxed.forEach_testErfFloat4Float4(inV, out);
            verifyResultsErfFloat4Float4(inV, out, true);
        } catch (Exception e) {
            throw new RSRuntimeException("RenderScript. Can't invoke forEach_testErfFloat4Float4: " + e.toString());
        }
    }

    private void verifyResultsErfFloat4Float4(Allocation inV, Allocation out, boolean relaxed) {
        float[] arrayInV = new float[INPUTSIZE * 4];
        inV.copyTo(arrayInV);
        float[] arrayOut = new float[INPUTSIZE * 4];
        out.copyTo(arrayOut);
        for (int i = 0; i < INPUTSIZE; i++) {
            for (int j = 0; j < 4 ; j++) {
                // Extract the inputs.
                ArgumentsFloatFloat args = new ArgumentsFloatFloat();
                args.inV = arrayInV[i * 4 + j];
                // Figure out what the outputs should have been.
                Target target = new Target(relaxed);
                CoreMathVerifier.computeErf(args, target);
                // Validate the outputs.
                boolean valid = true;
                if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                    valid = false;
                }
                if (!valid) {
                    StringBuilder message = new StringBuilder();
                    message.append("Input inV: ");
                    appendVariableToMessage(message, args.inV);
                    message.append("\n");
                    message.append("Expected output out: ");
                    appendVariableToMessage(message, args.out);
                    message.append("\n");
                    message.append("Actual   output out: ");
                    appendVariableToMessage(message, arrayOut[i * 4 + j]);
                    if (!args.out.couldBe(arrayOut[i * 4 + j])) {
                        message.append(" FAIL");
                    }
                    message.append("\n");
                    assertTrue("Incorrect output for checkErfFloat4Float4" +
                            (relaxed ? "_relaxed" : "") + ":\n" + message.toString(), valid);
                }
            }
        }
    }

    public void testErf() {
        checkErfFloatFloat();
        checkErfFloat2Float2();
        checkErfFloat3Float3();
        checkErfFloat4Float4();
    }
}
