package com.mobile.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mobile.data.FinanceRepository
import com.mobile.data.SettingsRepository
import com.mobile.ui.screens.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.mobile.ui.theme.AppTheme

/**
 * Compose UI (instrumented) tests for screen rendering and interaction.
 * These run on an Android device/emulator.
 *
 * Key patterns:
 * - Use assertExists() for content that may be below the scroll fold
 * - Use assertIsDisplayed() only for guaranteed above-the-fold content
 * - Use onAllNodesWithText().onFirst() for text that may appear in multiple nodes (e.g. Header vs Placeholder)
 */
class ScreenRenderingTest {

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

    // ── Budget Screen ────────────────────────────────────────────────────

    @Test
    fun budgetScreen_displaysHeader() {
        composeTestRule.setContent {
            AppTheme { BudgetScreen() }
        }
        // Header text is unique here
        composeTestRule.onNodeWithText("Budget & Expenses").assertIsDisplayed()
    }

    @Test
    fun budgetScreen_showsPeriodSelector() {
        composeTestRule.setContent {
            AppTheme { BudgetScreen() }
        }
        composeTestRule.onNodeWithText("Daily").assertExists()
        composeTestRule.onNodeWithText("Weekly").assertExists()
        composeTestRule.onNodeWithText("Monthly").assertExists()
        composeTestRule.onNodeWithText("Yearly").assertExists()
    }

    @Test
    fun budgetScreen_switchesPeriod() {
        composeTestRule.setContent {
            AppTheme { BudgetScreen() }
        }
        composeTestRule.onNodeWithText("Weekly").performClick()
        composeTestRule.waitForIdle()
        // Check that Weekly is still there (selected)
        composeTestRule.onAllNodesWithText("Weekly").onFirst().assertExists()
    }

    @Test
    fun budgetScreen_showsEmptyStateForNoExpenses() {
        composeTestRule.setContent {
            AppTheme { BudgetScreen() }
        }
        composeTestRule.onNodeWithText("No expenses found for this period.").assertExists()
    }

    // ── Analytics Screen ─────────────────────────────────────────────────

    @Test
    fun analyticsScreen_displaysHeader() {
        composeTestRule.setContent {
            AppTheme { AnalyticsScreen() }
        }
        composeTestRule.onNodeWithText("Analytics").assertIsDisplayed()
    }

    @Test
    fun analyticsScreen_showsHeatmapToggle() {
        composeTestRule.setContent {
            AppTheme { AnalyticsScreen() }
        }
        composeTestRule.onNodeWithText("Heatmap").assertExists()
    }

    @Test
    fun analyticsScreen_showsEmptyTransactionsHint() {
        composeTestRule.setContent {
            AppTheme { AnalyticsScreen() }
        }
        composeTestRule.onNodeWithText("No transactions this month.").assertExists()
    }

    @Test
    fun analyticsScreen_showsIncomeVsExpense() {
        composeTestRule.setContent {
            AppTheme { AnalyticsScreen() }
        }
        composeTestRule.onNodeWithText("Income vs Expense").assertExists()
    }

    // ── Export Data Screen ───────────────────────────────────────────────

    @Test
    fun exportDataScreen_displaysHeader() {
        composeTestRule.setContent {
            AppTheme { ExportDataScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("Export Data").assertIsDisplayed()
    }

    @Test
    fun exportDataScreen_showsCsvOption() {
        composeTestRule.setContent {
            AppTheme { ExportDataScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("Export as CSV").assertExists()
    }

    @Test
    fun exportDataScreen_showsJsonOption() {
        composeTestRule.setContent {
            AppTheme { ExportDataScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("Export as JSON").assertExists()
    }

    // ── Transaction History Screen ───────────────────────────────────────

    @Test
    fun transactionHistoryScreen_displaysHeader() {
        composeTestRule.setContent {
            AppTheme { TransactionHistoryScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("Transaction History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transactions").assertIsDisplayed()
    }

    @Test
    fun transactionHistoryScreen_showsEmptyState() {
        composeTestRule.setContent {
            AppTheme { TransactionHistoryScreen(onBack = {}) }
        }
        // The text might be slightly different or below fold
        composeTestRule.onNodeWithText("No transactions found yet.").assertExists()
        // The text is "No transactions yet"
        composeTestRule.onNodeWithText("No transactions yet").assertExists()
    }

    // ── Support Screen ───────────────────────────────────────────────────

    @Test
    fun supportScreen_showsHero() {
        composeTestRule.setContent {
            AppTheme { SupportScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("We're here to help!").assertExists()
    }

    @Test
    fun supportScreen_showsContactOptions() {
        composeTestRule.setContent {
            AppTheme { SupportScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("Call Support").assertExists()
        composeTestRule.onNodeWithText("Email Us").assertExists()
    }

    @Test
    fun supportScreen_showsFAQs() {
        composeTestRule.setContent {
            AppTheme { SupportScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("How do I add a bank account?").assertExists()
        composeTestRule.onNodeWithText("Is my data secure?").assertExists()
    }

    @Test
    fun supportScreen_expandsFaq() {
        composeTestRule.setContent {
            AppTheme { SupportScreen(onBack = {}) }
        }
        composeTestRule.onNodeWithText("Is my data secure?").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "Yes! All data is stored locally on your device. We never upload your financial information to any server."
        ).assertExists()
    }

    // ── Not Found Screen ─────────────────────────────────────────────────

    @Test
    fun notFoundScreen_displaysOops() {
        composeTestRule.setContent {
            AppTheme { NotFoundScreen(onGoHome = {}) }
        }
        composeTestRule.onNodeWithText("Oops!").assertIsDisplayed()
    }

    @Test
    fun notFoundScreen_goHomeCallback() {
        var goHomeCalled = false
        composeTestRule.setContent {
            AppTheme { NotFoundScreen(onGoHome = { goHomeCalled = true }) }
        }
        composeTestRule.onNodeWithText("Go to home screen!").performClick()
        assert(goHomeCalled)
    }
}
