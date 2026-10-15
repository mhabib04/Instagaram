package com.example.instagaram.utilities;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.List;

/**
 * Pages through the posts of followed accounts, newest first.
 *
 * Firestore's "in" filter takes at most 30 values, so the followed ids are split into groups of 30.
 * Each group is queried with its own cursor and the results are merged by timestamp, so every page
 * is the true next {@code pageSize} posts across all groups. Requires the composite index
 * posts(userId ASC, timestamp DESC).
 */
public final class FeedPager {

    private static final int MAX_IN_VALUES = 30;

    /** One group of up to 30 followed ids with its own query cursor. */
    private static final class Source {
        final Query query;
        final Deque<DocumentSnapshot> buffer = new ArrayDeque<>();
        DocumentSnapshot cursor;
        boolean exhausted;

        Source(Query query) {
            this.query = query;
        }
    }

    private final List<Source> sources = new ArrayList<>();
    private final int pageSize;

    public FeedPager(@NonNull Collection<String> followingIds, int pageSize) {
        this.pageSize = pageSize;
        List<String> ids = new ArrayList<>(followingIds);
        for (int i = 0; i < ids.size(); i += MAX_IN_VALUES) {
            sources.add(new Source(FirebaseFirestore.getInstance()
                    .collection(Constants.KEY_COLLECTION_POSTS)
                    .whereIn(Constants.KEY_USER_ID, ids.subList(i, Math.min(i + MAX_IN_VALUES, ids.size())))
                    .orderBy(Constants.KEY_TIMESTAMP, Query.Direction.DESCENDING)));
        }
    }

    /** True while at least one group may still have older posts. */
    public boolean hasMore() {
        for (Source source : sources) {
            if (!source.exhausted || !source.buffer.isEmpty()) return true;
        }
        return false;
    }

    /** Loads the next page: tops up every group's buffer, then takes the newest posts across them. */
    public Task<List<DocumentSnapshot>> nextPage() {
        List<Task<QuerySnapshot>> fetches = new ArrayList<>();
        List<Source> fetching = new ArrayList<>();
        for (Source source : sources) {
            if (source.exhausted || source.buffer.size() >= pageSize) continue;
            Query query = source.query.limit(pageSize);
            if (source.cursor != null) query = query.startAfter(source.cursor);
            fetches.add(query.get());
            fetching.add(source);
        }
        return Tasks.whenAllComplete(fetches).continueWith(done -> {
            for (int i = 0; i < fetches.size(); i++) {
                Task<QuerySnapshot> fetch = fetches.get(i);
                // Surface the real error (e.g. the missing-index message with its creation link).
                if (!fetch.isSuccessful()) throw fetch.getException();
                Source source = fetching.get(i);
                List<DocumentSnapshot> documents = fetch.getResult().getDocuments();
                source.buffer.addAll(documents);
                if (!documents.isEmpty()) source.cursor = documents.get(documents.size() - 1);
                if (documents.size() < pageSize) source.exhausted = true;
            }
            List<DocumentSnapshot> page = new ArrayList<>();
            while (page.size() < pageSize) {
                Source newest = null;
                for (Source source : sources) {
                    if (source.buffer.isEmpty()) continue;
                    if (newest == null || timeOf(source.buffer.peekFirst()).after(timeOf(newest.buffer.peekFirst()))) {
                        newest = source;
                    }
                }
                if (newest == null) break;
                page.add(newest.buffer.pollFirst());
            }
            return page;
        });
    }

    private static Date timeOf(DocumentSnapshot post) {
        Date date = post.getDate(Constants.KEY_TIMESTAMP);
        return date == null ? new Date(0) : date;
    }
}
