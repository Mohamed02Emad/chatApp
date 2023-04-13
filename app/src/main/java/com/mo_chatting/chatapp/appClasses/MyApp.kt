package com.mo_chatting.chatapp.appClasses

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.mo_chatting.chatapp.data.dataStore.DataStoreImpl
import dagger.Component
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class MyApp : Application()
