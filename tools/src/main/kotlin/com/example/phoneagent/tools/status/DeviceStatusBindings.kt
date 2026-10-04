package com.example.phoneagent.tools.status

import com.example.phoneagent.domain.tools.Tool
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DeviceStatusBindings {
    @Binds
    @IntoSet
    abstract fun bindDeviceStatusTool(tool: DeviceStatusTool): Tool

    @Binds
    abstract fun bindDeviceStatusPort(status: AndroidDeviceStatus): DeviceStatusPort
}
