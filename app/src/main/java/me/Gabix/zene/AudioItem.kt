package me.Gabix.zene

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val contentUri: Uri
)