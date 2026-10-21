package com.example.vozreco;

/**
 * Lista de comandos de voz agrupados por categoría.
 * Se usa para la ayuda hablada y para la lista en pantalla.
 */
public final class CatalogoComandos {

    public static final class Categoria {
        public final String nombre;
        /** Palabra que el usuario dice después de "ayuda" (ya normalizada). */
        public final String clave;
        public final String[] ejemplos;

        Categoria(String nombre, String clave, String... ejemplos) {
            this.nombre = nombre;
            this.clave = clave;
            this.ejemplos = ejemplos;
        }
    }

    public static final Categoria[] CATEGORIAS = {

            new Categoria("Básicos", "basicos",
                    "Hola",
                    "Ayuda",
                    "Ayuda y el nombre de una categoría, por ejemplo: ayuda llamadas",
                    "Lista de comandos",
                    "Repite",
                    "Detente, o cállate",
                    "Habla más rápido",
                    "Habla más lento",
                    "Velocidad normal",
                    "Activa el modo continuo",
                    "Desactiva el modo continuo",
                    "Salir"),

            new Categoria("Información", "informacion",
                    "Qué hora es",
                    "Qué fecha es hoy",
                    "Qué día es hoy",
                    "Cuánta batería tengo",
                    "Estoy conectado a internet",
                    "Dónde estoy"),

            new Categoria("Llamadas", "llamadas",
                    "Llama a mamá",
                    "Llama al 55 1234 5678",
                    "Cuál es el número de Juan",
                    "Quién me llamó",
                    "Llamadas perdidas"),

            new Categoria("Mensajes", "mensajes",
                    "Manda un mensaje a Juan diciendo ya voy en camino",
                    "Lee mis mensajes",
                    "Manda un WhatsApp a Ana diciendo llego en 10 minutos",
                    "Comparte mi ubicación con mamá"),

            new Categoria("Emergencia", "emergencia",
                    "Emergencia, o auxilio",
                    "Mi contacto de emergencia es mamá",
                    "Quién es mi contacto de emergencia"),

            new Categoria("Teléfono", "telefono",
                    "Enciende la linterna",
                    "Apaga la linterna",
                    "Sube el volumen",
                    "Baja el volumen",
                    "Volumen al máximo",
                    "Volumen al 50 por ciento",
                    "Modo vibración",
                    "Modo sonido",
                    "Abre WhatsApp, o el nombre de cualquier aplicación",
                    "Abre la cámara",
                    "Abre los ajustes",
                    "Abre los ajustes de wifi",
                    "Abre los ajustes de bluetooth",
                    "Abre los ajustes de accesibilidad"),

            new Categoria("Alarmas", "alarmas",
                    "Pon una alarma a las 7 de la mañana",
                    "Despiértame a las 6 y media",
                    "Pon una alarma en 20 minutos",
                    "Pon un temporizador de 5 minutos",
                    "Muestra mis alarmas"),

            new Categoria("Música", "musica",
                    "Pon música",
                    "Pausa",
                    "Continúa la música",
                    "Siguiente canción",
                    "Canción anterior",
                    "Busca cumbias en YouTube"),

            new Categoria("Internet y navegación", "internet",
                    "Busca recetas de pozole",
                    "Cómo está el clima",
                    "Cómo llego al centro",
                    "Llévame a la farmacia más cercana"),

            new Categoria("Calculadora", "calculadora",
                    "Cuánto es 25 más 17",
                    "Cuánto es 8 por 7",
                    "Cuánto es 100 entre 4",
                    "Raíz cuadrada de 81",
                    "15 por ciento de 200",
                    "12 al cuadrado",
                    "Abrir calculadora"),

            new Categoria("Notas", "notas",
                    "Anota comprar leche",
                    "Recuerda que la cita es el lunes",
                    "Lee mis notas",
                    "Borra mis notas"),

            new Categoria("Pantalla", "pantalla",
                    "Cambiar a azul",
                    "Cambiar a rojo",
                    "Cambiar a verde",
                    "Cambiar a negro",
                    "Alto contraste"),
    };

    private CatalogoComandos() {}

    public static Categoria buscar(String claveNormalizada) {

        for (Categoria c : CATEGORIAS) {
            if (claveNormalizada.contains(c.clave)) return c;
        }
        // Sinónimos frecuentes
        if (claveNormalizada.contains("llamar")) return buscar("llamadas");
        if (claveNormalizada.contains("mensaje")) return buscar("mensajes");
        if (claveNormalizada.contains("alarma") || claveNormalizada.contains("temporizador"))
            return buscar("alarmas");
        if (claveNormalizada.contains("nota")) return buscar("notas");
        if (claveNormalizada.contains("navegacion") || claveNormalizada.contains("buscar"))
            return buscar("internet");
        if (claveNormalizada.contains("color")) return buscar("pantalla");
        if (claveNormalizada.contains("cuenta") || claveNormalizada.contains("matematica"))
            return buscar("calculadora");
        return null;
    }

    /** Texto hablado para "ayuda". */
    public static String ayudaGeneral() {

        StringBuilder sb = new StringBuilder(
                "Toca cualquier parte de la pantalla y di una orden. Las categorías son: ");

        for (int i = 0; i < CATEGORIAS.length; i++) {
            if (i > 0) sb.append(i == CATEGORIAS.length - 1 ? " y " : ", ");
            sb.append(CATEGORIAS[i].nombre);
        }

        sb.append(". Di ayuda y el nombre de una categoría para escuchar sus comandos. "
                + "Por ejemplo: ayuda llamadas.");

        return sb.toString();
    }

    /** Texto hablado para "ayuda <categoría>". */
    public static String ayudaCategoria(Categoria c) {

        StringBuilder sb = new StringBuilder("Comandos de " + c.nombre + ": ");
        for (int i = 0; i < c.ejemplos.length; i++) {
            if (i > 0) sb.append(". ");
            sb.append(c.ejemplos[i]);
        }
        return sb.append('.').toString();
    }

    /** Lista completa para mostrar en pantalla. */
    public static String listaCompleta() {

        StringBuilder sb = new StringBuilder();
        for (Categoria c : CATEGORIAS) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(c.nombre.toUpperCase(TextoUtil.IDIOMA));
            for (String e : c.ejemplos) sb.append("\n• ").append(e);
        }
        return sb.toString();
    }
}
