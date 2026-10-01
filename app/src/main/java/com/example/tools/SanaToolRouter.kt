package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat

data class ToolExecutionResult(
    val success: Boolean,
    val toolName: String,
    val userSummary: String,
    val technicalDetails: String? = null,
    val requiresUserAction: Boolean = false
)

class SanaToolRouter(private val context: Context) {

    fun executeTool(toolName: String, args: Map<String, String>): ToolExecutionResult {
        return when (toolName) {
            "openApp" -> openApp(args["appName"] ?: args["packageName"].orEmpty())
            "findContact" -> findContact(args["name"].orEmpty())
            "makeCall" -> makeCall(args["phoneNumber"].orEmpty())
            "answerCall" -> answerCall()
            "endCall" -> endCall()
            "openWhatsApp" -> openWhatsApp(args["phoneNumber"].orEmpty(), args["message"].orEmpty(), args["contactName"].orEmpty())
            "composeMessage" -> composeMessage(args["phoneNumber"].orEmpty(), args["message"].orEmpty())
            "readSupportedNotifications" -> readSupportedNotifications()
            "openCamera" -> openCamera()
            "openGallery" -> openGallery()
            "openMaps" -> openMaps(args["query"].orEmpty())
            "startNavigation" -> startNavigation(args["destination"].orEmpty())
            "mediaControl" -> {
                when (args["action"]?.lowercase()) {
                    "play" -> mediaPlay()
                    "pause" -> mediaPause()
                    "next" -> mediaNext()
                    "previous" -> mediaPrevious()
                    else -> mediaPlay()
                }
            }
            "openSettings" -> openSettings(args["settingType"].orEmpty())
            "getBatteryStatus" -> getBatteryStatus()
            else -> ToolExecutionResult(
                success = false,
                toolName = toolName,
                userSummary = "Tool '$toolName' is not recognized by Android system.",
                technicalDetails = "Unimplemented tool identifier: $toolName"
            )
        }
    }

    fun openApp(query: String): ToolExecutionResult {
        if (query.isBlank()) {
            return ToolExecutionResult(false, "openApp", "Please specify an app name to open.")
        }

        val pm = context.packageManager
        val queryLower = query.lowercase().trim()

        // 1. Check direct package name or known apps
        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "calculator" to "com.google.android.calculator",
            "photos" to "com.google.android.apps.photos"
        )

        val targetPkg = knownPackages[queryLower] ?: query

        var launchIntent = pm.getLaunchIntentForPackage(targetPkg)

