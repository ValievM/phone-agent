package com.example.phoneagent.tools.files

import android.content.Context
import android.net.Uri
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.warn
import com.example.phoneagent.domain.storage.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Resolves the persisted SAF tree permission into the root node used by find_files. */
internal class RealSelectedFolder
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        @param:ApplicationContext private val context: Context,
        private val logger: AppLogger,
    ) : SelectedFolder {
        override suspend fun root(): DocumentNode? =
            try {
                settingsRepository.load().searchFolderUri?.let { uri ->
                    SafDocuments.rootNode(context.contentResolver, Uri.parse(uri))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn(
                    TAG,
                    "resolve selected folder failed",
                    fields = mapOf("exceptionType" to exception::class.java.simpleName),
                    throwable = exception,
                )
                null
            }

        private companion object {
            const val TAG = "SelectedFolder"
        }
    }
