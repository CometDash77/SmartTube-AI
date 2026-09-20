package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the local diagnostic report of one export (plan section 14, T13).
 *
 * <p>The report is written from an explicit field whitelist. It can never contain an API key, an
 * {@code Authorization} header, a cookie, account data, a signed URL, an HTTP body, subtitle text,
 * a full logcat dump or a preferences dump: those values are simply not read here, and every printed
 * value is forced onto one bounded line.
 *
 * <p>The report works without a timeline, without a configured key and with AI switched off, because
 * a failed snapshot is exactly the case the user has to be able to report.
 */
public final class SubtitleDiagnosticReport {
    public static final int FORMAT_VERSION = 1;
    public static final String FILE_NAME_PREFIX = "SmartTube-diagnostics-";
    public static final String FILE_EXTENSION = "txt";
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String EOL = "\r\n";
    private static final int MAX_VALUE_LENGTH = 80;
    private static final int MAX_EVENTS = SubtitleExportEventLog.MAX_EVENTS;

    /** Every field name the report may print, in output order. */
    public static final String[] WHITELIST = {
            "formatVersion", "generatedAt", "appVersionName", "appVersionCode", "packageName",
            "androidRelease", "androidSdk", "deviceManufacturer", "deviceModel", "storageFreeBytes",
            "aiEnabled", "keyConfigured", "displayMode", "targetLanguage",
            "sourceBound", "sourceType", "sourceMime", "sourceLanguageCode", "sourceVssId",
            "sourceTranslatable", "snapshotStatus",
            "timelineFrames", "timelineItems", "timelineFingerprintPresent",
            "cacheEntries", "cacheBytes", "cacheLimitEntries", "cacheLimitBytes",
            "statsRequests", "statsDeliveredItems", "statsFailedBatches", "statsCancelledBatches",
            "eventCount", "event", "excluded"
    };

    /** Values the platform supplies; all of them are non-personal device/application facts. */
    public static final class Environment {
        private final String mAppVersionName;
        private final int mAppVersionCode;
        private final String mPackageName;
        private final String mAndroidRelease;
        private final int mAndroidSdk;
        private final String mDeviceManufacturer;
        private final String mDeviceModel;
        private final long mStorageFreeBytes;

        public Environment(String appVersionName, int appVersionCode, String packageName,
                           String androidRelease, int androidSdk, String deviceManufacturer,
                           String deviceModel, long storageFreeBytes) {
            mAppVersionName = appVersionName;
            mAppVersionCode = appVersionCode;
            mPackageName = packageName;
            mAndroidRelease = androidRelease;
            mAndroidSdk = androidSdk;
            mDeviceManufacturer = deviceManufacturer;
            mDeviceModel = deviceModel;
            mStorageFreeBytes = storageFreeBytes;
        }

        public static Environment unknown() {
            return new Environment(null, -1, null, null, -1, null, null, -1);
        }

        public String getAppVersionName() {
            return mAppVersionName;
        }

        public int getAppVersionCode() {
            return mAppVersionCode;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public String getAndroidRelease() {
            return mAndroidRelease;
        }

        public int getAndroidSdk() {
            return mAndroidSdk;
        }

        public String getDeviceManufacturer() {
            return mDeviceManufacturer;
        }

        public String getDeviceModel() {
            return mDeviceModel;
        }

        public long getStorageFreeBytes() {
            return mStorageFreeBytes;
        }
    }

    private SubtitleDiagnosticReport() {
    }

    public static byte[] buildUtf8(SubtitleExportSnapshot snapshot, Environment environment) {
        return build(snapshot, environment).getBytes(UTF_8);
    }

