package com.example.data.repository

import com.example.data.dao.LouvorDao
import com.example.data.model.Culto
import com.example.data.model.Louvor
import kotlinx.coroutines.flow.Flow

class LouvorRepository(private val louvorDao: LouvorDao) {
    val allCultos: Flow<List<Culto>> = louvorDao.getAllCultos()

    fun getCultoById(id: Int): Flow<Culto?> = louvorDao.getCultoById(id)

    suspend fun insertCulto(culto: Culto): Long = louvorDao.insertCulto(culto)

    suspend fun deleteCulto(culto: Culto) = louvorDao.deleteCulto(culto)

    fun getLouvoresForCulto(cultoId: Int): Flow<List<Louvor>> = louvorDao.getLouvoresForCulto(cultoId)

    fun getLouvorById(id: Int): Flow<Louvor?> = louvorDao.getLouvorById(id)

    suspend fun insertLouvor(louvor: Louvor): Long = louvorDao.insertLouvor(louvor)

    suspend fun updateLouvor(louvor: Louvor) = louvorDao.updateLouvor(louvor)

    suspend fun deleteLouvor(louvor: Louvor) = louvorDao.deleteLouvor(louvor)

    suspend fun deleteLouvoresForCulto(cultoId: Int) = louvorDao.deleteLouvoresForCulto(cultoId)
}
