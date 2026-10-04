package com.example.phoneagent.domain.fakes

import com.example.phoneagent.domain.platform.TimeZoneProvider
import java.time.ZoneId

public class FakeTimeZoneProvider(
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : TimeZoneProvider {
    override fun zone(): ZoneId = zoneId
}
