package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object PhoneControlHelper {

    private const val TAG = "PhoneControlHelper"

    /**
     * Resolves a contact name or phone number from the device contacts.
     */
    fun findContact(context: Context, query: String): Pair<String, String>? {
        var cleanQuery = query.trim()
            .replace(Regex("^(?:call|dial|phone|please|to)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:please|now)$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanQuery.isBlank()) return null

        // Check if user provided raw phone number directly
        val rawDigits = cleanQuery.filter { it.isDigit() || it == '+' }
        if (rawDigits.length >= 7 && cleanQuery.replace(Regex("[+0-9\\s-]"), "").isEmpty()) {
            return Pair(cleanQuery, rawDigits)
        }

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContacts) {
            Log.w(TAG, "READ_CONTACTS permission not granted.")
            return null
        }

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            // 1. Direct LIKE query
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$cleanQuery%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex) ?: cleanQuery
                    val number = cursor.getString(numberIndex) ?: ""
                    if (number.isNotBlank()) {
                        return Pair(name, number)
                    }
                }
            }

            // 2. Fallback: Scan contacts with case-insensitive / token matching
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val targetLow = cleanQuery.lowercase()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    val nameLow = name.lowercase()
                    if (nameLow == targetLow || nameLow.contains(targetLow) || targetLow.contains(nameLow)) {
                        return Pair(name, number)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contact search error: ${e.message}")
        }
        return null
    }

    /**
     * Directly calls a contact or number safely.
     */
    @SuppressLint("MissingPermission")
    fun makeCall(context: Context, target: String): String {
        val contactMatch = findContact(context, target)
        val displayName = contactMatch?.first ?: target
        val numberToCall = contactMatch?.second?.replace(Regex("[^0-9+]"), "")
            ?: target.filter { it.isDigit() || it == '+' }

        if (numberToCall.isBlank()) {
            // Open dialer without prefill if contact not found
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                return "Contact not found for $target. Opening phone dialer."
            } catch (e: Exception) {
                return "Could not open phone dialer."
            }
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$numberToCall")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numberToCall")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            if (hasCallPermission) "Calling $displayName." else "Dialing $displayName."
        } catch (e: Exception) {
            Log.e(TAG, "MakeCall failed: ${e.message}", e)
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numberToCall")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                "Dialing $displayName."
            } catch (_: Exception) {
                "Unable to make call to $displayName."
            }
        }
    }

    /**
     * Dynamically launches any application installed on the user's phone.
     */
    fun launchApp(context: Context, appQuery: String): String? {
        val pm = context.packageManager
        val cleanName = appQuery.trim().lowercase()
            .replace(Regex("^(?:open|launch|start|run|the)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:app|application|please)$", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanName.isBlank()) return null

        // 1. Direct package map for instant launch of high-frequency apps
        val wellKnown = mapOf(
            "whatsapp" to "com.whatsapp",
            "discord" to "com.discord",
            "spotify" to "com.spotify.music",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "settings" to "com.android.settings",
            "uber" to "com.ubercab",
            "ola" to "com.olacabs.customer",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "netflix" to "com.netflix.mediaclient",
            "messages" to "com.google.android.apps.messaging",
            "sms" to "com.google.android.apps.messaging",
            "paytm" to "net.one97.paytm",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            "phonepe" to "com.phonepe.app",
            "camera" to "com.google.android.GoogleCamera",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "store" to "com.android.vending",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "reddit" to "com.reddit.frontpage",
            "files" to "com.google.android.apps.nbu.files",
            "drive" to "com.google.android.apps.docs"
        )

        val knownPkg = wellKnown[cleanName]
        if (knownPkg != null) {
            val intent = pm.getLaunchIntentForPackage(knownPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return cleanName.replaceFirstChar { it.uppercase() }
            }
        }

        // 2. Dynamic discovery: search all launcher activities on the device
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pkgList = pm.queryIntentActivities(mainIntent, 0)

            // Look for exact matching label first, then contains
            val exactMatch = pkgList.firstOrNull { ri ->
                val label = ri.loadLabel(pm).toString().lowercase()
                label == cleanName
            }
            val match = exactMatch ?: pkgList.firstOrNull { ri ->
                val label = ri.loadLabel(pm).toString().lowercase()
                val pkg = ri.activityInfo.packageName.lowercase()
                label.contains(cleanName) || cleanName.contains(label) || pkg.contains(cleanName)
            }

            if (match != null) {
                val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return match.loadLabel(pm).toString()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic app launch error: ${e.message}")
        }

        return null
    }

    /**
     * Sets an alarm with optional label and skips UI dialog directly.
     */
    fun setAlarm(context: Context, hour: Int, minute: Int, label: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "SetAlarm error: ${e.message}")
            false
        }
    }

    /**
     * Sets a countdown timer in seconds.
     */
    fun setTimer(context: Context, seconds: Int, label: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "SetTimer error: ${e.message}")
            false
        }
    }

    /**
     * Sets a calendar reminder.
     */
    fun setReminder(context: Context, title: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600000L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "SetReminder error: ${e.message}")
            false
        }
    }
}
