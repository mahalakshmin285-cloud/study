package com.example

import android.app.Application
import android.util.Log
import com.example.data.notifications.StudyNotificationHelper
import com.google.firebase.FirebaseApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class StudyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Throwable) {
            Log.e("StudyApplication", "FirebaseApp initialization handled safely: ${e.message}", e)
        }

        try {
            StudyNotificationHelper.createNotificationChannel(this)
        } catch (e: Throwable) {
            Log.e("StudyApplication", "Notification channel creation handled safely: ${e.message}", e)
        }

        try {
            PDFBoxResourceLoader.init(this)
        } catch (e: Throwable) {
            Log.e("StudyApplication", "PDFBox initialization handled safely: ${e.message}", e)
        }
    }
}
