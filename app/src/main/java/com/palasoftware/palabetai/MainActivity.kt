package com.palasoftware.palabetai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val bg = Color(0xFF091218)
private val surface = Color(0xFF17262D)
private val accent = Color(0xFF34D399)
private val muted = Color(0xFFB9C8CD)
private data class Match(val id:String, val home:String, val away:String, val utc:String, val status:String)
private data class Result(val matches:List<Match>,val source:String,val fetched:String)
private val leagues = listOf("BL1" to "Bundesliga", "BL2" to "2. Bundesliga", "PL" to "Premier League*", "PD" to "La Liga*", "SA" to "Serie A*", "FL1" to "Ligue 1*")
private fun local(utc:String):String = try { DateTimeFormatter.ofPattern("dd.MM.yyyy • HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(utc)) } catch (_:Exception) { utc }
private suspend fun fetch(base:String,league:String):Result = withContext(Dispatchers.IO) {
    require(base.startsWith("https://")) { "HTTPS bağlantısı gerekli" }
    val conn = URL("${base.trimEnd('/')}/fixtures/$league").openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    try {
        if(conn.responseCode != 200) throw IllegalStateException("Sunucu HTTP ${conn.responseCode} döndürdü")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        if(json.optString("mode") != "provider") throw IllegalStateException("Doğrulanmış sağlayıcı yanıtı alınamadı")
        val arr = json.getJSONArray("matches")
        val matches = (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val home = m.optString("home")
            val away = m.optString("away")
            if(home.isBlank() || away.isBlank()) null else Match(m.optString("id"),home,away,m.optString("utc_date"),m.optString("status"))
        }
        Result(matches,json.optString("source"),json.optString("fetched_at"))
    } finally { conn.disconnect() }
}

private data class Research(val status:String, val details:String)
private suspend fun fetchResearch(base:String, league:String, id:String):Research = withContext(Dispatchers.IO) {
    val conn = URL("${base.trimEnd('/')}/research/$league/$id").openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 20000
    try {
        if (conn.responseCode != 200) throw IllegalStateException("Analiz HTTP ${conn.responseCode}")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val analysis = json.optJSONObject("analysis")
        if (analysis == null) Research("insufficient_data", "Yeterli geçmiş maç verisi bulunamadı. Tahmin üretilmedi.")
        else {
            val p = analysis.getJSONObject("probabilities")
            fun pct(k:String) = String.format(java.util.Locale.US,"%.1f%%",p.getDouble(k)*100)
            val n = analysis.getJSONObject("sample")
            Research("estimated", "1: ${pct("home")}   X: ${pct("draw")}   2: ${pct("away")}\nKG Var: ${pct("btts")}\n2.5 Üst: ${pct("over25")}   Alt: ${pct("under25")}\nSon maç örnekleri: ${n.getInt("home")} / ${n.getInt("away")}\nYöntem: Geçmiş gol ortalamaları + Poisson. xG değildir; garanti içermez.")
        }
    } finally { conn.disconnect() }
}

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { Screen() } }
}