    public static String build(SubtitleExportSnapshot snapshot, Environment environment) {
        Environment env = environment != null ? environment : Environment.unknown();
        SubtitleExportSnapshot.Source source = snapshot != null ? snapshot.getSource() : SubtitleExportSnapshot.Source.none();
        SubtitleExportSnapshot.Session session = snapshot != null ? snapshot.getSession() : SubtitleExportSnapshot.Session.idle();
        SubtitleExportSnapshot.Counters counters = snapshot != null ? snapshot.getCounters() : SubtitleExportSnapshot.Counters.empty();
        SubtitleTimeline timeline = snapshot != null ? snapshot.getTimeline() : null;
        Map<String, String> translations = snapshot != null ? snapshot.getTranslations() : null;
        List<String> events = snapshot != null ? snapshot.getEvents() : null;
        SubtitleSrtFormatter.Coverage coverage = SubtitleSrtFormatter.measure(timeline, translations);
        StringBuilder out = new StringBuilder();

        out.append("# SmartTube AI subtitle diagnostic report").append(EOL);
        out.append("# Field whitelist only: no API key, Authorization header, cookie, account data, signed URL,").append(EOL);
        out.append("# HTTP body, subtitle text, logcat or preferences are included.").append(EOL);
        out.append("formatVersion=").append(FORMAT_VERSION).append(EOL);
        out.append("generatedAt=").append(snapshot != null ? date(snapshot.getCreatedAtMs()) : "unknown").append(EOL);
        out.append("appVersionName=").append(value(env.getAppVersionName())).append(EOL);
        out.append("appVersionCode=").append(env.getAppVersionCode()).append(EOL);
        out.append("packageName=").append(value(env.getPackageName())).append(EOL);
        out.append("androidRelease=").append(value(env.getAndroidRelease())).append(EOL);
        out.append("androidSdk=").append(env.getAndroidSdk()).append(EOL);
        out.append("deviceManufacturer=").append(value(env.getDeviceManufacturer())).append(EOL);
        out.append("deviceModel=").append(value(env.getDeviceModel())).append(EOL);
        out.append("storageFreeBytes=").append(env.getStorageFreeBytes()).append(EOL);
        out.append("aiEnabled=").append(session.isAiEnabled()).append(EOL);
        out.append("keyConfigured=").append(session.isKeyConfigured()).append(EOL);
        out.append("displayMode=").append(displayMode(session.getDisplayMode())).append(EOL);
        out.append("targetLanguage=").append(value(session.getTargetLanguage())).append(EOL);
        out.append("sourceBound=").append(source.isBound()).append(EOL);
        out.append("sourceType=").append(value(source.getType())).append(EOL);
        out.append("sourceMime=").append(value(source.getMimeType())).append(EOL);
        out.append("sourceLanguageCode=").append(value(source.getLanguageCode())).append(EOL);
        out.append("sourceVssId=").append(value(source.getVssId())).append(EOL);
        out.append("sourceTranslatable=").append(source.isTranslatable()).append(EOL);
        out.append("snapshotStatus=").append(value(session.getSnapshotStatus())).append(EOL);
        out.append("timelineFrames=").append(timeline != null ? timeline.size() : 0).append(EOL);
        out.append("timelineItems=").append(coverage.getItems()).append(EOL);
        out.append("timelineFingerprintPresent=").append(timeline != null && timeline.getContentFingerprint() != null).append(EOL);
        out.append("cacheEntries=").append(counters.getCacheEntries()).append(EOL);
        out.append("cacheBytes=").append(counters.getCacheBytes()).append(EOL);
        out.append("cacheLimitEntries=").append(SubtitleTranslationCache.MAX_ENTRIES).append(EOL);
        out.append("cacheLimitBytes=").append(SubtitleTranslationCache.MAX_BYTES).append(EOL);
        out.append("statsRequests=").append(counters.getRequests()).append(EOL);
        out.append("statsDeliveredItems=").append(counters.getDeliveredItems()).append(EOL);
        out.append("statsFailedBatches=").append(counters.getFailedBatches()).append(EOL);
        out.append("statsCancelledBatches=").append(counters.getCancelledBatches()).append(EOL);
        out.append("eventCount=").append(events != null ? events.size() : 0).append(EOL);

        if (events != null) {
            for (int i = 0; i < events.size() && i < MAX_EVENTS; i++) {
                out.append("event").append(i).append("=").append(value(events.get(i))).append(EOL);
            }
        }

        out.append("excluded=").append("apiKey,authorization,cookie,account,signedUrl,httpBody,subtitleText,logcat,preferences").append(EOL);

        return out.toString();
    }

    private static String displayMode(int mode) {
        switch (mode) {
            case SubtitleComposer.MODE_TRANSLATION_ONLY:
                return "TRANSLATION_ONLY";
            case SubtitleComposer.MODE_BILINGUAL:
                return "BILINGUAL";
            default:
                return "ORIGINAL_ONLY";
        }
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    /** One printable line of bounded length; an empty or hostile value never breaks the format. */
    static String value(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < raw.length() && out.length() < MAX_VALUE_LENGTH; i++) {
            char c = raw.charAt(i);

            if (c >= ' ' && c != '\u007f' && c != '=') {
                out.append(c);
            } else if (c == '=') {
                out.append('_');
            }
        }

        return out.length() == 0 ? "unknown" : out.toString();
    }
}
