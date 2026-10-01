package com.example.phoneagent.di

import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.voice.SystemDialogSpeechInput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VoiceModule {
    @Binds
    abstract fun bindSpeechInput(implementation: SystemDialogSpeechInput): SpeechInput
}
