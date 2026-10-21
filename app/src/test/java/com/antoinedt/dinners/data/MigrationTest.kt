package com.antoinedt.dinners.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Data saved by version 1 of the app must survive the upgrade that added labels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun migrate1To2KeepsData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `url` TEXT, `imageUrl` TEXT, `ingredients` TEXT NOT NULL, `instructions` TEXT NOT NULL, `notes` TEXT NOT NULL, `servings` TEXT, `totalTime` TEXT, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_name` ON `recipes` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `dinners` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `recipeId` INTEGER NOT NULL, FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dinners_epochDay` ON `dinners` (`epochDay`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dinners_recipeId` ON `dinners` (`recipeId`)")
            db.execSQL("INSERT INTO recipes (id, name, ingredients, instructions, notes, createdAt) VALUES (1, 'Curry', '', '', '', 0)")
            db.execSQL("INSERT INTO dinners (epochDay, recipeId) VALUES (20000, 1)")
            db.version = 1
        }

        val room = AppDatabase.build(context, name)
        runBlocking {
            val recipe = room.recipeDao().getAll().single()
            assertEquals("Curry", recipe.name)
            assertEquals(emptyList<String>(), recipe.labelList)
            room.recipeDao().update(recipe.withLabels(listOf("Épicé")))
            assertEquals(listOf("Épicé"), room.recipeDao().getAll().single().labelList)
            assertEquals(1, room.dinnerDao().getAll().size)
        }
        room.close()
    }
}
