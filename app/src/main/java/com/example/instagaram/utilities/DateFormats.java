package com.example.instagaram.utilities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.instagaram.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Locale-aware date strings; the patterns live in values/ and values-in/ strings. */
public final class DateFormats {

    private DateFormats() { }

    /**
     * When a like happened, short form: "14.39" today, "Kemarin 14.39", "6 Okt, 14.39" this year,
     * "6 Okt 2025" before that (English equivalents in values/strings.xml). Empty for old likes
     * that were stored without a timestamp.
     */
    @NonNull
    public static String likeTime(@NonNull Context context, @Nullable Date date) {
        if (date == null) return "";
        Calendar when = Calendar.getInstance();
        when.setTime(date);
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        int pattern;
        if (sameDay(when, today)) {
            pattern = R.string.like_time_today_pattern;
        } else if (sameDay(when, yesterday)) {
            pattern = R.string.like_time_yesterday_pattern;
        } else if (when.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            pattern = R.string.like_time_this_year_pattern;
        } else {
            pattern = R.string.like_time_older_pattern;
        }
        return new SimpleDateFormat(context.getString(pattern), Locale.getDefault()).format(date);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Post timestamp, e.g. "14.00 - 1 Januari 2026" (id) or "02.00 PM - January 1, 2026" (en). */
    @NonNull
    public static String postDate(@NonNull Context context, @Nullable Date date) {
        if (date == null) return "";
        return new SimpleDateFormat(context.getString(R.string.post_date_pattern), Locale.getDefault())
                .format(date);
    }
}
