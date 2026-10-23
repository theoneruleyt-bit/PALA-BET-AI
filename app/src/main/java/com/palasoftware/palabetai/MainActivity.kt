package com.palasoftware.palabetai

import android.os.Bundle
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
import java.util.Locale
import kotlin.math.*

private val Background = Color(0xFF0B1216)
private val Surface = Color(0xFF162229)
private val Accent = Color(0xFF25C38B)
data class Fixture(val home: String, val away: String, val league: String, val homeAttack: Double, val awayAttack: Double)
private val demos = listOf(
 Fixture("Marmara FK", "Anadolu SK", "Örnek Lig A", 1.65, 1.12),
 Fixture("Kuzeyspor", "Güney FK", "Örnek Lig B", 1.24, 1.41),
 Fixture("Yıldız SK", "Deniz FK", "Örnek Lig A", 1.82, 0.93),
 Fixture("Ormanspor", "Başkent SK", "Örnek Lig C", 1.10, 1.17)
)
data class Odds(val home: Double, val draw: Double, val away: Double, val over25: Double)
private fun poisson(lambda: Double, k: Int): Double { var factorial = 1.0; for (i in 2..k) factorial *= i; return exp(-lambda) * lambda.pow(k) / factorial }
private fun predict(f: Fixture): Odds {
 var h=0.0; var d=0.0; var a=0.0; var over=0.0
 for (i in 0..12) for (j in 0..12) {
  val p=poisson(f.homeAttack,i)*poisson(f.awayAttack,j)
  when { i>j -> h+=p; i==j -> d+=p; else -> a+=p }
  if(i+j>2) over+=p
 }
 val total=h+d+a
 return Odds(h/total,d/total,a/total,over)
}
private fun pct(x: Double) = String.format(Locale.US,"%.1f%%",x*100)
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}
@Composable private fun App() {
 var selected by remember { mutableStateOf<Fixture?>(null) }
 var search by remember { mutableStateOf("") }
 MaterialTheme(colorScheme=darkColorScheme(primary=Accent,background=Background,surface=Surface,onBackground=Color.White,onSurface=Color.White)) {
  Column(Modifier.fillMaxSize().background(Background).padding(18.dp)) {
   Text("PALA BET AI",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black,color=Accent)
   Text("FOOTBALL INTELLIGENCE • V0.1",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)
   Spacer(Modifier.height(14.dp))
   Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF254038))) {
    Text("DEMO MODU • Bu karşılaşmalar gerçek değildir. Tahminler örnek parametrelerle hesaplanır; bahis tavsiyesi değildir.",modifier=Modifier.padding(14.dp),color=Color.White)
   }
   Spacer(Modifier.height(18.dp))
   if(selected==null) {
    Text("Maç Analiz Merkezi",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value=search,onValueChange={search=it},label={Text("Takım veya lig ara")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Spacer(Modifier.height(10.dp))
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
     items(demos.filter { it.home.contains(search,true)||it.away.contains(search,true)||it.league.contains(search,true) }) { f ->
      Card(onClick={selected=f},colors=CardDefaults.cardColors(containerColor=Surface),modifier=Modifier.fillMaxWidth()) {
       Column(Modifier.padding(18.dp)) {
        Text(f.league,color=Accent,style=MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text("${f.home}  –  ${f.away}",fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Olasılık analizini görüntüle  →",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
       }
      }
     }
    }
   } else {
    val f=selected!!; val o=remember(f) { predict(f) }
    TextButton(onClick={selected=null}) { Text("← Maç listesine dön") }
    Text("${f.home} – ${f.away}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text(f.league,color=Color.LightGray)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     listOf("1" to o.home,"X" to o.draw,"2" to o.away).forEach { (label,value) ->
      Card(modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Surface)) {
       Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label,color=Color.LightGray); Text(pct(value),color=Accent,fontWeight=FontWeight.Bold)
       }
      }
     }
    }
    Spacer(Modifier.height(14.dp))
    Card(colors=CardDefaults.cardColors(containerColor=Surface)) {
     Column(Modifier.fillMaxWidth().padding(18.dp)) {
      Text("Gol modelleri",fontWeight=FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      Text("2.5 Üst: ${pct(o.over25)}")
      Text("2.5 Alt: ${pct(1-o.over25)}")
      Text("Örnek beklenen gol: ${f.home} ${f.homeAttack} / ${f.away} ${f.awayAttack}")
      Spacer(Modifier.height(12.dp))
      Text("Yöntem: Bağımsız Poisson gol modeli. Kadro, sakatlık, güncel form veya canlı veri bu sürümde kullanılmaz.",color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
     }
    }
   }
  }
 }
}
