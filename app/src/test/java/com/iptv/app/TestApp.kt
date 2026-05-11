package com.iptv.app

import android.app.Application

/**
 * Stand-in [Application] for Robolectric runs. Real [IptvApp] eagerly opens
 * EncryptedSharedPreferences and schedules WorkManager jobs — both fail
 * outside an Android device. This empty Application is used via the
 * `@Config(application = ...)` annotation on Compose UI tests.
 */
class TestApp : Application()
