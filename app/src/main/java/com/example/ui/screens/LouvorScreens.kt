package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Culto
import com.example.data.model.Louvor
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// Online Cloud synchronization helpers using a free, anonymous JSON store
suspend fun uploadCultoOnline(culto: Culto, louvores: List<Louvor>, key: String? = null): String? = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject()
        json.put("nome", culto.nome)
        json.put("data", culto.data)
        json.put("horario", culto.horario)
        
        val array = JSONArray()
        for (l in louvores) {
            val lJson = JSONObject()
            lJson.put("musica", l.nomeMusica)
            lJson.put("link", l.linkYoutube)
            lJson.put("cantor", l.cantor)
            lJson.put("tom", l.tom)
            lJson.put("obs", l.observacoes)
            lJson.put("status", l.status)
            array.put(lJson)
        }
        json.put("louvores", array)

        val url = if (key != null) URL("https://api.npoint.io/$key") else URL("https://api.npoint.io")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = if (key != null) "PUT" else "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.doOutput = true
        
        conn.outputStream.use { os ->
            os.write(json.toString().toByteArray(StandardCharsets.UTF_8))
        }

        if (conn.responseCode == 200 || conn.responseCode == 201) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            if (key != null) {
                key
            } else {
                val resJson = JSONObject(response.toString())
                resJson.optString("id", null)
            }
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun downloadCultoOnline(key: String): Pair<Culto, List<Louvor>>? = withContext(Dispatchers.IO) {
    try {
        val cleanKey = key.trim().lowercase()
        val id = if (cleanKey.contains("api.npoint.io/")) {
            cleanKey.substringAfter("api.npoint.io/").trim()
        } else {
            cleanKey
        }
        
        if (id.isEmpty()) return@withContext null

        val url = URL("https://api.npoint.io/$id")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        if (conn.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            val json = JSONObject(response.toString())
            val culto = Culto(
                nome = json.getString("nome"),
                data = json.getString("data"),
                horario = json.getString("horario")
            )
            
            val louvores = mutableListOf<Louvor>()
            val array = json.getJSONArray("louvores")
            for (i in 0 until array.length()) {
                val lJson = array.getJSONObject(i)
                louvores.add(
                    Louvor(
                        cultoId = 0,
                        nomeMusica = lJson.getString("musica"),
                        linkYoutube = lJson.optString("link", ""),
                        cantor = lJson.optString("cantor", ""),
                        tom = lJson.optString("tom", ""),
                        observacoes = lJson.optString("obs", ""),
                        status = lJson.optString("status", "Recebido")
                    )
                )
            }
            Pair(culto, louvores)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Serialization utilities for sharing Cults without login
fun serializeCulto(culto: Culto, louvores: List<Louvor>): String {
    return try {
        val json = JSONObject()
        json.put("nome", culto.nome)
        json.put("data", culto.data)
        json.put("horario", culto.horario)
        
        val array = JSONArray()
        for (l in louvores) {
            val lJson = JSONObject()
            lJson.put("musica", l.nomeMusica)
            lJson.put("link", l.linkYoutube)
            lJson.put("cantor", l.cantor)
            lJson.put("tom", l.tom)
            lJson.put("obs", l.observacoes)
            lJson.put("status", l.status)
            array.put(lJson)
        }
        json.put("louvores", array)
        
        val rawBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        "LOUVORLINK:" + Base64.encodeToString(rawBytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

fun deserializeCulto(code: String): Pair<Culto, List<Louvor>>? {
    return try {
        val cleanCode = code.trim()
        val index = cleanCode.indexOf("LOUVORLINK:")
        if (index == -1) return null
        val base64Part = cleanCode.substring(index + "LOUVORLINK:".length).trim().lines().firstOrNull() ?: ""
        val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
        val jsonString = String(decodedBytes, StandardCharsets.UTF_8)
        
        val json = JSONObject(jsonString)
        val culto = Culto(
            nome = json.getString("nome"),
            data = json.getString("data"),
            horario = json.getString("horario")
        )
        
        val louvores = mutableListOf<Louvor>()
        val array = json.getJSONArray("louvores")
        for (i in 0 until array.length()) {
            val lJson = array.getJSONObject(i)
            louvores.add(
                Louvor(
                    cultoId = 0,
                    nomeMusica = lJson.getString("musica"),
                    linkYoutube = lJson.optString("link", ""),
                    cantor = lJson.optString("cantor", ""),
                    tom = lJson.optString("tom", ""),
                    observacoes = lJson.optString("obs", ""),
                    status = lJson.optString("status", "Recebido")
                )
            )
        }
        Pair(culto, louvores)
    } catch (e: Exception) {
        null
    }
}

fun shareCultoText(context: Context, culto: Culto, louvores: List<Louvor>) {
    val code = serializeCulto(culto, louvores)
    if (code.isBlank()) {
        Toast.makeText(context, "Erro ao gerar código de compartilhamento", Toast.LENGTH_SHORT).show()
        return
    }

    val sb = java.lang.StringBuilder()
    sb.append("🌟 *Culto: ${culto.nome}* 🌟\n")
    sb.append("📅 *Data:* ${culto.data}\n")
    sb.append("⏰ *Horário:* ${culto.horario}\n\n")
    sb.append("🎵 *Músicas Selecionadas:*\n")
    for ((index, louvor) in louvores.withIndex()) {
        sb.append("${index + 1}. *${louvor.nomeMusica}* ")
        if (louvor.tom.isNotBlank()) sb.append("(${louvor.tom}) ")
        if (louvor.cantor.isNotBlank()) sb.append("- _${louvor.cantor}_")
        sb.append("\n")
        if (louvor.linkYoutube.isNotBlank()) {
            sb.append("   🔗 YouTube: ${louvor.linkYoutube}\n")
        }
        if (louvor.observacoes.isNotBlank()) {
            sb.append("   📝 Obs: ${louvor.observacoes}\n")
        }
    }
    
    sb.append("\n📥 *Como importar no LouvorLink:*\n")
    sb.append("Copie esta mensagem inteira e cole na opção \"Importar Culto\" na tela inicial do app LouvorLink para sincronizar tudo!\n\n")
    sb.append("*Código:*\nLOUVORLINK:$code")

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Cronograma do Culto - ${culto.nome}")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar programação com..."))
}

// Helper to open Youtube Links safely
fun openYoutubeLink(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "Link do YouTube não informado", Toast.LENGTH_SHORT).show()
        return
    }
    var formattedUrl = url.trim()
    if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
        formattedUrl = "https://$formattedUrl"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o link. Verifique se a URL é válida.", Toast.LENGTH_LONG).show()
    }
}

// Helper to resolve colors based on Status
fun getStatusColor(status: String): Color {
    return when (status) {
        "Recebido" -> StatusRecebido
        "Baixado" -> StatusBaixado
        "Ensaiado" -> StatusEnsaiado
        "Pronto" -> StatusPronto
        else -> GrayStatus
    }
}

val GrayStatus = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCultoDialog(
    onDismiss: () -> Unit,
    onImportCulto: (Culto, List<Louvor>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var codeText by remember { mutableStateOf("") }
    var whatsappText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = NavyMedium,
        title = {
            Text(
                "Importar Culto",
                fontWeight = FontWeight.Bold,
                color = GoldMetallic
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Selector Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedTab == 0) GoldMetallic else NavyDark, RoundedCornerShape(20.dp))
                            .clickable { if (!isLoading) selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nuvem ☁️",
                            color = if (selectedTab == 0) NavyDark else OffWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedTab == 1) GoldMetallic else NavyDark, RoundedCornerShape(20.dp))
                            .clickable { if (!isLoading) selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "WhatsApp 💬",
                            color = if (selectedTab == 1) NavyDark else OffWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))

                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Digite a chave online gerada pelo app ou colar o link para baixar o cronograma do culto.",
                            color = OffWhite,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { codeText = it },
                            placeholder = { Text("Ex: de2c7106093554ca2d76", color = GrayStatus) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_online_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldMetallic,
                                unfocusedBorderColor = NavyLight,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isLoading
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Cole a mensagem do WhatsApp que contém a linha \"LOUVORLINK:\" para importar.",
                            color = OffWhite,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = whatsappText,
                            onValueChange = { whatsappText = it },
                            placeholder = { Text("Cole o texto do WhatsApp aqui...", color = GrayStatus) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .testTag("import_whatsapp_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldMetallic,
                                unfocusedBorderColor = NavyLight,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isLoading
                        )
                    }
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = GoldMetallic,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Buscando sincronização...", color = GoldLight, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        isLoading = true
                        coroutineScope.launch {
                            val res = downloadCultoOnline(codeText)
                            isLoading = false
                            if (res != null) {
                                onImportCulto(res.first, res.second)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Erro ao buscar na Nuvem. Verifique a chave ou conexão.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val text = whatsappText
                        val index = text.indexOf("LOUVORLINK:")
                        if (index == -1) {
                            Toast.makeText(context, "Código de importação não encontrado no texto.", Toast.LENGTH_LONG).show()
                        } else {
                            val code = text.substring(index).trim().lines().firstOrNull() ?: ""
                            val result = deserializeCulto(code)
                            if (result == null) {
                                Toast.makeText(context, "Código inválido ou corrompido.", Toast.LENGTH_LONG).show()
                            } else {
                                onImportCulto(result.first, result.second)
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !isLoading && (if (selectedTab == 0) codeText.isNotBlank() else whatsappText.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic)
            ) {
                Text(if (selectedTab == 0) "Baixar da Nuvem" else "Confirmar", color = NavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar", color = PureWhite)
            }
        }
    )
}

// 1. LISTA DE CULTOS (Cultos List Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultosListScreen(
    cultos: List<Culto>,
    onAddCultoClicked: () -> Unit,
    onCultoSelected: (Int) -> Unit,
    onDeleteCulto: (Culto) -> Unit,
    onImportCulto: (Culto, List<Louvor>) -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showImportDialog) {
        ImportCultoDialog(
            onDismiss = { showImportDialog = false },
            onImportCulto = { importedCulto, importedLouvores ->
                showImportDialog = false
                onImportCulto(importedCulto, importedLouvores)
                Toast.makeText(context, "Culto importado com sucesso! ✨", Toast.LENGTH_LONG).show()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Logo LouvorLink",
                            tint = GoldMetallic,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "LouvorLink",
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = GoldLight),
                        modifier = Modifier.testTag("import_culto_action_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Importar",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = PureWhite
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCultoClicked,
                containerColor = GoldMetallic,
                contentColor = NavyDark,
                modifier = Modifier
                    .testTag("create_culto_fab")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Culto")
            }
        },
        containerColor = NavyDark,
        modifier = Modifier.testTag("culto_list_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(all = 16.dp)
        ) {
            Text(
                text = "Cultos Programados",
                style = MaterialTheme.typography.headlineSmall,
                color = GoldMetallic,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (cultos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Calendário vazio",
                            tint = GoldMetallic,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhum culto cadastrado ainda.",
                            color = PureWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toque no botão + abaixo para agendar o próximo culto da sua igreja.",
                            color = OffWhite.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(cultos) { culto ->
                        CultoCard(
                            culto = culto,
                            onClicked = { onCultoSelected(culto.id) },
                            onDelete = { onDeleteCulto(culto) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CultoCard(
    culto: Culto,
    onClicked: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = NavyMedium,
            title = { Text("Excluir Culto?", color = PureWhite) },
            text = { Text("Isso apagará permanentemente o culto \"${culto.nome}\" e todos os seus louvores.", color = OffWhite) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Excluir", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = PureWhite)
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("culto_card_${culto.id}")
            .clickable { onClicked() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NavyMedium,
            contentColor = PureWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, NavyLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = culto.nome,
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldLight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info, // Placeholder for Date/Calendar check
                        contentDescription = "Data",
                        tint = GoldMetallic,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = culto.data,
                        color = OffWhite,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        imageVector = Icons.Default.Info, // Placeholder for Time/Clock
                        contentDescription = "Horário",
                        tint = GoldMetallic,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = culto.horario,
                        color = OffWhite,
                        fontSize = 14.sp
                    )
                }
            }
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(48.dp) // Accessibility check standard
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir Culto",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}


// 2. CRIAR CULTO (Create Culto Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCultoScreen(
    onNavigateBack: () -> Unit,
    onSaveCulto: (String, String, String) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var horario by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agendar Novo Culto", fontWeight = FontWeight.Bold, color = PureWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = PureWhite
                )
            )
        },
        containerColor = NavyDark,
        modifier = Modifier.testTag("create_culto_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Preencha as informações do Culto",
                color = GoldMetallic,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text("Nome do Culto", color = GoldLight) },
                placeholder = { Text("ex. Culto de Domingo, Culto de Jovens", color = GrayStatus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("culto_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldMetallic,
                    unfocusedBorderColor = NavyLight,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = NavyMedium,
                    unfocusedContainerColor = NavyMedium
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = data,
                onValueChange = { data = it },
                label = { Text("Data", color = GoldLight) },
                placeholder = { Text("ex. 12/06/2026", color = GrayStatus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("culto_date_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldMetallic,
                    unfocusedBorderColor = NavyLight,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = NavyMedium,
                    unfocusedContainerColor = NavyMedium
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = horario,
                onValueChange = { horario = it },
                label = { Text("Horário", color = GoldLight) },
                placeholder = { Text("ex. 19:30", color = GrayStatus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("culto_time_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldMetallic,
                    unfocusedBorderColor = NavyLight,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = NavyMedium,
                    unfocusedContainerColor = NavyMedium
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (nome.isNotBlank() && data.isNotBlank() && horario.isNotBlank()) {
                        onSaveCulto(nome, data, horario)
                    }
                },
                enabled = nome.isNotBlank() && data.isNotBlank() && horario.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    disabledContainerColor = GrayStatus.copy(alpha = 0.5f),
                    contentColor = NavyDark,
                    disabledContentColor = PureWhite.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_culto_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Agendar Culto", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCultoDialog(
    culto: Culto,
    louvores: List<Louvor>,
    onCloudIdSaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var generatedKey by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        containerColor = NavyMedium,
        title = {
            Text(
                text = "Compartilhar Culto",
                fontWeight = FontWeight.Bold,
                color = GoldMetallic
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (generatedKey == null) {
                    Text(
                        text = "Escolha como deseja compartilhar a programação do culto \"${culto.nome}\":",
                        color = OffWhite,
                        fontSize = 14.sp
                    )
                    
                    // Option 1: WhatsApp
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                shareCultoText(context, culto, louvores)
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(containerColor = NavyDark),
                        border = BorderStroke(1.dp, NavyLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "WhatsApp",
                                tint = GoldLight,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Mensagem do WhatsApp",
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Gera um texto formatado com os links e códigos.",
                                    color = GrayStatus,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Option 2: Cloud Sync
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUploading) {
                                isUploading = true
                                coroutineScope.launch {
                                    val key = uploadCultoOnline(culto, louvores, culto.cloudId)
                                    isUploading = false
                                    if (key != null) {
                                        generatedKey = key
                                        onCloudIdSaved(key)
                                    } else {
                                        Toast.makeText(context, "Erro ao gerar sincronização online.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = NavyDark),
                        border = BorderStroke(1.dp, NavyLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Nuvem",
                                tint = GoldMetallic,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = culto.cloudId?.let { "Atualizar na Nuvem ☁️" } ?: "Sincronizar na Nuvem ☁️",
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = culto.cloudId?.let { "Atualiza as músicas e status sob a chave existente." } ?: "Gera uma chave online para importação automática.",
                                    color = GrayStatus,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (isUploading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = GoldMetallic, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Gerando sincronização na nuvem...", color = GoldLight, fontSize = 13.sp)
                        }
                    }

                } else {
                    // Success View with generated key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Sucesso",
                            tint = Color.Green,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Culto Sincronizado!",
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Envie esta chave para a outra pessoa colar no app LouvorLink:",
                            color = OffWhite,
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Key Display Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NavyDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = generatedKey ?: "",
                                    fontWeight = FontWeight.Bold,
                                    color = GoldMetallic,
                                    fontSize = 15.sp,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Chave LouvorLink", generatedKey)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Chave copiada para a área de transferência! ✨", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("COPIAR", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Chave de Sincronização - LouvorLink")
                                    putExtra(Intent.EXTRA_TEXT, "Aqui está a chave de sincronização do culto *${culto.nome}* no LouvorLink:\n\n🔑 Código: *${generatedKey}*\n\nAbra o app, vá em \"Importar\" e cole esta chave na aba Nuvem!")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartilhar chave com..."))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, contentDescription = "Enviar", tint = NavyDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compartilhar Chave", color = NavyDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text("Fechar", color = PureWhite)
            }
        }
    )
}


// 3. DETALHES DO CULTO COM LISTA DE LOUVORES (Culto Details Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultoDetailsScreen(
    cultoId: Int,
    culto: Culto?,
    louvores: List<Louvor>,
    onNavigateBack: () -> Unit,
    onAddLouvorClicked: (Int) -> Unit,
    onLouvorSelected: (Int) -> Unit,
    onOpenYoutubeUrl: (String) -> Unit,
    onUpdateCulto: (Culto) -> Unit,
    onSyncCultoWithCloud: (Int, Culto, List<Louvor>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showShareDialog by remember { mutableStateOf(false) }

    if (showShareDialog && culto != null) {
        ShareCultoDialog(
            culto = culto,
            louvores = louvores,
            onCloudIdSaved = { key ->
                onUpdateCulto(culto.copy(cloudId = key))
            },
            onDismiss = { showShareDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(culto?.nome ?: "Detalhes do Culto", fontWeight = FontWeight.Bold, color = PureWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PureWhite)
                    }
                },
                actions = {
                    culto?.let { c ->
                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.testTag("share_culto_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartilhar",
                                tint = GoldLight
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = PureWhite
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddLouvorClicked(cultoId) },
                containerColor = GoldMetallic,
                contentColor = NavyDark,
                modifier = Modifier
                    .testTag("add_louvor_fab")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Louvor")
            }
        },
        containerColor = NavyDark,
        modifier = Modifier.testTag("culto_details_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(all = 16.dp)
        ) {
            // Culto Meta Header Card
            culto?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyMedium),
                    border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "INFORMAÇÕES DO CULTO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldMetallic,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it.nome,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = NavyLight, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Data", color = GoldLight, fontSize = 12.sp)
                                Text(it.data, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Horário", color = GoldLight, fontSize = 12.sp)
                                Text(it.horario, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Músicas", color = GoldLight, fontSize = 12.sp)
                                Text("${louvores.size}", color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        it.cloudId?.let { key ->
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = NavyLight, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Chave da Nuvem",
                                        tint = GoldMetallic,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("Código Sincronizado", color = OffWhite, fontSize = 10.sp)
                                        Text(
                                            text = key,
                                            color = GoldLight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Refresh/Pull Button
                                    var isSyncingDown by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {
                                            isSyncingDown = true
                                            coroutineScope.launch {
                                                val res = downloadCultoOnline(key)
                                                isSyncingDown = false
                                                if (res != null) {
                                                    onSyncCultoWithCloud(it.id, res.first.copy(cloudId = key), res.second)
                                                    Toast.makeText(context, "Atualizado da nuvem com sucesso! ✨", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Erro ao baixar atualizações da nuvem.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = !isSyncingDown,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        if (isSyncingDown) {
                                            CircularProgressIndicator(color = GoldMetallic, modifier = Modifier.size(16.dp))
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = "Baixar atualizações", tint = GoldLight)
                                        }
                                    }

                                    // Upload/Push Button
                                    var isSyncingUp by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {
                                            isSyncingUp = true
                                            coroutineScope.launch {
                                                val resKey = uploadCultoOnline(it, louvores, key)
                                                isSyncingUp = false
                                                if (resKey != null) {
                                                    Toast.makeText(context, "Seus ajustes foram enviados para a nuvem! 📤", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Erro ao enviar atualizações.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        enabled = !isSyncingUp,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        if (isSyncingUp) {
                                            CircularProgressIndicator(color = GoldMetallic, modifier = Modifier.size(16.dp))
                                        } else {
                                            Icon(Icons.Default.Share, contentDescription = "Enviar alterações", tint = GoldMetallic)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Louvores Selecionados",
                style = MaterialTheme.typography.titleMedium,
                color = GoldMetallic,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (louvores.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Sem músicas",
                            tint = GoldLight.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhum louvor cadastrado.",
                            color = PureWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adicione louvores para que o sonoplasta possa organizar os links.",
                            color = OffWhite.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("louvor_list_column")
                ) {
                    items(louvores) { louvor ->
                        LouvorItemCard(
                            louvor = louvor,
                            onClicked = { onLouvorSelected(louvor.id) },
                            onOpenYoutube = { onOpenYoutubeUrl(louvor.linkYoutube) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LouvorItemCard(
    louvor: Louvor,
    onClicked: () -> Unit,
    onOpenYoutube: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("louvor_card_${louvor.id}")
            .clickable { onClicked() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, NavyLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge (Recebido, Baixado, Ensaiado, Pronto)
                    Box(
                        modifier = Modifier
                            .background(getStatusColor(louvor.status), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = louvor.status.uppercase(),
                            color = NavyDark,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (louvor.tom.isNotBlank()) {
                        Text(
                            text = "Tom: ${louvor.tom}",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = louvor.nomeMusica,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (louvor.cantor.isNotBlank()) {
                    Text(
                        text = "Cantor(a): ${louvor.cantor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OffWhite.copy(alpha = 0.8f)
                    )
                }
            }

            if (louvor.linkYoutube.isNotBlank()) {
                Button(
                    onClick = onOpenYoutube,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic),
                    modifier = Modifier
                        .testTag("open_video_button_${louvor.id}")
                        .minimumInteractiveComponentSize(), // Ensure is 48dp+
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Abrir vídeo",
                            tint = NavyDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Iniciar", color = NavyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// 4. ADICIONAR LOUVOR (Add Louvor Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLouvorScreen(
    cultoId: Int,
    onNavigateBack: () -> Unit,
    onSaveLouvor: (Int, String, String, String, String, String, String) -> Unit
) {
    var nomeMusica by remember { mutableStateOf("") }
    var linkYoutube by remember { mutableStateOf("") }
    var cantor by remember { mutableStateOf("") }
    var tom by remember { mutableStateOf("") }
    var observacoes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Recebido") } // Default status

    val statuses = listOf("Recebido", "Baixado", "Ensaiado", "Pronto")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar Louvor", fontWeight = FontWeight.Bold, color = PureWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = PureWhite
                )
            )
        },
        containerColor = NavyDark,
        modifier = Modifier.testTag("add_louvor_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = nomeMusica,
                onValueChange = { nomeMusica = it },
                label = { Text("Nome da Música", color = GoldLight) },
                placeholder = { Text("ex. Maranata, Grandioso És Tu", color = GrayStatus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("louvor_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldMetallic,
                    unfocusedBorderColor = NavyLight,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = NavyMedium,
                    unfocusedContainerColor = NavyMedium
                ),
                shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
                value = linkYoutube,
                onValueChange = { linkYoutube = it },
                label = { Text("Link do YouTube / Navegador", color = GoldLight) },
                placeholder = { Text("ex. https://youtube.com/...", color = GrayStatus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("louvor_youtube_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldMetallic,
                    unfocusedBorderColor = NavyLight,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = NavyMedium,
                    unfocusedContainerColor = NavyMedium
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cantor,
                    onValueChange = { cantor = it },
                    label = { Text("Quem vai cantar", color = GoldLight) },
                    placeholder = { Text("Ministro", color = GrayStatus) },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("louvor_cantor_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldMetallic,
                        unfocusedBorderColor = NavyLight,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = NavyMedium,
                        unfocusedContainerColor = NavyMedium
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = tom,
                    onValueChange = { tom = it },
                    label = { Text("Tom", color = GoldLight) },
                    placeholder = { Text("G#m", color = GrayStatus) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("louvor_tom_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldMetallic,
                        unfocusedBorderColor = NavyLight,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = NavyMedium,
                        unfocusedContainerColor = NavyMedium
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            OutlinedTextField(
                value = observacoes,
                onValueChange = { observacoes = it },
                label = { Text("Observações adicionais", color = GoldLight) },
                placeholder = { Text("Ex: repete refrão 2x no final, etc.", color = GrayStatus) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .testTag("louvor_obs_input"),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldMetallic,
                    unfocusedBorderColor = NavyLight,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = NavyMedium,
                    unfocusedContainerColor = NavyMedium
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Custom Sleek Segmented Selection for Status
            Text("Status Inicial do Louvor", color = GoldLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("louvor_status_selector"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                statuses.forEach { possibleStatus ->
                    val isSelected = status == possibleStatus
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isSelected) getStatusColor(possibleStatus) else NavyMedium,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { status = possibleStatus }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = possibleStatus,
                            color = if (isSelected) NavyDark else PureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (nomeMusica.isNotBlank()) {
                        onSaveLouvor(cultoId, nomeMusica, linkYoutube, cantor, tom, observacoes, status)
                    }
                },
                enabled = nomeMusica.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    disabledContainerColor = GrayStatus.copy(alpha = 0.5f),
                    contentColor = NavyDark,
                    disabledContentColor = PureWhite.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_louvor_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Adicionar Louvor", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// 5. DETALHES DO LOUVOR (Louvor Details Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LouvorDetailsScreen(
    louvor: Louvor?,
    onNavigateBack: () -> Unit,
    onStatusChanged: (Louvor, String) -> Unit,
    onDeleteLouvor: (Louvor) -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }

    // State placeholders for Edit view
    var editMusica by remember { mutableStateOf("") }
    var editLink by remember { mutableStateOf("") }
    var editCantor by remember { mutableStateOf("") }
    var editTom by remember { mutableStateOf("") }
    var editObs by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Sync form values on start or edit click
    val startEditing = {
        louvor?.let {
            editMusica = it.nomeMusica
            editLink = it.linkYoutube
            editCantor = it.cantor
            editTom = it.tom
            editObs = it.observacoes
            isEditing = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Louvor" else "Detalhes do Louvor", fontWeight = FontWeight.Bold, color = PureWhite) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = PureWhite)
                    }
                },
                actions = {
                    if (!isEditing && louvor != null) {
                        IconButton(onClick = { startEditing() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GoldLight)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = PureWhite
                )
            )
        },
        containerColor = NavyDark,
        modifier = Modifier.testTag("louvor_details_screen")
    ) { innerPadding ->
        if (louvor == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Buscando louvor...", color = PureWhite)
            }
        } else {
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    containerColor = NavyMedium,
                    title = { Text("Excluir Louvor?", color = PureWhite) },
                    text = { Text("Tem certeza de que deseja apagar \"${louvor.nomeMusica}\"?", color = OffWhite) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteLouvor(louvor)
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Excluir", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancelar", color = PureWhite)
                        }
                    }
                )
            }

            if (isEditing) {
                // EDIT MODE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editMusica,
                        onValueChange = { editMusica = it },
                        label = { Text("Nome da Música", color = GoldLight) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldMetallic,
                            unfocusedBorderColor = NavyLight,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = NavyMedium,
                            unfocusedContainerColor = NavyMedium
                        )
                    )

                    OutlinedTextField(
                        value = editLink,
                        onValueChange = { editLink = it },
                        label = { Text("Link do YouTube / Navegador", color = GoldLight) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldMetallic,
                            unfocusedBorderColor = NavyLight,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = NavyMedium,
                            unfocusedContainerColor = NavyMedium
                        )
                    )

                    OutlinedTextField(
                        value = editCantor,
                        onValueChange = { editCantor = it },
                        label = { Text("Ministro de louvor", color = GoldLight) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldMetallic,
                            unfocusedBorderColor = NavyLight,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = NavyMedium,
                            unfocusedContainerColor = NavyMedium
                        )
                    )

                    OutlinedTextField(
                        value = editTom,
                        onValueChange = { editTom = it },
                        label = { Text("Tom", color = GoldLight) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldMetallic,
                            unfocusedBorderColor = NavyLight,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = NavyMedium,
                            unfocusedContainerColor = NavyMedium
                        )
                    )

                    OutlinedTextField(
                        value = editObs,
                        onValueChange = { editObs = it },
                        label = { Text("Observações", color = GoldLight) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldMetallic,
                            unfocusedBorderColor = NavyLight,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = NavyMedium,
                            unfocusedContainerColor = NavyMedium
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { isEditing = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyMedium),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", color = PureWhite)
                        }

                        Button(
                            onClick = {
                                if (editMusica.isNotBlank()) {
                                    val updated = louvor.copy(
                                        nomeMusica = editMusica,
                                        linkYoutube = editLink,
                                        cantor = editCantor,
                                        tom = editTom,
                                        observacoes = editObs
                                    )
                                    onStatusChanged(updated, updated.status)
                                    isEditing = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Salvar", color = NavyDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // READ-ONLY / SONOPLASTA DETAIL SCREEN
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NavyMedium),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, NavyLight)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(getStatusColor(louvor.status), shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = louvor.status.uppercase(),
                                        color = NavyDark,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                }

                                if (louvor.tom.isNotBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = NavyLight),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "TOM: ${louvor.tom}",
                                            color = GoldLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = louvor.nomeMusica,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )

                            if (louvor.cantor.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ministrado por: ${louvor.cantor}",
                                    fontSize = 14.sp,
                                    color = OffWhite.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // YouTube launch button
                    if (louvor.linkYoutube.isNotBlank()) {
                        Button(
                            onClick = { openYoutubeLink(context, louvor.linkYoutube) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("details_open_video_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = NavyDark,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Abrir vídeo no YouTube", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NavyMedium),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GoldMetallic)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Nenhum link do YouTube informado.", color = OffWhite, fontSize = 14.sp)
                            }
                        }
                    }

                    // Update Status Quick Actions for rehearsals
                    Text(
                        text = "Alterar Status do Ensaio",
                        fontWeight = FontWeight.Bold,
                        color = GoldMetallic,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusList = listOf("Recebido", "Baixado", "Ensaiado", "Pronto")
                        statusList.forEach { stat ->
                            val active = louvor.status == stat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_select_$stat")
                                    .background(
                                        color = if (active) getStatusColor(stat) else NavyMedium,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onStatusChanged(louvor, stat) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) NavyDark else PureWhite
                                )
                            }
                        }
                    }

                    // Observations Card
                    if (louvor.observacoes.isNotBlank()) {
                        Text(
                            text = "Observações",
                            fontWeight = FontWeight.Bold,
                            color = GoldMetallic,
                            fontSize = 14.sp
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            colors = CardDefaults.cardColors(containerColor = NavyMedium),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NavyLight)
                        ) {
                            Text(
                                text = louvor.observacoes,
                                color = OffWhite,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Delete action safely placed at bottom
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .minimumInteractiveComponentSize()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.Red.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Excluir Louvor", color = Color.Red.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
