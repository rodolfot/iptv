package com.iptv.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Statically verifies that the migrations registered in [ALL_MIGRATIONS] cover
 * every step from version 1 up to the database's current version. If someone
 * bumps `version` without adding the matching `MIGRATION_N_N+1`, this test
 * fails before users hit a destructive fallback.
 */
class MigrationCoverageTest {

    @Test
    fun `every version step has a registered migration`() {
        // Mantenha igual ao `version` da @Database em AppDatabase. As anotações
        // do Room não são retidas em runtime, então não dá pra ler por
        // reflection — ao bumpar o schema, atualize aqui e adicione a migração.
        val currentDbVersion = 10
        val expectedSteps = (1 until currentDbVersion).map { it to it + 1 }
        val actualSteps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(expectedSteps, actualSteps)
    }
}
