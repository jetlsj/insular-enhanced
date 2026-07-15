package com.oasisfeng.island.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.util.Log

/**
 * Bridges an activity launch out of a background profile process.
 *
 * Android 14+ rejects a direct startActivity() made by the profile-side Shuttle provider because
 * that process is not visible. A PendingIntent created in the target profile preserves its user,
 * while sending it from the visible activity in the parent profile satisfies BAL restrictions.
 */
object CrossProfileActivityLauncher {

	@JvmStatic @OwnerUser @ProfileUser fun prepare(
		context: Context, source: Intent, requestCode: Int, trace: String
	): PendingIntent? {
		val intent = Intent(source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		if (intent.component == null) {
			val component = intent.resolveActivity(context.packageManager)
			if (component == null) {
				Log.e(TAG, "[$trace] No activity resolves ${intent.toUri(Intent.URI_INTENT_SCHEME)} in user ${Users.currentId()}")
				return null
			}
			intent.component = component
		}
		return try {
			val options = activityOptions(forCreator = true)
			PendingIntent.getActivity(context, requestCode, intent,
				FLAG_CANCEL_CURRENT or FLAG_ONE_SHOT or FLAG_IMMUTABLE, options).also {
				Log.i(TAG, "[$trace] Prepared activity ${intent.component?.flattenToShortString()} in user ${Users.currentId()}") }
		} catch (e: RuntimeException) {
			Log.e(TAG, "[$trace] Failed to prepare activity in user ${Users.currentId()}", e)
			null
		}
	}

	@JvmStatic fun send(context: Context, action: PendingIntent, trace: String): Boolean = try {
		action.send(context, 0, null, null, null, null, activityOptions(forCreator = false))
		Log.i(TAG, "[$trace] Dispatched cross-profile activity from user ${Users.currentId()}")
		true
	} catch (e: PendingIntent.CanceledException) {
		Log.e(TAG, "[$trace] Cross-profile activity PendingIntent was canceled", e)
		false
	} catch (e: RuntimeException) {
		Log.e(TAG, "[$trace] Failed to dispatch cross-profile activity", e)
		false
	}

	private fun activityOptions(forCreator: Boolean) = ActivityOptions.makeBasic().apply {
		if (SDK_INT >= UPSIDE_DOWN_CAKE) {
			if (forCreator) setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
			else setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
		}
	}.toBundle()
}

private const val TAG = "Island.XPAL"
