# CESBA VoiceControl — Lista de comandos de voz

Toca el botón amarillo grande (o cualquier zona libre de la pantalla) y di una orden.
No hace falta decirla exacta: se aceptan variantes como «oye…», «por favor…», «¿me puedes…?»,
y los números pueden decirse con palabras o dígitos.

Los comandos que envían algo o llaman a emergencias **piden confirmación**: el asistente
pregunta y escucha automáticamente tu «sí» o «no».

## Básicos
| Di… | Qué hace |
|---|---|
| Hola | Saludo según la hora del día |
| Ayuda | Dice las categorías disponibles |
| Ayuda llamadas *(o cualquier categoría)* | Lee los comandos de esa categoría |
| Lista de comandos | Muestra la lista completa en pantalla |
| Repite | Repite la última respuesta |
| Detente / cállate / silencio | Deja de hablar |
| Habla más rápido / más lento / velocidad normal | Cambia la velocidad de la voz (se guarda) |
| Activa / desactiva el modo continuo | Vuelve a escuchar solo después de cada respuesta (manos libres) |
| Salir | Cierra la aplicación |

## Información
| Di… | Qué hace |
|---|---|
| ¿Qué hora es? | «Son las 5 y 20 de la tarde» |
| ¿Qué fecha es hoy? / ¿Qué día es hoy? | Fecha completa |
| ¿Cuánta batería tengo? | Porcentaje y si está cargando |
| ¿Estoy conectado a Internet? | Wifi / datos / sin conexión |
| ¿Dónde estoy? | Dice la calle, colonia y ciudad actuales |

## Llamadas
| Di… | Qué hace |
|---|---|
| Llama a mamá | Busca el contacto y llama |
| Llama al 55 1234 5678 | Llama al número dictado |
| ¿Cuál es el número de Juan? | Lee el número dígito a dígito |
| ¿Quién me llamó? | Lee las últimas 3 llamadas |
| Llamadas perdidas | Lee las últimas 3 llamadas perdidas |

## Mensajes
| Di… | Qué hace |
|---|---|
| Manda un mensaje a Juan diciendo ya voy en camino | Lee el mensaje, pide confirmación y lo envía por SMS |
| Manda un mensaje a Juan | Pregunta qué debe decir el mensaje |
| Lee mis mensajes | Lee los últimos 3 SMS recibidos |
| Manda un WhatsApp a Ana diciendo llego en 10 minutos | Abre WhatsApp con el texto escrito (hay que pulsar Enviar) |
| Comparte mi ubicación con mamá | Envía por SMS un enlace de Google Maps con tu ubicación |

## Emergencia
| Di… | Qué hace |
|---|---|
| Emergencia / auxilio / ayuda urgente | Llama a tu contacto de emergencia (o abre el marcador con el 911) |
| Mi contacto de emergencia es mamá | Guarda el contacto de emergencia |
| ¿Quién es mi contacto de emergencia? | Lo dice |

## Teléfono
| Di… | Qué hace |
|---|---|
| Enciende / apaga la linterna | Controla el flash |
| Sube / baja el volumen | Ajusta el volumen multimedia |
| Volumen al máximo / al mínimo / al 50 por ciento | Fija el volumen |
| Sube el volumen del timbre | Ajusta el volumen de llamadas |
| Modo vibración / modo sonido | Cambia el modo de timbre |
| Abre WhatsApp *(o cualquier app instalada)* | Abre la aplicación |
| Abre la cámara | Abre la cámara |
| Abre los ajustes (de wifi, bluetooth, accesibilidad, ubicación, sonido, pantalla, batería, texto a voz) | Abre esa sección de Ajustes |
| Abre los ajustes de la aplicación | Para activar permisos negados |
| Enciende el wifi / bluetooth | Abre el panel correspondiente (Android no permite cambiarlo directamente) |

## Alarmas y temporizadores
| Di… | Qué hace |
|---|---|
| Pon una alarma a las 7 de la mañana | Crea la alarma sin abrir el reloj |
| Despiértame a las 6 y media | Si no dices mañana/tarde, elige la próxima vez que ocurra |
| Pon una alarma en 20 minutos | Alarma relativa a la hora actual |
| Pon un temporizador de 5 minutos | También «media hora», «1 hora y media» |
| Muestra mis alarmas | Abre la lista de alarmas |

## Música
| Di… | Qué hace |
|---|---|
| Pon música | Abre tu app de música |
| Pausa / continúa | Pausa o reanuda lo que suene |
| Siguiente canción / canción anterior | Cambia de pista |
| Busca cumbias en YouTube | Busca en YouTube |

## Internet y navegación
| Di… | Qué hace |
|---|---|
| Busca recetas de pozole | Búsqueda web |
| ¿Qué es un eclipse? / ¿Quién fue Benito Juárez? | Búsqueda web |
| ¿Cómo está el clima? | Búsqueda del clima |
| ¿Cómo llego al centro? / Llévame a la farmacia | Navegación **a pie** con indicaciones habladas en Maps |

## Calculadora
| Di… | Qué hace |
|---|---|
| ¿Cuánto es 25 más 17? | Suma, resta, multiplicación («por») y división («entre»), con prioridad de operaciones |
| Raíz cuadrada de 81 | |
| 15 por ciento de 200 | |
| 12 al cuadrado / al cubo | |
| Abrir calculadora / cerrar calculadora | Muestra la calculadora en pantalla |

## Notas
| Di… | Qué hace |
|---|---|
| Anota comprar leche | Guarda una nota (con acentos y ñ) |
| Recuérdame que la cita es el lunes | Guarda una nota |
| Lee mis notas | Las lee numeradas |
| Borra la última nota | |
| Borra mis notas | Pide confirmación |

## Pantalla
| Di… | Qué hace |
|---|---|
| Cambiar a azul / rojo / verde / negro / morado | Color de fondo |
| Alto contraste | Fondo negro con letras amarillas (baja visión) |

## Seguridad
Una orden que empieza con negación («no llames a Juan») **no se ejecuta**.

## Permisos
La app solo pide cada permiso la primera vez que usas un comando que lo necesita, y antes
explica en voz alta para qué es: micrófono, contactos, llamadas, SMS, registro de llamadas
y ubicación.

## Probar sin micrófono (solo builds de depuración)
```bash
adb shell am start -n com.example.vozreco/.MainActivity --es comando "\"que hora es\""
```
