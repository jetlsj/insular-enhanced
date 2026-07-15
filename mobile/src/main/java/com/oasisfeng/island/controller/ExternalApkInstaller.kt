package com.oasisfeng.island.controller

import android.Manifest.permission.REQUEST_INSTALL_PACKAGES
import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.oasisfeng.island.installer.InstallerExtras
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.CrossProfileActivityLauncher
import com.oasisfeng.island.util.ModuleContext
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.Users.Companion.toId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Installs a user-selected standalone APK directly in Island.
 *
 * File selection deliberately runs in the managed profile. The resulting content URI is therefore
 * granted to the same user that owns the installer session, avoiding cross-user URI grants that are
 * frequently stripped by customized Android systems.
 */
object ExternalApkInstaller {

	@JvmStatic fun request(activity: FragmentActivity) {
		val profile = Users.profile
		if (profile == null) {
			Toast.makeText(activity, R.string.prompt_island_not_ready, Toast.LENGTH_LONG).show()
			return
		}

		val trace = "external-apk:u${profile.toId()}:${SystemClock.elapsedRealtime()}"
		Log.i(TAG, "[$trace] External APK installation requested")
		activity.lifecycleScope.launch {
			val action = withContext(Dispatchers.IO) {
				Shuttle(activity.applicationContext, to = profile).invokeNoThrows(with = trace) {
					ExternalApkInstallActivity.prepareAction(this, it)
				}
			}
			if (action == null) {
				Log.e(TAG, "[$trace] Island is unavailable or failed to prepare APK picker")
				Toast.makeText(activity, R.string.toast_apk_install_island_unavailable, Toast.LENGTH_LONG).show()
			} else if (! CrossProfileActivityLauncher.send(activity, action, trace)) {
				Toast.makeText(activity, R.string.toast_apk_install_launch_failed, Toast.LENGTH_LONG).show()
			}
		}
	}
}

