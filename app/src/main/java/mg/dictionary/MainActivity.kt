package mg.dictionary

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF111111)
private val InkSoft = Color(0xFF3A3A3A)
private val Blue = Color(0xFF0B3D91)
private val Green = Color(0xFF1B5E20)
private val PageBg = Color(0xFFF4F5F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = DictionaryDb(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    background = PageBg,
                    surface = PageBg,
                    onSurface = Ink,
                    onBackground = Ink
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = PageBg, contentColor = Ink) {
                    DictionaryScreen(db)
                }
            }
        }
    }
}

@Composable
fun DictionaryScreen(db: DictionaryDb) {
    var query by remember { mutableStateOf("") }
    var mgToEn by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<Result>>(emptyList()) }

    LaunchedEffect(query, mgToEn) {
        results = withContext(Dispatchers.IO) { db.search(query, mgToEn) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (mgToEn) "Hikaroka teny Malagasy" else "Search English word") }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mgToEn) Button(onClick = {}) { Text("MG → EN") }
            else OutlinedButton(onClick = { mgToEn = true }) { Text("MG → EN") }
            if (!mgToEn) Button(onClick = {}) { Text("EN → MG") }
            else OutlinedButton(onClick = { mgToEn = false }) { Text("EN → MG") }
        }
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty()) {
            Text("Tsy nahitana teny.", color = InkSoft)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.headword }) { ResultCard(db, it) }
        }
        Text(
            "Offline • Loharano: ny tenin'ny app + Wiktionary (CC BY-SA 4.0) via kaikki.org",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun DefLine(label: String, text: String, labelColor: Color) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = labelColor)) { append("$label  ") }
            append(text)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Ink
    )
}

@Composable
fun ResultCard(db: DictionaryDb, r: Result) {
    var open by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Ink),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(r.headword, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Ink)
            r.meanings.forEachIndexed { i, m ->
                Spacer(Modifier.height(8.dp))
                val pos = if (m.pos.isNotBlank()) "  (${m.pos})" else ""
                Text("${i + 1}. ${m.translation}$pos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = Ink)
                if (m.defMg.isNotBlank()) DefLine("MG", m.defMg, Blue)
                if (m.defEn.isNotBlank()) DefLine("EN", m.defEn, Green)
                if (open) {
                    if (m.hasImage) MeaningImage(db, m.id)
                    if (m.exSrc.isNotBlank()) {
                        Text(
                            m.exSrc + if (m.exDst.isNotBlank()) "\n${m.exDst}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = InkSoft,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    val src = if (m.source == "wiktionary") "Wiktionary" else "Rakibolana"
                    val ver = if (m.verified) "voamarina" else "tsy mbola voamarina"
                    Text("Loharano: $src • $ver",
                        style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            }
        }
    }
}

@Composable
fun MeaningImage(db: DictionaryDb, id: Long) {
    val bmp by produceState<Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { db.image(id) }
    }
    bmp?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(vertical = 6.dp)
        )
    }
}
