package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Culto
import com.example.data.model.Louvor
import com.example.data.repository.LouvorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LouvorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LouvorRepository

    val allCultos: StateFlow<List<Culto>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LouvorRepository(database.louvorDao())
        
        allCultos = repository.allCultos
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    // Culto operations
    fun getCultoById(id: Int): Flow<Culto?> = repository.getCultoById(id)

    fun createCulto(nome: String, data: String, horario: String, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertCulto(Culto(nome = nome, data = data, horario = horario))
            onFinished()
        }
    }

    fun deleteCulto(culto: Culto) {
        viewModelScope.launch {
            repository.deleteCulto(culto)
        }
    }

    fun updateCulto(culto: Culto, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertCulto(culto)
            onFinished()
        }
    }

    fun syncCultoWithCloud(cultoId: Int, updatedCulto: Culto, updatedLouvores: List<Louvor>, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            repository.insertCulto(updatedCulto.copy(id = cultoId))
            repository.deleteLouvoresForCulto(cultoId)
            for (louvor in updatedLouvores) {
                repository.insertLouvor(louvor.copy(cultoId = cultoId))
            }
            onFinished()
        }
    }

    fun importCultoWithLouvores(culto: Culto, louvores: List<Louvor>, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            val cultoId = repository.insertCulto(culto).toInt()
            for (louvor in louvores) {
                repository.insertLouvor(louvor.copy(cultoId = cultoId))
            }
            onFinished()
        }
    }

    // Louvor operations
    fun getLouvoresForCulto(cultoId: Int): Flow<List<Louvor>> = repository.getLouvoresForCulto(cultoId)

    fun getLouvorById(id: Int): Flow<Louvor?> = repository.getLouvorById(id)

    fun addLouvor(
        cultoId: Int,
        nomeMusica: String,
        linkYoutube: String,
        cantor: String,
        tom: String,
        observacoes: String,
        status: String,
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.insertLouvor(
                Louvor(
                    cultoId = cultoId,
                    nomeMusica = nomeMusica,
                    linkYoutube = linkYoutube,
                    cantor = cantor,
                    tom = tom,
                    observacoes = observacoes,
                    status = status
                )
            )
            onFinished()
        }
    }

    fun updateLouvorStatus(louvor: Louvor, newStatus: String) {
        viewModelScope.launch {
            repository.updateLouvor(louvor.copy(status = newStatus))
        }
    }

    fun updateLouvor(louvor: Louvor, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateLouvor(louvor)
            onFinished()
        }
    }

    fun deleteLouvor(louvor: Louvor) {
        viewModelScope.launch {
            repository.deleteLouvor(louvor)
        }
    }
}
