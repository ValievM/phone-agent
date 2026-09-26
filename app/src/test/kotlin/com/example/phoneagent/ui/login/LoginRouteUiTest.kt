package com.example.phoneagent.ui.login

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.auth.AppAuthStatus
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.auth.AuthErrorReason
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class LoginRouteUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `restoring status shows only neutral progress without login or chat`() {
        val coordinator = TestAuthCoordinator(AppAuthStatus.Restoring)
        show(coordinator)

        assertNeutralRestoringScreen()
    }

    @Test
    fun `login state starts restoring before coordinator status is collected`() {
        val viewModel = LoginViewModel(TestAuthCoordinator(AppAuthStatus.Restoring))

        assertTrue(viewModel.state.value.isRestoring)
    }

    @Test
    fun `restoring transitions directly to authenticated content`() {
        val coordinator = TestAuthCoordinator(AppAuthStatus.Restoring)
        show(coordinator)

        assertNeutralRestoringScreen()

        coordinator.setStatus(AppAuthStatus.SignedIn(AuthMethod.API_KEY))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("authenticated content").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.login_title)).assertDoesNotExist()
    }

    @Test
    fun `signed out status shows the login screen`() {
        show(TestAuthCoordinator(AppAuthStatus.SignedOut()))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.login_title)).assertIsDisplayed()
        composeRule.onNodeWithText("authenticated content").assertDoesNotExist()
        composeRule.onNodeWithTag(RESTORING_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `restore failure shows login with an error instead of waiting forever`() {
        show(TestAuthCoordinator(AppAuthStatus.Failure(AuthErrorReason.NETWORK)))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.login_error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.login_openai)).assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORING_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `restoring screen remains neutral after saved state recreation`() {
        val coordinator = TestAuthCoordinator(AppAuthStatus.Restoring)
        val viewModel = LoginViewModel(coordinator)
        val restoration = StateRestorationTester(composeRule)

        restoration.setContent {
            AppTheme(useDynamicColor = false) {
                LoginRoute(
                    authenticatedContent = { Text("authenticated content") },
                    viewModel = viewModel,
                )
            }
        }
        assertNeutralRestoringScreen()

        restoration.emulateSavedInstanceStateRestore()

        assertNeutralRestoringScreen()
    }

    private fun show(coordinator: TestAuthCoordinator) {
        val viewModel = LoginViewModel(coordinator)
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                LoginRoute(
                    authenticatedContent = { Text("authenticated content") },
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertNeutralRestoringScreen() {
        composeRule.onNodeWithTag(RESTORING_INDICATOR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.login_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.login_openai)).assertDoesNotExist()
        composeRule.onNodeWithText("authenticated content").assertDoesNotExist()
    }

    private class TestAuthCoordinator(
        initial: AppAuthStatus,
    ) : AuthCoordinator {
        override val state = MutableStateFlow(initial)

        fun setStatus(status: AppAuthStatus) {
            state.value = status
        }

        override suspend fun restore() = Unit

        override suspend fun signInWithBrowser() = Unit

        override suspend fun signInWithDeviceCode() = Unit

        override suspend fun signInWithApiKey(key: String) = Unit

        override suspend fun signOut() = Unit

        override fun openDeviceVerificationPage() = Unit
    }

    private companion object {
        const val RESTORING_INDICATOR_TAG = "auth-restoring-indicator"
    }
}
