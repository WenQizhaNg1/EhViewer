/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.dao.DaoMaster;
import com.hippo.ehviewer.dao.DaoSession;
import com.hippo.ehviewer.dao.DownloadDirnameDao;
import com.hippo.ehviewer.dao.DownloadDirnameRaw;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabelDao;
import com.hippo.ehviewer.dao.DownloadLabelRaw;
import com.hippo.ehviewer.dao.DownloadsDao;
import com.hippo.ehviewer.dao.GalleryInfoDao;
import com.hippo.ehviewer.dao.GalleryInfoRaw;
import com.hippo.ehviewer.dao.HistoryInfoDao;
import com.hippo.ehviewer.dao.HistoryInfoRaw;
import com.hippo.ehviewer.dao.LocalFavoritesDao;
import com.hippo.ehviewer.dao.LocalFavoritesRaw;
import com.hippo.ehviewer.dao.QuickSearchDao;
import com.hippo.ehviewer.dao.QuickSearchRaw;
import com.hippo.yorozuya.sparse.SparseJLArray;

import java.util.ArrayList;
import java.util.List;

public class EhDB {

    private static final String TAG = EhDB.class.getSimpleName();

    private static DaoSession sDaoSession;

    private static boolean sHasOldDB;
    private static boolean sNewDB;

    private static class DBOpenHelper extends DaoMaster.OpenHelper {

        public DBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            super.onCreate(db);
            sNewDB = true;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static class OldDBHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "data";
        private static final int VERSION = 4;

        private static final String TABLE_GALLERY = "gallery";
        private static final String TABLE_LOCAL_FAVOURITE = "local_favourite";
        private static final String TABLE_TAG = "tag";
        private static final String TABLE_DOWNLOAD = "download";
        private static final String TABLE_HISTORY = "history";

