package com.oasisfeng.island.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo.FLAG_STOPPED
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.getSystemService
import com.oasisfeng.android.util.Apps
import com.oasisfeng.island.util.*
import com.oasisfeng.island.util.Users.Companion.toId

/**
 * Utilities of "managed profile" related functionality
 *
 * Created by Oasis on 2017/2/20.
 */
object IslandManager {

    @JvmStatic fun ensureLegacyInstallNonMarketAppAllowed(context: Context, policies: DevicePolicies): Boolean {
        if (isInstallFromUnknownSourcesAllowed(context)) return true
        if (policies.isProfileOwner) {
            policies.clearUserRestrictionsIfNeeded(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            if (SDK_INT < O) @Suppress("DEPRECATION")
                policies.execute(DPM::setSecureSetting, Settings.Secure.INSTALL_NON_MARKET_APPS, "1")
        }
        return isInstallFromUnknownSourcesAllowed(context)
    }

    @Suppress("DEPRECATION") private fun isInstallFromUnknownSourcesAllowed(context: Context) =
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0

    @JvmStatic @OwnerUser @ProfileUser fun ensureAppHiddenState(context: Context, pkg: String, state: Boolean): Boolean {
        val policies = DevicePolicies(context)
        if (policies.setApplicationHidden(pkg, state)) return true
        // Since setApplicationHidden() return false if already in that state, also check the current state.
        val hidden = policies(DPM::isApplicationHidden, pkg)
        return state == hidden
    }

    /** @return error information, or empty string for success. */
    @JvmStatic @OwnerUser @ProfileUser fun ensureAppFreeToLaunch(context: Context, pkg: String): String {
        val policies = DevicePolicies(context)
        if (policies(DPM::isApplicationHidden, pkg) && ! policies.setApplicationHidden(pkg, false)
            && ! Apps.of(context).isInstalledInCurrentUser(pkg))
                return "not_installed" // Not installed in profile, just give up.
        try { if (policies.isPackageSuspended(pkg)) policies(DPM::setPackagesSuspended, arrayOf(pkg), false) }
        catch (_: PackageManager.NameNotFoundException) { return "not_found" }
        return ""
    }

    @JvmStatic @OwnerUser fun launchApp(context: Context, pkg: String, profile: UserHandle): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        try {
            val activities = launcherApps.getActivityList(pkg, profile)
            if (activities.isNullOrEmpty())
                return false.also { Log.w(TAG, "Unable to launch $pkg in profile ${profile.toId()}") }
            Log.i(TAG, "Launching $pkg in profile ${profile.toId()}...")
            launcherApps.startMainActivity(activities[0].componentName, profile, null, null)
            return true }
        catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "App not found while launching: $pkg @ user $profile", e)
            return false }
        catch (e: RuntimeException) {    // SecurityException: Cannot retrieve activities for unrelated profile 10
            Log.e(TAG, "Error launching app: $pkg @ user $profile", e)
            return false }
    }

    /**
     * A newly installed package remains stopped until its first successful launch.  This flag is
     * also useful for detecting vendor firmware that silently drops LauncherApps.startMainActivity().
     */
    @JvmStatic @OwnerUser fun isAppStopped(context: Context, pkg: String, profile: UserHandle): Boolean? {
        return try {
            context.getSystemService<LauncherApps>()!!.getApplicationInfo(pkg, 0, profile)
                .flags and FLAG_STOPPED != 0
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to read stopped state: $pkg @ user ${profile.toId()}", e)
            null
        }
    }

    /** Prepare the MIUI-compatible launch path in the target profile. Must be sent by the visible caller. */
    @JvmStatic @ProfileUser fun prepareAppLaunch(context: Context, pkg: String, trace: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return null.also { Log.e(TAG, "[$trace] No profile-side launch intent for $pkg in user ${Users.currentId()}") }
        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return CrossProfileActivityLauncher.prepare(context, intent, pkg.hashCode() xor REQUEST_APP_LAUNCH, trace)
    }

    @JvmStatic fun getProfileIdsIncludingDisabled(context: Context): IntArray =
        context.getSystemService<UserManager>()!!.getProfileIds(Users.currentId(), false)
            ?: context.getSystemService<UserManager>()!!.userProfiles.map { it.toId() }.toIntArray() // Fallback to profiles without disabled.

    fun isReady(context: Context, profile: UserHandle) = context.getSystemService<UserManager>()!!.isUserRunning(profile)
}

private const val REQUEST_APP_LAUNCH = 0x4C41
private const val TAG = "Island.Manager"
