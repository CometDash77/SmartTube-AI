package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.os.Environment;

import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;

import java.io.File;

/**
 * Reads the non-personal platform facts the diagnostic report is allowed to print (plan section 14,
 * T13: application, Android and device version).
 *
 * <p>No account, no sign-in state and nothing from the preference store is read here.
 */
public final class SubtitleDiagnosticEnvironment {
    private SubtitleDiagnosticEnvironment() {
    }

    public static SubtitleDiagnosticReport.Environment read(Context context) {
        if (context == null) {
            return SubtitleDiagnosticReport.Environment.unknown();
        }

        return new SubtitleDiagnosticReport.Environment(
                AppInfoHelpers.getAppVersionName(context),
                AppInfoHelpers.getAppVersionCode(context),
                context.getPackageName(),
                android.os.Build.VERSION.RELEASE,
                AppInfoHelpers.getRealSdkVersion(context),
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
                freeStorageBytes());
    }

    private static long freeStorageBytes() {
        File root = Environment.getExternalStorageDirectory();

        return root != null ? root.getUsableSpace() : -1;
    }
}
