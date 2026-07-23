package com.example.data.dao

import androidx.room.*
import com.example.data.model.Culto
import com.example.data.model.Louvor
import kotlinx.coroutines.flow.Flow

@Dao
interface LouvorDao {
    // Cultos queries
    @Query("SELECT * FROM cultos ORDER BY data DESC, horario DESC")
    fun getAllCultos(): Flow<List<Culto>>

    @Query("SELECT * FROM cultos WHERE id = :id LIMIT 1")
    fun getCultoById(id: Int): Flow<Culto?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCulto(culto: Culto): Long

    @Delete
    suspend fun deleteCulto(culto: Culto)

    // Louvores queries
    @Query("SELECT * FROM louvores WHERE cultoId = :cultoId ORDER BY nomeMusica ASC")
    fun getLouvoresForCulto(cultoId: Int): Flow<List<Louvor>>

    @Query("SELECT * FROM louvores WHERE id = :id LIMIT 1")
    fun getLouvorById(id: Int): Flow<Louvor?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLouvor(louvor: Louvor): Long

    @Update
    suspend fun updateLouvor(louvor: Louvor)

    @Delete
    suspend fun deleteLouvor(louvor: Louvor)

    @Query("DELETE FROM louvores WHERE cultoId = :cultoId")
    suspend fun deleteLouvoresForCulto(cultoId: Int)
}
