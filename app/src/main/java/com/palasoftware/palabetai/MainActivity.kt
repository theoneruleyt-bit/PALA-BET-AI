package com.palasoftware.palabetai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bg = Color(0xFF07111E)
private val surface = Color(0xFF142738)
private val surface2 = Color(0xFF20394D)
private val mint = Color(0xFF34D6A1)
private val purple = Color(0xFFB375F5)
private val muted = Color(0xFFA9BBC7)
private val white = Color(0xFFF5F8FC)
private val leagues = listOf("TR1" to "🇹🇷 Süper Lig 2026/27", "BL1" to "Bundesliga", "BL2" to "2. Bundesliga", "BL3" to "3. Liga", "UCL" to "Şampiyonlar Ligi", "UEL" to "Avrupa Ligi", "DFB" to "Almanya Kupası", "PLFREE" to "Premier League (topluluk)", "LALIGA" to "La Liga (topluluk)", "WBL" to "Kadınlar Bundesliga", "NLA" to "Uluslar Ligi A", "RLN" to "Regionalliga Nord", "RLNO" to "Regionalliga Nordost", "PL" to "Premier League*", "PD" to "La Liga*", "SA" to "Serie A*", "FL1" to "Ligue 1*")
private data class Match(val id:String,val home:String,val away:String,val utc:String,val status:String)
private data class Fixtures(val matches:List<Match>,val source:String,val fetched:String)
private data class League(val code:String,val name:String)
private data class ExtendedMarkets(val goalLines:List<Pair<String,Double>>,val scores:List<Pair<String,Double>>,val teamGoals:List<Pair<String,Double>>,val eventMarkets:JSONObject?)
private suspend fun loadTurkishLeagues(base:String):List<League> = withContext(Dispatchers.IO) {
    val j=request("${base.trimEnd('/')}/leagues")
    val arr=j.optJSONArray("turkish_discovery") ?: return@withContext emptyList()
    (0 until arr.length()).mapNotNull { i ->
        val x=arr.optJSONObject(i) ?: return@mapNotNull null
        val code=x.optString("code");val name=x.optString("name")
        if(code.startsWith("TR:") && name.isNotBlank()) League(code,name) else null
    }.distinctBy { it.code }
}
private suspend fun extendedMarkets(base:String,league:String,id:String):ExtendedMarkets? = withContext(Dispatchers.IO) {
    val response=request("${base.trimEnd('/')}/research/$league/$id")
    val j=response.optJSONObject("markets") ?: return@withContext null
    val goal=j.optJSONObject("goal_lines")
    val lines=goal?.keys()?.asSequence()?.toList()?.sortedBy { it.toDoubleOrNull() ?: 99.0 }?.mapNotNull { key ->
        val over=goal.optJSONObject(key)?.optDouble("over") ?: return@mapNotNull null
        "${key} Üst" to over
    } ?: emptyList()
    val score=j.optJSONArray("correct_score_top5")
    val scores=if(score==null) emptyList() else (0 until score.length()).mapNotNull { i ->
        val item=score.optJSONObject(i) ?: return@mapNotNull null
        item.optString("score") to item.optDouble("probability")
    }
    val tg=j.optJSONObject("team_goals")
    val team=if(tg==null) emptyList() else tg.keys().asSequence().toList().sorted().map { it.replace('_',' ') to tg.optDouble(it) }
    ExtendedMarkets(lines,scores,team,response.optJSONObject("event_markets"))
}
private data class Analysis(val home:Double,val draw:Double,val away:Double,val btts:Double,val over:Double,val under:Double,val homeSample:Int,val awaySample:Int,val homeGoals:Double?,val awayGoals:Double?,val method:String)
private fun local(value:String):String = try { DateTimeFormatter.ofPattern("dd.MM.yyyy • HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) } catch (_:Exception) { value }
private fun percentage(n:Double):String = String.format(Locale.US,"%.1f%%",n*100)
private fun request(url:String):JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 25000
    try {
        if(conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    } finally { conn.disconnect() }
}
private suspend fun fixtures(base:String,league:String):Fixtures = withContext(Dispatchers.IO) {
    require(base.startsWith("https://")) { "HTTPS adresi gerekli" }
    val j = request("${base.trimEnd('/')}/fixtures/$league")
    if(j.optString("mode") != "provider") throw IllegalStateException("Sağlayıcı verisi doğrulanamadı")
    val arr = j.getJSONArray("matches")
    val matches = (0 until arr.length()).mapNotNull { i ->
        val m=arr.getJSONObject(i)
        val h=m.optString("home");val a=m.optString("away")
        if(h.isBlank() || a.isBlank()) null else Match(m.optString("id"),h,a,m.optString("utc_date"),m.optString("status"))
    }
    Fixtures(matches,j.optString("source"),j.optString("fetched_at"))
}
private suspend fun analysis(base:String,league:String,id:String):Analysis? = withContext(Dispatchers.IO) {
    val j=request("${base.trimEnd('/')}/research/$league/$id").optJSONObject("analysis") ?: return@withContext null
    val p=j.getJSONObject("probabilities");val n=j.getJSONObject("sample");val goals=j.optJSONObject("goal_rates")
    Analysis(p.getDouble("home"),p.getDouble("draw"),p.getDouble("away"),p.getDouble("btts"),p.getDouble("over25"),p.getDouble("under25"),n.getInt("home"),n.getInt("away"),goals?.optDouble("home")?.takeUnless { it.isNaN() },goals?.optDouble("away")?.takeUnless { it.isNaN() },j.optString("method","Poisson + geçmiş gol ortalamaları"))
}
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(7,19,29);window.navigationBarColor=android.graphics.Color.rgb(7,19,29);setContent { App() } }
}
@Composable private fun App() {
    val base=BuildConfig.API_BASE_URL
    var league by remember { mutableStateOf("TR1") }
    var turkish by remember { mutableStateOf<List<League>>(emptyList()) }
    var expanded by remember { mutableStateOf<ExtendedMarkets?>(null) }
    // Historical community Turkish leagues are deliberately hidden; only current fixtures are selectable.
    var search by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Fixtures?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Match?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var estimate by remember { mutableStateOf<Analysis?>(null) }
    var analysisLoading by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(league,refresh) {
        selected=null;result=null;error=null
        if(base.isBlank()) return@LaunchedEffect
        loading=true
        try { result=fixtures(base,league) } catch(e:Exception) { error=e.message ?: "Bağlantı kurulamadı" } finally { loading=false }
    }
    LaunchedEffect(selected?.id,league) {
        estimate=null;expanded=null;analysisError=null
        val m=selected ?: return@LaunchedEffect
        if(base.isBlank() || (league !in listOf("BL1","BL2","BL3","UCL","UEL","DFB","PLFREE","LALIGA","WBL","NLA","RLN","RLNO","TR1") && !league.startsWith("TR:"))) return@LaunchedEffect
        analysisLoading=true
        try { estimate=analysis(base,league,m.id); if(estimate!=null) expanded=extendedMarkets(base,league,m.id) } catch(e:Exception) { analysisError=e.message ?: "Analiz alınamadı" } finally { analysisLoading=false }
    }
    MaterialTheme(colorScheme=darkColorScheme(primary=mint,background=bg,surface=surface,onBackground=white,onSurface=white)) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=12.dp)) {
                Text("PALA BET AI",color=mint,fontSize=29.sp,fontWeight=FontWeight.Black)
                Text("MATCH INTELLIGENCE   •   V${BuildConfig.VERSION_NAME}",color=muted,fontSize=12.sp,letterSpacing=1.sp)
            }
            if(selected == null) {
                Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                    Text("Maç merkezi",fontSize=25.sp,color=white,fontWeight=FontWeight.Bold)
                    Text("Güncel fikstür • Veri temelli olasılıklar",color=muted,fontSize=13.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("LİG SEÇİMİ",color=mint,fontWeight=FontWeight.Bold,fontSize=12.sp,letterSpacing=1.sp)
                    Spacer(Modifier.height(8.dp))
                    var leaguePicker by remember { mutableStateOf(false) }
                    OutlinedButton(onClick={leaguePicker=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) {
                        Text(leagues.firstOrNull { it.first==league }?.second ?: league,modifier=Modifier.weight(1f),color=white)
                        Text("⌄",color=mint,fontSize=20.sp)
                    }
                    if(leaguePicker) AlertDialog(onDismissRequest={leaguePicker=false},title={Text("Lig seç",color=white)},text={
                        Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState())) {
                            leagues.forEach { (code,name) ->
                                Text(name,modifier=Modifier.fillMaxWidth().clickable { league=code;leaguePicker=false }.padding(13.dp),color=if(league==code) mint else white)
                                HorizontalDivider(color=surface2)
                            }
                        }
                    },confirmButton={TextButton(onClick={leaguePicker=false}){Text("Kapat")}},containerColor=surface)
                    Text("Türkiye: 2026/27 güncel sezon • Kaynak durumunu maç listesinde kontrol et",color=muted,fontSize=11.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value=search,onValueChange={search=it},label={Text("Takım ara")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(onClick={refresh++},enabled=!loading && base.isNotBlank()) { Text("Fikstürü yenile") }
                    if(loading) { Spacer(Modifier.height(12.dp));CircularProgressIndicator() }
                    if(base.isBlank()) MessageCard("Kurulum bekleniyor","APK'ya HTTPS backend adresi eklenmemiş.")
                    if(error != null) MessageCard("Bağlantı hatası",error ?: "")
                    result?.let { data ->
                        Spacer(Modifier.height(12.dp))
                        Text("● Kaynak: ${data.source}",color=mint,fontSize=12.sp)
                        Text("Son veri alımı: ${local(data.fetched)}",color=muted,fontSize=11.sp)
                        Spacer(Modifier.height(12.dp))
                        val matches=data.matches.filter { it.status != "FINISHED" && (it.home.contains(search,true) || it.away.contains(search,true)) }
                        if(matches.isEmpty()) MessageCard("Yaklaşan maç yok","Seçilen ligde veya arama filtresinde yaklaşan karşılaşma bulunamadı. Başka lig veya takım deneyin.") else Text("${matches.size} yaklaşan karşılaşma",color=white,fontWeight=FontWeight.Bold,fontSize=15.sp)
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                            items(matches,key={it.id}) { m ->
                                Card(Modifier.fillMaxWidth().clickable { selected=m },shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=surface)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(local(m.utc),color=mint,fontSize=12.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("${m.home}  –  ${m.away}",color=white,fontWeight=FontWeight.Bold,fontSize=17.sp)
                                        Text("ANALİZİ AÇ   →",color=mint,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val match=selected!!
                var tab by remember(match.id) { mutableStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    TextButton(onClick={selected=null},modifier=Modifier.padding(start=10.dp)) { Text("← Maç listesine dön",color=mint) }
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        item { MatchHero(match,league) }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("Özet","İstatistik","Goller","Korner","Kartlar","Penaltı","Kadrolar","H2H").forEachIndexed { i,label ->
                                    FilterChip(selected=tab==i,onClick={tab=i},label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=surface2,selectedLabelColor=mint))
                                }
                            }
                        }
                        if(analysisLoading) item { Panel { Row(verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Text("Geçmiş maçlar analiz ediliyor...",color=white) } } }
                        else if(analysisError != null) item { MessageCard("Analiz alınamadı",analysisError ?: "") }
                        else if(estimate == null) item { MessageCard("Yeterli veri yok","Bu maç için doğrulanabilir geçmiş sonuçlar yeterli değil. Tahmin yüzdeleri gösterilmiyor.") }
                        else {
                            val a=estimate!!
                            when(tab) {
                                0 -> {
                                    item { Panel { Text("MAÇ SONUCU • 1X2",color=white,fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ProbabilityTile("Ev sahibi",a.home,mint,Modifier.weight(1f));ProbabilityTile("Beraberlik",a.draw,muted,Modifier.weight(1f));ProbabilityTile("Deplasman",a.away,purple,Modifier.weight(1f)) } } }
                                    item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Panel(Modifier.weight(1f)) { Text("Çifte şans",color=white,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));BarLine("1X",a.home+a.draw,mint);BarLine("X2",a.draw+a.away,muted);BarLine("12",a.home+a.away,purple) };Panel(Modifier.weight(1f)) { Text("Toplam gol (2.5)",color=white,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));BarLine("Üst",a.over,mint);BarLine("Alt",a.under,muted) } } }
                                    item { Panel { Text("Karşılıklı gol (KG)",color=white,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { ProbabilityTile("Var",a.btts,mint,Modifier.weight(1f));ProbabilityTile("Yok",1-a.btts,muted,Modifier.weight(1f)) } } }
                                    item { Panel { Text("Analiz özeti",color=mint,fontWeight=FontWeight.Bold,fontSize=18.sp);Spacer(Modifier.height(8.dp));Text("Gerçek geçmiş maç sonuçlarından hesaplanan araştırma olasılıkları. xG veya doğrulanmış Elo modeli değildir.",color=white);Spacer(Modifier.height(10.dp));Text("Örnek sayısı: ${match.home}: ${a.homeSample} • ${match.away}: ${a.awaySample}",color=muted,fontSize=13.sp);Text("Model: ${a.method}",color=muted,fontSize=12.sp);Text("Kaynak: ${result?.source ?: "Bilinmiyor"}",color=muted,fontSize=12.sp);Text("Model sonuçları araştırma amaçlıdır. Kesin sonuç veya kazanç garantisi yoktur.",color=muted,fontSize=12.sp) } }
                                }
                                2 -> {
                                    item { Panel { Text("Gelişmiş gol pazarları • Üst olasılıkları",color=mint,fontWeight=FontWeight.Bold,fontSize=18.sp);Spacer(Modifier.height(8.dp));if(expanded==null) Text("Pazar verisi yüklenemedi",color=muted) else expanded!!.goalLines.forEach { (label,p) -> BarLine(label,p,mint) } } }
                                    item { Panel { Text("Takım gol olasılıkları",color=white,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));expanded?.teamGoals?.forEach { (label,p) -> BarLine(label,p,purple) } } }
                                    item { Panel { Text("En olası skorlar",color=white,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));expanded?.scores?.forEach { (label,p) -> BarLine(label,p,mint) } } }
                                }
                                3,4,5 -> item {
                                    val event=expanded?.eventMarkets
                                    val names=if(tab==3) listOf("corners" to "Toplam korner") else if(tab==4) listOf("yellow_cards" to "Sarı kart", "red_cards" to "Kırmızı kart") else listOf("penalties" to "Penaltı")
                                    Panel {
                                        Text(if(tab==3) "KORNER ANALİZİ" else if(tab==4) "KART ANALİZİ" else "PENALTI ANALİZİ",color=mint,fontWeight=FontWeight.Bold,fontSize=18.sp)
                                        Spacer(Modifier.height(12.dp))
                                        names.forEach { (key,label) ->
                                            Text(label,color=white,fontWeight=FontWeight.SemiBold)
                                            val market=event?.optJSONObject(key)
                                            if(market?.optString("status")=="estimated") {
                                                Text("Geçmiş ortalama: ${market.optDouble("average")} • Örnek: ${market.optInt("sample")}",color=muted,fontSize=12.sp)
                                                val over=market.optJSONObject("over")
                                                over?.keys()?.asSequence()?.toList()?.sortedBy { it.toDoubleOrNull() ?: 99.0 }?.forEach { line ->
                                                    BarLine("$line Üst",over.optDouble(line),mint)
                                                }
                                            } else {
                                                Text("Yeterli doğrulanmış olay verisi yok. Tahmin üretilmedi.",color=muted,fontSize=13.sp)
                                            }
                                            Spacer(Modifier.height(12.dp))
                                        }
                                        Text(event?.optString("source") ?: "Veri kaynağı bulunamadı",color=muted,fontSize=11.sp)
                                        Text("Tahminler geçmiş olay ortalamalarından elde edilen deneysel tahminlerdir; garanti değildir.",color=muted,fontSize=11.sp)
                                    }
                                }
                                1 -> item { Panel { Text("Beklenen gol ortalaması (model)",color=white,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text("${match.home}: ${a.homeGoals?.let { String.format(Locale.US,"%.2f",it) } ?: "Veri yok"}",color=mint);Text("${match.away}: ${a.awayGoals?.let { String.format(Locale.US,"%.2f",it) } ?: "Veri yok"}",color=purple);Spacer(Modifier.height(8.dp));Text("Bu değerler geçmiş skorlarla hesaplanan Poisson parametreleridir, xG değildir.",color=muted,fontSize=12.sp) } }
                                else -> item { MessageCard(if(tab==6) "Kadro bilgisi mevcut değil" else "İkili rekabet verisi mevcut değil","Bu bölüm için doğrulanmış veri kaynağı henüz bağlı değil. Bilgi uydurulmuyor.") }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun MatchHero(m:Match,league:String) {
    Panel {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text(if(league=="TR1") "🇹🇷 TÜRKİYE SÜPER LİG 2026/27" else if(league=="BL1") "BUNDESLIGA" else if(league=="BL2") "2. BUNDESLIGA" else leagues.firstOrNull { it.first==league }?.second ?: league,color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
            Text(local(m.utc),color=muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Team(m.home,Modifier.weight(1f))
            Text("VS",color=muted,fontSize=21.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(horizontal=8.dp))
            Team(m.away,Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("${if(m.status=="FINISHED") "TAMAMLANDI" else "YAKLAŞAN MAÇ"}   •   Veri sağlayıcı fikstürü",color=muted,fontSize=12.sp)
    }
}
@Composable private fun Team(name:String,modifier:Modifier=Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(surface2).border(1.dp,mint.copy(alpha=.4f),CircleShape),contentAlignment=Alignment.Center) {
            Text(name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT),color=white,fontWeight=FontWeight.Black,fontSize=20.sp)
        }
        Spacer(Modifier.height(9.dp))
        Text(name,color=white,fontWeight=FontWeight.Bold,fontSize=15.sp,textAlign=TextAlign.Center,maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=surface)) { Column(Modifier.padding(16.dp),content=content) }
}
@Composable private fun ProbabilityTile(title:String,value:Double,color:Color,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(surface2).border(1.dp,color.copy(alpha=.5f),RoundedCornerShape(12.dp)).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(title,color=muted,fontSize=11.sp,maxLines=1)
        Text(percentage(value),color=color,fontSize=20.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(progress={value.toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=color,trackColor=bg)
    }
}
@Composable private fun BarLine(title:String,value:Double,color:Color) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.padding(vertical=6.dp)) {
        Text(title,color=white,fontSize=12.sp,modifier=Modifier.width(85.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
        LinearProgressIndicator(progress={value.toFloat().coerceIn(0f,1f)},modifier=Modifier.weight(1f).height(7.dp).clip(CircleShape),color=color,trackColor=surface2)
        Text(percentage(value),color=color,fontSize=11.sp,modifier=Modifier.width(43.dp),textAlign=TextAlign.End)
    }
}
@Composable private fun MessageCard(title:String,detail:String) { Panel { Text(title,color=mint,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Text(detail,color=white) } }
