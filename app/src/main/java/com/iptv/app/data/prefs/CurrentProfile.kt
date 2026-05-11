package com.iptv.app.data.prefs

import com.iptv.app.data.db.DEFAULT_PROFILE_ID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the current profile id for user-data scoping. Returns the active
 * profile id when one is set, falling back to the legacy "default" sentinel
 * for migrated data and for installs that never created a named profile.
 */
@Singleton
class CurrentProfile @Inject constructor(
    private val secure: SecureStore
) {
    fun id(): String = secure.getActiveProfileId() ?: DEFAULT_PROFILE_ID

    /** True when the active profile is in Kids mode. Read directly from the
     *  encrypted profile blob so it stays cheap to call from VMs and DAOs. */
    fun isKids(): Boolean = activeProfile()?.kids == true

    /** Allow-list of "kind:categoryId" tokens for the Kids profile, or empty. */
    fun allowedCategoryTokens(): Set<String> = activeProfile()?.allowedCategories ?: emptySet()

    private fun activeProfile(): Profile? {
        val activeId = secure.getActiveProfileId() ?: return null
        return Profile.listFromJson(secure.getProfilesJson()).firstOrNull { it.id == activeId }
    }
}
