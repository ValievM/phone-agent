package com.example.phoneagent.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.warn
import com.example.phoneagent.ui.sanitizeUntrustedUiText
import kotlinx.coroutines.CancellationException

private const val TAG = "SearchFolderDisplayName"

internal fun readSearchFolderDisplayName(
    contentResolver: ContentResolver,
    treeUriString: String,
    logger: AppLogger,
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
    } catch (exception: Exception) {
        logFolderDisplayNameFailure(logger, exception)
        null
    }

internal fun logFolderDisplayNameFailure(
    logger: AppLogger,
    exception: Exception,
) {
    logger.warn(
        TAG,
        "read search folder display name failed",
        fields = mapOf("exceptionType" to exception::class.java.simpleName),
    )
}