/** Transparent trampoline that owns both the document URI grant and installer launch in Island. */
@ProfileUser class ExternalApkInstallActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (Users.isParentProfile()) {
			Log.e(TAG, "[$trace] Refusing external APK flow in parent profile")
			Toast.makeText(this, R.string.toast_apk_install_wrong_profile, Toast.LENGTH_LONG).show()
			finish()
			return
		}
		Log.i(TAG, "[$trace] APK picker opened in user ${Users.currentId()}")
		if (savedInstanceState == null) ensurePermissionThenPickApk()
	}

	private fun ensurePermissionThenPickApk() {
		val installerContext = ModuleContext(this).forDeclaredPermission(REQUEST_INSTALL_PACKAGES)
		if (installerContext == null) {
			Log.e(TAG, "[$trace] Installer module is unavailable in user ${Users.currentId()}")
			Toast.makeText(this, R.string.toast_apk_installer_unavailable, Toast.LENGTH_LONG).show()
			finish()
			return
		}
		if (SDK_INT >= O && ! installerContext.packageManager.canRequestPackageInstalls()) {
			Log.w(TAG, "[$trace] REQUEST_INSTALL_PACKAGES is not granted in user ${Users.currentId()}")
			Toast.makeText(this, R.string.toast_apk_install_permission_required, Toast.LENGTH_LONG).show()
			val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
				Uri.fromParts(SCHEME_PACKAGE, installerContext.packageName, null))
			return startForResultOrFinish(settings, REQUEST_UNKNOWN_SOURCES,
				R.string.toast_permission_settings_unavailable)
		}
		pickApk()
	}

	private fun pickApk() {
		val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
			.addCategory(Intent.CATEGORY_OPENABLE)
			.setType(APK_MIME)
		// HyperOS may rewrite an implicit OPEN_DOCUMENT into its private file-manager action even when that
		// file manager is absent from the managed profile. Resolve and pin the real profile-side picker first.
		val handler = packageManager.queryIntentActivities(picker, MATCH_DEFAULT_ONLY)
			.firstOrNull { it.activityInfo.packageName != ANDROID_PACKAGE }
		if (handler != null) picker.component = ComponentName(handler.activityInfo.packageName, handler.activityInfo.name)
		Log.i(TAG, "[$trace] Launching APK document picker ${picker.component ?: "implicitly"} in user ${Users.currentId()}")
		startForResultOrFinish(picker, REQUEST_PICK_APK, R.string.toast_apk_picker_unavailable)
	}

	private fun install(uri: Uri) {
		Log.i(TAG, "[$trace] APK selected: $uri")
		val install = Intent(Intent.ACTION_INSTALL_PACKAGE)
			.setDataAndType(uri, APK_MIME)
			// Pin the bundled installer. Adding the private category here would exclude its MIME-aware filter.
			.setComponent(ComponentName(packageName, APP_INSTALLER_ACTIVITY))
			.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			.putExtra(Intent.EXTRA_RETURN_RESULT, true)
			.putExtra(InstallerExtras.EXTRA_OPERATION_TRACE, trace)
		install.clipData = ClipData.newRawUri("Island APK", uri) // Keep URI grant on Android 8+ and customized ROMs.
		startForResultOrFinish(install, REQUEST_INSTALL_APK, R.string.toast_apk_install_launch_failed)
	}

	private fun startForResultOrFinish(intent: Intent, requestCode: Int, error: Int) {
		try {
			startActivityForResult(intent, requestCode)
		} catch (e: ActivityNotFoundException) {
			Log.e(TAG, "[$trace] No activity handles ${intent.action}", e)
			Toast.makeText(this, error, Toast.LENGTH_LONG).show()
			finish()
		} catch (e: SecurityException) {
			Log.e(TAG, "[$trace] Cannot launch ${intent.action}", e)
			Toast.makeText(this, error, Toast.LENGTH_LONG).show()
			finish()
		}
	}

	@Deprecated("Deprecated in Java")
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		when (requestCode) {
			REQUEST_UNKNOWN_SOURCES -> {
				val installerContext = ModuleContext(this).forDeclaredPermission(REQUEST_INSTALL_PACKAGES)
				if (SDK_INT < O || installerContext?.packageManager?.canRequestPackageInstalls() == true) {
					Log.i(TAG, "[$trace] REQUEST_INSTALL_PACKAGES granted; continuing to picker")
					pickApk()
				} else {
					Log.w(TAG, "[$trace] REQUEST_INSTALL_PACKAGES still denied")
					Toast.makeText(this, R.string.toast_apk_install_permission_denied, Toast.LENGTH_LONG).show()
					finish()
				}
			}
			REQUEST_PICK_APK -> {
				val uri = if (resultCode == RESULT_OK) data?.data else null
				if (uri != null) install(uri)
				else {
					Log.i(TAG, "[$trace] APK selection canceled")
					finish()
				}
			}
			REQUEST_INSTALL_APK -> {
				Log.i(TAG, "[$trace] Installer activity returned result=$resultCode")
				finish()
			}
		}
	}

	private val trace: String by lazy {
		intent.getStringExtra(InstallerExtras.EXTRA_OPERATION_TRACE)
			?: "external-apk:u${Users.currentId()}:${SystemClock.elapsedRealtime()}"
	}

	companion object {
		@JvmStatic fun prepareAction(context: Context, trace: String): PendingIntent? {
			val intent = Intent(context, ExternalApkInstallActivity::class.java)
				.putExtra(InstallerExtras.EXTRA_OPERATION_TRACE, trace)
			return CrossProfileActivityLauncher.prepare(context, intent, trace.hashCode(), trace)
		}
	}
}

private const val APK_MIME = "application/vnd.android.package-archive"
private const val APP_INSTALLER_ACTIVITY = "com.oasisfeng.island.installer.AppInstallerActivity"
private const val ANDROID_PACKAGE = "android"
private const val SCHEME_PACKAGE = "package"
private const val REQUEST_UNKNOWN_SOURCES = 0xA91
private const val REQUEST_PICK_APK = 0xA92
private const val REQUEST_INSTALL_APK = 0xA93
private const val TAG = "Island.ExternalApk"
