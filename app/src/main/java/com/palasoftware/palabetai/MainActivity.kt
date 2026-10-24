package com.palasoftware.palabetai

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.*

private val Background=Color(0xFF0B1216)
private val Surface=Color(0xFF162229)
private val Accent=Color(0xFF25C38B)
private data class Fixture(val home:String,val away:String,val league:String,val homeAttack:Double,val awayAttack:Double)
private data class RemoteMatch(val id:String,val home:String,val away:String,val kickoff:String,val status:String)
private data class FixtureResponse(val matches:List<RemoteMatch>,val fetchedAt:String,val source:String)
private val demos=listOf(
 Fixture("Marmara FK","Anadolu SK","Örnek Lig A",1.65,1.12),
 Fixture("Kuzeyspor","Güney FK","Örnek Lig B",1.24,1.41),
 Fixture("Yıldız SK","Deniz FK","Örnek Lig A",1.82,0.93),
 Fixture("Ormanspor","Başkent SK","Örnek Lig C",1.10,1.17)
)
private data class Odds(val home:Double,val draw:Double,val away:Double,val over25:Double,val btts:Double)
private fun poisson(lambda:Double,k:Int):Double { var fact=1.0; for(i in 2..k) fact*=i; return exp(-lambda)*lambda.pow(k)/fact }
private fun predict(f:Fixture):Odds {
 var h=0.0;var d=0.0;var a=0.0;var over=0.0;var btts=0.0
 for(i in 0..12) for(j in 0..12){val v=poisson(f.homeAttack,i)*poisson(f.awayAttack,j);if(i>j)h+=v else if(i==j)d+=v else a+=v;if(i+j>2)over+=v;if(i>0&&j>0)btts+=v}
 val sum=h+d+a;return Odds(h/sum,d/sum,a/sum,over/sum,btts/sum)
}
private fun pct(x:Double)=String.format(Locale.US,"%.1f%%",x*100)
private fun localTime(utc:String):String=try { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(utc)) }catch(_:Exception){utc}
private fun fetchFixtures(server:String,competition:String):FixtureResponse {
 val base=server.trim().trimEnd('/')
 require(base.startsWith("https://",ignoreCase=true)){"Sunucu adresi https:// ile başlamalı"}
 val url=URL("$base/fixtures/$competition")
 val conn=(url.openConnection() as HttpURLConnection)
 conn.connectTimeout=10000;conn.readTimeout=10000;conn.requestMethod="GET"
 try {
  if(conn.responseCode!=200) throw IllegalStateException("Sunucu HTTP ${conn.responseCode} döndürdü. API anahtarını ve sunucuyu kontrol edin.")
  val data=conn.inputStream.bufferedReader().use{JSONObject(it.readText())}
  if(data.optString("mode")!="provider") throw IllegalStateException("Yanıt doğrulanmış sağlayıcı verisi değil")
  val items=data.getJSONArray("matches")
  val matches=ArrayList<RemoteMatch>()
  for(i in 0 until items.length()){
   val m=items.getJSONObject(i)
   val home=m.optString("home");val away=m.optString("away")
   if(home.isNotBlank()&&away.isNotBlank()) matches.add(RemoteMatch(m.optString("id"),home,away,m.optString("utc_date"),m.optString("status")))
  }
  return FixtureResponse(matches,data.optString("fetched_at"),data.optString("source"))
 }finally{conn.disconnect()}
}
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{App()}}}
@Composable private fun App(){
 var selectedDemo by remember{mutableStateOf<Fixture?>(null)}
 var selectedReal by remember{mutableStateOf<RemoteMatch?>(null)}
 var search by remember{mutableStateOf("")}
 var server by remember{mutableStateOf("")}
 var competition by remember{mutableStateOf("PL")}
 var loading by remember{mutableStateOf(false)}
 var result by remember{mutableStateOf<FixtureResponse?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 val request=remember{mutableStateOf(0)}
 LaunchedEffect(request.value){
  if(request.value>0){
   loading=true;error=null;result=null;selectedReal=null
   val address=server;val league=competition
   // Network requests never run on the main/UI thread.
   thread {
    val loaded=runCatching{fetchFixtures(address,league)}
    Handler(Looper.getMainLooper()).post{loaded.onSuccess{result=it}.onFailure{error=it.message?:"Bağlantı başarısız"};loading=false}
   }
  }
 }
 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Background,surface=Surface,onBackground=Color.White,onSurface=Color.White)){
  Column(Modifier.fillMaxSize().background(Background).padding(16.dp)){
   Text("PALA BET AI",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black,color=Accent)
   Text("FOOTBALL INTELLIGENCE • V0.4.0",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
   Spacer(Modifier.height(10.dp))
   if(selectedDemo!=null){
    val f=selectedDemo!!;val o=remember(f){predict(f)}
    TextButton(onClick={selectedDemo=null}){Text("← Maç listesi")}
    Text("${f.home} – ${f.away}",style=MaterialTheme.typography.titleLarge)
    Text("DEMO • ${f.league}",color=Accent)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     listOf("1" to o.home,"X" to o.draw,"2" to o.away).forEach{(label,value)->
      Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Surface)){
       Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(label);Text(pct(value),color=Accent,fontWeight=FontWeight.Bold)}
      }
     }
    }
    Spacer(Modifier.height(12.dp))
    Card(colors=CardDefaults.cardColors(containerColor=Surface)){
     Column(Modifier.fillMaxWidth().padding(16.dp)){
      Text("KG Var: ${pct(o.btts)}",color=Accent)
      Text("2.5 Alt: ${pct(1-o.over25)}")
      Text("2.5 Üst: ${pct(o.over25)}")
      Spacer(Modifier.height(8.dp))
      Text("Yalnızca örnek Poisson parametreleriyle hesaplandı. Gerçek maç tahmini değildir.",style=MaterialTheme.typography.bodySmall)
     }
    }
   }else if(selectedReal!=null){
    val m=selectedReal!!
    TextButton(onClick={selectedReal=null}){Text("← Gerçek maç listesi")}
    Text("${m.home} – ${m.away}",style=MaterialTheme.typography.titleLarge)
    Text("Başlama: ${localTime(m.kickoff)}",color=Color.LightGray)
    Text("Durum: ${m.status}",color=Color.LightGray)
    Spacer(Modifier.height(12.dp))
    Card(colors=CardDefaults.cardColors(containerColor=Surface)){
     Text("GERÇEK FİKSTÜR • Tahmin üretilmedi. Geçmiş performans, xG ve kadro verisi doğrulanmadan olasılık gösterilmiyor.",modifier=Modifier.padding(16.dp))
    }
   }else{
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
     item{
      Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF254038))){
       Text("GERÇEK FİKSTÜR BAĞLANTISI • API anahtarı telefona girilmez. HTTPS backend adresinizi girin.",Modifier.padding(12.dp))
      }
      Spacer(Modifier.height(10.dp))
      OutlinedTextField(server,{server=it},label={Text("Backend HTTPS adresi")},placeholder={Text("https://ornek-sunucu.com")},modifier=Modifier.fillMaxWidth(),singleLine=true)
      Spacer(Modifier.height(6.dp))
      Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
       listOf("PL","PD","BL1","SA","FL1").forEach{code->
        FilterChip(selected=competition==code,onClick={competition=code},label={Text(code)})
       }
      }
      Button(onClick={request.value++},enabled=!loading&&server.isNotBlank()) {Text(if(loading)"Yükleniyor..." else "Gerçek fikstürü getir")}
      if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      if(error!=null) Text("Bağlantı hatası: $error",color=Color(0xFFFF9B9B))
      if(result!=null){
       Text("Kaynak: ${result!!.source} • Alınma: ${localTime(result!!.fetchedAt)}",color=Accent,style=MaterialTheme.typography.bodySmall)
       if(result!!.matches.isEmpty()) Text("Bu lig için planlanmış maç bulunamadı.")
      }
      Spacer(Modifier.height(12.dp))
      Text("Maç Merkezi",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
      OutlinedTextField(search,{search=it},label={Text("Takım veya lig ara")},modifier=Modifier.fillMaxWidth(),singleLine=true)
     }
     if(result!=null){
      items(result!!.matches.filter{it.home.contains(search,true)||it.away.contains(search,true)}){m->
       Card(onClick={selectedReal=m},colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp)){
         Text("GERÇEK FİKSTÜR • ${localTime(m.kickoff)}",color=Accent,style=MaterialTheme.typography.labelMedium)
         Text("${m.home} – ${m.away}",fontWeight=FontWeight.Bold)
        }
       }
      }
     }
     item{Text("DEMO MAÇLAR • Gerçek değildir",color=Color.LightGray,style=MaterialTheme.typography.titleSmall)}
     items(demos.filter{it.home.contains(search,true)||it.away.contains(search,true)||it.league.contains(search,true)}){f->
      Card(onClick={selectedDemo=f},colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth()){
       Column(Modifier.padding(14.dp)){
        Text("DEMO • ${f.league}",color=Accent,style=MaterialTheme.typography.labelMedium)
        Text("${f.home} – ${f.away}",fontWeight=FontWeight.Bold)
        Text("Örnek olasılıkları görüntüle →",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
       }
      }
     }
    }
   }
  }
 }
}
