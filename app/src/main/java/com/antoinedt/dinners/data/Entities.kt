package com.antoinedt.dinners.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes", indices = [Index(value = ["name"])])
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Website the recipe was imported from, if any. */
    val url: String? = null,
    val imageUrl: String? = null,
    /** One ingredient per line. */
    val ingredients: String = "",
    /** One step per line. */
    val instructions: String = "",
    val notes: String = "",
    val servings: String? = null,
    val totalTime: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val ingredientList: List<String> get() = ingredients.lines().filter { it.isNotBlank() }
    val instructionList: List<String> get() = instructions.lines().filter { it.isNotBlank() }
}

/** A dinner eaten on a given day. Dates are stored as [java.time.LocalDate.toEpochDay]. */
@Entity(
    tableName = "dinners",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["epochDay"]), Index(value = ["recipeId"])],
)
data class Dinner(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val recipeId: Long,
)

/** Flat join of a dinner with its recipe name. */
data class DinnerEntry(
    @ColumnInfo(name = "id") val dinnerId: Long,
    val epochDay: Long,
    val recipeId: Long,
    val recipeName: String,
)
