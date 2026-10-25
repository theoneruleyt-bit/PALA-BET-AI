package com.palasoftware.palabetai

import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
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
private fun extendedMarkets(response:JSONObject):ExtendedMarkets? {
    val j=response.optJSONObject("markets") ?: return null
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
    return ExtendedMarkets(lines,scores,team,response.optJSONObject("event_markets"))
}
private data class Analysis(val home:Double,val draw:Double,val away:Double,val btts:Double,val over:Double,val under:Double,val homeSample:Int,val awaySample:Int,val homeGoals:Double?,val awayGoals:Double?,val method:String)
private fun local(value:String):String = try { DateTimeFormatter.ofPattern("dd.MM.yyyy • HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) } catch (_:Exception) { value }
private fun percentage(n:Double):String = String.format(Locale.US,"%.1f%%",n*100)
private val httpCache = mutableMapOf<String,Pair<Long,String>>()
private val httpCacheLock = Any()
private fun request(url:String, forceRefresh:Boolean=false):JSONObject {
    val now=System.currentTimeMillis()
    if(!forceRefresh) synchronized(httpCacheLock) {
        val cached=httpCache[url]
        val ttl=if(url.contains("/research/")) 600000L else 180000L
        if(cached!=null && now-cached.first<ttl) return JSONObject(cached.second)
    }
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 25000
    try {
        if(conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        val body=conn.inputStream.bufferedReader().use { it.readText() }
        synchronized(httpCacheLock) {
            if(httpCache.size>150) httpCache.clear()
            httpCache[url]=System.currentTimeMillis() to body
        }
        return JSONObject(body)
    } finally { conn.disconnect() }
}
private suspend fun fixtures(base:String,league:String,forceRefresh:Boolean=false):Fixtures = withContext(Dispatchers.IO) {
    require(base.startsWith("https://")) { "HTTPS adresi gerekli" }
    val j = request("${base.trimEnd('/')}/fixtures/$league",forceRefresh)
    if(j.optString("mode") != "provider") throw IllegalStateException("Sağlayıcı verisi doğrulanamadı")
    val arr = j.getJSONArray("matches")
    val matches = (0 until arr.length()).mapNotNull { i ->
        val m=arr.getJSONObject(i)
        val h=m.optString("home");val a=m.optString("away")
        if(h.isBlank() || a.isBlank()) null else Match(m.optString("id"),h,a,m.optString("utc_date"),m.optString("status"))
    }
    Fixtures(matches,j.optString("source"),j.optString("fetched_at"))
}
private fun analysis(response:JSONObject):Analysis? {
    val j=response.optJSONObject("analysis") ?: return null
    val p=j.getJSONObject("probabilities");val n=j.getJSONObject("sample");val goals=j.optJSONObject("goal_rates")
    return Analysis(p.getDouble("home"),p.getDouble("draw"),p.getDouble("away"),p.getDouble("btts"),p.getDouble("over25"),p.getDouble("under25"),n.getInt("home"),n.getInt("away"),goals?.optDouble("home")?.takeUnless { it.isNaN() },goals?.optDouble("away")?.takeUnless { it.isNaN() },j.optString("method","Poisson + geçmiş gol ortalamaları"))
}
private lateinit var appPrefs: android.content.SharedPreferences
private fun getPrefsHolder():android.content.SharedPreferences = appPrefs
class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);appPrefs=getSharedPreferences("pala_ai_score",MODE_PRIVATE);window.statusBarColor=android.graphics.Color.rgb(7,19,29);window.navigationBarColor=android.graphics.Color.rgb(7,19,29);setContent { App() } }
}
@Composable private fun App() {
    val base=BuildConfig.API_BASE_URL
    val prefs=remember { getPrefsHolder() }
    var league by remember { mutableStateOf(prefs.getString("default_league","TR1") ?: "TR1") }
    var turkish by remember { mutableStateOf<List<League>>(emptyList()) }
    var expanded by remember { mutableStateOf<ExtendedMarkets?>(null) }
    // Historical community Turkish leagues are deliberately hidden; only current fixtures are selectable.
    var search by remember { mutableStateOf("") }
    var section by remember { mutableIntStateOf(0) }
    var dayFilter by remember { mutableIntStateOf(prefs.getInt("default_day",0)) }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var showAllSeason by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var compactCards by remember { mutableStateOf(prefs.getBoolean("compact_cards", false)) }
    var showSource by remember { mutableStateOf(prefs.getBoolean("show_source", true)) }
    var lightTheme by remember { mutableStateOf(prefs.getBoolean("light_theme", false)) }
    var networkStatus by remember { mutableStateOf("Bekleniyor") }
    fun matchKey(m:Match)="$league:${m.id}"
    fun toggleFavorite(m:Match) {
        val key=matchKey(m)
        favorites=if(key in favorites) favorites-key else favorites+key
        prefs.edit().putStringSet("favorites",favorites).apply()
    }
    var result by remember { mutableStateOf<Fixtures?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Match?>(null) }
    BackHandler(enabled=selected != null) { selected=null }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var estimate by remember { mutableStateOf<Analysis?>(null) }
    var analysisLoading by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(league,refresh) {
        selected=null;result=null;error=null
        if(base.isBlank()) return@LaunchedEffect
        loading=true
        try { result=fixtures(base,league,refresh>0);networkStatus="Bağlandı" }
        catch(e:Exception) { error=e.message ?: "Bağlantı kurulamadı";networkStatus="Bağlantı sorunu" }
        finally { loading=false }
    }
    LaunchedEffect(selected?.id,league) {
        estimate=null;expanded=null;analysisError=null
        val m=selected ?: return@LaunchedEffect
        if(base.isBlank() || (league !in listOf("BL1","BL2","BL3","UCL","UEL","DFB","PLFREE","LALIGA","WBL","NLA","RLN","RLNO","TR1") && !league.startsWith("TR:"))) return@LaunchedEffect
        analysisLoading=true
        try {
            val response=withContext(Dispatchers.IO) { request("${base.trimEnd('/')}/research/$league/${m.id}") }
            estimate=analysis(response)
            expanded=extendedMarkets(response)
        } catch(e:Exception) { analysisError=e.message ?: "Analiz alınamadı" } finally { analysisLoading=false }
    }
    MaterialTheme(colorScheme=darkColorScheme(primary=mint,secondary=mint,tertiary=mint,background=bg,surface=surface,onBackground=white,onSurface=white)) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=5.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(surface2),contentAlignment=Alignment.Center) {
                    Icon(Icons.Filled.SportsSoccer,contentDescription=null,tint=mint,modifier=Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("PALA AI SCORE",color=mint,fontSize=21.sp,fontWeight=FontWeight.Black,maxLines=1)
                    Text("FUTBOL ANALİZİ  •  ${BuildConfig.VERSION_NAME}",color=muted,fontSize=10.sp,letterSpacing=.5.sp)
                }
                if(selected==null) IconButton(onClick={section=4}) { Icon(Icons.Filled.Settings,contentDescription="Ayarlar",tint=muted) }
            }
            if(selected == null && section==4) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("Ayarlar",fontSize=27.sp,color=white,fontWeight=FontWeight.Bold)
                    Text("Görünüm, lig ve veri tercihleri",color=muted)
                    Panel { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle,contentDescription=null,tint=if(networkStatus=="Bağlandı") mint else muted);Spacer(Modifier.width(8.dp));Column { Text("API durumu: $networkStatus",color=white);Text("Sunucu: Render • Yalnızca HTTPS",color=muted,fontSize=11.sp) } } }
                    Panel { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Kompakt maç kartları",color=white);Text("Ekranda daha fazla maç göster",color=muted,fontSize=12.sp) };Switch(checked=compactCards,onCheckedChange={compactCards=it;prefs.edit().putBoolean("compact_cards",it).apply()}) } }
                    Panel { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Veri kaynağını göster",color=white);Text("Analizlerin hangi kaynaktan geldiğini belirt",color=muted,fontSize=12.sp) };Switch(checked=showSource,onCheckedChange={showSource=it;prefs.edit().putBoolean("show_source",it).apply()}) } }
                    Panel {
                        Text("Varsayılan lig",color=mint,fontWeight=FontWeight.Bold)
                        var settingsLeaguePicker by remember { mutableStateOf(false) }
                        OutlinedButton(onClick={settingsLeaguePicker=true},modifier=Modifier.fillMaxWidth()) { Text(leagues.firstOrNull{it.first==league}?.second ?: league,color=white) }
                        if(settingsLeaguePicker) AlertDialog(onDismissRequest={settingsLeaguePicker=false},title={Text("Varsayılan lig")},text={
                            Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                                leagues.forEach { (code,name) -> Text(name,Modifier.fillMaxWidth().clickable { league=code;prefs.edit().putString("default_league",code).apply();settingsLeaguePicker=false }.padding(12.dp),color=white) }
                            }
                        },confirmButton={TextButton(onClick={settingsLeaguePicker=false}){Text("Kapat")}},containerColor=surface)
                        Text("Varsayılan tarih filtresi",color=white,fontSize=13.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("Bugün","Yarın","7 gün","Tümü").forEachIndexed { i,label ->
                                FilterChip(selected=dayFilter==i,onClick={dayFilter=i;prefs.edit().putInt("default_day",i).apply()},label={Text(label)},modifier=Modifier.padding(end=5.dp))
                            }
                        }
                    }
                    Panel { Text("Veri güncelliği",color=mint,fontWeight=FontWeight.Bold);Text("Fikstürler 3 dakika, analizler 10 dakika bellekte saklanır. Yenile düğmesi fikstürü tekrar indirir.",color=white,fontSize=13.sp) }
                    Panel { Text("Tahminlerin sınırları",color=mint,fontWeight=FontWeight.Bold);Text("Gol modeli geçmiş sonuçları kullanır; korner ve kart pazarları yalnızca olay verisi bulunduğunda hesaplanır. Penaltı ve eksik pazarlar için tahmin uydurulmaz. Kazanç garantisi yoktur.",color=white,fontSize=13.sp) }
                    OutlinedButton(onClick={synchronized(httpCacheLock){httpCache.clear()};refresh++},modifier=Modifier.fillMaxWidth()) { Text("Önbelleği temizle ve yeniden yükle",color=mint) }
                    Text("PALA AI SCORE • ${BuildConfig.VERSION_NAME}",color=muted,fontSize=12.sp)
                }
            } else if(selected == null) {
                Column(Modifier.weight(1f).padding(horizontal=18.dp)) {
                    Text(when(section){1->"Favori maçlar";2->"Ligler";3->"Analiz merkezi";else->"Maç merkezi"},fontSize=25.sp,color=white,fontWeight=FontWeight.Bold)
                    Text("Güncel fikstür  •  Veriye dayalı olasılıklar",color=muted,fontSize=12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        DashboardStat("Lig",leagues.firstOrNull{it.first==league}?.second?.substringBefore(" ") ?: "TR",Modifier.weight(1f))
                        DashboardStat("Maç",result?.matches?.count{it.status!="FINISHED"}?.toString() ?: "—",Modifier.weight(1f))
                        DashboardStat("Durum",if(loading) "Yükleniyor" else if(error!=null) "Hata" else "Hazır",Modifier.weight(1f))
                    }
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
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        listOf("Bugün","Yarın","7 gün","Tümü").forEachIndexed { index,label ->
                            FilterChip(selected=dayFilter==index,onClick={dayFilter=index},label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=mint,selectedLabelColor=bg))
                        }
                    }
                    Text(if(league=="TR1") "Türkiye 2026/27 • Genel fikstür" else "Veri kapsamı lige göre değişebilir",color=muted,fontSize=11.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value=search,onValueChange={search=it},label={Text("Takım ara")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Button(onClick={refresh++},enabled=!loading && base.isNotBlank()) { Icon(Icons.Filled.Refresh,contentDescription=null);Spacer(Modifier.width(6.dp));Text("Yenile") }
                        Spacer(Modifier.width(10.dp))
                        Text(if(loading) "Güncelleniyor…" else "Önbellek etkin",color=muted,fontSize=11.sp)
                    }
                    if(loading) { Spacer(Modifier.height(12.dp));LinearProgressIndicator(Modifier.fillMaxWidth(),color=mint) }
                    if(base.isBlank()) MessageCard("Kurulum bekleniyor","APK'ya HTTPS backend adresi eklenmemiş.")
                    if(error != null) MessageCard("Bağlantı hatası", "${error ?: ""}. İnternet bağlantını kontrol et ve Yenile'ye dokun.")
                    result?.let { data ->
                        Spacer(Modifier.height(12.dp))
                        if(showSource) Text("● Kaynak: ${data.source}",color=mint,fontSize=12.sp)
                        Text("Son veri alımı: ${local(data.fetched)}",color=muted,fontSize=11.sp)
                        Spacer(Modifier.height(12.dp))
                        val today=LocalDate.now()
                        val matches=data.matches.filter { m ->
                            val matchDay=try { Instant.parse(m.utc).atZone(ZoneId.systemDefault()).toLocalDate() } catch(_:Exception){null}
                            val days=if(matchDay!=null) ChronoUnit.DAYS.between(today,matchDay) else -1L
                            m.status != "FINISHED" && days>=0 &&
                            (dayFilter==3 || (dayFilter==0 && days==0L) || (dayFilter==1 && days==1L) || (dayFilter==2 && days<7)) &&
                            (m.home.contains(search,true) || m.away.contains(search,true)) &&
                            (section!=1 || matchKey(m) in favorites)
                        }
                        if(matches.isEmpty()) MessageCard("Yaklaşan maç yok","Seçilen ligde veya arama filtresinde yaklaşan karşılaşma bulunamadı. Başka lig veya takım deneyin.") else Text("${matches.size} yaklaşan karşılaşma",color=white,fontWeight=FontWeight.Bold,fontSize=15.sp)
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                            items(matches,key={it.id}) { m ->
                                Card(Modifier.fillMaxWidth().clickable { selected=m },shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=surface)) {
                                    Row(Modifier.fillMaxWidth().padding(if(compactCards) 10.dp else 15.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(local(m.utc),color=mint,fontSize=12.sp)
                                            Spacer(Modifier.height(if(compactCards) 3.dp else 9.dp))
                                            Text("${m.home}  –  ${m.away}",color=white,fontWeight=FontWeight.Bold,fontSize=if(compactCards) 15.sp else 17.sp)
                                            Text("TAHMİNLERİ İNCELE  ›",color=mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                                        }
                                        IconButton(onClick={toggleFavorite(m)}) {
                                            Icon(if(matchKey(m) in favorites) Icons.Filled.Star else Icons.Filled.StarBorder,contentDescription="Favori",tint=if(matchKey(m) in favorites) Color(0xFFFFCB56) else muted)
                                        }
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
                        item { Panel {
                            Text("ANALİZ DURUMU",color=mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
                            Text(if(analysisLoading) "Hesaplanıyor…" else if(estimate!=null) "Geçmiş maçlara dayalı model hazır" else "Veri bekleniyor",color=white,fontSize=13.sp)
                        } }
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
            if(selected==null) {
                NavigationBar(containerColor=surface,tonalElevation=0.dp) {
                    listOf("Maçlar","Favoriler","Ligler","Analiz","Ayarlar").forEachIndexed { i,label ->
                        val ic=when(i){0->Icons.Filled.SportsSoccer;1->Icons.Filled.Star;2->Icons.Filled.Public;3->Icons.Filled.BarChart;else->Icons.Filled.Settings}
                        NavigationBarItem(selected=section==i,onClick={section=i;if(i==1) dayFilter=3},icon={Icon(ic,contentDescription=label)},label={Text(label,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=bg,selectedTextColor=mint,indicatorColor=mint,unselectedIconColor=muted,unselectedTextColor=muted))
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

@Composable private fun DashboardStat(title:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(surface).padding(horizontal=10.dp,vertical=9.dp)) {
        Text(title,color=muted,fontSize=10.sp)
        Text(value,color=mint,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