        public OldDBHelper(Context context) {
            super(context, DB_NAME, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    public static void initialize(Context context) {
        sHasOldDB = context.getDatabasePath("data").exists();

        DBOpenHelper helper = new DBOpenHelper(
                context.getApplicationContext(), "eh.db", null);

        SQLiteDatabase db = helper.getWritableDatabase();
        DaoMaster daoMaster = new DaoMaster(db);

        sDaoSession = daoMaster.newSession();
    }

    public static boolean needMerge() {
        return sNewDB && sHasOldDB;
    }

    public static void mergeOldDB(Context context) {
        sNewDB = false;

        OldDBHelper oldDBHelper = new OldDBHelper(context);
        SQLiteDatabase oldDB;
        try {
            oldDB = oldDBHelper.getReadableDatabase();
        } catch (Exception e) {
            return;
        }

        // Get GalleryInfo list
        SparseJLArray<GalleryInfo> map = new SparseJLArray<>();
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_GALLERY, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        GalleryInfo gi = new GalleryInfo();
                        gi.gid = cursor.getInt(0);
                        gi.token = cursor.getString(1);
                        gi.title = cursor.getString(2);
                        gi.posted = cursor.getString(3);
                        gi.category = cursor.getInt(4);
                        gi.thumb = cursor.getString(5);
                        gi.uploader = cursor.getString(5);
                        gi.rating = cursor.getFloat(6);

                        map.put(gi.gid, gi);

                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignore
        }

        // Merge gallery info
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_GALLERY, null);
            if (cursor != null) {
                GalleryInfoDao dao = sDaoSession.getGalleryInfoDao();
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        GalleryInfoRaw raw = new GalleryInfoRaw();
                        raw.setGid((long) cursor.getInt(0));
                        raw.setToken(cursor.getString(1));
                        raw.setTitle(cursor.getString(2));
                        raw.setPosted(cursor.getString(3));
                        raw.setCategory(cursor.getInt(4));
                        raw.setThumb(cursor.getString(5));
                        raw.setUploader(cursor.getString(6));
                        raw.setRating(cursor.getFloat(7));
                        raw.setReference(cursor.getInt(8));
                        dao.insert(raw);
                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignore
        }

        // Merge local favorites
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_LOCAL_FAVOURITE, null);
            if (cursor != null) {
                LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
                if (cursor.moveToFirst()) {
                    long i = 0L;
                    while (!cursor.isAfterLast()) {
                        LocalFavoritesRaw raw = new LocalFavoritesRaw();
                        raw.setGid((long) cursor.getInt(0));
                        raw.setDate(i);
                        dao.insert(raw);
                        cursor.moveToNext();
                        i++;
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignore
        }


        // Merge quick search
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_TAG, null);
            if (cursor != null) {
                QuickSearchDao dao = sDaoSession.getQuickSearchDao();
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        QuickSearchRaw raw = new QuickSearchRaw();

                        int mode = cursor.getInt(2);
                        String search = cursor.getString(4);
                        String tag = cursor.getString(7);
                        if (mode == ListUrlBuilder.MODE_UPLOADER && search != null &&
                                search.startsWith("uploader:")) {
                            search = search.substring("uploader:".length());
                        }

                        raw.setDate((long) cursor.getInt(0));
                        raw.setName(cursor.getString(1));
                        raw.setMode(mode);
                        raw.setCategory(cursor.getInt(3));
                        raw.setKeyword(mode == ListUrlBuilder.MODE_TAG ? tag : search);
                        raw.setAdvanceSearch(cursor.getInt(5));
                        raw.setMinRating(cursor.getInt(6));

                        dao.insert(raw);
                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignore
        }

        // Merge download info
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_DOWNLOAD, null);
            if (cursor != null) {
                DownloadsDao dao = sDaoSession.getDownloadsDao();
                if (cursor.moveToFirst()) {
                    long i = 0L;
                    while (!cursor.isAfterLast()) {
                        // Get GalleryInfo first
                        long gid = cursor.getInt(0);
                        GalleryInfo gi = map.get(gid);
                        if (gi == null) {
                            Log.e(TAG, "Can't get GalleryInfo with gid: " + gid);
                            cursor.moveToNext();
                            continue;
                        }

                        DownloadInfo info = new DownloadInfo(gi);
                        int state = cursor.getInt(2);
                        int legacy = cursor.getInt(3);
                        if (state == DownloadInfo.STATE_FINISH && legacy > 0) {
                            state = DownloadInfo.STATE_FAILED;
                        }
                        info.setState(state);
                        info.setLegacy(legacy);
                        if (cursor.getColumnCount() == 5) {
                            info.setTime(cursor.getLong(4));
                        } else {
                            info.setTime(i);
                        }
                        dao.insert(info);
                        cursor.moveToNext();
                        i++;
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            // Merge history info
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_HISTORY, null);
            if (cursor != null) {
                HistoryInfoDao dao = sDaoSession.getHistoryInfoDao();
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        HistoryInfoRaw raw = new HistoryInfoRaw();
                        raw.setGid((long) cursor.getInt(0));
                        raw.setMode(cursor.getInt(1));
                        raw.setDate(cursor.getLong(2));
                        dao.insert(raw);
                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Ignore
        }

        try {
            oldDBHelper.close();
        } catch (Exception e) {
            // Ignore
        }
    }

    private static synchronized void addGalleryInfo(GalleryInfo galleryInfo) {
        GalleryInfoDao dao = sDaoSession.getGalleryInfoDao();
        GalleryInfoRaw raw = dao.load(galleryInfo.gid);
        if (raw == null) {
            // add new item, set reference 1
            raw = new GalleryInfoRaw();
            raw.setGid(galleryInfo.gid);
            raw.setToken(galleryInfo.token);
            raw.setTitle(galleryInfo.title);
            raw.setThumb(galleryInfo.thumb);
            raw.setCategory(galleryInfo.category);
            raw.setPosted(galleryInfo.posted);
            raw.setUploader(galleryInfo.uploader);
            raw.setRating(galleryInfo.rating);
            raw.setReference(1);
            dao.insert(raw);
        } else {
            // already in db, add reference
            raw.setReference(raw.getReference() + 1);
            dao.update(raw);
        }
    }

    private static synchronized void removeGalleryInfo(int gid) {
        GalleryInfoDao dao = sDaoSession.getGalleryInfoDao();
        GalleryInfoRaw raw = dao.load((long) gid);
        int reference = raw.getReference();
        if (reference <= 1) {
            // No reference, delete it
            dao.deleteByKey((long) gid);
        } else {
            // Still has reference, sub reference
            raw.setReference(reference - 1);
            dao.update(raw);
        }
    }

    public static synchronized GalleryInfo getGalleryInfo(long gid) {
        GalleryInfoDao dao = sDaoSession.getGalleryInfoDao();
        GalleryInfoRaw raw = dao.load(gid);
        if (raw != null) {
            GalleryInfo gi = new GalleryInfo();
            gi.gid = raw.getGid();
            gi.token = raw.getToken();
            gi.title = raw.getTitle();
            gi.thumb = raw.getThumb();
            gi.category = raw.getCategory();
            gi.posted = raw.getPosted();
            gi.uploader = raw.getUploader();
            gi.rating = raw.getRating();
            gi.generateSLang();
            return gi;
        } else {
            return null;
        }
    }

    public static synchronized List<DownloadInfo> getAllDownloadInfos() {
        DownloadsDao dao = sDaoSession.getDownloadsDao();
        List<DownloadInfo> list = dao.queryBuilder().orderAsc(DownloadsDao.Properties.Time).list();
        // Fix state
        for (DownloadInfo info: list) {
            if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
                info.state = DownloadInfo.STATE_NONE;
            }
        }
        return list;
    }

    // Insert or update
    public static synchronized void putDownloadInfo(DownloadInfo downloadInfo) {
        DownloadsDao dao = sDaoSession.getDownloadsDao();
        if (null != dao.load(downloadInfo.gid)) {
            // Update
            dao.update(downloadInfo);
        } else {
            // Insert
            dao.insert(downloadInfo);
        }
    }

