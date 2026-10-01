package com.example.data

enum class ErrorCategory {
    AUTHENTICATION_ERROR,
    PERMISSION_ERROR,
    QUOTA_ERROR,
    BILLING_ERROR,
    MODEL_ERROR,
    NETWORK_ERROR,
    VOICE_ERROR,
    UNKNOWN_ERROR
}

data class ClassifiedError(
    val category: ErrorCategory,
    val title: String,
    val technicalDetails: String,
    val explanation: String,
    val suggestedFix: String
)

object GeminiErrorClassifier {

    fun classifyHttp(statusCode: Int, responseBody: String): ClassifiedError {
        val lowerBody = responseBody.lowercase()

        return when {
            statusCode == 401 || lowerBody.contains("api_key_invalid") || lowerBody.contains("invalid api key") || lowerBody.contains("unauthenticated") -> {
                ClassifiedError(
                    category = ErrorCategory.AUTHENTICATION_ERROR,
                    title = "Authentication Error",
                    technicalDetails = "HTTP $statusCode: $responseBody",
                    explanation = "The provided Gemini API key is missing, invalid, or expired.",
                    suggestedFix = "Verify your API key in AI Studio Secrets panel or configure a valid API key in Gemini Connection settings."
                )
            }
            statusCode == 403 && (lowerBody.contains("billing") || lowerBody.contains("account suspended")) -> {
                ClassifiedError(
                    category = ErrorCategory.BILLING_ERROR,
                    title = "Billing Error",
                    technicalDetails = "HTTP $statusCode: $responseBody",
                    explanation = "Billing is disabled or not configured for the associated Google Cloud project.",
                    suggestedFix = "Enable billing in the Google Cloud Console for the project linked with this API key."
                )
            }
            statusCode == 403 && (lowerBody.contains("permission_denied") || lowerBody.contains("api not enabled")) -> {
                ClassifiedError(
                    category = ErrorCategory.PERMISSION_ERROR,
                    title = "Permission Error",
                    technicalDetails = "HTTP $statusCode: $responseBody",
                    explanation = "The Generative Language API is not enabled, or the API key is restricted by IP or bundle ID.",
                    suggestedFix = "Ensure Generative Language API is enabled in your Google Cloud Console and verify API key restrictions."
                )
            }
            statusCode == 429 || lowerBody.contains("resource_exhausted") || lowerBody.contains("quota") || lowerBody.contains("rate limit") -> {
                ClassifiedError(
                    category = ErrorCategory.QUOTA_ERROR,
                    title = "Quota Exceeded",
                    technicalDetails = "HTTP $statusCode: $responseBody",
                    explanation = "You have reached the free tier rate limit or project quota for Gemini API requests.",
                    suggestedFix = "Wait a few seconds before retrying, or upgrade to a paid Gemini API tier for higher rate limits."
                )
            }
            statusCode == 404 || lowerBody.contains("not found") || lowerBody.contains("unsupported model") || lowerBody.contains("models/") -> {
                ClassifiedError(
                    category = ErrorCategory.MODEL_ERROR,
                    title = "Model Error",
                    technicalDetails = "HTTP $statusCode: $responseBody",
                    explanation = "The requested model is either unavailable, deprecated, or does not support the requested modality.",
                    suggestedFix = "Switch to a supported model like 'gemini-3.5-flash' for text or 'gemini-2.5-flash-preview-tts' for speech."
                )
            }
            else -> {
                ClassifiedError(
                    category = ErrorCategory.UNKNOWN_ERROR,
                    title = "API Error ($statusCode)",
                    technicalDetails = "HTTP $statusCode: $responseBody",
                    explanation = "The Gemini API returned an unexpected response.",
                    suggestedFix = "Review the technical details and verify your network and Google Cloud project configuration."
                )
            }
        }
    }

    fun classifyException(throwable: Throwable): ClassifiedError {
        val message = throwable.message.orEmpty()
        val cause = throwable.cause?.message.orEmpty()
        val combined = "$message $cause".lowercase()

        return when {
            throwable is java.net.UnknownHostException || combined.contains("unable to resolve host") -> {
                ClassifiedError(
                    category = ErrorCategory.NETWORK_ERROR,
                    title = "Network Unreachable",
                    technicalDetails = throwable.javaClass.simpleName + ": " + throwable.message,
                    explanation = "No internet connection or DNS resolution failed for generativelanguage.googleapis.com.",
                    suggestedFix = "Check your device's Wi-Fi or cellular data connection."
                )
            }
            throwable is java.net.SocketTimeoutException || combined.contains("timeout") -> {
                ClassifiedError(
                    category = ErrorCategory.NETWORK_ERROR,
                    title = "Connection Timeout",
                    technicalDetails = throwable.javaClass.simpleName + ": " + throwable.message,
                    explanation = "The request timed out while communicating with the Gemini API servers.",
                    suggestedFix = "Check connection speed and retry. The timeout is configured to 60 seconds."
                )
            }
            combined.contains("api_key") || combined.contains("auth") -> {
                ClassifiedError(
                    category = ErrorCategory.AUTHENTICATION_ERROR,
                    title = "Authentication Exception",
                    technicalDetails = throwable.javaClass.simpleName + ": " + throwable.message,
                    explanation = "An error occurred with API key authentication.",
                    suggestedFix = "Check your API key configuration in Gemini Connection settings."
                )
            }
            else -> {
                ClassifiedError(
                    category = ErrorCategory.NETWORK_ERROR,
                    title = "Network Communication Failure",
                    technicalDetails = throwable.javaClass.simpleName + ": " + throwable.message,
                    explanation = "Failed to establish a connection with the Gemini API.",
                    suggestedFix = "Verify your internet connection and check if firewall or VPN is blocking Google APIs."
                )
            }
        }
    }

    fun voiceError(reason: String, technical: String): ClassifiedError {
        return ClassifiedError(
            category = ErrorCategory.VOICE_ERROR,
            title = "Voice Error",
            technicalDetails = technical,
            explanation = reason,
            suggestedFix = "Check speaker output volume, microphone permission, and verify if the selected model supports audio modality."
        )
    }
}
