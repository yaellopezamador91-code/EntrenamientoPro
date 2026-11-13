Warning: truncated output (original token count: 14347)
Total output lines: 1345

package com.seisdoce.entrenamientopro

import android.os.Bundle
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
//import com.seisdoce.entrenamientopro.ui.theme.EntrenamientoProTheme
import com.example.entrenamientopro.ui.theme.EntrenamientoProTheme
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Build
import android.app.PictureInPictureParams
// --- MODELOS Y DATOS ---

data class ConfigEntrenamiento(
    val id: Long = System.currentTimeMillis(),
    val nombre: String,
    val roundsTotales: Int,
    val seriesPorRound: Int,
    val tiempoTrabajo: Int,
    val tiempoDescansoCorto: Int,
    val tiempoDescansoLargo: Int,
    val esBoxeo: Boolean,
    val tiempoPreparacion: Int = 10,
    val avisoFin: Int = 10,

    // --- NUEVOS CAMPOS DE PERSONALIZACIÓN DE SONIDO ---
    // Se guardan como String (el nombre del sonido) para facilitar la selección
    val sonidoInicioTrabajo: String = "Campana",
    val sonidoAvisoFin: String = "Tablas",      // Solo se usará si esBoxeo = true
    val sonidoInicioDescanso: String = "Campana",  // Para descanso corto o largo
    val sonidoFinEntrenamiento: String = "Campana",
    val avisoFinDescanso: Int = 10,
    val sonidoAvisoFinDescanso: String = "Tablas"
)

// --- ACTIVIDAD PRINCIPAL ---
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    companion object {
        var estaEjecutandoRutinaState = mutableStateOf(false)
    }

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_TOGGLE_PAUSE") {
                estaEjecutandoRutinaState.value = !estaEjecutandoRutinaState.value
                actualizarAccionesPip()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this) {}

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                }
            }
        }

        setContent {
            EntrenamientoProTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContainer(
                        onSpeak = { texto ->
                            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
                        },
                        estaEjecutandoRutina = estaEjecutandoRutinaState.value,
                        onEstadoRutinaCambio = { ejecutando ->
                            estaEjecutandoRutinaState.value = ejecutando
                        }
                    )
                }
            }
        }
    } // <-- Llave de cierre de onCreate limpia

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = android.content.IntentFilter("ACTION_TOGGLE_PAUSE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, filter)
            }
        }
    }
//cargarListaRutinas
    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { unregisterReceiver(pipReceiver) } catch (e: Exception) {}
        }
    }

    private fun actualizarAccionesPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val estaCorriendo = estaEjecutandoRutinaState.value
            val iconRes = if (estaCorriendo) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            val title = if (estaCorriendo) "Pausar" else "Reanudar"

            val intent = android.content.Intent("ACTION_TOGGLE_PAUSE").apply {
                setPackage(packageName)
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this@MainActivity,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val action = android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this@MainActivity, iconRes),
                title,
                title,
                pendingIntent
            )

            val params = PictureInPictureParams.Builder()
                .setActions(listOf(action))
                .build()

            setPictureInPictureParams(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (estaEjecutandoRutinaState.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            actualizarAccionesPip()
            val params = PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("es", "ES"))
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }


    // --- COMPONENTES DE AUDIO MEJORADOS ---
    fun crearPlayer(context: Context, nombreSonido: String): MediaPlayer? {
        return try {
            if (nombreSonido.startsWith("content://")) {
                MediaPlayer().apply {
                    setDataSource(context, Uri.parse(nombreSonido))
                    prepare()
                }
            } else {
                val resId = when (nombreSonido) {
                    "Silbato" -> R.raw.silbato
                    "Tablas" -> R.raw.tablas
                    else -> R.raw.campana
                }
                MediaPlayer.create(context, resId)
            }
        } catch (e: Exception) {
            MediaPlayer.create(context, R.raw.campana)
        }
    }
}

//EntrenamientoPrefs
@Composable
fun PantallaInfoExtra(onVolver: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Información y Aviso Legal", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚠️ DESCARGO DE RESPONSABILIDAD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Consulte a un médico antes de iniciar cualquier programa de ejercicio de alta intensidad (HIIT) o boxeo. " +
                                "Esta aplicación es solo una herramienta de cronometraje. El uso de esta app es bajo su propio riesgo. " +
                                "Los desarrolladores no se hacen responsables por lesiones o problemas de salud derivados de la práctica deportiva.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Instrucciones:", fontWeight = FontWeight.Bold)
            Text("• Modo Boxeo: Rounds largos con avisos de finalización.\n" +
                    "• Modo HIIT: Intervalos rápidos con descansos cortos.\n" +
                    "• Sonidos: Puede usar sonidos internos o subir sus propios archivos (máx. 3 segundos).")

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
                Text("ENTENDIDO")
            }
        }
    }
}

