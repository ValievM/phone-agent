package com.example.phoneagent.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.phoneagent.ui.sanitizeUntrustedUiText
import kotlinx.coroutines.CancellationException

internal fun readSearchFolderDisplayName(
    contentResolver: ContentResolver,
    treeUriString: String,
): String? =
    try {
        val treeUri = Uri.parse(treeUriString)
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        contentResolver
            .query(
                rootDocumentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor
                        .getString(0)
                        ?.let(::sanitizeUntrustedUiText)
                        ?.takeIf(String::isNotBlank)
                } else {
                    null
                }
            }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        null
    }
