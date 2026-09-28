package com.example.phoneagent.ui.designsystem.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.domain.interaction.ApprovalRequest
import com.example.phoneagent.domain.tools.ArgumentReview
import com.example.phoneagent.domain.tools.CallDescription
import com.example.phoneagent.domain.tools.EditableField
import com.example.phoneagent.domain.tools.EditableFieldKind
import com.example.phoneagent.domain.tools.Risk
import com.example.phoneagent.domain.tools.ToolArgumentEditor
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class EditableApprovalSheetUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `editing minute refreshes the description`() {
        var approvedArguments: JsonObject? = null
        show(request(editor = alarmEditor), onApprove = { approvedArguments = it })

        composeRule.onNodeWithText("Час").assertIsDisplayed()
        composeRule.onNodeWithText("Минуты").assertIsDisplayed()
        composeRule.onNodeWithText("Работа").assertIsDisplayed()
        composeRule.onNodeWithText("На 07:30 — Работа").assertIsDisplayed()

        composeRule.onNodeWithText("30").performTextReplacement("45")

        composeRule.onNodeWithText("На 07:45 — Работа").assertIsDisplayed()
        composeRule.onNodeWithText("На 07:30 — Работа").assertDoesNotExist()
        composeRule.onNodeWithText("Разрешить").performClick()

        assertEquals(arguments(minute = 45), approvedArguments)
    }

    @Test
    fun `invalid hour disables approval and shows validation reason`() {
        show(request(editor = alarmEditor))

        composeRule.onNodeWithText("7").performTextReplacement("25")

        composeRule.onNodeWithText("Час должен быть от 0 до 23").assertIsDisplayed()
        composeRule.onNodeWithText("Разрешить").assertIsNotEnabled()
    }

    @Test
    fun `request without editor keeps the plain approval sheet`() {
        show(request())

        composeRule.onNodeWithText("Подробности").assertIsDisplayed()
        composeRule.onNodeWithText("Час").assertDoesNotExist()
        composeRule.onNodeWithText("Минуты").assertDoesNotExist()
    }

    @Test
    fun `sensitive request never renders editor fields`() {
        show(request(risk = Risk.SENSITIVE, editor = alarmEditor))

        composeRule.onNodeWithText("Проверьте и подтвердите").assertIsDisplayed()
        composeRule.onNodeWithText("Час").assertDoesNotExist()
        composeRule.onNodeWithText("Минуты").assertDoesNotExist()
    }

    @Test
    fun `rejecting an edited draft does not approve it`() {
        var approvedArguments: JsonObject? = null
        var rejected = false
        show(
            request(editor = alarmEditor),
            onApprove = { approvedArguments = it },
            onReject = { rejected = true },
        )

        composeRule.onNodeWithText("30").performTextReplacement("45")
        composeRule.onNodeWithText("Отклонить").performClick()

        assertNull(approvedArguments)
        assertEquals(true, rejected)
    }

    private fun show(
        request: ApprovalRequest,
        onApprove: (JsonObject) -> Unit = {},
        onReject: () -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) {
                ApprovalSheet(
                    request = request,
                    arguments = request.arguments,
                    onApprove = onApprove,
                    onReject = onReject,
                )
            }
        }
    }

    private fun request(
        risk: Risk = Risk.CONFIRM,
        editor: ToolArgumentEditor? = null,
    ) = ApprovalRequest(
        description = alarmEditor.review(arguments()).description,
        risk = risk,
        arguments = arguments(),
        editor = editor,
    )

    private fun arguments(
        hour: Int = 7,
        minute: Int = 30,
        message: String = "Работа",
    ) = buildJsonObject {
        put("hour", hour)
        put("minute", minute)
        put("message", message)
    }

    private val alarmEditor =
        object : ToolArgumentEditor {
            override val fields =
                listOf(
                    EditableField("hour", "Час", EditableFieldKind.INTEGER),
                    EditableField("minute", "Минуты", EditableFieldKind.INTEGER),
                    EditableField("message", "Название", EditableFieldKind.TEXT),
                )

            override fun review(args: JsonObject): ArgumentReview {
                val hour = args["hour"]?.jsonPrimitive?.content?.toIntOrNull()
                val minute = args["minute"]?.jsonPrimitive?.content?.toIntOrNull()
                val message = args["message"]?.jsonPrimitive?.content.orEmpty()
                val error =
                    when {
                        hour == null || minute == null -> "Укажите время будильника"
                        hour !in 0..23 -> "Час должен быть от 0 до 23"
                        minute !in 0..59 -> "Минуты должны быть от 0 до 59"
                        else -> null
                    }
                return ArgumentReview(
                    description = CallDescription("Будильник", "На %02d:%02d — %s".format(hour ?: 0, minute ?: 0, message)),
                    validationError = error,
                )
            }
        }
}
