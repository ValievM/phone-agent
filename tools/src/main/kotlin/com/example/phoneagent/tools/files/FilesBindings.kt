package com.example.phoneagent.tools.files

import com.example.phoneagent.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Adds find_files to the app-wide [Tool] set (consumed by ToolRegistry) and wires its scope port. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FilesBindings {
    @Binds
    @IntoSet
    abstract fun bindFindFilesTool(tool: FindFilesTool): Tool

    companion object {
        @Provides
        fun provideSelectedFolder(): SelectedFolder = UnselectedFolder
    }
}