@Composable private fun Screen() {
    val base = BuildConfig.API_BASE_URL
    var league by remember { mutableStateOf("BL1") }
    var search by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Match?>(null) }
    var loading by remember { mutableStateOf(false) }
    var research by remember { mutableStateOf<Research?>(null) }
    var researchError by remember { mutableStateOf<String?>(null) }
    var researchLoading by remember { mutableStateOf(false) }
    LaunchedEffect(selected?.id, league) {
        research = null; researchError = null
        val m = selected ?: return@LaunchedEffect
        if (base.isBlank() || league !in listOf("BL1","BL2")) return@LaunchedEffect
        researchLoading = true
        try { research = fetchResearch(base,league,m.id) }
        catch (e:Exception) { researchError = e.message ?: "Analiz alınamadı" }
        finally { researchLoading = false }
    }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(league,refresh) {
        selected = null
        response = null
        error = null
        if(base.isBlank()) return@LaunchedEffect
        loading = true
        try { response = fetch(base,league) } catch(e:Exception) { error = e.message ?: "Bağlantı kurulamadı" } finally { loading = false }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=accent,background=bg,surface=surface,onBackground=Color.White,onSurface=Color.White)) {
        Column(Modifier.fillMaxSize().background(bg).padding(horizontal=18.dp, vertical=14.dp)) {
            Text("PALA BET AI",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black,color=accent)
            Text("FOOTBALL INTELLIGENCE  •  V${BuildConfig.VERSION_NAME}",color=muted,style=MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(22.dp))
            if(selected != null) {
                val match = selected!!
                TextButton(onClick={selected=null}) { Text("← Maç listesine dön") }
                Text("${match.home} – ${match.away}",style=MaterialTheme.typography.headlineSmall,color=Color.White,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(local(match.utc),color=muted)
                Text("Durum: ${match.status}",color=muted)
                Spacer(Modifier.height(20.dp))
                Card(colors=CardDefaults.cardColors(containerColor=surface)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("GERÇEK FİKSTÜR",color=accent,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("ARAŞTIRMA TAHMİNİ • V0.6",color=accent,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if(researchLoading) CircularProgressIndicator(Modifier.size(24.dp))
                        if(research != null) Text(research!!.details,color=Color.White)
                        else if(researchError != null) Text("Analiz alınamadı: $researchError",color=muted)
                        else Text("Bu lig veya maç için doğrulanabilir geçmiş veri yok; tahmin gösterilmiyor.",color=Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Kaynak: ${response?.source ?: "Bilinmiyor"}",color=muted,style=MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text("Maç merkezi",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Color.White)
                Text("Gerçek fikstürler • Kaynak bilgisiyle",color=muted,style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    leagues.forEach { (code,name) -> FilterChip(selected=league==code,onClick={league=code},label={Text(name)}) }
                }
                Spacer(Modifier.height(8.dp))
                Text("* Bu ligler için sunucuda isteğe bağlı API anahtarı gerekir.",color=muted,style=MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value=search,onValueChange={search=it},label={Text("Takım ara")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={refresh++},enabled=!loading && base.isNotBlank()) { Text("Fikstürü yenile") }
                    if(loading) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                }
                Spacer(Modifier.height(12.dp))
                if(base.isBlank()) {
                    Notice("Kurulum bekleniyor", "Bu APK henüz yayımlanmış bir backend adresi içermiyor. DEPLOY_V05.md dosyasındaki adımlarla sunucuyu kurup API_BASE_URL değerini tanımlayın.")
                }
                if(error != null) Notice("Veri alınamadı", "${error}. Bağlantıyı ve sunucu durumunu kontrol edip yeniden deneyin.")
                val data = response
                if(data != null) {
                    Text("Kaynak: ${data.source}",color=accent,style=MaterialTheme.typography.labelMedium)
                    Text("Son veri alımı: ${local(data.fetched)}",color=muted,style=MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(10.dp))
                    val upcoming = data.matches.filter { it.status != "FINISHED" && (it.home.contains(search,true) || it.away.contains(search,true)) }
                    if(upcoming.isEmpty()) Notice("Maç bulunamadı", "Bu ligde veya arama sonucunda yaklaşan fikstür bulunmuyor. Veri kaynağı eksik veya gecikmeli olabilir.")
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        items(upcoming,key={it.id}) { m ->
                            Card(modifier=Modifier.fillMaxWidth().clickable{selected=m},colors=CardDefaults.cardColors(containerColor=surface),shape=RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(local(m.utc),color=accent,style=MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Text("${m.home} – ${m.away}",color=Color.White,fontWeight=FontWeight.Bold)
                                    Text("Maç ayrıntısı →",color=muted,style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Notice(title:String,message:String) {
    Card(colors=CardDefaults.cardColors(containerColor=surface),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title,color=accent,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(message,color=Color.White,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
