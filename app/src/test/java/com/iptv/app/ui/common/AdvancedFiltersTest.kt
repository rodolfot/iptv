package com.iptv.app.ui.common

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.iptv.app.TestApp
import com.iptv.app.ui.theme.IptvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApp::class)
class AdvancedFiltersTest {

    @Test
    fun `parses year and rating numeric inputs and applies`() = runComposeUiTest {
        val applied = mutableStateOf<AdvancedFilters?>(null)
        setContent {
            IptvTheme {
                AdvancedFiltersDialog(
                    initial = AdvancedFilters(),
                    // No genres for this case so the dialog doesn't render a LazyColumn
                    // that would interfere with reaching the Apply button via Robolectric.
                    availableGenres = emptyList(),
                    onDismiss = {},
                    onApply = { applied.value = it }
                )
            }
        }

        onNodeWithText("From").performTextInput("2010")
        onNodeWithText("To").performTextInput("2020")
        onNodeWithText("0 to 10").performTextInput("7.5")
        onNodeWithText("Apply").performClick()

        val result = applied.value
        assertTrue(result != null)
        assertEquals(2010, result!!.yearMin)
        assertEquals(2020, result.yearMax)
        assertEquals(7.5, result.ratingMin!!, 0.001)
        assertTrue(result.genres.isEmpty())
    }

    @Test
    fun `parseYear extracts the first plausible 4-digit year`() {
        assertEquals(2021, parseYear("2021-08-15"))
        assertEquals(1999, parseYear("released 1999"))
        assertNull(parseYear(""))
        assertNull(parseYear("foo bar"))
    }
}
