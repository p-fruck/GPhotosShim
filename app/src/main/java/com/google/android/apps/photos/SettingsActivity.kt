package com.google.android.apps.photos

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/*")
        val apps = packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second }

        AlertDialog.Builder(this)
            .setTitle("Open photos with")
            .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_PACKAGE, apps[which].first).apply()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        const val PREFS = "shim"
        const val KEY_PACKAGE = "gallery_package"
    }
}
