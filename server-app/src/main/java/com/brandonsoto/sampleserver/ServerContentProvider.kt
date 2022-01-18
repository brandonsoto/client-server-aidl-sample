package com.brandonsoto.sampleserver

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH). apply {
    addURI(ServerContentProvider.PROVIDER_NAME, "objects", 1)
    addURI(ServerContentProvider.PROVIDER_NAME, "objects/#", 2)
}

class ServerContentProvider : ContentProvider() {
    companion object {
        val PROVIDER_NAME = "com.brandonsoto.sample_server.ServerContentProvider"
        val URL = "content://$PROVIDER_NAME/objects"
        val CONTENT_URI = Uri.parse(URL)

        val _ID = "_id"
        val NAME = "name"
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        TODO("Not yet implemented")
    }

    override fun getType(uri: Uri): String? {
        TODO("Not yet implemented")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        TODO("Not yet implemented")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        TODO("Not yet implemented")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        TODO("Not yet implemented")
    }
}