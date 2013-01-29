/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.actfm.sync;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.service.Autowired;
import com.todoroo.andlib.service.ContextManager;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.andlib.sql.Criterion;
import com.todoroo.andlib.sql.Functions;
import com.todoroo.andlib.sql.Join;
import com.todoroo.andlib.sql.Query;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.dao.UserDao;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.RemoteModel;
import com.todoroo.astrid.data.TagData;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.service.MetadataService;
import com.todoroo.astrid.service.TagDataService;
import com.todoroo.astrid.tags.TagService;

public final class ActFmDataService {

    // --- constants

    /** Utility for joining tasks with metadata */
    public static final Join METADATA_JOIN = Join.left(Metadata.TABLE, Task.ID.eq(Metadata.TASK));

    // --- instance variables

    protected final Context context;

    @Autowired TaskDao taskDao;

    @Autowired UserDao userDao;

    @Autowired MetadataService metadataService;

    @Autowired ActFmPreferenceService actFmPreferenceService;

    @Autowired TagDataService tagDataService;

    public ActFmDataService() {
        this.context = ContextManager.getContext();
        DependencyInjectionService.getInstance().inject(this);
    }

    // --- task and metadata methods

    /**
     * Save / Merge JSON tagData
     * @param tagObject
     * @throws JSONException
     */
    @SuppressWarnings("nls")
    public void saveTagData(JSONObject tagObject) throws JSONException {
        TodorooCursor<TagData> cursor = tagDataService.query(Query.select(TagData.PROPERTIES).where(
                Criterion.or(TagData.UUID.eq(tagObject.get("id")),
                        Criterion.and(TagData.UUID.eq(RemoteModel.NO_UUID),
                        TagData.NAME.eqCaseInsensitive(tagObject.getString("name"))))));
        try {
            cursor.moveToNext();
            TagData tagData = new TagData();
            if(!cursor.isAfterLast()) {
                tagData.readFromCursor(cursor);
                if(!tagData.getValue(TagData.NAME).equals(tagObject.getString("name")))
                    TagService.getInstance().rename(tagData.getValue(TagData.NAME), tagObject.getString("name"));
                cursor.moveToNext();
            }
            ActFmSyncService.JsonHelper.tagFromJson(tagObject, tagData);
            tagDataService.save(tagData);

            // delete the rest

            for(; !cursor.isAfterLast(); cursor.moveToNext()) {
                tagData.readFromCursor(cursor);
                if(!tagData.getValue(TagData.NAME).equals(tagObject.getString("name")))
                    TagService.getInstance().rename(tagData.getValue(TagData.NAME), tagObject.getString("name"));
                tagDataService.delete(tagData.getId());
            }
        } finally {
            cursor.close();
        }
    }

    @SuppressWarnings("nls")
    public void saveFeaturedList(JSONObject featObject) throws JSONException {
        TodorooCursor<TagData> cursor = tagDataService.query(Query.select(TagData.PROPERTIES).where(
                Criterion.and(Functions.bitwiseAnd(TagData.FLAGS, TagData.FLAG_FEATURED).gt(0), TagData.UUID.eq(featObject.get("id")))));
        try {
            cursor.moveToNext();
            TagData tagData = new TagData();
            if (!cursor.isAfterLast()) {
                tagData.readFromCursor(cursor);
                if(!tagData.getValue(TagData.NAME).equals(featObject.getString("name")))
                    TagService.getInstance().rename(tagData.getValue(TagData.NAME), featObject.getString("name"));
                cursor.moveToNext();
            }
            ActFmSyncService.JsonHelper.featuredListFromJson(featObject, tagData);
            tagDataService.save(tagData);

        } finally {
            cursor.close();
        }
    }
}
