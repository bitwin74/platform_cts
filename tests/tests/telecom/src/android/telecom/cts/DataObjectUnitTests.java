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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.*;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.telecom.CallAudioState;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.test.InstrumentationTestCase;

import com.android.cts.telecom.R;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies the parcelable interface of all the telecom objects.
 */
public class DataObjectUnitTests extends InstrumentationTestCase {


    /**
     * Tests the PhoneAccount object creation and recreation from a Parcel.
     */
    public void testPhoneAccount() throws Exception {
        Context context = getInstrumentation().getContext();
        PhoneAccountHandle accountHandle = new PhoneAccountHandle(
                new ComponentName(PACKAGE, COMPONENT),
                ACCOUNT_ID);
        Icon phoneIcon = Icon.createWithResource(context, R.drawable.ic_phone_24dp);
        Uri tel = Uri.parse("tel:555-TEST");
        PhoneAccount account = PhoneAccount.builder(
                accountHandle, LABEL)
                .setAddress(tel)
                .setSubscriptionAddress(tel)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setHighlightColor(Color.RED)
                .setShortDescription(LABEL)
                .setSupportedUriSchemes(Arrays.asList("tel"))
                .setIcon(phoneIcon)
                .build();
        assertNotNull(account);
        assertEquals(accountHandle, account.getAccountHandle());
        assertEquals(tel, account.getAddress());
        assertEquals(tel, account.getSubscriptionAddress());
        assertEquals(PhoneAccount.CAPABILITY_CALL_PROVIDER, account.getCapabilities());
        assertEquals(Color.RED, account.getHighlightColor());
        assertEquals(LABEL, account.getShortDescription());
        assertEquals(Arrays.asList("tel"), account.getSupportedUriSchemes());
        assertEquals(phoneIcon.toString(), account.getIcon().toString());
        assertEquals(0, account.describeContents());

        // Create a parcel of the object and recreate the object back
        // from the parcel.
        Parcel p = Parcel.obtain();
        account.writeToParcel(p, 0);
        p.setDataPosition(0);
        PhoneAccount parcelAccount = PhoneAccount.CREATOR.createFromParcel(p);
        assertNotNull(parcelAccount);
        assertEquals(accountHandle, parcelAccount.getAccountHandle());
        assertEquals(tel, parcelAccount.getAddress());
        assertEquals(tel, parcelAccount.getSubscriptionAddress());
        assertEquals(PhoneAccount.CAPABILITY_CALL_PROVIDER, parcelAccount.getCapabilities());
        assertEquals(Color.RED, parcelAccount.getHighlightColor());
        assertEquals(LABEL, parcelAccount.getShortDescription());
        assertEquals(Arrays.asList("tel"), parcelAccount.getSupportedUriSchemes());
        assertEquals(phoneIcon.toString(), parcelAccount.getIcon().toString());
        assertEquals(0, parcelAccount.describeContents());
        p.recycle();
    }

    /**
     * Tests the ConnectionRequest object creation and recreation from a Parcel.
     */
    public void testConnectionRequest() throws Exception {
        PhoneAccountHandle accountHandle = new PhoneAccountHandle(
                new ComponentName(PACKAGE, COMPONENT),
                ACCOUNT_ID);
        Bundle extras = new Bundle();
        extras.putString(
                TelecomManager.GATEWAY_PROVIDER_PACKAGE,
                PACKAGE);
        ConnectionRequest request = new ConnectionRequest(
                accountHandle,
                Uri.parse("tel:555-TEST"),
                extras,
                VideoProfile.STATE_AUDIO_ONLY);
        assertEquals(accountHandle, request.getAccountHandle());
        assertEquals(Uri.parse("tel:555-TEST"), request.getAddress());
        assertEquals(extras.getString(
                TelecomManager.GATEWAY_PROVIDER_PACKAGE),
                request.getExtras().getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE));
        assertEquals(VideoProfile.STATE_AUDIO_ONLY, request.getVideoState());
        assertEquals(0, request.describeContents());

