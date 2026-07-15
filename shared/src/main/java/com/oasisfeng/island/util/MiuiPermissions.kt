package com.oasisfeng.island.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** HyperOS / MIUI-only permissions which are not part of the Android SDK. */
object MiuiPermissions {

	@JvmStatic fun permissionEditorIntent(context: Context): Intent =
		Intent(ACTION_APP_PERMISSION_EDITOR)
			.setComponent(ComponentName(SECURITY_CENTER_PACKAGE, PERMISSIONS_EDITOR_ACTIVITY))
			.putExtra(EXTRA_PACKAGE_NAME_MIUI, context.packageName)
			.putExtra(EXTRA_PACKAGE_NAME, context.packageName)

	@JvmStatic fun isPermissionEditorAvailable(context: Context): Boolean =
		permissionEditorIntent(context).resolveActivity(context.packageManager) != null
}

private const val ACTION_APP_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
private const val PERMISSIONS_EDITOR_ACTIVITY = "com.miui.permcenter.permissions.PermissionsEditorActivity"
private const val EXTRA_PACKAGE_NAME_MIUI = "extra_pkgname"
private const val EXTRA_PACKAGE_NAME = "package_name"
