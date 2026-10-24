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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = DictionaryDb(applicationContext)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { DictionaryScreen(db) }
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
            Text("Tsy nahitana teny.", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.headword }) { ResultCard(db, it) }
        }
    }
}

@Composable
fun ResultCard(db: DictionaryDb, r: Result) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp)) {
            Text(r.headword, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            r.meanings.forEachIndexed { i, m ->
                Spacer(Modifier.height(6.dp))
                val pos = if (m.pos.isNotBlank()) "  (${m.pos})" else ""
                Text("${i + 1}. ${m.translation}$pos",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (m.defMg.isNotBlank()) {
                    Text("MG: ${m.defMg}", style = MaterialTheme.typography.bodyMedium)
                }
                if (m.defEn.isNotBlank()) {
                    Text("EN: ${m.defEn}", style = MaterialTheme.typography.bodyMedium)
                }
                if (open) {
                    if (m.hasImage) MeaningImage(db, m.id)
                    if (m.exSrc.isNotBlank()) {
                        Text(
                            m.exSrc + if (m.exDst.isNotBlank()) "\n${m.exDst}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (!m.verified) {
                        Text("Tsy mbola voamarina", style = MaterialTheme.typography.labelSmall)
                    }
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
