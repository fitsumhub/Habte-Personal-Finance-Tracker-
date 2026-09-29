package com.mobile.data

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsParser {

    // Messages describing a transaction that didn't actually complete (rejected/reversed
    // *attempts*, not to be confused with a reversal *refund*, which is a real credit and
    // is left to the normal credit-keyword path below).
    private val failedTransactionRegex = Regex(
        "\\b(?:failed|unsuccessful|declined|not\\s+successful|could\\s+not\\s+be\\s+completed|insufficient\\s+(?:balance|funds?)|cancelled|canceled|rejected)\\b",
        RegexOption.IGNORE_CASE
    )

    // Messages announcing a transaction that hasn't happened yet (a future-dated standing order/subscription notice).
    private val pendingTransactionRegex = Regex(
        "\\bwill\\s+be\\s+(?:debited|credited|charged)\\b|\\bis\\s+scheduled\\s+to\\s+be\\b",
        RegexOption.IGNORE_CASE
    )

    // Promotional & advertisement texts that are not actual transaction notifications.
    private val promoRegex = Regex(
        "\\b(?:win\\b|bonus\\b|subscribe\\s+to|dial\\s+\\*|chance\\s+to\\s+win|promotional|offer\\b|congratulations!\\s+you\\s+have\\s+won)\\b",
        RegexOption.IGNORE_CASE
    )

    // Security advisory footers (e.g. "will never ask for your PIN/OTP", "do not share your password")
    private val securityDisclaimerRegex = Regex(
        """(?:will\s+never\s+ask|do\s+not\s+share|never\s+share|don'?t\s+share|never\s+disclose)\b.*""",
        RegexOption.IGNORE_CASE
    )

    // OTP / PIN verification code regex
    private val otpRegex = Regex(
        """(?:otp|one[- ]time password|verification code|security code|pin|password)\b|\bcode\s+has\s+been\s+sent\b|\bsent\s+to\s+your\s+phone\b""",
        RegexOption.IGNORE_CASE
    )

    // Amount & Currency regexes
    private const val CURRENCY_WORD = "(?:etb|birr|br\\.?|kes|ksh|ብር)"
    private val hasMoneyAmountRegex = Regex(
        """(?:etb|birr|br\.?|kes|ksh|ብር|amount|amt)\s*[:=]?\s*[0-9,]+\.?[0-9]*|[0-9,]+\.?[0-9]*\s*(?:etb|birr|br\.?|kes|ksh|ብር)""",
        RegexOption.IGNORE_CASE
    )
    private val hasTransferRecipientRegex = Regex(
        """(?:to|for|ለ)\s+(?:\d{9,}|\d{4,}|[a-z0-9*#./-]{3,}\s*\d{4,}|[a-z0-9*#./-]{4,}\s+[a-z0-9*#./-]{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val amountRegex = Regex(
        """(?:$CURRENCY_WORD|amount[:\s]?of|amount:?|credited\s+with|debited\s+with|transfer\s+of)\s*([0-9,]+\.?[0-9]*)""",
        RegexOption.IGNORE_CASE
    )
    private val amountRegex2 = Regex(
        """([0-9,]+\.?[0-9]*)\s*(?:$CURRENCY_WORD)""",
        RegexOption.IGNORE_CASE
    )

    // Balance Regex
    private val balanceRegex = Regex(
        """(?:balance\s*(?:is\s+now|is|now)?|balance:?|your\s+balance|available\s+balance\s*(?:is\s+now|is|now)?|current\s+balance|remaining\s+balance|ቀሪ\s+ሂሳብ(?:ዎ)?|ቀሪ\s+ሒሳብ(?:ዎ)?|ያለዎት\s+የ(?:m-pesa)?\s*ቀሪ\s+ሂሳብ(?:ዎ)?|ያለዎት\s+የ(?:m-pesa)?\s*ቀሪ\s+ሒሳብ(?:ዎ)?|ያለዎት\s+ቀሪ|ቀሪዎ|ሂሳብ(?:ዎ)?|ሒሳብ(?:ዎ)?)\s*(?:is\s+now|is|now)?\s*(?:etb|birr|br\.?|kes|ksh|ብር)?\s*([0-9,]+\.?[0-9]*)""",
        RegexOption.IGNORE_CASE
    )

    // Account Suffix Regexes
    private val myAccountRegex = Regex(
        """(?:from\s+your(?:\s+[a-zA-Z\-]+)?\s+account|your(?:\s+[a-zA-Z\-]+)?\s+account|account\s+ending\s+in|credited\s+to\s+account|የአካውንት\s+ቁጥርዎ|የአካውንት\s+ቁጥር|አካውንት\s+ቁጥር|ሒሳብ|ሂሳብ)\s*(?:no\.?|number|:)?\s*['"]?\s*(?:[•*#\.Xx]+)?([0-9Xx*#•.\-]{3,})""",
        RegexOption.IGNORE_CASE
    )
    private val genericAccountRegex = Regex(
        """(?:a/c|account|acc|wallet)\s*(?:no\.?|number|:)?\s*['"]?\s*(?:[•*#\.Xx]+)?([0-9Xx*#•.\-]{3,})""",
        RegexOption.IGNORE_CASE
    )

    // Transaction Reference Regex
    private val referenceRegex = Regex(
        "(?:ref(?:erence)?(?:\\s*(?:no|number))?|txn\\s*(?:id|no|number)?|trx|transaction\\s*(?:id|no|number)|branchreceipt/|receipt/|\\?id=|\\bid=|ማጣቀሻ(?:\\s*ቁጥር)?|ቁጥር)\\s*(?:is|[:#=-])?\\s*([A-Za-z0-9]{6,30})",
        RegexOption.IGNORE_CASE
    )

    fun parseMessage(sender: String, body: String, timestamp: Long): Transaction? {
        val institution = InstitutionCatalog.findBySmsSender(sender) ?: return null
        return parseBody(institution, body, timestamp, idPrefix = "sms", sourceKey = sender)
    }

    fun parseBody(institution: InstitutionProfile, body: String, timestamp: Long, idPrefix: String, sourceKey: String): Transaction? {
        return try {
            val lowerBody = body.lowercase()
            val bankShortName = institution.shortName

        if (failedTransactionRegex.containsMatchIn(lowerBody) || pendingTransactionRegex.containsMatchIn(lowerBody)) {
            return null
        }

        // Strip security advisory footers before checking for actual OTP/verification code messages.
        val cleanedBody = lowerBody.replace(securityDisclaimerRegex, "")

        if (otpRegex.containsMatchIn(cleanedBody)) {
            return null
        }

        val hasMoneyAmount = hasMoneyAmountRegex.containsMatchIn(lowerBody)
        val hasTransferRecipient = hasTransferRecipientRegex.containsMatchIn(lowerBody)
        val hasTransferSignal = lowerBody.contains("transfer") ||
            lowerBody.contains("wallet") ||
            lowerBody.contains("account") ||
            lowerBody.contains("to ") ||
            lowerBody.contains("to:") ||
            lowerBody.contains("for ") ||
            lowerBody.contains("from ") ||
            lowerBody.contains("ዝውውር")

        val isTransferLikeDebit = lowerBody.contains("transfer send") ||
            lowerBody.contains("transfer of") ||
            lowerBody.contains("money out") ||
            lowerBody.contains("bundle") ||
            lowerBody.contains("package") ||
            lowerBody.contains("ጥቅል") ||
            lowerBody.contains("የሳፋሪኮም") ||
            (hasMoneyAmount && (
                lowerBody.contains("pay") ||
                lowerBody.contains("paid") ||
                lowerBody.contains("withdraw") ||
                lowerBody.contains("purchased") ||
                lowerBody.contains("purchase") ||
                lowerBody.contains("charged") ||
                lowerBody.contains("transferred") ||
                (hasTransferSignal && (lowerBody.contains("sent") || lowerBody.contains("send"))) ||
                (hasTransferRecipient && (lowerBody.contains("sent") || lowerBody.contains("send")))
            ))

        val type = when {
            lowerBody.contains("credited") || lowerBody.contains("received") ||
            lowerBody.contains("deposited") || lowerBody.contains("incoming") ||
            lowerBody.contains("money in") || lowerBody.contains("reversal") || lowerBody.contains("refund") ||
            lowerBody.contains("ገቢ") || lowerBody.contains("ተቀብለዋል") || lowerBody.contains("ገብቷል") ||
            lowerBody.contains("ተጨምሯል") || lowerBody.contains("የተጨመረ") || lowerBody.contains("ተመላሽ") -> "credit"

            lowerBody.contains("debited") || lowerBody.contains("paid") ||
            lowerBody.contains("transferred") || lowerBody.contains("withdraw") ||
            lowerBody.contains("purchase") || lowerBody.contains("charged") ||
            isTransferLikeDebit ||
            lowerBody.contains("ወጪ") || lowerBody.contains("ከፍለዋል") || lowerBody.contains("ልከዋል") ||
            lowerBody.contains("አስተላልፈዋል") || lowerBody.contains("ተቀንሷል") || lowerBody.contains("የተቀነሰ") ||
            lowerBody.contains("ተከፍሏል") || lowerBody.contains("money out") -> "debit"

            else -> return null
        }

        // Exclude pure promotional SMS that don't represent a completed transaction
        if (promoRegex.containsMatchIn(lowerBody) && !lowerBody.contains("credited") && !lowerBody.contains("debited")) {
            return null
        }

        val matchResult = amountRegex.find(lowerBody) ?: amountRegex2.find(lowerBody)

        val amount = matchResult?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: return null

        if (amount <= 0.0) return null

        // Extract remaining balance
        val balanceMatch = balanceRegex.find(lowerBody)
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        // Extract Account Suffix
        fun extractSuffix(token: String): String {
            val segments = token.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
            return (segments.lastOrNull() ?: token.filter { it.isDigit() }).takeLast(4)
        }

        var accountSuffix: String? = null
        val myMatch = myAccountRegex.find(lowerBody)
        if (myMatch != null) {
            accountSuffix = extractSuffix(myMatch.groupValues[1])
        } else {
            val allMatches = genericAccountRegex.findAll(lowerBody).toList()
            for (match in allMatches) {
                val index = match.range.first
                val precedingText = lowerBody.substring(kotlin.math.max(0, index - 30), index)

                if (type == "debit" && precedingText.contains(Regex("\\bto\\b"))) {
                    continue
                }

                accountSuffix = extractSuffix(match.groupValues[1])
                break
            }
        }

        if (institution.type == InstitutionType.DIGITAL_WALLET) {
            accountSuffix = null
        }

        val title = categorizeTitle(lowerBody, body, type, institution.name)
        val category = categorizeExpense(title, type)

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val dateStr = sdf.format(Date(timestamp))
        val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(timestamp))

        // Unified transaction ID: tx-<BANK>-<STABLE_KEY>
        // Converges SMS and App Notifications describing the same financial event into one primary key.
        val stableKey = stableTransactionKey(bankShortName, body)

        return Transaction(
            id = "tx-$bankShortName-$stableKey",
            title = title,
            amount = amount,
            date = dateStr,
            time = timeStr,
            type = type,
            bankShortName = bankShortName,
            category = category,
            balance = balance,
            accountSuffix = accountSuffix
        )
        } catch (t: Throwable) {
            null
        }
    }

    private fun categorizeTitle(lowerBody: String, body: String, type: String, institutionName: String): String {
        extractCounterpartyName(body, type)?.let { return it }
        return when {
            lowerBody.contains("airtime") || lowerBody.contains("recharge") -> "Airtime Recharge"
            lowerBody.contains("electric") || lowerBody.contains("eepco") -> "Electricity Bill"
            lowerBody.contains("water") -> "Water Bill"
            lowerBody.contains("utility") -> "Utility Bill"
            lowerBody.contains("supermarket") || lowerBody.contains("market") || lowerBody.contains("grocery") -> "Groceries"
            lowerBody.contains("restaurant") || lowerBody.contains("cafe") || lowerBody.contains("hotel") -> "Dining"
            lowerBody.contains("fuel") || lowerBody.contains("petrol") -> "Fuel"
            lowerBody.contains("atm") || lowerBody.contains("withdraw") -> "ATM Withdrawal"
            lowerBody.contains("loan") -> "Loan Payment"
            lowerBody.contains("transfer") || lowerBody.contains("ዝውውር") -> "Bank Transfer"
            lowerBody.contains("fee") || lowerBody.contains("maintenance") || lowerBody.contains("charge") -> "Bank Fee"
            lowerBody.contains("salary") || lowerBody.contains("payroll") || Regex("\\bpay\\b").containsMatchIn(lowerBody) -> "Salary Deposit"
            lowerBody.contains("purchase") -> "Purchase"
            else -> institutionName
        }
    }

    private fun extractCounterpartyName(body: String, type: String): String? {
        val keyword = if (type == "credit") "(?:from|by|ከ)" else "(?:to|by|at|for|ለ)"
        val nameRegex = Regex(
            "(?:\\b$keyword|\\s+$keyword)\\s+([A-Za-z][A-Za-z .'\\-]{1,40}?)(?=\\s*(?:[.,;:(]|\\bon\\b|\\bat\\b|\\busing\\b|\\bvia\\b|\\bthrough\\b|\\baccount\\b|\\bacc\\b|\\bwallet\\b|\\bbalance\\b|\\d|[\\u1200-\\u137F])|$)",
            RegexOption.IGNORE_CASE
        )
        for (matchResult in nameRegex.findAll(body)) {
            val candidate = matchResult.groupValues.getOrNull(1)?.trim() ?: continue
            val name = cleanCounterpartyName(candidate) ?: continue
            return name
        }

        val parenRegex = Regex("\\(([A-Za-z][A-Za-z .'\\-]{1,40})\\)")
        val parenName = parenRegex.find(body)?.groupValues?.get(1)?.trim() ?: return null
        return cleanCounterpartyName(parenName)
    }

    private fun stableTransactionKey(bankShortName: String, body: String): String {
        referenceRegex.find(body)?.groupValues?.get(1)?.let { return it }

        val normalized = body.trim().lowercase().replace(Regex("\\s+"), " ")
        val digest = MessageDigest.getInstance("SHA-256").digest("$bankShortName|$normalized".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun extractDisplayAccountSuffix(raw: String): String? {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return null
        val digits = candidate.filter(Char::isDigit)
        return digits.takeIf { it.isNotEmpty() }?.takeLast(4)
    }

    private fun cleanCounterpartyName(rawName: String): String? {
        if (rawName.length < 2 || rawName.any { it.isDigit() }) return null

        val firstWordStop = setOf(
            "your", "the", "a", "an", "account", "acc", "wallet", "balance", "you",
            "dear", "hello", "hi", "respected", "download", "click", "view", "generate",
            "visit", "check", "open", "see", "link", "more", "choosing"
        )
        val anyWordStop = setOf(
            "bank", "banking", "s.c", "s.c.", "dear", "customer", "thank", "thanks", "regards",
            "sincerely", "note", "enquiry", "helpdesk", "support", "help", "info", "call", "contact", "details",
            "telebirr", "using", "service", "services", "app", "payment", "information", "feedback", "receipt", "slip", "fayda", "connect",
            "more", "choosing"
        )
        val words = rawName.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.first().lowercase() in firstWordStop) return null
        if (words.any { it.lowercase() in anyWordStop }) return null

        return words.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun categorizeExpense(title: String, type: String): String {
        if (type == "credit") return "Income"
        return when (title) {
            "Airtime Recharge", "Electricity Bill", "Water Bill", "Utility Bill" -> "Bills & Utilities"
            "Groceries", "Dining" -> "Food & Dining"
            "Fuel" -> "Transport"
            "ATM Withdrawal" -> "Cash"
            "Loan Payment" -> "Loan"
            "Bank Transfer" -> "Transfers"
            "Purchase" -> "Shopping"
            else -> "Other"
        }
    }
}

