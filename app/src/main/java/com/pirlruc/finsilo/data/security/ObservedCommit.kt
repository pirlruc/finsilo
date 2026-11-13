package com.pirlruc.finsilo.data.security

import android.content.SharedPreferences

/** commit() so a PIN or key write reports failure to the caller. apply() would hide that failure. */
// lint-exception-until: 2026-11-27
@Suppress("ApplySharedPref")
internal fun SharedPreferences.Editor.commitObserved(): Boolean = commit()
