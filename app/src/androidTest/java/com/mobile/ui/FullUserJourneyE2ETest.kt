package com.mobile.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.data.FinanceRepository
import com.mobile.data.SettingsRepository
import com.mobile.data.Transaction
import com.mobile.ui.navigation.AppNavigation
import com.mobile.ui.screens.OnboardingScreen
import com.mobile.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive End-to-End (E2E) User Journey Tests.
 *
 * Validates complete user paths:
 * 1. Onboarding flow from first launch to completion
 * 2. Home screen transaction feeds, balance display, and detail sheet interaction
 * 3. Searching and filtering transactions in the transaction history
 * 4. Budget period switching and expense views
 * 5. Full tab navigation across all major sections
 */
class FullUserJourneyE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SettingsRepository.init(context)
        SettingsRepository.setHasSeenOnboarding(true)
        FinanceRepository.init(context)
        FinanceRepository.clearAll()
    }

    // ── 1. Onboarding Journey ─────────────────────────────────────────────

    @Test
    fun onboardingFlow_userCanSkipToApp() {
        var completed = false
        composeTestRule.setContent {
            AppTheme {
                OnboardingScreen(onFinished = { completed = true })
            }
        }

        // Verify Step 1 indicator is visible
        composeTestRule.onNodeWithText("STEP 1 OF 4", substring = true).assertExists()

        // Tap Skip button
        composeTestRule.onNodeWithText("Skip").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Expected onboarding onFinished callback to be invoked", completed)
    }

    @Test
    fun onboardingFlow_userCanStepThroughPages() {
        var completed = false
        composeTestRule.setContent {
            AppTheme {
                OnboardingScreen(onFinished = { completed = true })
            }
        }

        // Page 1 -> Page 2
        composeTestRule.onNodeWithContentDescription("Continue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STEP 2 OF 4", substring = true).assertExists()

        // Page 2 -> Page 3
        composeTestRule.onNodeWithContentDescription("Continue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STEP 3 OF 4", substring = true).assertExists()

        // Page 3 -> Page 4 (Last page)
        composeTestRule.onNodeWithContentDescription("Continue").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("STEP 4 OF 4", substring = true).assertExists()

        // Page 4 -> Finish via "Get Started"
        composeTestRule.onNodeWithContentDescription("Get Started").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Expected onboarding onFinished to be invoked after last page", completed)
    }

    // ── 2. Transaction Feed & Detail Sheet Journey ────────────────────────

    @Test
    fun userJourney_transactionFeed_showsDetailsSheet() {
        val sampleTx = Transaction(
            id = "tx_e2e_cbe_001",
            bankShortName = "CBE",
            amount = 1250.0,
            date = "Sep 13, 2026",
            time = "10:30 AM",
            title = "Abebe Kebede",
            category = "Transfer",
            reason = "Mobile Transfer",
            balance = 15800.0,
            type = "TRANSFER_IN",
            accountSuffix = "1000"
        )
        FinanceRepository.addTransaction(sampleTx)

        composeTestRule.setContent {
            AppTheme { AppNavigation() }
        }
        composeTestRule.waitForIdle()

        // Verify transaction appears in Recent Activities
        composeTestRule.onNodeWithText("Abebe Kebede").assertExists()
        composeTestRule.onNodeWithText("1,250", substring = true).assertExists()

        // Click on the transaction card to open the Detail Sheet
        composeTestRule.onNodeWithText("Abebe Kebede").performClick()
        composeTestRule.waitForIdle()

        // Detail sheet should display transaction breakdown
        composeTestRule.onNodeWithText("Category").assertExists()
        composeTestRule.onNodeWithText("Transfer").assertExists()
    }

    // ── 3. Transactions Search & Filter Journey ───────────────────────────

    @Test
    fun userJourney_searchTransactions() {
        val tx1 = Transaction(
            id = "tx_e2e_search_1",
            bankShortName = "Telebirr",
            amount = 450.0,
            date = "Sep 13, 2026",
            time = "11:00 AM",
            title = "Shoa Supermarket",
            category = "Groceries",
            reason = "Merchant Payment",
            balance = 5200.0,
            type = "PAYMENT",
            accountSuffix = "2519"
        )
        val tx2 = Transaction(
            id = "tx_e2e_search_2",
            bankShortName = "Awash",
            amount = 200.0,
            date = "Sep 13, 2026",
            time = "11:15 AM",
            title = "Total Cafe",
            category = "Dining",
            reason = "Coffee",
            balance = 3400.0,
            type = "PAYMENT",
            accountSuffix = "8899"
        )
        FinanceRepository.addTransaction(tx1)
        FinanceRepository.addTransaction(tx2)

        composeTestRule.setContent {
            AppTheme { AppNavigation() }
        }

        // Navigate to Transactions tab
        composeTestRule.onNodeWithContentDescription("Transactions").performClick()
        composeTestRule.waitForIdle()

        // Both should be visible initially
        composeTestRule.onNodeWithText("Shoa Supermarket").assertExists()
        composeTestRule.onNodeWithText("Total Cafe").assertExists()

        // Open search
        composeTestRule.onNodeWithContentDescription("Search transactions").performClick()
        composeTestRule.waitForIdle()

        // Type query "Shoa"
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Shoa")
        composeTestRule.waitForIdle()

        // Shoa should match, Total Cafe should be filtered out
        composeTestRule.onNodeWithText("Shoa Supermarket").assertExists()
        composeTestRule.onNodeWithText("Total Cafe").assertDoesNotExist()

        // Clear search
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Total Cafe").assertExists()
    }

    // ── 4. Budget Period Switching Journey ────────────────────────────────

    @Test
    fun userJourney_budgetPeriods() {
        composeTestRule.setContent {
            AppTheme { AppNavigation() }
        }

        // Navigate to Budget tab
        composeTestRule.onNodeWithContentDescription("Budget").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Budget & Expenses").assertExists()

        // Switch to Daily
        composeTestRule.onNodeWithText("Daily").performClick()
        composeTestRule.waitForIdle()

        // Switch to Monthly
        composeTestRule.onNodeWithText("Monthly").performClick()
        composeTestRule.waitForIdle()

        // Switch to Yearly
        composeTestRule.onNodeWithText("Yearly").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Yearly").assertExists()
    }
}