    public static synchronized void removeDownloadInfo(long gid) {
        sDaoSession.getDownloadsDao().deleteByKey(gid);
    }

    @Nullable
    public static synchronized String getDownloadDirname(long gid) {
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        DownloadDirnameRaw raw = dao.load(gid);
        if (raw != null) {
            return raw.getDirname();
        } else {
            return null;
        }
    }

    /**
     * Insert or update
     */
    public static synchronized void putDownloadDirname(long gid, String dirname) {
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        DownloadDirnameRaw raw = dao.load(gid);
        if (raw != null) { // Update
            raw.setDirname(dirname);
            dao.update(raw);
        } else { // Insert
            raw = new DownloadDirnameRaw();
            raw.setGid(gid);
            raw.setDirname(dirname);
            dao.insert(raw);
        }
    }

    public static synchronized void removeDownloadDirname(long gid) {
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        dao.deleteByKey(gid);
    }

    @NonNull
    public static synchronized List<DownloadLabelRaw> getAllDownloadLabelList() {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        return dao.queryBuilder().orderAsc(DownloadLabelDao.Properties.Time).list();
    }

    public static synchronized DownloadLabelRaw addDownloadLabel(String label) {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        DownloadLabelRaw raw = new DownloadLabelRaw();
        raw.setLabel(label);
        raw.setTime(System.currentTimeMillis());
        raw.setId(dao.insert(raw));
        return raw;
    }

    public static synchronized void updateDownloadLabel(DownloadLabelRaw raw) {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        dao.update(raw);
    }

    public static synchronized void moveDownloadLabel(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }

        boolean reverse = fromPosition > toPosition;
        int offset = reverse ? toPosition : fromPosition;
        int limit = reverse ? fromPosition - toPosition + 1 : toPosition - fromPosition + 1;

        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        List<DownloadLabelRaw> list = dao.queryBuilder().orderAsc(DownloadLabelDao.Properties.Time)
                .offset(offset).limit(limit).list();

        int step = reverse ? 1 : -1;
        int start = reverse ? limit - 1 : 0;
        int end = reverse ? 0 : limit - 1;
        long toTime = list.get(end).getTime();
        for (int i = end; reverse ? i < start : i > start; i += step) {
            list.get(i).setTime(list.get(i + step).getTime());
        }
        list.get(start).setTime(toTime);

        dao.updateInTx(list);
    }

    public static synchronized void removeDownloadLabel(DownloadLabelRaw raw) {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        dao.delete(raw);
    }

    public static synchronized List<GalleryInfo> getAllLocalFavorites() {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        List<LocalFavoritesRaw> list = dao.queryBuilder().orderAsc(LocalFavoritesDao.Properties.Date).list();
        List<GalleryInfo> result = new ArrayList<>(list.size());
        for (LocalFavoritesRaw raw: list) {
            GalleryInfo gi = getGalleryInfo(raw.getGid());
            if (null == gi) {
                continue;
            }
            result.add(gi);
        }
        return result;
    }

    public static synchronized List<GalleryInfo> searchLocalFavorites(String query) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        List<LocalFavoritesRaw> list = dao.queryBuilder().orderAsc(LocalFavoritesDao.Properties.Date).list();
        List<GalleryInfo> result = new ArrayList<>(list.size());
        for (LocalFavoritesRaw raw: list) {
            GalleryInfo gi = getGalleryInfo((int) (long) raw.getGid());
            if (null == gi || !gi.title.toLowerCase().contains(query.toLowerCase())) {
                continue;
            }
            result.add(gi);
        }
        return result;
    }

    public static synchronized void removeLocalFavorites(long gid) {
        sDaoSession.getLocalFavoritesDao().deleteByKey(gid);
    }

    public static synchronized void removeLocalFavorites(long[] gidArray) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        for (long gid: gidArray) {
            dao.deleteByKey(gid);
        }
    }

    public static synchronized boolean containLocalFavorites(long gid) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        return null != dao.load(gid);
    }

    public static synchronized void addLocalFavorites(GalleryInfo galleryInfo) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        if (null != dao.load(galleryInfo.gid)) {
            // Contained
            return;
        }

        addGalleryInfo(galleryInfo);
        LocalFavoritesRaw raw = new LocalFavoritesRaw();
        raw.setGid(galleryInfo.gid);
        raw.setDate(System.currentTimeMillis());
        dao.insert(raw);
    }

    public static synchronized void addLocalFavorites(List<GalleryInfo> galleryInfoList) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        for (GalleryInfo gi: galleryInfoList) {
            if (null != dao.load(gi.gid)) {
                // Contained
                continue;
            }

            addGalleryInfo(gi);
            LocalFavoritesRaw raw = new LocalFavoritesRaw();
            raw.setGid(gi.gid);
            raw.setDate(System.currentTimeMillis());
            dao.insert(raw);
        }
    }
}
