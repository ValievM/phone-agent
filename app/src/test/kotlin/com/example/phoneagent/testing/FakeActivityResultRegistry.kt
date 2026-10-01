package com.example.phoneagent.testing

import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

public class FakeActivityResultRegistry : ActivityResultRegistry() {
    public data class Launch(
        public val requestCode: Int,
        public val input: Any?,
    )

    public val launches: MutableList<Launch> = mutableListOf()
    public var launchFailure: Throwable? = null

    private val pendingRequestCodes = mutableListOf<Int>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += Launch(requestCode, input)
        pendingRequestCodes += requestCode
        launchFailure?.let {
            pendingRequestCodes.removeAt(pendingRequestCodes.lastIndex)
            throw it
        }
    }

    public fun respondWith(
        resultCode: Int,
        data: Intent?,
    ) {
        check(pendingRequestCodes.isNotEmpty()) { "No pending activity result launch to respond to" }
        val requestCode = pendingRequestCodes.removeAt(pendingRequestCodes.lastIndex)
        dispatchResult(requestCode, resultCode, data)
    }
}
