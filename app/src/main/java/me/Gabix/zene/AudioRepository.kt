package me.Gabix.zene

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object AudioRepository {
    private const val TAG = "AudioRepository"

    fun fetchAudioFiles(context: Context): List<AudioItem> {
        val audioList = mutableListOf<AudioItem>()

        try {
            queryInternal(context, audioList, "${MediaStore.Audio.Media.IS_MUSIC} != 0")
        } catch (t: Throwable) {
            Log.e(TAG, "Query 1 failed", t)
        }

        if (audioList.isEmpty()) {
            try {
                queryInternal(context, audioList, "${MediaStore.Audio.Media.DURATION} > 10000")
            } catch (t: Throwable) {
                Log.e(TAG, "Query 2 failed", t)
            }
        }

        return audioList
    }

    private fun queryInternal(
        context: Context,
        output: MutableList<AudioItem>,
        selection: String?
    ) {
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.IS_PENDING)
        }

        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            uri,
            projection.toTypedArray(),
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val isPendingColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_PENDING)

            if (idColumn < 0) return

            while (cursor.moveToNext()) {
                if (isPendingColumn >= 0 && cursor.getInt(isPendingColumn) != 0) continue

                val id = cursor.getLong(idColumn)
                val title = if (titleColumn >= 0) cursor.getString(titleColumn) ?: "Ismeretlen cím" else "Ismeretlen cím"
                val artist = if (artistColumn >= 0) cursor.getString(artistColumn) ?: "Ismeretlen előadó" else "Ismeretlen előadó"
                val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L

                val contentUri = ContentUris.withAppendedId(uri, id)
                output.add(AudioItem(id, title, artist, duration, contentUri))
            }
        }
    }
}