package com.example.vozreco;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int PERMISO_ACCION = 102;

    private static final String PREFS = "vozreco";
    private static final String PREF_VELOCIDAD = "velocidad_voz";
    private static final String PREF_CONTINUO = "modo_continuo";
    private static final String PREF_EMERGENCIA_NOMBRE = "emergencia_nombre";
    private static final String PREF_EMERGENCIA_NUMERO = "emergencia_numero";
    private static final String PREF_NOTAS = "notas";

    private static final Locale idioma = TextoUtil.IDIOMA;

    /** Respuesta esperada a una pregunta hecha por el asistente. */
    private interface Respuesta {
        void recibir(String fraseOriginal, String comando);
    }

    // Componentes de Android
    private SpeechRecognizer reconocedor;
    private TextToSpeech voz;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Estados
    private boolean escuchando = false;
    private boolean vozLista = false;
    private boolean destruida = false;
    private boolean enPrimerPlano = false;
    private boolean modoContinuo = false;
    private int fallosSeguidos = 0;
    private int contadorFrases = 0;

    private String ultimaRespuesta = "";
    private String idFraseActual;
    private Runnable accionTrasHablar;
    private Runnable accionConPermiso;
    private String[] permisosPendientes;
    private Respuesta esperandoRespuesta;

    /** Resultado del último regex evaluado con coincide(). */
    private Matcher m;

    // Componentes visuales
    private TextView txtEstado;
    private TextView txtReconocido;
    private TextView txtRespuesta;
    private TextView txtComandos;

    private Button btnEscuchar;
    private Button btnCancelar;

    private LinearLayout mainPanel;
    private LinearLayout panelCalculadora;

    private EditText numA;
    private EditText numB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        modoContinuo = prefs.getBoolean(PREF_CONTINUO, false);

        // Relacionar Java con XML
        txtEstado = findViewById(R.id.txtEstado);
        txtReconocido = findViewById(R.id.txtReconocido);
        txtRespuesta = findViewById(R.id.txtRespuesta);
        txtComandos = findViewById(R.id.txtComandos);

        btnEscuchar = findViewById(R.id.btnEscuchar);
        btnCancelar = findViewById(R.id.btnCancelar);

        mainPanel = findViewById(R.id.mainPanel);
        panelCalculadora = findViewById(R.id.panelCalculadora);

        numA = findViewById(R.id.numA);
        numB = findViewById(R.id.numB);

        // Respetar barra de estado y navegación (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return insets;
        });

        // Eventos de los botones
        btnEscuchar.setOnClickListener(v -> {
            if (escuchando) cancelarEscucha();
            else solicitarOEscuchar();
        });

        btnCancelar.setOnClickListener(v -> cancelarEscucha());

        // Tocar cualquier zona libre de la pantalla también inicia la escucha
        mainPanel.setOnClickListener(v -> {
            if (!escuchando) solicitarOEscuchar();
        });
        mainPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        findViewById(R.id.btnSumar).setOnClickListener(v -> operarCalculadora('+'));
        findViewById(R.id.btnRestar).setOnClickListener(v -> operarCalculadora('-'));
        findViewById(R.id.btnMultiplicar).setOnClickListener(v -> operarCalculadora('*'));
        findViewById(R.id.btnDividir).setOnClickListener(v -> operarCalculadora('/'));

        findViewById(R.id.btnCerrarCalc).setOnClickListener(v -> {
            panelCalculadora.setVisibility(View.GONE);
            responder("Calculadora cerrada");
        });

        findViewById(R.id.btnComandos).setOnClickListener(v -> mostrarListaComandos());

        txtComandos.setText(CatalogoComandos.listaCompleta());
        btnCancelar.setEnabled(false);

        // Inicializar servicios
        prepararVoz(savedInstanceState == null);
        prepararReconocimiento();

        procesarComandoDePrueba(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        procesarComandoDePrueba(intent);
    }

    /**
     * Solo en builds de depuración: permite probar comandos sin micrófono, p. ej.
     * adb shell am start -n com.example.vozreco/.MainActivity --es comando "que hora es"
     */
    private void procesarComandoDePrueba(Intent intent) {

        boolean depuracion = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String comando = intent != null ? intent.getStringExtra("comando") : null;
        if (!depuracion || comando == null) return;

        intent.removeExtra("comando");
        // Esperar a que el sintetizador de voz esté listo
        handler.postDelayed(() -> procesarFrases(Collections.singletonList(comando)), 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enPrimerPlano = true;
    }

    @Override
    protected void onPause() {
        enPrimerPlano = false;
        // Android no permite usar el micrófono en segundo plano
        if (escuchando) cancelarEscucha();
        super.onPause();
    }

    // --------------------------------
    // 1. INICIALIZAR TEXT TO SPEECH
    // --------------------------------
    private void prepararVoz(boolean darBienvenida) {

        voz = new TextToSpeech(this, estado -> {

            if (destruida) return;

            if (estado == TextToSpeech.SUCCESS) {

                int resultado = voz.setLanguage(idioma);
                if (resultado == TextToSpeech.LANG_MISSING_DATA
                        || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    resultado = voz.setLanguage(Locale.forLanguageTag("es"));
                }

                vozLista = resultado != TextToSpeech.LANG_MISSING_DATA
                        && resultado != TextToSpeech.LANG_NOT_SUPPORTED;

                voz.setSpeechRate(prefs.getFloat(PREF_VELOCIDAD, 1.0f));
                voz.setOnUtteranceProgressListener(new OyenteVoz());

            } else {
                vozLista = false;
            }

            if (!vozLista) {
                txtRespuesta.setText("No hay voz en español instalada. "
                        + "Instálala en Ajustes > Accesibilidad > Texto a voz.");
            } else if (darBienvenida) {
                responder("Bienvenido a CESBA Voice Control. "
                        + "Toca el botón grande, en el centro de la pantalla, y di una orden. "
                        + "Di ayuda para conocer los comandos.", null);
            }
        });
    }

    /** Ejecuta la acción pendiente cuando termina de hablar. */
    private class OyenteVoz extends UtteranceProgressListener {

        @Override
        public void onStart(String id) {}

        @Override
        public void onDone(String id) {
            runOnUiThread(() -> terminarFrase(id, true));
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onError(String id) {
            runOnUiThread(() -> terminarFrase(id, true));
        }

        @Override
        public void onStop(String id, boolean interrumpida) {
            // Interrumpida por el usuario o por otra frase: no ejecutar nada
            runOnUiThread(() -> terminarFrase(id, false));
        }
    }

    private void terminarFrase(String id, boolean ejecutar) {

        if (destruida || id == null || !id.equals(idFraseActual)) return;

        Runnable accion = accionTrasHablar;
        accionTrasHablar = null;
        idFraseActual = null;

        if (ejecutar && accion != null) accion.run();
    }

    // --------------------------------
    // 2. CONFIGURAR RECONOCIMIENTO
    // --------------------------------
    private void prepararReconocimiento() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            txtEstado.setText("Reconocimiento no disponible. Instala la app de Google.");
            btnEscuchar.setEnabled(false);
            return;
        }

        reconocedor = SpeechRecognizer.createSpeechRecognizer(this);
        reconocedor.setRecognitionListener(new OyenteReconocimiento());

        txtEstado.setText("Listo para escuchar");
    }

    private class OyenteReconocimiento implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            txtEstado.setText("Escuchando...");
            vibrar(60);
        }

        @Override
        public void onBeginningOfSpeech() {
            txtEstado.setText("Voz detectada");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Intensidad del sonido
        }

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            txtEstado.setText("Procesando...");
        }

        @Override
        public void onError(int error) {

            if (!escuchando) return; // Error tras una cancelación

            finalizarEscucha();

            String mensaje;
            boolean reiniciar = false;

            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                    mensaje = "No reconocí las palabras";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    mensaje = "No detecté tu voz";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER:
                case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                    mensaje = "Error de conexión. Revisa tu Internet";
                    reiniciar = true;
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    mensaje = "Sin permiso del micrófono";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                case SpeechRecognizer.ERROR_CLIENT:
                    mensaje = "El reconocedor estaba ocupado. Intenta de nuevo";
                    reiniciar = true;
                    break;
                case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                    mensaje = "El español no está disponible en el reconocedor de voz";
                    break;
                case SpeechRecognizer.ERROR_AUDIO:
                    mensaje = "Error con el micrófono";
                    reiniciar = true;
                    break;
                default:
                    mensaje = "Error de reconocimiento: " + error;
                    reiniciar = true;
            }

            if (reiniciar) reiniciarReconocedor();

            txtEstado.setText("Error");

            if (esperandoRespuesta != null) {
                esperandoRespuesta = null;
                responder(mensaje + ". Acción cancelada.", null);
                return;
            }

            if (modoContinuo && ++fallosSeguidos < 3) {
                responder(mensaje, MainActivity.this::solicitarOEscuchar);
            } else {
                fallosSeguidos = 0;
                responder(mensaje + (modoContinuo
                        ? ". Toca la pantalla cuando me necesites." : ""), null);
            }
        }

        @Override
        public void onResults(Bundle results) {

            finalizarEscucha();
            fallosSeguidos = 0;

            ArrayList<String> frases =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (frases == null || frases.isEmpty()) {
                esperandoRespuesta = null;
                responder("No se obtuvo una transcripción");
                return;
            }

            procesarFrases(frases);
        }

        @Override
        public void onPartialResults(Bundle results) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    }

    private void reiniciarReconocedor() {

        if (reconocedor != null) {
            reconocedor.destroy();
            reconocedor = null;
        }
        if (!destruida) prepararReconocimiento();
    }

    // --------------------------------
    // 3. PERMISOS
    // --------------------------------
    private void solicitarOEscuchar() {

        if (destruida || escuchando || reconocedor == null) return;

        conPermisos(new String[]{Manifest.permission.RECORD_AUDIO},
                "Para escucharte necesito permiso del micrófono.",
                this::iniciarEscucha);
    }

    private boolean concedido(String permiso) {
        return checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean esUbicacion(String permiso) {
        return permiso.equals(Manifest.permission.ACCESS_FINE_LOCATION)
                || permiso.equals(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** La ubicación aproximada se acepta aunque no se conceda la precisa. */
    private boolean faltanPermisos(String[] permisos) {

        for (String p : permisos) {
            if (esUbicacion(p)) {
                if (!concedido(Manifest.permission.ACCESS_FINE_LOCATION)
                        && !concedido(Manifest.permission.ACCESS_COARSE_LOCATION)) return true;
            } else if (!concedido(p)) {
                return true;
            }
        }
        return false;
    }

    /** Ejecuta la acción si hay permisos; si no, explica por qué y los pide. */
    private void conPermisos(String[] permisos, String motivo, Runnable accion) {

        if (!faltanPermisos(permisos)) {
            accion.run();
            return;
        }

        List<String> faltan = new ArrayList<>();
        for (String p : permisos) if (!concedido(p)) faltan.add(p);

        accionConPermiso = accion;
        permisosPendientes = permisos;

        responder(motivo + " Te mostraré una ventana. Elige permitir.", () ->
                requestPermissions(faltan.toArray(new String[0]), PERMISO_ACCION));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != PERMISO_ACCION || permisosPendientes == null) return;

        Runnable accion = accionConPermiso;
        String[] pedidos = permisosPendientes;
        accionConPermiso = null;
        permisosPendientes = null;

        if (!faltanPermisos(pedidos)) {
            if (accion != null) accion.run();
        } else {
            responder("Sin ese permiso no puedo hacerlo. Para activarlo di: "
                    + "abre los ajustes de la aplicación.", null);
        }
    }

    // --------------------------------
    // 4. INICIAR / CANCELAR RECONOCIMIENTO
    // --------------------------------
    private void iniciarEscucha() {

        if (destruida || escuchando || reconocedor == null || !enPrimerPlano) return;

        // Evitar reconocer la propia voz sintética
        if (voz != null) voz.stop();
        accionTrasHablar = null;
        idFraseActual = null;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

        escuchando = true;

        btnEscuchar.setText("ESCUCHANDO…\nTOCA PARA DETENER");
        btnCancelar.setEnabled(true);
        txtEstado.setText("Preparando micrófono...");

        try {
            reconocedor.startListening(intent);
        } catch (Exception e) {
            finalizarEscucha();
            txtEstado.setText("No se pudo iniciar el reconocimiento");
            txtRespuesta.setText(e.getMessage());
        }
    }

    private void cancelarEscucha() {

        if (!escuchando || reconocedor == null) return;

        reconocedor.cancel();
        finalizarEscucha();
        txtEstado.setText("Escucha cancelada");

        if (esperandoRespuesta != null) {
            esperandoRespuesta = null;
            responder("Acción cancelada", null);
        }
    }

    private void finalizarEscucha() {

        escuchando = false;
        btnEscuchar.setText("TOCA PARA HABLAR");
        btnEscuchar.setEnabled(reconocedor != null && !destruida);
        btnCancelar.setEnabled(false);
    }

    // --------------------------------
    // 5. PREGUNTAS Y CONFIRMACIONES
    // --------------------------------

    /** Hace una pregunta y escucha la respuesta automáticamente. */
    private void preguntar(String pregunta, Respuesta alResponder) {
        esperandoRespuesta = alResponder;
        responder(pregunta, this::solicitarOEscuchar);
    }

    /** Pide un "sí" antes de una acción delicada (enviar, llamar a emergencias, borrar). */
    private void confirmar(String pregunta, Runnable siAcepta) {

        preguntar(pregunta + " Di sí o no.", (original, c) -> {
            if (c.matches("^(si|claro|correcto|ok|okay|de acuerdo|adelante|hazlo|confirmo"
                    + "|confirmar|envialo|enviar|llama|llamale|afirmativo|esta bien|va|dale"
                    + "|borra|borralas)\\b.*")) {
                siAcepta.run();
            } else if (c.matches("^(no|cancela|cancelar|negativo|mejor no|olvidalo|detente"
                    + "|alto)\\b.*")) {
                responder("De acuerdo, cancelado.");
            } else {
                responder("No entendí la respuesta, así que cancelé la acción.");
            }
        });
    }

    // --------------------------------
    // 6. INTERPRETAR LOS COMANDOS
    // --------------------------------
    private void procesarFrases(List<String> frases) {

        String principal = frases.get(0);
        txtReconocido.setText(principal);

        if (esperandoRespuesta != null) {
            Respuesta r = esperandoRespuesta;
            esperandoRespuesta = null;
            r.recibir(principal, TextoUtil.normalizar(principal));
            return;
        }

        // Se prueban las alternativas del reconocedor hasta que una sea un comando
        for (String frase : frases) {
            if (ejecutar(frase)) {
                txtReconocido.setText(frase);
                return;
            }
        }

        responder("No entendí: " + principal
                + ". Di ayuda para conocer los comandos.");
    }

    private boolean coincide(String comando, String regex) {
        m = Pattern.compile(regex).matcher(comando);
        return m.find();
    }

    /** @return false si la frase no corresponde a ningún comando. */
    private boolean ejecutar(String frase) {

        String c = TextoUtil.quitarMuletillas(TextoUtil.normalizar(frase));
        if (c.isEmpty()) return false;

        // Impedir ejecutar órdenes negadas ("no llames a Juan")
        if (c.matches("^(no|nunca|jamas|evita|evitar)( .*)?$")) {
            responder("Detecté una negación. No ejecutaré la acción.");
            return true;
        }

        return comandosEmergencia(c)
                || comandosBasicos(c)
                || comandosInformacion(c)
                || comandosLlamadas(c)
                || comandosMensajes(c, frase)
                || comandosAlarmas(c)
                || comandosMusica(c)
                || comandosCalculadora(c)
                || comandosNotas(c, frase)
                || comandosPantalla(c)
                || comandosTelefono(c)
                || comandosInternet(c);
    }

    // ---------- Emergencia ----------
    private boolean comandosEmergencia(String c) {

        if (c.matches("^(emergencia|auxilio|socorro|ayuda urgente|ayudame|ayuda por favor"
                + "|llama a emergencias|llamar a emergencias|es una emergencia)$")) {

            String nombre = prefs.getString(PREF_EMERGENCIA_NOMBRE, null);
            String numero = prefs.getString(PREF_EMERGENCIA_NUMERO, null);

            if (numero != null) {
                confirmar("¿Llamo a tu contacto de emergencia, " + nombre + "?",
                        () -> llamarNumero(numero, nombre));
            } else {
                confirmar("No tienes contacto de emergencia guardado. "
                        + "¿Abro el marcador con el 9 1 1?",
                        () -> llamarNumero("911", "911"));
            }
            return true;
        }

        if (coincide(c, "^(?:mi contacto de emergencia es|(?:guarda|guardar|configura"
                + "|configurar|establece|cambia|cambiar|pon|agrega)(?: a)?(?: mi| el| como)?"
                + " contacto de emergencia(?: a| como| es)?) (.+)$")) {

            String nombre = m.group(1);
            conPermisos(new String[]{Manifest.permission.READ_CONTACTS},
                    "Para buscar el contacto necesito acceso a tus contactos.", () -> {
                        Contactos.Contacto ct = Contactos.buscar(this, nombre);
                        if (ct == null) {
                            responder("No encontré a " + nombre + " en tus contactos.");
                            return;
                        }
                        prefs.edit()
                                .putString(PREF_EMERGENCIA_NOMBRE, ct.nombre)
                                .putString(PREF_EMERGENCIA_NUMERO, ct.numero)
                                .apply();
                        responder("Listo. " + ct.nombre + " es tu contacto de emergencia. "
                                + "Di emergencia para llamarle.");
                    });
            return true;
        }

        if (coincide(c, "(quien|cual) es mi contacto de emergencia")) {
            String nombre = prefs.getString(PREF_EMERGENCIA_NOMBRE, null);
            responder(nombre == null
                    ? "No tienes contacto de emergencia. Di, por ejemplo: "
                    + "mi contacto de emergencia es mamá."
                    : "Tu contacto de emergencia es " + nombre + ".");
            return true;
        }

        return false;
    }

    // ---------- Básicos ----------
    private boolean comandosBasicos(String c) {

        if (c.matches("^(hola|hola cesba|buenos dias|buenas tardes|buenas noches|que tal"
                + "|hey|hola asistente)$")) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            String saludo = h < 12 ? "Buenos días" : (h < 20 ? "Buenas tardes" : "Buenas noches");
            responder(saludo + ". Bienvenido a CESBA Voice Control. ¿En qué te ayudo?");
            return true;
        }

        if (c.matches("^(ayuda|ayudame con los comandos|que puedes hacer|que sabes hacer"
                + "|comandos|instrucciones|opciones|como funciona|como te uso)$")) {
            responder(CatalogoComandos.ayudaGeneral());
            return true;
        }

        if (coincide(c, "^ayuda (?:de |con |para |sobre |en )?(?:la |las |los |el )?(.+)$")) {
            CatalogoComandos.Categoria cat = CatalogoComandos.buscar(m.group(1));
            responder(cat != null
                    ? CatalogoComandos.ayudaCategoria(cat)
                    : "No conozco esa categoría. " + CatalogoComandos.ayudaGeneral());
            return true;
        }

        if (coincide(c, "(lista|todos los|muestra los|mostrar los|ver los|ver) comandos")) {
            mostrarListaComandos();
            return true;
        }

        if (c.matches("^(repite|repitelo|repetir|otra vez|que dijiste|mande|como dijiste"
                + "|puedes repetir)$")) {
            responder(ultimaRespuesta.isEmpty() ? "Aún no he dicho nada." : ultimaRespuesta);
            return true;
        }

        if (c.matches("^(detente|para|alto|callate|silencio|basta|stop|deten|ya|ya basta"
                + "|guarda silencio)$")) {
            if (voz != null) voz.stop();
            accionTrasHablar = null;
            txtRespuesta.setText("Detenido.");
            return true;
        }

        if (coincide(c, "(habla|hablar|lee|leer)? ?mas (rapido|deprisa)|acelera la voz")) {
            cambiarVelocidad(0.25f);
            return true;
        }

        if (coincide(c, "(habla|hablar|lee|leer)? ?mas (lento|despacio)|mas lenta")) {
            cambiarVelocidad(-0.25f);
            return true;
        }

        if (coincide(c, "velocidad normal|habla normal")) {
            cambiarVelocidad(0);
            return true;
        }

        if (coincide(c, "^(activa|activar|enciende|inicia|iniciar|pon)( el)? modo"
                + " (continuo|conversacion|manos libres)")) {
            modoContinuo = true;
            prefs.edit().putBoolean(PREF_CONTINUO, true).apply();
            responder("Modo continuo activado. Volveré a escucharte después de cada respuesta.");
            return true;
        }

        if (coincide(c, "^(desactiva|desactivar|apaga|termina|quita|salir del|sal del)( el)?"
                + " modo (continuo|conversacion|manos libres)")) {
            modoContinuo = false;
            prefs.edit().putBoolean(PREF_CONTINUO, false).apply();
            responder("Modo continuo desactivado.");
            return true;
        }

        if (c.matches("^(salir|sal|cerrar aplicacion|cierra la aplicacion|cerrar la aplicacion"
                + "|adios|hasta luego|terminar|cierra la app)$")) {
            responder("Hasta luego.", this::finish);
            return true;
        }

        return false;
    }

    private void cambiarVelocidad(float delta) {

        float actual = prefs.getFloat(PREF_VELOCIDAD, 1.0f);
        float nueva = delta == 0 ? 1.0f : Math.max(0.5f, Math.min(2.5f, actual + delta));

        prefs.edit().putFloat(PREF_VELOCIDAD, nueva).apply();
        if (voz != null) voz.setSpeechRate(nueva);

        if (delta > 0 && nueva == actual) responder("Ya estoy hablando lo más rápido posible.");
        else if (delta < 0 && nueva == actual) responder("Ya estoy hablando lo más lento posible.");
        else responder("Velocidad de voz al " + Math.round(nueva * 100) + " por ciento.");
    }

    private void mostrarListaComandos() {

        txtComandos.setVisibility(View.VISIBLE);
        responder("La lista completa de comandos está en la pantalla. "
                + CatalogoComandos.ayudaGeneral());
    }

    // ---------- Información ----------
    private boolean comandosInformacion(String c) {

        if (coincide(c, "que horas? (es|son)|dime la hora|^hora$|^la hora$|me dices la hora"
                + "|que hora tienes")) {
            Calendar ahora = Calendar.getInstance();
            responder(TextoUtil.horaParaVoz(ahora.get(Calendar.HOUR_OF_DAY),
                    ahora.get(Calendar.MINUTE)));
            return true;
        }

        if (coincide(c, "que fecha|fecha de hoy|^fecha$|que dia es|a que estamos|en que dia"
                + "|que dia estamos|en que mes")) {
            String fecha = new SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", idioma)
                    .format(new Date());
            responder("Hoy es " + fecha);
            return true;
        }

        if (coincide(c, "bateria|cuanta carga|nivel de carga|\\bpila\\b")) {
            BatteryManager bm = getSystemService(BatteryManager.class);
            if (bm == null) {
                responder("No pude leer la batería.");
                return true;
            }
            int nivel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            String extra = bm.isCharging() ? " y se está cargando." : ".";
            if (!bm.isCharging() && nivel <= 15) extra = ". Te recomiendo conectar el cargador.";
            responder("Tienes " + nivel + " por ciento de batería" + extra);
            return true;
        }

        if (coincide(c, "estoy conectado|tengo internet|hay internet|tengo conexion"
                + "|estado de (la )?(red|conexion)|tengo senal|tengo datos|estoy en (el )?wifi"
                + "|a que red")) {
            informarConexion();
            return true;
        }

        if (c.matches("^(donde estoy|en donde estoy|cual es mi ubicacion|en que calle estoy"
                + "|cual es mi direccion|dime mi ubicacion|mi ubicacion|ubicacion actual"
                + "|donde me encuentro)$")) {
            conPermisos(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    "Para saber dónde estás necesito permiso de ubicación.", () -> {
                        responder("Buscando tu ubicación, espera un momento.", null);
                        Ubicacion.obtener(this, new Ubicacion.Callback() {
                            @Override
                            public void listo(Location u, String direccion) {
                                responder(direccion != null
                                        ? "Estás en " + direccion + "."
                                        : String.format(Locale.US,
                                        "No pude obtener la dirección. Tus coordenadas son "
                                                + "latitud %.4f, longitud %.4f.",
                                        u.getLatitude(), u.getLongitude()));
                            }

                            @Override
                            public void error(String mensaje) {
                                responder(mensaje);
                            }
                        });
                    });
            return true;
        }

        return false;
    }

    private void informarConexion() {

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network red = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities nc = red != null ? cm.getNetworkCapabilities(red) : null;

        if (nc == null) {
            responder("No tienes conexión a Internet.");
            return;
        }

        String tipo;
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) tipo = "por wifi";
        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) tipo = "por datos móviles";
        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) tipo = "por cable";
        else tipo = "";

        boolean internet = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        responder(internet
                ? "Sí, estás conectado a Internet " + tipo + "."
                : "Estás conectado " + tipo + ", pero parece que no hay Internet.");
    }

    // ---------- Llamadas ----------
    private boolean comandosLlamadas(String c) {

        if (coincide(c, "^(?:llamadas perdidas|tengo llamadas perdidas|quien me marco y no conteste"
                + "|lee (?:las |mis )?llamadas perdidas)$")) {
            leerLlamadas(true);
            return true;
        }

        if (coincide(c, "^(?:quien me (?:llamo|ha llamado|marco|hablo)|ultimas llamadas"
                + "|registro de llamadas|historial de llamadas|lee (?:mis |las )?(?:ultimas )?llamadas"
                + "|mis llamadas)$")) {
            leerLlamadas(false);
            return true;
        }

        if (coincide(c, "^(?:cual es |dime |dame |busca )?(?:el )?(?:numero|telefono)"
                + "(?: de telefono)? de (.+)$")) {
            String nombre = m.group(1);
            conPermisos(new String[]{Manifest.permission.READ_CONTACTS},
                    "Para buscar el número necesito acceso a tus contactos.", () -> {
                        Contactos.Contacto ct = Contactos.buscar(this, nombre);
                        responder(ct == null
                                ? "No encontré a " + nombre + " en tus contactos."
                                : "El número de " + ct.nombre + " es "
                                + TextoUtil.digitosParaVoz(ct.numero) + ".");
                    });
            return true;
        }

        if (coincide(c, "^(?:llama|llamar|llamale|marca|marcar|marcale|hablale"
                + "|telefonea|haz una llamada|hacer una llamada)(?: a| al| para)?"
                + "(?: (?:el )?(?:numero|telefono))? (.+)$")) {

            String destino = m.group(1);
            String numero = TextoUtil.extraerTelefono(destino);

            if (numero != null) {
                llamarNumero(numero, "el " + TextoUtil.digitosParaVoz(numero));
            } else {
                conPermisos(new String[]{Manifest.permission.READ_CONTACTS},
                        "Para llamar a tus contactos necesito acceso a ellos.", () -> {
                            Contactos.Contacto ct = Contactos.buscar(this, destino);
                            if (ct == null) {
                                responder("No encontré a " + destino + " en tus contactos.");
                            } else {
                                llamarNumero(ct.numero, ct.nombre);
                            }
                        });
            }
            return true;
        }

        return false;
    }

    private void leerLlamadas(boolean soloPerdidas) {

        conPermisos(new String[]{Manifest.permission.READ_CALL_LOG},
                "Para leer tus llamadas necesito acceso al registro de llamadas.", () -> {
                    List<String> lista = Contactos.ultimasLlamadas(this, soloPerdidas, 3);
                    if (lista.isEmpty()) {
                        responder(soloPerdidas
                                ? "No tienes llamadas perdidas."
                                : "No hay llamadas en el registro.");
                    } else {
                        responder(String.join(". ", lista) + ".");
                    }
                });
    }

    private void llamarNumero(String numero, String quien) {

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            responder("Este dispositivo no puede hacer llamadas.");
            return;
        }

        String limpio = numero.replaceAll("[^0-9+*#]", "");
        Uri uri = Uri.parse("tel:" + Uri.encode(limpio));

        // Android solo permite que el marcador del sistema llame a emergencias
        if (limpio.matches("911|112|066|060|065|080")) {
            abrir(new Intent(Intent.ACTION_DIAL, uri));
            responder("Abrí el marcador con el " + TextoUtil.digitosParaVoz(limpio)
                    + ". Presiona el botón verde de llamar, abajo al centro de la pantalla.", null);
            return;
        }

        conPermisos(new String[]{Manifest.permission.CALL_PHONE},
                "Para hacer llamadas necesito tu permiso.",
                () -> responder("Llamando a " + quien + ".", () -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_CALL, uri));
                    } catch (Exception e) {
                        responder("No pude hacer la llamada.");
                    }
                }));
    }

    // ---------- Mensajes ----------
    private boolean comandosMensajes(String c, String frase) {

        if (coincide(c, "^(?:lee|leer|leeme|lea)(?: mis| los)?(?: ultimos)? (?:mensajes?|sms)"
                + "|^(?:tengo|hay) mensajes|^mensajes nuevos$|^leer mensaje$")) {
            conPermisos(new String[]{Manifest.permission.READ_SMS,
                            Manifest.permission.READ_CONTACTS},
                    "Para leer tus mensajes necesito acceso a ellos y a tus contactos.", () -> {
                        List<String> lista = Contactos.ultimosMensajes(this, 3);
                        responder(lista.isEmpty()
                                ? "No tienes mensajes."
                                : String.join(". ", lista));
                    });
            return true;
        }

        if (coincide(c, "^(?:comparte|compartir|manda|mandar|envia|enviar|mandale|enviale)"
                + " (?:mi )?ubicacion (?:a|con|para) (.+)$")) {
            compartirUbicacion(m.group(1));
            return true;
        }

        if (!coincide(c, "^(?:manda|mandar|envia|enviar|escribe|escribir|escribele|mandale"
                + "|enviale|manda le)(?: un| una| el)? (mensaje de texto|mensaje por whatsapp"
                + "|mensaje de whatsapp|mensaje|sms|whatsapp|wasap|guasap|whats)"
                + " (?:a|para|al) (.+)$")) {
            return false;
        }

        boolean whatsapp = !m.group(1).startsWith("mensaje") || m.group(1).contains("whatsapp");
        String resto = m.group(2);

        // Separar destinatario y texto
        Matcher sep = Pattern.compile(
                "^(.+?) (?:diciendo|diciendole|que diga|que dice|con el texto|que)(?: (.*))?$")
                .matcher(resto);

        String nombre = sep.matches() ? sep.group(1) : resto;
        if (nombre.endsWith(" por whatsapp")) {
            whatsapp = true;
            nombre = nombre.substring(0, nombre.length() - " por whatsapp".length());
        }

        String texto = TextoUtil.extraerOriginal(frase,
                "\\b(?:diciendo|diciendole|que diga|que dice|con el texto)[\\s,:]+(.+)$");
        if (texto == null && sep.matches() && sep.group(2) != null) {
            texto = TextoUtil.extraerOriginal(frase,
                    "\\b(?:a|para|al)\\s+\\S.*?\\s+que[\\s,:]+(.+)$");
        }

        String destino = nombre;
        boolean porWhatsapp = whatsapp;

        if (texto == null || texto.isEmpty()) {
            preguntar("¿Qué quieres que diga el mensaje?",
                    (original, norm) -> prepararMensaje(destino, original, porWhatsapp));
        } else {
            prepararMensaje(destino, texto, porWhatsapp);
        }
        return true;
    }

    private void prepararMensaje(String destino, String texto, boolean whatsapp) {

        String numeroDictado = TextoUtil.extraerTelefono(TextoUtil.normalizar(destino));

        Runnable conContacto = () -> {
            String numero;
            String nombre;

            if (numeroDictado != null) {
                numero = numeroDictado;
                nombre = "el " + TextoUtil.digitosParaVoz(numero);
            } else {
                Contactos.Contacto ct = Contactos.buscar(this, destino);
                if (ct == null) {
                    responder("No encontré a " + destino + " en tus contactos.");
                    return;
                }
                numero = ct.numero;
                nombre = ct.nombre;
            }

            if (whatsapp) {
                abrirWhatsapp(numero, texto);
            } else {
                confirmar("Voy a enviar a " + nombre + ": " + texto + ". ¿Lo envío?",
                        () -> enviarSms(numero, nombre, texto));
            }
        };

        if (numeroDictado != null) conContacto.run();
        else conPermisos(new String[]{Manifest.permission.READ_CONTACTS},
                "Para buscar el contacto necesito acceso a tus contactos.", conContacto);
    }

    @SuppressWarnings("deprecation")
    private void enviarSms(String numero, String nombre, String texto) {

        conPermisos(new String[]{Manifest.permission.SEND_SMS},
                "Para enviar mensajes necesito tu permiso.", () -> {
                    try {
                        SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                ? getSystemService(SmsManager.class)
                                : SmsManager.getDefault();
                        ArrayList<String> partes = sms.divideMessage(texto);
                        sms.sendMultipartTextMessage(numero, null, partes, null, null);
                        responder("Mensaje enviado a " + nombre + ".");
                    } catch (Exception e) {
                        responder("No pude enviar el mensaje.");
                    }
                });
    }

    private void abrirWhatsapp(String numero, String texto) {

        String digitos = numero.replaceAll("\\D", "");
        // Números nacionales de 10 dígitos: se asume México (+52)
        if (digitos.length() == 10 && !numero.trim().startsWith("+")) digitos = "52" + digitos;

        Uri uri = Uri.parse("https://api.whatsapp.com/send?phone=" + digitos
                + "&text=" + Uri.encode(texto));

        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.setPackage("com.whatsapp");

        if (abrir(i) || abrir(new Intent(Intent.ACTION_VIEW, uri))) {
            responder("Abrí WhatsApp con el mensaje escrito. Para enviarlo, presiona el botón "
                    + "enviar, abajo a la derecha.", null);
        } else {
            responder("No encontré WhatsApp en este teléfono.");
        }
    }

    private void compartirUbicacion(String destino) {

        conPermisos(new String[]{Manifest.permission.READ_CONTACTS},
                "Para buscar el contacto necesito acceso a tus contactos.", () -> {
                    Contactos.Contacto ct = Contactos.buscar(this, destino);
                    if (ct == null) {
                        responder("No encontré a " + destino + " en tus contactos.");
                        return;
                    }

                    confirmar("¿Envío tu ubicación por mensaje a " + ct.nombre + "?", () ->
                            conPermisos(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.SEND_SMS},
                                    "Necesito permiso de ubicación y de mensajes.", () -> {
                                        responder("Buscando tu ubicación.", null);
                                        Ubicacion.obtener(this, new Ubicacion.Callback() {
                                            @Override
                                            public void listo(Location u, String direccion) {
                                                String texto = String.format(Locale.US,
                                                        "Esta es mi ubicación: "
                                                                + "https://maps.google.com/?q=%.6f,%.6f",
                                                        u.getLatitude(), u.getLongitude());
                                                if (direccion != null) {
                                                    texto += " (" + direccion + ")";
                                                }
                                                enviarSms(ct.numero, ct.nombre, texto);
                                            }

                                            @Override
                                            public void error(String mensaje) {
                                                responder(mensaje);
                                            }
                                        });
                                    }));
                });
    }

    // ---------- Alarmas y temporizadores ----------
    private boolean comandosAlarmas(String c) {

        if (c.matches("^(?:muestra|mostrar|ver|abre|abrir|cuales son|consulta|revisa)"
                + "(?: mis| las)? alarmas$|^mis alarmas$")) {
            if (abrir(new Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
                responder("Abriendo tus alarmas.");
            } else {
                responder("No encontré la aplicación de reloj.");
            }
            return true;
        }

        if (coincide(c, "temporizador|cronometro|cuenta regresiva|avisame en|timer")) {
            int segundos = TextoUtil.extraerDuracionSegundos(c);
            if (segundos > 0) {
                ponerTemporizador(segundos);
            } else {
                preguntar("¿De cuánto tiempo quieres el temporizador?", (o, r) -> {
                    int s = TextoUtil.extraerDuracionSegundos(r);
                    if (s > 0) ponerTemporizador(s);
                    else responder("No entendí la duración. Di, por ejemplo: "
                            + "pon un temporizador de 5 minutos.");
                });
            }
            return true;
        }

        if (coincide(c, "alarma|despiertame|despertador|levantame")) {

            // "en 20 minutos" -> hora actual + duración
            if (coincide(c, "\\ben (\\d|un|una|media)")) {
                int segundos = TextoUtil.extraerDuracionSegundos(c);
                if (segundos > 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.SECOND, segundos);
                    ponerAlarma(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                    return true;
                }
            }

            int[] hora = TextoUtil.extraerHora(c, Calendar.getInstance());
            if (hora != null) {
                ponerAlarma(hora[0], hora[1]);
            } else {
                preguntar("¿A qué hora quieres la alarma?", (o, r) -> {
                    String t = r.matches("^(a|para) las? .*") ? r : "a las " + r;
                    int[] h = TextoUtil.extraerHora(t, Calendar.getInstance());
                    if (h != null) ponerAlarma(h[0], h[1]);
                    else responder("No entendí la hora. Di, por ejemplo: "
                            + "pon una alarma a las 7 de la mañana.");
                });
            }
            return true;
        }

        return false;
    }

    private void ponerAlarma(int hora, int minuto) {

        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hora)
                .putExtra(AlarmClock.EXTRA_MINUTES, minuto)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "VozReco")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);

        if (abrir(i)) {
            responder("Alarma programada para " + TextoUtil.horaTexto(hora, minuto) + ".");
        } else {
            responder("No encontré la aplicación de reloj para poner la alarma.");
        }
    }

    private void ponerTemporizador(int segundos) {

        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, segundos)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "VozReco")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);

        if (abrir(i)) {
            responder("Temporizador de " + TextoUtil.duracionParaVoz(segundos) + " iniciado.");
        } else {
            responder("No encontré la aplicación de reloj para el temporizador.");
        }
    }

    // ---------- Música ----------
    private boolean comandosMusica(String c) {

        if (coincide(c, "^(?:busca|buscar|pon|poner|reproduce|reproducir|ver|mira|toca)"
                + " (.+) en youtube$")) {
            String q = m.group(1);
            Intent yt = new Intent(Intent.ACTION_SEARCH)
                    .setPackage("com.google.android.youtube")
                    .putExtra("query", q);
            if (abrir(yt) || abrir(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.youtube.com/results?search_query=" + Uri.encode(q))))) {
                responder("Buscando " + q + " en YouTube.");
            } else {
                responder("No encontré YouTube.");
            }
            return true;
        }

        if (c.matches("^(?:pon|poner|reproduce|reproducir|toca|escuchar|abre|abrir|quiero escuchar)"
                + "(?: la| algo de| un poco de)? musica$|^musica$")) {
            Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MUSIC);
            if (abrir(i)) responder("Abriendo tu aplicación de música.");
            else responder("No encontré una aplicación de música.");
            return true;
        }

        if (c.matches("^(pausa|pausar|pausa la musica|pausa la cancion|deten la musica"
                + "|para la musica|detener la musica|quita la musica)$")) {
            teclaMultimedia(KeyEvent.KEYCODE_MEDIA_PAUSE);
            responder("Música en pausa.", null);
            return true;
        }

        if (c.matches("^(continua|continuar|reanuda|reanudar|sigue|play|continua la musica"
                + "|reanuda la musica|reproduce la musica|sigue la musica|quita la pausa)$")) {
            teclaMultimedia(KeyEvent.KEYCODE_MEDIA_PLAY);
            return true;
        }

        if (c.matches("^((la )?(siguiente|proxima|otra) cancion|siguiente|cambia de cancion"
                + "|cambiar cancion|adelanta la cancion|salta la cancion)$")) {
            teclaMultimedia(KeyEvent.KEYCODE_MEDIA_NEXT);
            return true;
        }

        if (c.matches("^((la )?cancion anterior|anterior|regresa la cancion"
                + "|la cancion de antes)$")) {
            teclaMultimedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return true;
        }

        return false;
    }

    private void teclaMultimedia(int codigo) {

        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, codigo));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, codigo));
        txtRespuesta.setText("Control de música enviado.");
    }

    // ---------- Calculadora ----------
    private boolean comandosCalculadora(String c) {

        if (c.matches("^(abre|abrir|muestra|mostrar|ver)( la)? calculadora$|^calculadora$")) {
            panelCalculadora.setVisibility(View.VISIBLE);
            responder("Calculadora abierta. También puedes preguntarme, por ejemplo: "
                    + "cuánto es 8 por 7.");
            return true;
        }

        if (c.matches("^(cierra|cerrar|oculta|quita)( la)? calculadora$")) {
            panelCalculadora.setVisibility(View.GONE);
            responder("Calculadora cerrada");
            return true;
        }

        Double r = TextoUtil.calcular(c);
        if (r == null) return false;

        if (r.isNaN() || r.isInfinite()) {
            responder("No se puede calcular eso. Recuerda que no se puede dividir entre cero.");
        } else {
            responder("El resultado es " + TextoUtil.formatearNumero(r));
        }
        return true;
    }

    private void operarCalculadora(char op) {

        try {
            double a = Double.parseDouble(numA.getText().toString().trim());
            double b = Double.parseDouble(numB.getText().toString().trim());

            if (op == '/' && b == 0) {
                responder("No se puede dividir entre cero");
                return;
            }

            double resultado;
            switch (op) {
                case '+':
                    resultado = a + b;
                    break;
                case '-':
                    resultado = a - b;
                    break;
                case '*':
                    resultado = a * b;
                    break;
                default:
                    resultado = a / b;
            }

            responder("El resultado es " + TextoUtil.formatearNumero(resultado));

        } catch (NumberFormatException e) {
            responder("Introduce dos números válidos");
        }
    }

    // ---------- Notas ----------
    private boolean comandosNotas(String c, String frase) {

        if (c.matches("^(?:lee|leer|leeme|dime|cuales son)(?: mis| las)? notas$|^mis notas$")) {
            JSONArray notas = leerNotas();
            if (notas.length() == 0) {
                responder("No tienes notas guardadas.");
                return true;
            }
            StringBuilder sb = new StringBuilder("Tienes " + notas.length()
                    + (notas.length() == 1 ? " nota. " : " notas. "));
            for (int i = 0; i < notas.length(); i++) {
                sb.append("Nota ").append(i + 1).append(": ")
                        .append(notas.optString(i)).append(". ");
            }
            responder(sb.toString().trim());
            return true;
        }

        if (c.matches("^(?:borra|borrar|elimina|eliminar)(?: todas)?(?: mis| las)? notas$")) {
            int total = leerNotas().length();
            if (total == 0) {
                responder("No tienes notas guardadas.");
            } else {
                confirmar("¿Borro tus " + total + (total == 1 ? " nota?" : " notas?"), () -> {
                    prefs.edit().remove(PREF_NOTAS).apply();
                    responder("Notas borradas.");
                });
            }
            return true;
        }

        if (c.matches("^(?:borra|borrar|elimina|eliminar) (?:la )?ultima nota$")) {
            JSONArray notas = leerNotas();
            if (notas.length() == 0) {
                responder("No tienes notas guardadas.");
            } else {
                String ultima = notas.optString(notas.length() - 1);
                notas.remove(notas.length() - 1);
                prefs.edit().putString(PREF_NOTAS, notas.toString()).apply();
                responder("Borré la nota: " + ultima + ".");
            }
            return true;
        }

        if (coincide(c, "^(?:anota|apunta|toma nota|toma una nota|guarda una nota|crea una nota"
                + "|recuerda|recuerdame|nota)(?: que| de)? (.+)$")) {

            String texto = TextoUtil.extraerOriginal(frase,
                    "\\b(?:anota|apunta|toma nota|toma una nota|guarda una nota|crea una nota"
                            + "|recuerda|recuerdame|nota)(?: que| de)?[\\s,:]+(.+)$");
            if (texto == null) texto = m.group(1);

            JSONArray notas = leerNotas();
            notas.put(texto);
            prefs.edit().putString(PREF_NOTAS, notas.toString()).apply();
            responder("Anotado: " + texto + ". Di lee mis notas para escucharlas.");
            return true;
        }

        return false;
    }

    private JSONArray leerNotas() {
        try {
            return new JSONArray(prefs.getString(PREF_NOTAS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    // ---------- Pantalla ----------
    private boolean comandosPantalla(String c) {

        if (coincide(c, "^(?:cambia|cambiar|pon|poner)(?: el)?(?: color| fondo)?(?: de la pantalla)?"
                + "(?: a| en)? (azul|rojo|verde|negro|morado)$")) {
            switch (m.group(1)) {
                case "rojo":
                    aplicarColores(Color.rgb(126, 34, 52), Color.WHITE);
                    break;
                case "verde":
                    aplicarColores(Color.rgb(20, 83, 45), Color.WHITE);
                    break;
                case "negro":
                    aplicarColores(Color.BLACK, Color.WHITE);
                    break;
                case "morado":
                    aplicarColores(Color.rgb(76, 29, 109), Color.WHITE);
                    break;
                default:
                    aplicarColores(Color.rgb(24, 42, 87), Color.WHITE);
            }
            responder("Color " + m.group(1) + " aplicado");
            return true;
        }

        if (coincide(c, "alto contraste|contraste alto")) {
            aplicarColores(Color.BLACK, Color.YELLOW);
            responder("Alto contraste activado: fondo negro y letras amarillas.");
            return true;
        }

        return false;
    }

    private void aplicarColores(int fondo, int texto) {

        mainPanel.setBackgroundColor(fondo);
        findViewById(R.id.raiz).setBackgroundColor(fondo);

        int[] ids = {R.id.txtTitulo, R.id.txtSubtitulo, R.id.txtReconocido,
                R.id.txtRespuesta, R.id.txtComandos, R.id.txtNota};
        for (int id : ids) ((TextView) findViewById(id)).setTextColor(texto);
    }

    // ---------- Teléfono ----------
    private boolean comandosTelefono(String c) {

        // Linterna
        if (coincide(c, "^(?:apaga|apagar|desactiva|quita)(?: la)? (?:linterna|lampara|luz)")) {
            linterna(false);
            return true;
        }
        if (coincide(c, "^(?:enciende|encender|prende|prender|activa|abre|pon)(?: la)?"
                + " (?:linterna|lampara|luz)|^linterna$")) {
            linterna(true);
            return true;
        }

        // Volumen
        if (coincide(c, "volumen")) {
            int stream = coincide(c, "timbre|tono|llamada|ringtone")
                    ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC;

            if (coincide(c, "(\\d+)(?: por ciento)?$")) {
                volumenPorcentaje(stream, Integer.parseInt(m.group(1)));
            } else if (coincide(c, "maximo|tope|todo")) {
                volumenPorcentaje(stream, 100);
            } else if (coincide(c, "minimo")) {
                volumenPorcentaje(stream, 10);
            } else if (coincide(c, "sube|subir|aumenta|aumentar|mas alto|subele")) {
                ajustarVolumen(stream, AudioManager.ADJUST_RAISE);
            } else if (coincide(c, "baja|bajar|disminuye|disminuir|mas bajo|bajale")) {
                ajustarVolumen(stream, AudioManager.ADJUST_LOWER);
            } else {
                informarVolumen(stream);
            }
            return true;
        }
        if (c.matches("^(sube|subele|mas fuerte|habla mas fuerte)$")) {
            ajustarVolumen(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE);
            return true;
        }
        if (c.matches("^(baja|bajale|mas bajo|habla mas bajo|mas quedito)$")) {
            ajustarVolumen(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER);
            return true;
        }

        // Modo de timbre
        if (coincide(c, "modo (silencio|silencioso|vibracion|vibrar)|^vibrar$|pon(?:lo|el telefono)?"
                + " en (?:silencio|vibracion)|silencia (?:el )?(?:telefono|celular)")) {
            modoTimbre(AudioManager.RINGER_MODE_VIBRATE,
                    "Modo vibración activado. El teléfono no sonará, solo vibrará.");
            return true;
        }
        if (coincide(c, "modo (sonido|normal|timbre)|quita (?:el )?(?:modo )?(?:silencio|vibracion)"
                + "|activa (?:el )?sonido")) {
            modoTimbre(AudioManager.RINGER_MODE_NORMAL, "Modo sonido activado.");
            return true;
        }

        // Cámara
        if (c.matches("^(?:abre|abrir|inicia|iniciar)(?: la)? camara$|^(?:toma|tomar) (?:una )?foto$")) {
            if (abrir(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) {
                responder("Abriendo la cámara.");
            } else {
                responder("No encontré la cámara.");
            }
            return true;
        }

        // Wifi y bluetooth: Android ya no permite cambiarlos desde otras apps
        if (coincide(c, "^(?:activa|activar|desactiva|desactivar|enciende|encender|apaga|apagar"
                + "|prende|prender|conecta)(?: el)? (wifi|wi fi|bluetooth|datos)")) {
            String que = m.group(1);
            abrirAjustes(que.contains("blue") ? "bluetooth" : "wifi");
            return true;
        }

        // Ajustes
        if (coincide(c, "^(?:abre|abrir|ve a|ir a|muestra|mostrar|entra a)(?: los| la| el| las)?"
                + " (?:ajustes|configuracion|configuraciones|opciones)(?: de(?:l| la| los)? (.+))?$")) {
            abrirAjustes(m.group(1));
            return true;
        }

        // Cualquier aplicación instalada
        if (coincide(c, "^(?:abre|abrir|inicia|iniciar|ejecuta|lanza|entra a|entrar a)"
                + "(?: la aplicacion| la app| el| la| los)? (.+)$")) {
            abrirAplicacion(m.group(1));
            return true;
        }

        return false;
    }

    private void linterna(boolean encender) {

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            responder("Este teléfono no tiene linterna.");
            return;
        }

        try {
            CameraManager cm = getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    cm.setTorchMode(id, encender);
                    responder(encender ? "Linterna encendida." : "Linterna apagada.");
                    return;
                }
            }
            responder("No encontré la linterna.");
        } catch (Exception e) {
            responder("No pude controlar la linterna. Puede que otra app esté usando la cámara.");
        }
    }

    private String nombreStream(int stream) {
        return stream == AudioManager.STREAM_RING ? "del timbre" : "multimedia";
    }

    private void ajustarVolumen(int stream, int direccion) {

        AudioManager am = getSystemService(AudioManager.class);
        try {
            am.adjustStreamVolume(stream, direccion, AudioManager.FLAG_SHOW_UI);
            am.adjustStreamVolume(stream, direccion, 0);
            informarVolumen(stream);
        } catch (SecurityException e) {
            responder("No puedo cambiar el volumen mientras está activo No molestar.");
        }
    }

    private void volumenPorcentaje(int stream, int porcentaje) {

        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(stream);
        int nivel = Math.round(Math.max(0, Math.min(100, porcentaje)) * max / 100f);

        // Con volumen 0 el usuario ya no podría oír al asistente
        if (stream == AudioManager.STREAM_MUSIC && nivel == 0) nivel = 1;

        try {
            am.setStreamVolume(stream, nivel, AudioManager.FLAG_SHOW_UI);
            informarVolumen(stream);
        } catch (SecurityException e) {
            responder("No puedo cambiar el volumen mientras está activo No molestar.");
        }
    }

    private void informarVolumen(int stream) {

        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(stream);
        int actual = am.getStreamVolume(stream);
        int pct = max == 0 ? 0 : Math.round(actual * 100f / max);

        responder("Volumen " + nombreStream(stream) + " al " + pct + " por ciento.");
    }

    private void modoTimbre(int modo, String mensaje) {

        AudioManager am = getSystemService(AudioManager.class);
        try {
            am.setRingerMode(modo);
            responder(mensaje);
        } catch (SecurityException e) {
            responder("No tengo permiso para cambiar el modo de sonido mientras está activo "
                    + "No molestar.");
        }
    }

    private void abrirAjustes(String seccion) {

        String accion = Settings.ACTION_SETTINGS;
        String nombre = "los ajustes";
        Intent intent = null;

        if (seccion != null) {
            if (seccion.matches(".*(wifi|wi fi|internet|red).*")) {
                accion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                        : Settings.ACTION_WIFI_SETTINGS;
                nombre = "los ajustes de Internet y wifi";
            } else if (seccion.contains("bluetooth")) {
                accion = Settings.ACTION_BLUETOOTH_SETTINGS;
                nombre = "los ajustes de bluetooth";
            } else if (seccion.contains("accesibilidad") || seccion.contains("talkback")) {
                accion = Settings.ACTION_ACCESSIBILITY_SETTINGS;
                nombre = "los ajustes de accesibilidad";
            } else if (seccion.matches(".*(ubicacion|gps|localizacion).*")) {
                accion = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
                nombre = "los ajustes de ubicación";
            } else if (seccion.matches(".*(sonido|volumen|tono).*")) {
                accion = Settings.ACTION_SOUND_SETTINGS;
                nombre = "los ajustes de sonido";
            } else if (seccion.matches(".*(pantalla|brillo).*")) {
                accion = Settings.ACTION_DISPLAY_SETTINGS;
                nombre = "los ajustes de pantalla";
            } else if (seccion.matches(".*(bateria|energia).*")) {
                accion = Intent.ACTION_POWER_USAGE_SUMMARY;
                nombre = "los ajustes de batería";
            } else if (seccion.matches(".*(voz|texto a voz).*")) {
                accion = "com.android.settings.TTS_SETTINGS";
                nombre = "los ajustes de texto a voz";
            } else if (seccion.matches(".*(esta aplicacion|la aplicacion|permisos|vozreco|app).*")) {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                nombre = "los ajustes de esta aplicación. Busca la opción Permisos";
            } else if (seccion.matches(".*(aplicaciones|apps).*")) {
                accion = Settings.ACTION_APPLICATION_SETTINGS;
                nombre = "los ajustes de aplicaciones";
            }
        }

        if (intent == null) intent = new Intent(accion);

        if (abrir(intent) || abrir(new Intent(Settings.ACTION_SETTINGS))) {
            responder("Abriendo " + nombre + ".", null);
        } else {
            responder("No pude abrir los ajustes.");
        }
    }

    @SuppressWarnings("deprecation")
    private void abrirAplicacion(String nombre) {

        String buscado = nombre
                .replace("wasap", "whatsapp")
                .replace("guasap", "whatsapp")
                .replace("whats app", "whatsapp")
                .replace("feis", "facebook")
                .replace("mapas", "maps")
                .replace("correo", "gmail")
                .replace("navegador", "chrome")
                .replace("tienda", "play store");

        if (buscado.equals("whats")) buscado = "whatsapp";
        if (buscado.equals("face")) buscado = "facebook";

        Intent lanzador = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(lanzador, 0);

        ResolveInfo mejor = null;
        int mejorPuntaje = 0;
        String mejorNombre = null;

        for (ResolveInfo ri : apps) {
            String etiqueta = ri.loadLabel(getPackageManager()).toString();
            String n = TextoUtil.normalizar(etiqueta);
            int puntaje;

            if (n.equals(buscado)) puntaje = 3;
            else if (n.startsWith(buscado) || buscado.startsWith(n)) puntaje = 2;
            else if (n.contains(buscado)) puntaje = 1;
            else continue;

            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejor = ri;
                mejorNombre = etiqueta;
            }
        }

        if (mejor == null) {
            responder("No encontré la aplicación " + nombre + ".");
            return;
        }

        Intent i = getPackageManager().getLaunchIntentForPackage(mejor.activityInfo.packageName);
        if (i != null && abrir(i)) responder("Abriendo " + mejorNombre + ".", null);
        else responder("No pude abrir " + mejorNombre + ".");
    }

    // ---------- Internet y navegación ----------
    private boolean comandosInternet(String c) {

        if (coincide(c, "\\b(clima|va a llover|llovera|temperatura|pronostico)\\b"
                + "|^(?:como esta|que tal esta|como estara) el tiempo")) {
            buscarEnInternet("clima");
            return true;
        }

        if (coincide(c, "^(?:como llego|como llegar|como me voy|llevame|guiame|navega|navegar"
                + "|ruta|indicaciones|direcciones|quiero ir)(?: hacia| hasta| para ir| para llegar)?"
                + "(?: a| al)? (.+)$")) {
            String destino = m.group(1).replaceFirst("^(la|el|los|las) ", "");
            Intent nav = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=" + Uri.encode(destino) + "&mode=w"));
            if (abrir(nav) || abrir(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + Uri.encode(destino))))) {
                responder("Abriendo la navegación a pie hacia " + destino
                        + ". Sigue las indicaciones habladas de Maps.", null);
            } else {
                responder("No encontré una aplicación de mapas.");
            }
            return true;
        }

        if (coincide(c, "^(?:busca en internet|buscar en internet|busca en google|buscar en google"
                + "|busca|buscar|googlea|investiga|investigar) (.+)$")) {
            buscarEnInternet(m.group(1));
            return true;
        }

        if (coincide(c, "^(?:que es|que son|quien es|quien fue|que significa|que quiere decir)"
                + " .+$")) {
            buscarEnInternet(c);
            return true;
        }

        return false;
    }

    private void buscarEnInternet(String consulta) {

        Intent i = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, consulta);

        if (abrir(i) || abrir(new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/search?q=" + Uri.encode(consulta))))) {
            responder("Buscando " + consulta + " en Internet.", null);
        } else {
            responder("No encontré un navegador.");
        }
    }

    // --------------------------------
    // 7. UTILIDADES
    // --------------------------------
    private boolean abrir(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrar(long ms) {

        Vibrator v = getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    // --------------------------------
    // 8. RESPUESTAS HABLADAS
    // --------------------------------

    /** Responde y, en modo continuo, vuelve a escuchar al terminar. */
    private void responder(String mensaje) {
        responder(mensaje, modoContinuo ? this::solicitarOEscuchar : null);
    }

    /** Muestra y dice el mensaje; ejecuta despues cuando termine de hablar. */
    private void responder(String mensaje, Runnable despues) {

        if (destruida) return;

        ultimaRespuesta = mensaje;
        txtRespuesta.setText(mensaje);
        txtEstado.setText("Listo para escuchar");

        String id = "cesba-" + (++contadorFrases);
        idFraseActual = id;
        accionTrasHablar = despues;

        if (vozLista && voz != null) {
            voz.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, id);
        } else if (despues != null) {
            handler.postDelayed(() -> terminarFrase(id, true), 300);
        }
    }

    // --------------------------------
    // 9. LIBERAR RECURSOS
    // --------------------------------
    @Override
    protected void onDestroy() {

        destruida = true;
        handler.removeCallbacksAndMessages(null);

        if (reconocedor != null) {
            reconocedor.cancel();
            reconocedor.destroy();
            reconocedor = null;
        }

        if (voz != null) {
            voz.stop();
            voz.shutdown();
            voz = null;
        }

        super.onDestroy();
    }
}
