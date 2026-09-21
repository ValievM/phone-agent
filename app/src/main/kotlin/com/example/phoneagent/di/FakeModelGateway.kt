package com.example.phoneagent.di

import android.content.Context
import com.example.phoneagent.R
import com.example.phoneagent.domain.model.ModelEvent
import com.example.phoneagent.domain.model.ModelGateway
import com.example.phoneagent.domain.model.ModelRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val FAKE_RESPONSE_DELAY_MILLIS = 350L

internal class FakeModelGateway
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ModelGateway {
        override fun stream(request: ModelRequest): Flow<ModelEvent> =
            flow {
                delay(FAKE_RESPONSE_DELAY_MILLIS)
                emit(ModelEvent.TextDelta(context.getString(R.string.fake_model_response)))
                emit(ModelEvent.Completed())
            }
    }
