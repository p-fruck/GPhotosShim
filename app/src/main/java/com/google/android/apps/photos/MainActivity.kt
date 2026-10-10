package com.google.android.apps.photos

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.lang.Exception

class MainActivity : Activity() {
    private var cameraRelaunchIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receivedIntent = this.intent

        // Extract all necessary information from the intent
        // We store the relaunch intent in this class so it can be used in callbacks
        // (In this case that is just the permission callback)
        val sessionId = receivedIntent.getStringExtra("external_session_id")
        val uri = Uri.parse(receivedIntent.data.toString())
        val type = receivedIntent.type
        val processingURI: Uri? =
            receivedIntent.getParcelableExtra("processing_uri_intent_extra")
        cameraRelaunchIntent =
            receivedIntent.getParcelableExtra("CAMERA_RELAUNCH_INTENT_EXTRA")

        // Make sure there is even a session id, the call is probably invalid if this is gone
        if (sessionId == null) return finish()

        // Make sure that we have the READ_EXTERNAL_STORAGE permission,
        // for some stupid reason we need this permission to check if a URI is pending
        if (checkSelfPermission(MEDIA_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.i("PhotosShim", "No media permission, requesting it")
            requestPermissions(arrayOf(MEDIA_PERMISSION), PERMISSIONS_INT)
            // We can now quit the app, as GCam wil relaunch it again automatically
            return finish()
        }

        Log.i(
            "PhotosShim", "SessionId: '" + sessionId +
                    "', URI:'" + uri +
                    "', type: '" + type +
                    "', processingURI: '" + processingURI + "'"
        )

        // Keep polling the URI to see if it has stopped being pending
        var success = false
        for (i in 0 until iterations) {
            if (!isUriPending(applicationContext, uri)) {
                success = true
                break
            }
            Log.i("PhotosShim", "Pending...")
            sleep(waitMS.toLong())
        }
        if (!success) return quit()

        // If the URI is not pending anymore we send an implicit intent
        // so you can now handle the image with any gallery you want
        val intent = Intent()
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(uri, "image/*")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
            .getString(SettingsActivity.KEY_PACKAGE, null)
            ?.let { intent.setPackage(it) }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Chosen gallery was uninstalled: fall back to the system chooser
            intent.setPackage(null)
            startActivity(intent)
        }
        finish()
    }

    // Try to relaunch the camera and then quit this app
    private fun quit() {
        try {
            cameraRelaunchIntent?.send()
        } catch (e: Exception) {
            Log.e("PhotosShim", "Error sending intent", e)
        }
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode != PERMISSIONS_INT) return // Check if this result is for us
        if (permissions.isEmpty() || grantResults.isEmpty()) return // Check if has useful data
        // And if it has the correct permission we can quit to relaunch with the right privileges
        if (permissions[0] == MEDIA_PERMISSION) quit()
    }

    companion object {
        // Polling settings for checking if the URI is finished yet
        private const val waitMS = 100
        private const val maximumSeconds = 30000
        private const val iterations = maximumSeconds * 1000 / waitMS

        // Random integer to identify app in permission callback
        private const val PERMISSIONS_INT = 755009201

        private val MEDIA_PERMISSION =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        // Check if a URI for an image is still being processed by Google Camera
        fun isUriPending(context: Context, contentUri: Uri): Boolean {
            val proj = arrayOf(MediaStore.Images.Media.IS_PENDING)

            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            val cursor = context.contentResolver
                .query(contentUri, proj, args, null)
            val result =
                if (cursor != null && cursor.moveToFirst()) {
                    // Get the data of the IS_PENDING property on the first result
                    val columnIndexData = cursor.getColumnIndex(proj[0])
                    cursor.getInt(columnIndexData) != 0
                } else {
                    false
                }
            cursor?.close()

            return result
        }

        // Simple function to sleep while catching any thread errors
        fun sleep(millis: Long) {
            try {
                Thread.sleep(millis)
            } catch (e: Exception) {
                Log.e("PhotosShim", "Error sleeping", e)
            }
        }
    }
}