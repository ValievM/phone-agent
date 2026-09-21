package com.example.phoneagent.di

import com.example.phoneagent.domain.model.ModelGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModelModule {
    @Binds
    @Singleton
    abstract fun bindModelGateway(gateway: FakeModelGateway): ModelGateway
}
