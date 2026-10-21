# CESBA VoiceControl (VozReco)

Asistente de voz en español para Android pensado para que personas invidentes o con baja
visión puedan usar su smartphone: llamadas, mensajes, alarmas, ubicación, música, notas,
calculadora y más, todo con respuestas habladas.

- Botón grande que ocupa media pantalla; tocar cualquier zona libre también activa la escucha.
- Confirmación hablada («sí / no») antes de enviar mensajes o llamar a emergencias.
- Los permisos se piden solo cuando se usa un comando que los necesita, explicando para qué.
- Modo continuo (manos libres) y velocidad de voz ajustable.

Consulta la **[lista completa de comandos](COMANDOS.md)**.

## Requisitos
- Android 7.0 (API 24) o superior.
- Servicio de reconocimiento de voz (app de Google) y voz en español para Texto a voz.

## Compilar
```bash
./gradlew assembleDebug
```

## Pruebas
```bash
./gradlew testDebugUnitTest
```

En builds de depuración se puede enviar un comando sin micrófono:
```bash
adb shell am start -n com.example.vozreco/.MainActivity --es comando "\"que hora es\""
```
