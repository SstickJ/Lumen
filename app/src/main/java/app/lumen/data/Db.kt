package app.lumen.data

import androidx.room.*
import app.lumen.core.IngredientDraft
import app.lumen.core.Totals
import app.lumen.core.totals
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries")
data class EntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val name: String,
    val meal: String,
    val source: String,          // AI | BARCODE | MANUAL
    val photoPath: String? = null,
    val barcode: String? = null,
    val confidence: Double? = null,
    val notes: String? = null
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")]
)
data class IngredientRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val barcode: String? = null
)

@Entity(tableName = "exercises", indices = [Index(value = ["externalId"], unique = true)])
data class ExerciseRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val name: String,
    val minutes: Int,
    val kcal: Double,
    val source: String,          // MANUAL | HEALTH_CONNECT
    val externalId: String? = null
)

data class EntryWithIngredients(
    @Embedded val entry: EntryRow,
    @Relation(parentColumn = "id", entityColumn = "entryId") val ingredients: List<IngredientRow>
)

fun IngredientRow.toDraft() = IngredientDraft(name, grams, kcal100, protein100, carbs100, fat100, barcode)
fun IngredientDraft.toRow(entryId: Long) =
    IngredientRow(entryId = entryId, name = name, grams = grams, kcal100 = kcalPer100, protein100 = proteinPer100,
        carbs100 = carbsPer100, fat100 = fatPer100, barcode = barcode)

fun EntryWithIngredients.sum(): Totals = ingredients.map { it.toDraft() }.totals()

@Dao
interface LumenDao {
    @Transaction
    @Query("SELECT * FROM entries WHERE ts >= :from AND ts < :to ORDER BY ts DESC")
    fun entries(from: Long, to: Long): Flow<List<EntryWithIngredients>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: Long): EntryWithIngredients?

    @Insert suspend fun insertEntry(e: EntryRow): Long
    @Update suspend fun updateEntry(e: EntryRow)
    @Insert suspend fun insertIngredients(l: List<IngredientRow>)
    @Query("DELETE FROM ingredients WHERE entryId = :id") suspend fun clearIngredients(id: Long)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM exercises WHERE ts >= :from AND ts < :to ORDER BY ts DESC")
    fun exercises(from: Long, to: Long): Flow<List<ExerciseRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercise(e: ExerciseRow): Long
    @Query("DELETE FROM exercises WHERE id = :id") suspend fun deleteExercise(id: Long)
}

@Database(entities = [EntryRow::class, IngredientRow::class, ExerciseRow::class], version = 1, exportSchema = false)
abstract class LumenDb : RoomDatabase() {
    abstract fun dao(): LumenDao
}