        // Create a parcel of the object and recreate the object back
        // from the parcel.
        Parcel p = Parcel.obtain();
        request.writeToParcel(p, 0);
        p.setDataPosition(0);
        ConnectionRequest parcelRequest = ConnectionRequest.CREATOR.createFromParcel(p);
        assertEquals(accountHandle, parcelRequest.getAccountHandle());
        assertEquals(Uri.parse("tel:555-TEST"), parcelRequest.getAddress());
        assertEquals(
                extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE),
                parcelRequest.getExtras().getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE));
        assertEquals(VideoProfile.STATE_AUDIO_ONLY, parcelRequest.getVideoState());
        assertEquals(0, parcelRequest.describeContents());
        p.recycle();
    }

    /**
     * Tests the DisconnectCause object creation and recreation from a Parcel.
     */
    public void testDisconnectCause() throws Exception {
        Context context = getInstrumentation().getContext();
        final CharSequence label = "Out of service area";
        final CharSequence description = "Mobile network not available";
        final String reason = "CTS Testing";
        DisconnectCause cause = new DisconnectCause(
                DisconnectCause.ERROR,
                label,
                description,
                reason,
                ToneGenerator.TONE_CDMA_CALLDROP_LITE);
        assertEquals(DisconnectCause.ERROR, cause.getCode());
        assertEquals(label, cause.getLabel());
        assertEquals(description, cause.getDescription());
        assertEquals(reason, cause.getReason());
        assertEquals(ToneGenerator.TONE_CDMA_CALLDROP_LITE, cause.getTone());
        assertEquals(0, cause.describeContents());

        // Create a parcel of the object and recreate the object back
        // from the parcel.
        Parcel p = Parcel.obtain();
        cause.writeToParcel(p, 0);
        p.setDataPosition(0);
        DisconnectCause parcelCause = DisconnectCause.CREATOR.createFromParcel(p);
        assertEquals(DisconnectCause.ERROR, parcelCause.getCode());
        assertEquals(label, parcelCause.getLabel());
        assertEquals(description, parcelCause.getDescription());
        assertEquals(reason, parcelCause.getReason());
        assertEquals(ToneGenerator.TONE_CDMA_CALLDROP_LITE, parcelCause.getTone());
        assertEquals(0, parcelCause.describeContents());
        assertEquals(cause, parcelCause);
        p.recycle();
    }

    /**
     * Tests the StatusHints object creation and recreation from a Parcel.
     */
    public void testStatusHints() throws Exception {
        Context context = getInstrumentation().getContext();
        final CharSequence label = "Wi-Fi call";
        Bundle extras = new Bundle();
        extras.putString(
                TelecomManager.GATEWAY_PROVIDER_PACKAGE,
                PACKAGE);
        Icon icon = Icon.createWithResource(context, R.drawable.ic_phone_24dp);
        StatusHints hints = new StatusHints(
                label,
                icon,
                extras);
        assertEquals(label, hints.getLabel());
        assertEquals(icon.toString(), hints.getIcon().toString());
        assertEquals(extras.getString(
                TelecomManager.GATEWAY_PROVIDER_PACKAGE),
                hints.getExtras().getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE));
        assertEquals(0, hints.describeContents());

        // Create a parcel of the object and recreate the object back
        // from the parcel.
        Parcel p = Parcel.obtain();
        hints.writeToParcel(p, 0);
        p.setDataPosition(0);
        StatusHints parcelHints = StatusHints.CREATOR.createFromParcel(p);
        assertEquals(label, parcelHints.getLabel());
        assertEquals(icon.toString(), parcelHints.getIcon().toString());
        assertEquals(
                extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE),
                parcelHints.getExtras().getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE));
        assertEquals(0, parcelHints.describeContents());
        // This fails because Bundle does not have a equals implementation.
        // assertEquals(hints, parcelHints);
        p.recycle();
    }

    /**
     * Tests the GatewayInfo object creation and recreation from a Parcel.
     */
    public void testGatewayInfo() throws Exception {
        final CharSequence label = "Wi-Fi call";
        Uri originalAddress = Uri.parse("http://www.google.com");
        Uri gatewayAddress = Uri.parse("http://www.google.com");
        GatewayInfo info = new GatewayInfo(
                PACKAGE,
                gatewayAddress,
                originalAddress);
        assertEquals(PACKAGE, info.getGatewayProviderPackageName());
        assertEquals(gatewayAddress, info.getGatewayAddress());
        assertEquals(originalAddress, info.getOriginalAddress());
        assertEquals(0, info.describeContents());

        // Create a parcel of the object and recreate the object back
        // from the parcel.
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        GatewayInfo parcelInfo = GatewayInfo.CREATOR.createFromParcel(p);
        assertEquals(PACKAGE, parcelInfo.getGatewayProviderPackageName());
        assertEquals(gatewayAddress, parcelInfo.getGatewayAddress());
        assertEquals(originalAddress, parcelInfo.getOriginalAddress());
        assertEquals(0, parcelInfo.describeContents());
        p.recycle();
    }

    /**
     * Tests the CallAudioState object creation and recreation from a Parcel.
     */
    public void testCallAudioState() throws Exception {
        CallAudioState audioState = new CallAudioState(
                true,
                CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_WIRED_OR_EARPIECE);
        assertEquals(true, audioState.isMuted());
        assertEquals(CallAudioState.ROUTE_EARPIECE, audioState.getRoute());
        assertEquals(CallAudioState.ROUTE_WIRED_OR_EARPIECE, audioState.getSupportedRouteMask());
        assertEquals(0, audioState.describeContents());

        // Create a parcel of the object and recreate the object back
        // from the parcel.
        Parcel p = Parcel.obtain();
        audioState.writeToParcel(p, 0);
        p.setDataPosition(0);
        CallAudioState parcelAudioState = CallAudioState.CREATOR.createFromParcel(p);
        assertEquals(true, parcelAudioState.isMuted());
        assertEquals(CallAudioState.ROUTE_EARPIECE, parcelAudioState.getRoute());
        assertEquals(CallAudioState.ROUTE_WIRED_OR_EARPIECE, parcelAudioState.getSupportedRouteMask());
        assertEquals(0, parcelAudioState.describeContents());
        assertEquals(audioState, parcelAudioState);
        p.recycle();
    }
}