@Composable
fun MainContainer(
    onSpeak: (String) -> Unit,
    estaEjecutandoRutina: Boolean = false,
    onEstadoRutinaCambio: (Boolean) -> Unit // <-- Nuevo parámetro
) {
    val context = LocalContext.current
    var pestañaSeleccionada by remember { mutableIntStateOf(0) }
    var pantallaActual by remember { mutableIntStateOf(0) }
    var rutinaParaEjecutar by remember { mutableStateOf<ConfigEntrenamiento?>(null) }
    var rutinaParaEditar by remember { mutableStateOf<ConfigEntrenamiento?>(null) }
    var modoEsBoxeo by remember { mutableStateOf(true) }

    val estaEnPip = rememberEstaEnPip() // Detectamos PiP

    Scaffold(
        bottomBar = {
            // Solo mostramos navegación si estamos en pantalla 0 Y NO estamos en PiP
            if (pantallaActual == 0 && !estaEnPip) {
                Column {
                    BannerAd(modifier = Modifier.padding(bottom = 4.dp))
                    NavigationBar {
                        NavigationBarItem(
                            selected = pestañaSeleccionada == 0,
                            onClick = { pestañaSeleccionada = 0; modoEsBoxeo = true },
                            icon = { Icon(Icons.Default.ListAlt, "Boxeo") },
                            label = { Text("Boxeo") }
                        )
                        NavigationBarItem(
                            selected = pestañaSeleccionada == 1,
                            onClick = { pestañaSeleccionada = 1; modoEsBoxeo = false },
                            icon = { Icon(Icons.Default.Timer, "HIIT") },
                            label = { Text("HIIT") }
                        )
                        NavigationBarItem(
                            selected = pestañaSeleccionada == 2,
                            onClick = { pestañaSeleccionada = 2 },
                            icon = { Icon(Icons.Default.History, "Libre") },
                            label = { Text("Libre") }
                        )
                        NavigationBarItem(
                            selected = pestañaSeleccionada == 3,
                            onClick = { pestañaSeleccionada = 3 },
                            icon = { Icon(Icons.Default.Info, "Info") },
                            label = { Text("Info") }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (pantallaActual == 0 && (pestañaSeleccionada == 0 || pestañaSeleccionada == 1) && !estaEnPip) {
                FloatingActionButton(onClick = {
                    rutinaParaEditar = null
                    modoEsBoxeo = (pestañaSeleccionada == 0)
                    pantallaActual = 1
                }) {
                    Icon(Icons.Default.Add, "Nueva Rutina")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(if (estaEnPip) PaddingValues(0.dp) else paddingValues)) {
            when (pantallaActual) {
                0 -> {
                    when (pestañaSeleccionada) {
                        0 -> PantallaSelectorRutinas(true, { r -> rutinaParaEjecutar = r; pantallaActual = 2 }, { r -> rutinaParaEditar = r; pantallaActual = 1 })
                        1 -> PantallaSelectorRutinas(false, { r -> rutinaParaEjecutar = r; pantallaActual = 2 }, { r -> rutinaParaEditar = r; pantallaActual = 1 })
                        2 -> PantallaCronometroLibre()
                        3 -> PantallaInfoExtra(onVolver = { pestañaSeleccionada = 0 })
                    }
                }
                1 -> PantallaConfiguracion(
                    rutinaExistente = rutinaParaEditar,
                    esBoxeoInicial = modoEsBoxeo,
                    onRutinaCreada = { nueva ->
                        val lista = cargarListaRutinas(context, nueva.esBoxeo).toMutableList()
                        val index = lista.indexOfFirst { it.id == nueva.id }
                        if (index != -1) lista[index] = nueva else lista.add(nueva)
                        guardarListaRutinas(context, lista, nueva.esBoxeo)
                        pantallaActual = 0
                    },
                    onVolver = { pantallaActual = 0 }
                )
                2 -> rutinaParaEjecutar?.let {
                    CronometroEntrenamiento(
                        config = it,
                        estaEjecutandoRutina = estaEjecutandoRutina,
                        onVolver = {
                            onEstadoRutinaCambio(false)
                            pantallaActual = 0
                        },
                        onSpeak = onSpeak,
                        onEstadoRutinaCambio = onEstadoRutinaCambio
                    )
                }
            }
        }
    }
}
@Composable
fun PantallaListaRutinas(
    rutinas: List<ConfigEntrenamiento>,
    onNuevaRutina: (Boolean) -> Unit,
    onSeleccionar: (ConfigEntrenamiento) -> Unit,
    onEliminar: (ConfigEntrenamiento) -> Unit,
    onEditar: (ConfigEntrenamiento) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { onNuevaRutina(false) }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Rutina")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Mis Rutinas", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (rutinas.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay rutinas. ¡Crea una!", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rutinas) { rutina ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSeleccionar(rutina) },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rutina.nombre, style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        if (rutina.esBoxeo) "Modo: Boxeo • ${rutina.roundsTotales} Rounds"
                                        else "Modo: HIIT • ${rutina.seriesPorRound} Series",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                                IconButton(onClick = { onEditar(rutina) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.Gray)
                                }
                                IconButton(onClick = { onEliminar(rutina) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFD32F2F))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CronometroEntrenamiento(
    config: ConfigEntrenamiento,
    estaEjecutandoRutina: Boolean = false,
    onVolver: () -> Unit,
    onSpeak: (String) -> Unit,
    onEstadoRutinaCambio: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val estaEnPip = rememberEstaEnPip()

    // --- FUNCIÓN PARA MAPEAR NOMBRES A ARCHIVOS ---
    fun crearPlayer(context: Context, nombreSonido: String): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            if (nombreSonido.startsWith("content://")) {
                mp.setDataSource(context, Uri.parse(nombreSonido))
            } else {
                val resId = when (nombreSonido) {
                    "Silbato" -> R.raw.silbato
                    "Tablas" -> R.raw.tablas
                    else -> R.raw.campana
                }
                val assetFileDescriptor = context.resources.openRawResourceFd(resId)
                mp.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                assetFileDescriptor.close()
            }
            mp.prepare()
            mp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- REPRODUCTORES DE AUDIO ---
    val mpTrabajo = remember { crearPlayer(context, config.sonidoInicioTrabajo) }
    val mpAviso = remember { crearPlayer(context, config.sonidoAvisoFin) }
    val mpDescanso = remember { crearPlayer(context, config.sonidoInicioDescanso) }
    val mpFinTotal = remember { crearPlayer(context, config.sonidoFinEntrenamiento) }
    val mpAvisoDescanso = remember { crearPlayer(context, config.sonidoAvisoFinDescanso) }

    // --- ESTADOS ---
    var roundActual by remember { mutableIntStateOf(1) }
    var serieActual by remember { mutableIntStateOf(1) }
    var segundosRestantes by remember { mutableIntStateOf(config.tiempoPreparacion) }
    var estaCorriendo by remember { mutableStateOf(estaEjecutandoRutina) }
    var faseActual by remember { mutableStateOf("PREPARACION") }

    // Cálculo del tiempo total para la barra de progreso
    fun calcularSegundosTotales(c: ConfigEntrenamiento): Int {
        val tiempoPorRound = (c.tiempoTrabajo * c.seriesPorRound) + (c.tiempoDescansoCorto * (c.seriesPorRound - 1))
        return c.tiempoPreparacion + (tiempoPorRound * c.roundsTotales) + (c.tiempoDescansoLargo * (c.roundsTotales - 1))
    }
    var tiempoGlobalRestante by remember { mutableIntStateOf(calcularSegundosTotales(config)) }

    LaunchedEffect(estaEjecutandoRutina) {
        estaCorriendo = estaEjecutandoRutina
    }
    // Notificar si la rutina se está ejecutando o no para el permiso de PiP
    LaunchedEffect(estaCorriendo) {
        onEstadoRutinaCambio(estaCorriendo)
    }

    // Liberación de recursos y reseteo del estado PiP
    DisposableEffect(Unit) {
        onDispose {
            onEstadoRutinaCambio(false)
            mpTrabajo?.release()
            mpAviso?.release()
            mpDescanso?.release()
            mpFinTotal?.release()
            mpAvisoDescanso?.release()
        }
    }

    val colorFondo by animateColorAsState(
        targetValue = when (faseActual) {
            "PREPARACION" -> Color(0xFF607D8B)
            "TRABAJO" -> Color(0xFF00C407)
            "DESCANSO", "LARGO" -> Color(0xFFD31E00)
            else -> Color(0xFF1976D2)
        }, label = "ColorAnim"
    )

    // --- LÓGICA DEL CRONÓMETRO ---
    LaunchedEffect(estaCorriendo, segundosRestantes) {
        if (estaCorriendo && segundosRestantes > 0) {
            // Voz de preparación
            if (faseActual == "PREPARACION" && segundosRestantes == config.tiempoPreparacion) {
                onSpeak("Prepárate")
            }

            // AVISO FIN DE TRABAJO
            if (faseActual == "TRABAJO" && segundosRestantes == config.avisoFin) {
                reproducirConDucking(context, mpAviso)
            }

            // AVISO FIN DE DESCANSO
            if (faseActual == "LARGO" && segundosRestantes == config.avisoFinDescanso) {
                reproducirConDucking(context, mpAvisoDescanso)
            }

            delay(1000L)
            segundosRestantes -= 1
            if (tiempoGlobalRestante > 0) tiempoGlobalRestante -= 1

        } else if (segundosRestantes == 0 && estaCorriendo) {
            when (faseActual) {
                "PREPARACION" -> {
                    reproducirConDucking(context, mpTrabajo)
                    faseActual = "TRABAJO"
                    segundosRestantes = config.tiempoTrabajo
                }
                "TRABAJO" -> {
                    if (serieActual >= config.seriesPorRound) {
                        if (roundActual < config.roundsTotales) {
                            reproducirConDucking(context, mpDescanso)
                            onSpeak("Descanso largo")
                            faseActual = "LARGO"
                            segundosRestantes = if (config.tiempoDescansoLargo > 0) config.tiempoDescansoLargo else 1
                        } else {
                            estaCorriendo = false
                            reproducirConDucking(context, mpFinTotal)
                            onSpeak("Entrenamiento completado")
                        }
                    } else {
                        reproducirConDucking(context, mpDescanso)
                        faseActual = "DESCANSO"
                        segundosRestantes = if (config.tiempoDescansoCorto > 0) config.tiempoDescansoCorto else 1
                    }
                }
                "DESCANSO" -> {
                    reproducirConDucking(context, mpTrabajo)
                    faseActual = "TRABAJO"
                    serieActual += 1
                    segundosRestantes = config.tiempoTrabajo
                }
                "LARGO" -> {
                    reproducirConDucking(context, mpTrabajo)
                    faseActual = "TRABAJO"
                    roundActual += 1
                    serieActual = 1
                    segundosRestantes = config.tiempoTrabajo
                }
            }
        }
    }

    // --- INTERFAZ DE USUARIO ---
    Surface(color = colorFondo, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(if (estaEnPip) 4.dp else 16.dp)
        ) {
            // SI NO ESTÁ EN PIP, MOSTRAR EL TIEMPO TOTAL
            if (!estaEnPip) {
                Text(
                    "TIEMPO TOTAL",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "%02d:%02d".format(tiempoGlobalRestante / 60, tiempoGlobalRestante % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(40.dp))
            }

            // FASE ACTUAL
            Text(
                text = when(faseActual) {
                    "PREPARACION" -> "¡PREPÁRATE!"
                    "TRABAJO" -> "¡TRABAJO!"
                    "DESCANSO", "LARGO" -> "RECUPERA"
                    else -> ""
                },
                style = if (estaEnPip) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // ROUND O SERIE ACTUAL
            Text(
                text = if (config.esBoxeo) "R $roundActual/${config.roundsTotales}"
                else "S $serieActual/${config.seriesPorRound} (R$roundActual)",
                color = Color.White,
                style = if (estaEnPip) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge
            )

            // CRONÓMETRO PRINCIPAL (Ajusta tamaño según PiP)
            Text(
                text = "%02d:%02d".format(segundosRestantes / 60, segundosRestantes % 60),
                style = MaterialTheme.typography.displayLarge,
                fontSize = if (estaEnPip) 45.sp else 110.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            // SI NO ESTÁ EN PIP, MOSTRAR LOS BOTONES DE PAUSA Y SALIR
            if (!estaEnPip) {
                Spacer(modifier = Modifier.height(50.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { estaCorriendo = !estaCorriendo },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(60.dp).weight(1f)
                    ) {
                        Text(
                            if (estaCorriendo) "PAUSA" else "REANUDAR",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onVolver,
                        border = BorderStroke(1.dp, Color.White),
                        modifier = Modifier.height(60.dp).weight(1f)
                    ) {
                        Text("SALIR", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(titulo: String, desc: String) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
fun cargarListaRutinas(context: Context, esBoxeo: Boolean): List<ConfigEntrenamiento> {
    return try {
        val prefs = context.getSharedPreferences("PunchTimePrefs", Context.MODE_PRIVATE)
        val clave = if (esBoxeo) "lista_boxeo" else "lista_hiit"
        val json = prefs.ge…347 tokens truncated…     esBoxeo = true
                    )
                )
            } else {
                listOf(
                    ConfigEntrenamiento(
                        id = 4L,
                        nombre = "Tabata HIIT",
                        roundsTotales = 1,
                        seriesPorRound = 8,
                        tiempoTrabajo = 20,
                        tiempoDescansoCorto = 10,
                        tiempoDescansoLargo = 0,
                        esBoxeo = false
                    )
                )
            }
            // GUARDA FÍSICAMENTE EN EL CELULAR EN LA PRIMERA APERTURA
            guardarListaRutinas(context, listaPorDefecto, esBoxeo)
            listaPorDefecto
        } else {
            val type = object : TypeToken<List<ConfigEntrenamiento>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun guardarListaRutinas(context: Context, lista: List<ConfigEntrenamiento>, esBoxeo: Boolean) {
    val prefs = context.getSharedPreferences("PunchTimePrefs", Context.MODE_PRIVATE)
    val clave = if (esBoxeo) "lista_boxeo" else "lista_hiit"
    val json = Gson().toJson(lista)
    prefs.edit().putString(clave, json).apply()
}

@Composable
fun PantallaSelectorRutinas(
    esBoxeo: Boolean,
    onRutinaSeleccionada: (ConfigEntrenamiento) -> Unit,
    onModificar: (ConfigEntrenamiento) -> Unit
) {
    val context = LocalContext.current
    var rutinaAEliminar by remember { mutableStateOf<ConfigEntrenamiento?>(null) }

    // Lista reactiva que se sincroniza con el almacenamiento
    val rutinas = remember { mutableStateListOf<ConfigEntrenamiento>() }

    // Carga o recarga la lista cada vez que cambia la vista o se vuelve a ella
    LaunchedEffect(esBoxeo) {
        rutinas.clear()
        rutinas.addAll(cargarListaRutinas(context, esBoxeo))
    }

    if (rutinaAEliminar != null) {
        AlertDialog(
            onDismissRequest = { rutinaAEliminar = null },
            title = { Text("¿Eliminar rutina?", color = Color.White) },
            text = { Text("¿Estás seguro de que quieres borrar '${rutinaAEliminar?.nombre}'?", color = Color.LightGray) },
            containerColor = Color(0xFF2C2C2C),
            confirmButton = {
                Button(
                    onClick = {
                        val rutina = rutinaAEliminar
                        if (rutina != null) {
                            rutinas.remove(rutina)
                            guardarListaRutinas(context, rutinas.toList(), esBoxeo)
                        }
                        rutinaAEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { rutinaAEliminar = null }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                if (esBoxeo) "Mis Rutinas de Boxeo" else "Mis Rutinas HIIT",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        if (rutinas.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay rutinas creadas.\nPresiona + para empezar.",
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(rutinas) { rutina ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = { onRutinaSeleccionada(rutina) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rutina.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            val detalle = if (esBoxeo) "${rutina.roundsTotales} Rounds" else "${rutina.seriesPorRound} Series"
                            Text("$detalle | Prep: ${rutina.tiempoPreparacion}s", color = Color.Gray)
                        }
                        IconButton(onClick = { onModificar(rutina) }) {
                            Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { rutinaAEliminar = rutina }) {
                            Icon(Icons.Default.Delete, "Borrar", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaConfiguracion(
    rutinaExistente: ConfigEntrenamiento?,
    esBoxeoInicial: Boolean,
    onRutinaCreada: (ConfigEntrenamiento) -> Unit,
    onVolver: () -> Unit
) {
    // --- 1. ESTADOS BÁSICOS ---
    var nombre by remember { mutableStateOf(rutinaExistente?.nombre ?: "") }
    var esBoxeo by remember { mutableStateOf(rutinaExistente?.esBoxeo ?: esBoxeoInicial) }
    var preparacion by remember { mutableStateOf(rutinaExistente?.tiempoPreparacion?.toString() ?: "10") }

    // --- 2. ESTADOS DE TIEMPOS DE AVISO (PERSONALIZABLES) ---
    var avisoFin by remember { mutableStateOf(rutinaExistente?.avisoFin?.toString() ?: "10") }
    var avisoFinDescanso by remember { mutableStateOf(rutinaExistente?.avisoFinDescanso?.toString() ?: "10") }

    // --- 3. ESTADOS DE SELECCIÓN DE SONIDO ---
    var sonidoInicioTrabajo by remember { mutableStateOf(rutinaExistente?.sonidoInicioTrabajo ?: "Campana") }
    var sonidoAvisoFin by remember { mutableStateOf(rutinaExistente?.sonidoAvisoFin ?: "Tablas") }
    var sonidoInicioDescanso by remember { mutableStateOf(rutinaExistente?.sonidoInicioDescanso ?: "Campana") }
    var sonidoFinEntrenamiento by remember { mutableStateOf(rutinaExistente?.sonidoFinEntrenamiento ?: "Campana") }
    var sonidoAvisoFinDescanso by remember { mutableStateOf(rutinaExistente?.sonidoAvisoFinDescanso ?: "Campana") }

    // --- 4. ESTADOS DE TIEMPO DE EJERCICIO ---
    val tTrabajo = rutinaExistente?.tiempoTrabajo ?: 180
    var minT by remember { mutableStateOf((tTrabajo / 60).toString()) }
    var segT by remember { mutableStateOf((tTrabajo % 60).toString()) }

    var segDC by remember { mutableStateOf(rutinaExistente?.tiempoDescansoCorto?.toString() ?: "10") }
    var series by remember { mutableStateOf(rutinaExistente?.seriesPorRound?.toString() ?: "1") }
    var rounds by remember { mutableStateOf(rutinaExistente?.roundsTotales?.toString() ?: "1") }
    var segDL by remember { mutableStateOf(rutinaExistente?.tiempoDescansoLargo?.toString() ?: "60") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        item {
            // CABECERA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Configurar Rutina", style = MaterialTheme.typography.headlineMedium)

                // BOTÓN ENGRANE (Asegúrate de pasar todos los sonidos aquí)
                BotonConfiguracionSonidos(
                    config = ConfigEntrenamiento(
                        nombre = nombre,
                        roundsTotales = rounds.toIntOrNull() ?: 1,
                           seriesPorRound = series.toIntOrNull() ?: 1,
                        tiempoTrabajo = (minT.toIntOrNull() ?: 0) * 60 + (segT.toIntOrNull() ?: 0),
                        tiempoDescansoCorto = segDC.toIntOrNull() ?: 0,
                        tiempoDescansoLargo = segDL.toIntOrNull() ?: 0,
                        esBoxeo = esBoxeo,
                        sonidoInicioTrabajo = sonidoInicioTrabajo,
                        sonidoAvisoFin = sonidoAvisoFin,
                        sonidoInicioDescanso = sonidoInicioDescanso,
                        sonidoFinEntrenamiento = sonidoFinEntrenamiento,
                        sonidoAvisoFinDescanso = sonidoAvisoFinDescanso
                    ),
                    onConfigChanged = { nueva ->
                        sonidoInicioTrabajo = nueva.sonidoInicioTrabajo
                        sonidoAvisoFin = nueva.sonidoAvisoFin
                        sonidoInicioDescanso = nueva.sonidoInicioDescanso
                        sonidoFinEntrenamiento = nueva.sonidoFinEntrenamiento
                        sonidoAvisoFinDescanso = nueva.sonidoAvisoFinDescanso
                    }
                )
            }

            // MODO DE ENTRENAMIENTO
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("HIIT  ")
                Switch(checked = esBoxeo, onCheckedChange = { esBoxeo = it })
                Text("  BOXEO")
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre de la rutina") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TIEMPOS DE PREPARACIÓN Y AVISOS (ESTO CORRIGE TU ERROR)
            Text("Tiempos de aviso (segundos):", style = MaterialTheme.typography.titleSmall)
            Text("", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = preparacion,
                    onValueChange = { if (it.all { c -> c.isDigit() }) preparacion = it },
                    label = { Text("Preparate.") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = avisoFin,
                    onValueChange = { if (it.all { c -> c.isDigit() }) avisoFin = it },
                    label = { Text("Aviso de fin de round") },
                    modifier = Modifier.weight(1f),

                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                // NUEVO CAMPO PERSONALIZABLE
                OutlinedTextField(
                    value = avisoFinDescanso,
                    onValueChange = { if (it.all { c -> c.isDigit() }) avisoFinDescanso = it },
                    label = { Text("Aviso de fin de descanso.") },
                    modifier = Modifier.weight(1f),
               
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CONFIGURACIÓN DE TRABAJO
            Text("Tiempo de Trabajo:", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minT,
                    onValueChange = { if (it.all { c -> c.isDigit() }) minT = it },
                    label = { Text("Min") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = segT,
                    onValueChange = { if (it.all { c -> c.isDigit() }) segT = it },
                    label = { Text("Seg") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // HIIT: SERIES Y DESCANSO CORTO
            if (!esBoxeo) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Series e Intervalos HIIT:", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = segDC,
                        onValueChange = { if (it.all { c -> c.isDigit() }) segDC = it },
                        label = { Text("Desc. Corto") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = series,
                        onValueChange = { if (it.all { c -> c.isDigit() }) series = it },
                        label = { Text("Series") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Rounds y Descanso Largo:", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rounds,
                    onValueChange = { if (it.all { c -> c.isDigit() }) rounds = it },
                    label = { Text("Rounds") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = segDL,
                    onValueChange = { if (it.all { c -> c.isDigit() }) segDL = it },
                    label = { Text("Desc. Largo (seg)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOTÓN GUARDAR
            Button(
                onClick = {
                    onRutinaCreada(ConfigEntrenamiento(
                        id = rutinaExistente?.id ?: System.currentTimeMillis(),
                        nombre = nombre,
                        roundsTotales = rounds.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        seriesPorRound = if (esBoxeo) 1 else (series.toIntOrNull()?.coerceAtLeast(1) ?: 1),
                        tiempoTrabajo = ((minT.toIntOrNull() ?: 0) * 60 + (segT.toIntOrNull() ?: 0)).coerceAtLeast(1),
                        tiempoDescansoCorto = if (esBoxeo) 0 else (segDC.toIntOrNull() ?: 0),
                        tiempoDescansoLargo = segDL.toIntOrNull() ?: 0,
                        esBoxeo = esBoxeo,
                        tiempoPreparacion = preparacion.toIntOrNull() ?: 10,
                        avisoFin = avisoFin.toIntOrNull() ?: 10,
                        avisoFinDescanso = avisoFinDescanso.toIntOrNull() ?: 10, // <--- SE GUARDA AQUÍ
                        sonidoInicioTrabajo = sonidoInicioTrabajo,
                        sonidoAvisoFin = sonidoAvisoFin,
                        sonidoInicioDescanso = sonidoInicioDescanso,
                        sonidoFinEntrenamiento = sonidoFinEntrenamiento,
                        sonidoAvisoFinDescanso = sonidoAvisoFinDescanso
                    ))
                },
                enabled = nombre.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GUARDAR RUTINA")
            }

            TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
                Text("CANCELAR")
            }
        }
    }
}
///crearPlayer(context, it)?.start()    BotonConfiguracionSonidos

fun calcularTiempoTotal(c: ConfigEntrenamiento): String {
    val tiempoPorRound = (c.tiempoTrabajo * c.seriesPorRound) +
            (c.tiempoDescansoCorto * (c.seriesPorRound - 1))
    val tiempoTotalSegundos = c.tiempoPreparacion +
            (tiempoPorRound * c.roundsTotales) +
            (c.tiempoDescansoLargo * (c.roundsTotales - 1))

    val h = tiempoTotalSegundos / 3600
    val m = (tiempoTotalSegundos % 3600) / 60
    val s = tiempoTotalSegundos % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun PantallaCronometroLibre() {
    var tiempoMs by remember { mutableLongStateOf(0L) }
    var activo by remember { mutableStateOf(false) }

    LaunchedEffect(activo) {
        while (activo) {
            delay(10L)
            tiempoMs += 10
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CRONÓMETRO LIBRE", style = MaterialTheme.typography.headlineSmall)

        val min = (tiempoMs / 60000)
        val seg = (tiempoMs % 60000) / 1000
        val cent = (tiempoMs % 1000) / 10

        Text(
            text = "%02d:%02d:%02d".format(min, seg, cent),
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row {
            Button(onClick = { activo = !activo }) {
                Text(if (activo) "PAUSA" else "INICIAR")
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(onClick = { activo = false; tiempoMs = 0 }) {
                Text("RESET")
            }
        }
    }
}

@Composable
fun BotonConfiguracionSonidos(
    config: ConfigEntrenamiento,
    onConfigChanged: (ConfigEntrenamiento) -> Unit
) {
    var mostrarDialogo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val listaSonidosBase = listOf("Campana", "Silbato", "Tablas")

    var tipoSonidoEditando by remember { mutableStateOf("") }

    // Launcher con validación de duración
    val launcherSonido = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, selectedUri)
                val durationStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val millisecondos = durationStr?.toLong() ?: 0

                // VALIDACIÓN: Máximo 3 segundos (6000 ms)
                if (millisecondos > 3000) {
                    android.widget.Toast.makeText(context, "El sonido es muy largo (máx 3s)", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // PERSISTENCIA: Otorgar permiso persistente a la URI para que funcione al reiniciar el celular
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            selectedUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) { e.printStackTrace() }

                    val uriString = selectedUri.toString()
                    val nuevaConfig = when(tipoSonidoEditando) {
                        "inicio" -> config.copy(sonidoInicioTrabajo = uriString)
                        "aviso" -> config.copy(sonidoAvisoFin = uriString)
                        "descanso" -> config.copy(sonidoInicioDescanso = uriString)
                        "fin" -> config.copy(sonidoFinEntrenamiento = uriString)
                        "avisoDesc" -> config.copy(sonidoAvisoFinDescanso = uriString)
                        else -> config
                    }
                    onConfigChanged(nuevaConfig)
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error al procesar audio", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                mmr.release()
            }
        }
    }

    // Función para vista previa
    fun reproducirPreview(nombreSonido: String) {
        try {
            val mp = if (nombreSonido.startsWith("content://") || nombreSonido.startsWith("file://")) {
                MediaPlayer.create(context, Uri.parse(nombreSonido))
            } else {
                val resId = when (nombreSonido) {
                    "Silbato" -> R.raw.silbato
                    "Tablas" -> R.raw.tablas
                    else -> R.raw.campana
                }
                MediaPlayer.create(context, resId)

            }
            mp?.start()
            mp?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            // Si el archivo no es válido o no tiene permisos
        }
    }

    IconButton(onClick = { mostrarDialogo = true }) {
        Icon(Icons.Default.Settings, "Configurar Sonidos", tint = MaterialTheme.colorScheme.primary)
    }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("Personalizar Sonidos") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val secciones = listOf(
                        Triple("Inicio Trabajo", config.sonidoInicioTrabajo, "inicio"),
                        Triple("Aviso Fin Round", config.sonidoAvisoFin, "aviso"),
                        Triple("Inicio Descanso", config.sonidoInicioDescanso, "descanso"),
                        Triple("Fin Rutina", config.sonidoFinEntrenamiento, "fin"),
                        Triple("Aviso Fin Descanso", config.sonidoAvisoFinDescanso, "avisoDesc")
                    )

                    items(secciones) { (titulo, seleccionado, clave) ->
                        SeccionSeleccionSonido(
                            titulo = titulo,
                            seleccionado = seleccionado,
                            opciones = listaSonidosBase,
                            onSubirPropio = {
                                tipoSonidoEditando = clave
                                launcherSonido.launch("audio/*")
                            },
                            onSelect = {
                                val nueva = when(clave) {
                                    "inicio" -> config.copy(sonidoInicioTrabajo = it)
                                    "aviso" -> config.copy(sonidoAvisoFin = it)
                                    "descanso" -> config.copy(sonidoInicioDescanso = it)
                                    "fin" -> config.copy(sonidoFinEntrenamiento = it)
                                    else -> config.copy(sonidoAvisoFinDescanso = it)
                                }
                                onConfigChanged(nueva)
                                reproducirPreview(it) // <--- REPRODUCE AL TOCAR
                            }
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { mostrarDialogo = false }) { Text("Listo") } }
        )
    }
}

@Composable
fun SeccionSeleccionSonido(
    titulo: String,
    seleccionado: String,
    opciones: List<String>,
    onSubirPropio: () -> Unit,
    onSelect: (String) -> Unit // Define que recibe un String
) {
    Column {
        Text(titulo, style = MaterialTheme.typography.labelSmall, color = Color.Gray)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            opciones.forEach { opcion ->
                FilterChip(
                    selected = seleccionado == opcion,
                    onClick = { onSelect(opcion) }, // <--- IMPORTANTE
                    label = { Text(opcion, fontSize = 9.sp) }
                )
            }

            // Botón para el audio personalizado
            AssistChip(
                onClick = onSubirPropio,
                label = { Text("Subir propio", fontSize = 9.sp) },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(14.dp)) }
            )
        }
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                // Tamaño estándar de banner para móviles
                setAdSize(AdSize.BANNER)
                // ID DE PRUEBA de Google (No lo cambies hasta que subas la app)
                adUnitId = "ca-app-pub-7645686135936049/5367264636"

                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

fun reproducirConDucking(context: Context, mediaPlayer: MediaPlayer?) {
    if (mediaPlayer == null) return

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val playbackAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    var focusRequest: AudioFocusRequest? = null

    val resultadoFocus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()
        audioManager.requestAudioFocus(focusRequest)
    } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            { },
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    if (resultadoFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        mediaPlayer.setOnCompletionListener { mp ->
            // IMPORTANTE: NO usamos mp.release() aquí porque tus objetos mpTrabajo, mpDescanso, etc.,
            // se vuelven a usar en el siguiente round. Solo abandonamos el foco.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus({ })
            }
        }
        mediaPlayer.start()
    } else {
        mediaPlayer.start()
    }
}

@Composable
fun rememberEstaEnPip(): Boolean {
    val context = LocalContext.current
    var estaEnPip by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val listener = androidx.core.util.Consumer<android.content.res.Configuration> { newConfig ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                estaEnPip = activity.isInPictureInPictureMode
            }
        }
        activity?.addOnConfigurationChangedListener(listener)
        onDispose {
            activity?.removeOnConfigurationChangedListener(listener)
        }
    }

    return estaEnPip
}