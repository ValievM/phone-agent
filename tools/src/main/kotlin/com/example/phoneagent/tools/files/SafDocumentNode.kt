package com.example.phoneagent.tools.files

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

private const val DOCUMENT_ID_INDEX = 0
private const val DISPLAY_NAME_INDEX = 1
private const val MIME_TYPE_INDEX = 2
private const val LAST_MODIFIED_INDEX = 3

/**
 * [DocumentNode] backed by the Storage Access Framework: children come from a
 * ContentResolver.query against the document's child-documents URI. Thin Android wrapper by
 * design — no logic lives here (matching and limits are [SafTreeWalker]); per
 * docs/architecture/testing.md this wrapper is verified manually on a device, not by units.
 */
internal class SafDocumentNode(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val documentId: String,
    override val name: String,
    override val mimeType: String?,
    override val dateModifiedEpochMillis: Long?,
) : DocumentNode {
    override val isDirectory: Boolean = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

    override fun children(): List<DocumentNode> {
        if (isDirectory) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
            if (cursor != null) {
                return cursor.use { it.readChildren(resolver, treeUri) }
            }
        }
        return emptyList()
    }

    private companion object {
        // Column order for every projection here: document id, display name, mime type, modified time.
        private val CHILD_PROJECTION = documentProjection()
    }
}

internal object SafDocuments {
    /**
     * Node for the root of a persisted folder tree Uri (from OpenDocumentTree +
     * takePersistableUriPermission), or null when the provider can no longer serve it (e.g. the
     * permission was revoked); callers turn that into a tool failure, not a crash.
     */
    fun rootNode(
        resolver: ContentResolver,
        treeUri: Uri,
    ): SafDocumentNode? {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val cursor = resolver.query(documentUri, ROOT_PROJECTION, null, null, null)
        return cursor?.use { it.readRoot(resolver, treeUri, rootId) }
    }

    private val ROOT_PROJECTION = documentProjection()
}

private fun documentProjection(): Array<String> =
    arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

private fun Cursor.readChildren(
    resolver: ContentResolver,
    treeUri: Uri,
): List<DocumentNode> =
    buildList {
        while (moveToNext()) {
            val childId = getString(DOCUMENT_ID_INDEX)
            val childName = getString(DISPLAY_NAME_INDEX)
            val childMimeType = getString(MIME_TYPE_INDEX)
            val modifiedMillis =
                if (isNull(LAST_MODIFIED_INDEX)) null else getLong(LAST_MODIFIED_INDEX)
            if (childId != null && childName != null) {
                add(
                    SafDocumentNode(
                        resolver = resolver,
                        treeUri = treeUri,
                        documentId = childId,
                        name = childName,
                        mimeType = childMimeType,
                        dateModifiedEpochMillis = modifiedMillis,
                    ),
                )
            }
        }
    }

private fun Cursor.readRoot(
    resolver: ContentResolver,
    treeUri: Uri,
    rootId: String,
): SafDocumentNode? =
    if (moveToFirst()) {
        val mimeType = getString(MIME_TYPE_INDEX)
        SafDocumentNode(
            resolver = resolver,
            treeUri = treeUri,
            documentId = rootId,
            name = getString(DISPLAY_NAME_INDEX) ?: "",
            mimeType = mimeType,
            dateModifiedEpochMillis = if (isNull(LAST_MODIFIED_INDEX)) null else getLong(LAST_MODIFIED_INDEX),
        )
    } else {
        null
    }
