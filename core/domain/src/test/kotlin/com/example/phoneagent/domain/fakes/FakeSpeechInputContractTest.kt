package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.contracts.SpeechInputContract
import com.example.phoneagent.domain.speech.SpeechEvent
import com.example.phoneagent.domain.speech.SpeechInput
import com.example.phoneagent.domain.speech.SpeechRequest

class FakeSpeechInputContractTest : SpeechInputContract() {
    private val fake = FakeSpeechInput()

    override fun subject(): SpeechInput = fake

    override fun offer(vararg events: SpeechEvent) {
        fake.script(*events)
    }

    override fun recordedRequests(): List<SpeechRequest> = fake.requests

    override fun assertReleased() = fake.assertReleased()
}