        // 2. If not found by package, scan installed applications by app label
        if (launchIntent == null) {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installed) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(queryLower) || queryLower.contains(label)) {
                    launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) break
                }
            }
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ToolExecutionResult(
                success = true,
                toolName = "openApp",
                userSummary = "Opening $query for you, Boss."
            )
        } else {
            ToolExecutionResult(
                success = false,
                toolName = "openApp",
                userSummary = "Boss, I couldn't find an installed app named '$query' on this device.",
                technicalDetails = "Package lookup failed for query: $query"
            )
        }
    }

    fun findContact(nameQuery: String): ToolExecutionResult {
        if (nameQuery.isBlank()) {
            return ToolExecutionResult(false, "findContact", "Please specify a contact name, Boss.")
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolExecutionResult(
                success = false,
                toolName = "findContact",
                userSummary = "Boss, I need Contacts permission to look up '$nameQuery'. Please grant it in Permissions Center.",
                technicalDetails = "android.permission.READ_CONTACTS not granted"
            )
        }

        var foundNumber: String? = null
        var foundName: String? = null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$nameQuery%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                foundName = cursor.getString(nameIndex)
                foundNumber = cursor.getString(numberIndex)
            }
        } catch (e: Exception) {
            return ToolExecutionResult(
                false,
                "findContact",
                "Error reading contacts: ${e.message}",
                e.stackTraceToString()
            )
        } finally {
            cursor?.close()
        }

        return if (foundNumber != null) {
            ToolExecutionResult(
                success = true,
                toolName = "findContact",
                userSummary = "Found contact $foundName: $foundNumber",
                technicalDetails = "Number: $foundNumber"
            )
        } else {
            ToolExecutionResult(
                success = false,
                toolName = "findContact",
                userSummary = "No contact matching '$nameQuery' was found in your address book.",
                technicalDetails = "0 rows returned from ContactsContract"
            )
        }
    }

    fun makeCall(phoneNumberOrName: String): ToolExecutionResult {
        if (phoneNumberOrName.isBlank()) {
            return ToolExecutionResult(false, "makeCall", "Please provide a phone number or contact name.")
        }

        var number = phoneNumberOrName.trim()

        // If it's a contact name rather than digits, try searching
        if (!number.matches(Regex("^[+0-9\\s\\-()]+$"))) {
            val searchRes = findContact(number)
            if (searchRes.success && searchRes.technicalDetails != null) {
                number = searchRes.technicalDetails.removePrefix("Number: ").trim()
            }
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        } else {
            // Safe fallback to dialer
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            if (hasCallPermission) {
                ToolExecutionResult(
                    success = true,
                    toolName = "makeCall",
                    userSummary = "Calling $number now, Boss."
                )
            } else {
                ToolExecutionResult(
                    success = true,
                    toolName = "makeCall",
                    userSummary = "Dialer opened for $number. Tap call to connect.",
                    requiresUserAction = true
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                toolName = "makeCall",
                userSummary = "Boss, couldn't initiate call to $number: ${e.message}",
                technicalDetails = e.stackTraceToString()
            )
        }
    }

    fun answerCall(): ToolExecutionResult {
        return ToolExecutionResult(
            success = false,
            toolName = "answerCall",
            userSummary = "Boss, modern Android (API 29+) restricts answering calls programmatically without default Phone dialer role. Please tap the incoming call banner.",
            technicalDetails = "TelecomManager.acceptRingingCall requires MANAGE_ONGOING_CALLS or default dialer role"
        )
    }

    fun endCall(): ToolExecutionResult {
        return ToolExecutionResult(
            success = false,
            toolName = "endCall",
            userSummary = "Boss, Android security policy prevents third-party apps from terminating active calls without system dialer privileges. Please tap end call on your screen.",
            technicalDetails = "TelecomManager.endCall requires default dialer role on Android 10+"
        )
    }

    fun openWhatsApp(phoneNumber: String, message: String, contactName: String = ""): ToolExecutionResult {
        var cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")

        // If phone is missing but contact name is present, try finding contact
        if (cleanPhone.isBlank() && contactName.isNotBlank()) {
            val contactRes = findContact(contactName)
            if (contactRes.success && contactRes.technicalDetails != null) {
                cleanPhone = contactRes.technicalDetails.removePrefix("Number: ").replace(Regex("[^0-9+]"), "")
            }
        }

        // WhatsApp direct send intent or web api intent
        val uri = if (cleanPhone.isNotBlank()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
        } else {
            Uri.parse("whatsapp://send?text=${Uri.encode(message)}")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pm = context.packageManager
        val isInstalled = try {
            pm.getPackageInfo("com.whatsapp", 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }

        return if (isInstalled || intent.resolveActivity(pm) != null) {
            try {
                context.startActivity(intent)
                ToolExecutionResult(
                    success = true,
                    toolName = "openWhatsApp",
                    userSummary = "Boss, I've prepared the message. Android requires you to tap Send.",
                    requiresUserAction = true
                )
            } catch (e: Exception) {
                ToolExecutionResult(
                    success = false,
                    toolName = "openWhatsApp",
                    userSummary = "Failed to launch WhatsApp: ${e.message}",
                    technicalDetails = e.stackTraceToString()
                )
            }
        } else {
            ToolExecutionResult(
                success = false,
                toolName = "openWhatsApp",
                userSummary = "Boss, WhatsApp is not installed on this device.",
                technicalDetails = "Package com.whatsapp not found"
            )
        }
    }

    fun composeMessage(phoneNumber: String, message: String): ToolExecutionResult {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = if (cleanPhone.isNotBlank()) Uri.parse("smsto:$cleanPhone") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ToolExecutionResult(
                success = true,
                toolName = "composeMessage",
                userSummary = "Boss, I've prepared your SMS message. Android requires you to tap Send.",
                requiresUserAction = true
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                toolName = "composeMessage",
                userSummary = "Failed to open SMS composer: ${e.message}",
                technicalDetails = e.stackTraceToString()
            )
        }
    }

    fun readSupportedNotifications(): ToolExecutionResult {
        return ToolExecutionResult(
            success = false,
            toolName = "readSupportedNotifications",
            userSummary = "Boss, reading system notifications requires Android Notification Listener access. You can grant this in Android Settings → Special App Access.",
            technicalDetails = "Requires android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        )
    }

    fun openCamera(): ToolExecutionResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "openCamera", "Opening camera for you, Boss.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "openCamera", "Unable to open camera app: ${e.message}")
        }
    }

    fun openGallery(): ToolExecutionResult {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "openGallery", "Opening your photo gallery, Boss.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "openGallery", "Unable to open gallery: ${e.message}")
        }
    }

    fun openMaps(query: String): ToolExecutionResult {
        val uri = if (query.isNotBlank()) {
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        } else {
            Uri.parse("geo:0,0")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "openMaps", "Opening maps for '$query', Boss.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "openMaps", "Unable to launch maps: ${e.message}")
        }
    }

    fun startNavigation(destination: String): ToolExecutionResult {
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "startNavigation", "Starting turn-by-turn navigation to $destination, Boss.")
        } catch (e: Exception) {
            // Fallback to standard geo intent
            openMaps(destination)
        }
    }

    fun mediaPlay(): ToolExecutionResult = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Playing media")
    fun mediaPause(): ToolExecutionResult = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, "Pausing media")
    fun mediaNext(): ToolExecutionResult = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipping to next track")
    fun mediaPrevious(): ToolExecutionResult = dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Skipping to previous track")

    private fun dispatchMediaKey(keyCode: Int, actionDesc: String): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            return ToolExecutionResult(false, "mediaControl", "Audio service unavailable.")
        }

        try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            return ToolExecutionResult(true, "mediaControl", "$actionDesc, Boss.")
        } catch (e: Exception) {
            return ToolExecutionResult(false, "mediaControl", "Failed to dispatch media control: ${e.message}")
        }
    }

    fun openSettings(type: String): ToolExecutionResult {
        val action = when (type.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "application", "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(true, "openSettings", "Opening ${type.ifBlank { "system" }} settings.")
        } catch (e: Exception) {
            ToolExecutionResult(false, "openSettings", "Unable to open settings: ${e.message}")
        }
    }

    fun getBatteryStatus(): ToolExecutionResult {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val batteryPct: Int = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }

        return if (batteryPct >= 0) {
            val stateText = if (isCharging) "charging ⚡" else "discharging"
            ToolExecutionResult(
                success = true,
                toolName = "getBatteryStatus",
                userSummary = "Boss, battery is at $batteryPct% and currently $stateText."
            )
        } else {
            ToolExecutionResult(
                success = false,
                toolName = "getBatteryStatus",
                userSummary = "Could not retrieve battery statistics."
            )
        }
    }
}
