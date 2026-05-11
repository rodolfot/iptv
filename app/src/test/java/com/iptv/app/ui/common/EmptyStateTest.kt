package com.iptv.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.iptv.app.TestApp
import com.iptv.app.ui.theme.IptvTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApp::class)
class EmptyStateTest {

    @Test
    fun `renders title and message`() = runComposeUiTest {
        setContent {
            IptvTheme {
                EmptyState(
                    title = "Nothing here",
                    message = "Add something to get started.",
                    icon = Icons.Filled.Inbox
                )
            }
        }
        onNodeWithText("Nothing here").assertIsDisplayed()
        onNodeWithText("Add something to get started.").assertIsDisplayed()
    }
}
