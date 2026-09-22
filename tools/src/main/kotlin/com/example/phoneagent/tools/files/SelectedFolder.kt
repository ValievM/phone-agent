package com.example.phoneagent.tools.files

/**
 * Access to the folder the user pre-selected in the app settings as the find_files search scope.
 * Returning null means "no folder selected yet" — the tool reports a setup error and the UI can
 * offer opening settings. Implementations must only ever expose the granted subtree, which is
 * what keeps the search inside the user-chosen folder.
 */
internal interface SelectedFolder {
    /** Root of the search scope, or null when the user has not selected a folder yet. */
    fun root(): DocumentNode?
}

/**
 * Temporary [SelectedFolder] until the settings flow persists the user's folder choice (next
 * stage): always reports "nothing selected", so find_files answers with its setup error and the
 * UI can offer opening settings.
 */
internal object UnselectedFolder : SelectedFolder {
    override fun root(): DocumentNode? = null
}
