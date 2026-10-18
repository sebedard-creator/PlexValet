package com.seb.sdpr.data

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val SERVER_IP  = stringPreferencesKey("server_ip")
    val PLEX_TOKEN = stringPreferencesKey("plex_token")
    val MACHINE_ID = stringPreferencesKey("machine_id")
    val COLLECTION_CATALOG = stringPreferencesKey("collection_catalog")
    val LAST_SYNC  = longPreferencesKey("last_sync")
}
