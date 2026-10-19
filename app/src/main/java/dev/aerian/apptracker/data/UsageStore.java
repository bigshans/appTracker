package dev.aerian.apptracker.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import dev.aerian.apptracker.util.HomePackages;

/**
 * Stores daily per-app foreground durations. This table is a cache/snapshot of
 * Android's own UsageStats aggregates, so we do not need to keep a background
 * service running to accumulate time.
 */
public final class UsageStore extends SQLiteOpenHelper {

    private static final String TAG = "UsageStore";
    private static final String DB_NAME = "daily_usage.db";
    private static final int DB_VERSION = 3;

    private static final String TABLE = "daily_usage";
    private static final String TABLE_INTERVALS = "usage_intervals";
    private static final String COL_DAY = "day";
    private static final String COL_PACKAGE = "package_name";
    private static final String COL_LABEL = "app_label";
    private static final String COL_DURATION = "duration_ms";

    private final Context context;

    public UsageStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_DAY + " INTEGER NOT NULL, "
                + COL_PACKAGE + " TEXT NOT NULL, "
                + COL_LABEL + " TEXT, "
                + COL_DURATION + " INTEGER NOT NULL, "
                + "PRIMARY KEY (" + COL_DAY + ", " + COL_PACKAGE + "))");
        db.execSQL("CREATE INDEX idx_daily_usage_pkg ON " + TABLE + " ("
                + COL_PACKAGE + ", " + COL_DAY + ")");
        createIntervalsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            createIntervalsTable(db);
        }
    }

    private static void createIntervalsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_INTERVALS + " ("
                + COL_DAY + " INTEGER NOT NULL, "
                + COL_PACKAGE + " TEXT NOT NULL, "
                + COL_LABEL + " TEXT, "
                + "start_time INTEGER NOT NULL, "
                + "end_time INTEGER NOT NULL, "
                + COL_DURATION + " INTEGER NOT NULL, "
                + "ongoing INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (" + COL_DAY + ", start_time, " + COL_PACKAGE + "))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_intervals_day ON " + TABLE_INTERVALS
                + " (" + COL_DAY + ", start_time)");
    }

    /** Replaces all aggregate rows and intervals for a day. */
    public void replaceDay(long epochDay, List<AppUsage> usage, List<UsageInterval> intervals) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, COL_DAY + " = ?", new String[]{String.valueOf(epochDay)});
            db.delete(TABLE_INTERVALS, COL_DAY + " = ?", new String[]{String.valueOf(epochDay)});
            for (AppUsage item : usage) {
                if (item.durationMs <= 0) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(COL_DAY, epochDay);
                values.put(COL_PACKAGE, item.packageName);
                values.put(COL_LABEL, item.appLabel);
                values.put(COL_DURATION, item.durationMs);
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            for (UsageInterval item : intervals) {
                if (item.durationMs() <= 0) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(COL_DAY, epochDay);
                values.put(COL_PACKAGE, item.packageName);
                values.put(COL_LABEL, item.appLabel);
                values.put("start_time", item.startTime);
                values.put("end_time", item.endTime);
                values.put(COL_DURATION, item.durationMs());
                values.put("ongoing", item.ongoing ? 1 : 0);
                db.insertWithOnConflict(TABLE_INTERVALS, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Unable to replace day", e);
        } finally {
            db.endTransaction();
        }
    }

    /** Visible apps for a day, sorted by duration descending. */
    public List<AppUsage> getDay(long epochDay) {
        List<AppUsage> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{COL_PACKAGE, COL_LABEL, COL_DURATION},
                COL_DAY + " = ?", new String[]{String.valueOf(epochDay)},
                null, null, COL_DURATION + " DESC")) {
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(0);
                if (isHidden(pkg)) {
                    continue;
                }
                result.add(new AppUsage(pkg, cursor.getString(1), cursor.getLong(2)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read day", e);
        }
        return result;
    }

    public long getDayTotal(long epochDay) {
        long total = 0L;
        for (AppUsage usage : getDay(epochDay)) {
            total += usage.durationMs;
        }
        return total;
    }

    /** Daily history for one app, newest first. */
    public List<DayUsage> getAppHistory(String packageName, long startDay, long endDay) {
        List<DayUsage> result = new ArrayList<>();
        if (packageName == null || isHidden(packageName)) {
            return result;
        }
        try (Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{COL_DAY, COL_DURATION},
                COL_PACKAGE + " = ? AND " + COL_DAY + " >= ? AND " + COL_DAY + " <= ?",
                new String[]{packageName, String.valueOf(startDay), String.valueOf(endDay)},
                null, null, COL_DAY + " DESC")) {
            while (cursor.moveToNext()) {
                result.add(new DayUsage(cursor.getLong(0), cursor.getLong(1)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read app history", e);
        }
        return result;
    }

    /** Foreground intervals for one day, oldest first. */
    public List<UsageInterval> getIntervals(long epochDay) {
        List<UsageInterval> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_INTERVALS,
                new String[]{COL_PACKAGE, COL_LABEL, "start_time", "end_time", "ongoing"},
                COL_DAY + " = ?", new String[]{String.valueOf(epochDay)},
                null, null, "start_time DESC")) {
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(0);
                if (isHidden(pkg)) {
                    continue;
                }
                result.add(new UsageInterval(pkg, cursor.getString(1),
                        cursor.getLong(2), cursor.getLong(3), cursor.getInt(4) != 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read intervals", e);
        }
        return result;
    }

    public List<UsageInterval> getIntervalsForPackage(long epochDay, String packageName) {
        List<UsageInterval> result = new ArrayList<>();
        if (packageName == null || isHidden(packageName)) {
            return result;
        }
        try (Cursor cursor = getReadableDatabase().query(TABLE_INTERVALS,
                new String[]{COL_PACKAGE, COL_LABEL, "start_time", "end_time", "ongoing"},
                COL_DAY + " = ? AND " + COL_PACKAGE + " = ?",
                new String[]{String.valueOf(epochDay), packageName},
                null, null, "start_time DESC")) {
            while (cursor.moveToNext()) {
                result.add(new UsageInterval(cursor.getString(0), cursor.getString(1),
                        cursor.getLong(2), cursor.getLong(3), cursor.getInt(4) != 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read package intervals", e);
        }
        return result;
    }

    public List<UsageInterval> getAllIntervals() {
        List<UsageInterval> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_INTERVALS,
                new String[]{COL_PACKAGE, COL_LABEL, "start_time", "end_time", "ongoing"},
                null, null, null, null, "start_time ASC")) {
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(0);
                if (isHidden(pkg)) {
                    continue;
                }
                result.add(new UsageInterval(pkg, cursor.getString(1),
                        cursor.getLong(2), cursor.getLong(3), cursor.getInt(4) != 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read all intervals", e);
        }
        return result;
    }

    public int countIntervals() {
        try (Cursor cursor = getReadableDatabase().query(TABLE_INTERVALS,
                new String[]{COL_PACKAGE}, null, null, null, null, null)) {
            int count = 0;
            while (cursor.moveToNext()) {
                if (!isHidden(cursor.getString(0))) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            Log.e(TAG, "Unable to count intervals", e);
            return 0;
        }
    }

    /** All stored rows, used by CSV export. */
    public List<ExportRow> getAllRows() {
        List<ExportRow> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{COL_DAY, COL_PACKAGE, COL_LABEL, COL_DURATION},
                null, null, null, null, COL_DAY + " ASC, " + COL_DURATION + " DESC")) {
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(1);
                if (isHidden(pkg)) {
                    continue;
                }
                result.add(new ExportRow(cursor.getLong(0), pkg,
                        cursor.getString(2), cursor.getLong(3)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read export rows", e);
        }
        return result;
    }

    public int countRows() {
        try (Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{COL_PACKAGE}, null, null, null, null, null)) {
            int count = 0;
            while (cursor.moveToNext()) {
                if (!isHidden(cursor.getString(0))) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            Log.e(TAG, "Unable to count rows", e);
            return 0;
        }
    }

    public void deleteAll() {
        try {
            getWritableDatabase().delete(TABLE, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Unable to delete data", e);
        }
    }

    private boolean isHidden(String packageName) {
        return BlacklistStore.isBlacklisted(context, packageName)
                || HomePackages.isHome(context, packageName);
    }

    public static final class ExportRow {
        public final long epochDay;
        public final String packageName;
        public final String appLabel;
        public final long durationMs;

        ExportRow(long epochDay, String packageName, String appLabel, long durationMs) {
            this.epochDay = epochDay;
            this.packageName = packageName;
            this.appLabel = appLabel;
            this.durationMs = durationMs;
        }
    }
}
