package com.trae.expensetracker

import android.app.Application
import com.trae.expensetracker.data.AppContainer

class ExpenseTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

