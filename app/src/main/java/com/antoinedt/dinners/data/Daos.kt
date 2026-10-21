package com.antoinedt.dinners.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes")
    suspend fun getAll(): List<Recipe>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observe(id: Long): Flow<Recipe?>

    @Query("SELECT * FROM recipes WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Recipe?

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)
}

@Dao
interface DinnerDao {
    @Query(
        """
        SELECT d.id, d.epochDay, d.recipeId, r.name AS recipeName
        FROM dinners d JOIN recipes r ON r.id = d.recipeId
        ORDER BY d.epochDay, d.id
        """
    )
    fun observeAll(): Flow<List<DinnerEntry>>

    @Query("SELECT * FROM dinners")
    suspend fun getAll(): List<Dinner>

    @Insert
    suspend fun insert(dinner: Dinner): Long

    @Query("DELETE FROM dinners WHERE id = :id")
    suspend fun delete(id: Long)
}
