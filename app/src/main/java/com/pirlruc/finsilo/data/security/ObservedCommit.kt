package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences

/** commit() so a PIN or key write reports failure to the caller. */
@Suppress("ApplySharedPref")
internal fun SharedPreferences.Editor.commitObserved(): Boolean = commit()
