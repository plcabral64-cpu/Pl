package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cultos")
data class Culto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val data: String,
    val horario: String,
    val cloudId: String? = null
)

@Entity(
    tableName = "louvores",
    foreignKeys = [
        ForeignKey(
            entity = Culto::class,
            parentColumns = ["id"],
            childColumns = ["cultoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cultoId"])]
)
data class Louvor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cultoId: Int,
    val nomeMusica: String,
    val linkYoutube: String,
    val cantor: String,
    val tom: String,
    val observacoes: String,
    val status: String // "Recebido", "Baixado", "Ensaiado", "Pronto"
)
