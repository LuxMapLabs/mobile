package com.luxmap.core.offline

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// The last server answer for a screen that has no other local copy (Home's task list, a work
// order's detail), kept so the engineer can keep working when the signal drops. key names the
// screen and its subject, e.g. "home" or "work_order:WO-0032"; json is the response as received.
// Cleared on login and logout: it belongs to the signed-in user.
@Entity(tableName = "local_offline_snapshot")
data class LocalOfflineSnapshotEntity(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Instant,
)
