package com.example.vozreco;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Funciones de texto sin dependencias de Android para que
 * puedan probarse con pruebas unitarias.
 */
public final class TextoUtil {

    public static final Locale IDIOMA = Locale.forLanguageTag("es-MX");

    private static final Map<String, Integer> NUMEROS = new HashMap<>();

    static {
        String[] unidades = {"cero", "uno", "dos", "tres", "cuatro",
                "cinco", "seis", "siete", "ocho", "nueve"};
        for (int i = 0; i < unidades.length; i++) NUMEROS.put(unidades[i], i);

        String[] especiales = {"diez", "once", "doce", "trece", "catorce",
                "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve",
                "veinte", "veintiuno", "veintidos", "veintitres", "veinticuatro",
                "veinticinco", "veintiseis", "veintisiete", "veintiocho",
                "veintinueve"};
        for (int i = 0; i < especiales.length; i++) NUMEROS.put(especiales[i], 10 + i);
        NUMEROS.put("veintiun", 21);

        String[] decenas = {"treinta", "cuarenta", "cincuenta", "sesenta",
                "setenta", "ochenta", "noventa"};
        for (int i = 0; i < decenas.length; i++) NUMEROS.put(decenas[i], 30 + i * 10);

        NUMEROS.put("cien", 100);
        NUMEROS.put("ciento", 100);
        String[] centenas = {"doscient", "trescient", "cuatrocient", "quinient",
                "seiscient", "setecient", "ochocient", "novecient"};
        int[] valores = {200, 300, 400, 500, 600, 700, 800, 900};
        for (int i = 0; i < centenas.length; i++) {
            NUMEROS.put(centenas[i] + "os", valores[i]);
            NUMEROS.put(centenas[i] + "as", valores[i]);
        }
        NUMEROS.put("mil", 1000);
    }

    private TextoUtil() {}

    // --------------------------------
    // NORMALIZACIÓN
    // --------------------------------

    /**
     * Pasa a minúsculas, quita acentos y signos, convierte símbolos
     * matemáticos en palabras y números escritos en palabras a dígitos.
     */
    public static String normalizar(String texto) {

        String t = texto.toLowerCase(IDIOMA);

        // Separadores de miles y decimales: 1,000 -> 1000 ; 3,5 -> 3.5
        t = t.replaceAll("(\\d),(\\d{3})(?!\\d)", "$1$2");
        t = t.replaceAll("(\\d),(\\d)", "$1.$2");

        // Símbolos matemáticos que el reconocedor suele escribir
        t = t.replace("+", " mas ")
                .replace("×", " por ")
                .replace("*", " por ")
                .replace("÷", " entre ")
                .replace("%", " por ciento ");
        t = t.replaceAll("(\\d)\\s*/\\s*(\\d)", "$1 entre $2");
        t = t.replaceAll("(\\d)\\s*-\\s*(\\d)", "$1 menos $2");
        t = t.replaceAll("(\\d)\\s*x\\s*(\\d)", "$1 por $2");

        t = Normalizer.normalize(t, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        t = t.replaceAll("[^a-z0-9. ]", " ");
        // Solo se conserva el punto decimal entre dígitos
        t = t.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        t = t.replaceAll("\\s+", " ").trim();

        return convertirNumeros(t);
    }

    /** Minúsculas sin acentos conservando la longitud y posiciones del texto. */
    public static String plano(String texto) {

        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            String d = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
            sb.append(Character.toLowerCase(d.isEmpty() ? ch : d.charAt(0)));
        }
        return sb.toString();
    }

    /**
     * Busca el regex (escrito en minúsculas sin acentos) y devuelve el grupo 1
     * tal como se dictó, conservando acentos, ñ y mayúsculas. Null si no coincide.
     */
    public static String extraerOriginal(String original, String regex) {

        Matcher m = Pattern.compile(regex).matcher(plano(original));
        if (!m.find() || m.group(1) == null) return null;

        String r = original.substring(m.start(1), m.end(1)).trim();
        return r.isEmpty() ? null : r;
    }

    /** Quita muletillas al inicio y al final: "oye", "por favor", "puedes"... */
    public static String quitarMuletillas(String comando) {

        String anterior;
        String t = comando;

        do {
            anterior = t;
            t = t.replaceFirst("^(oye|ok|okay|bueno|este|cesba|asistente"
                    + "|por favor|me puedes|me podrias|puedes|podrias"
                    + "|quiero que|quiero|necesito que|necesito"
                    + "|me gustaria|ayudame a) ", "");
            t = t.replaceFirst(" (por favor|porfa|gracias)$", "");
        } while (!t.equals(anterior) && !t.isEmpty());

        return t.trim();
    }

    /**
     * Convierte números escritos en palabras a dígitos:
     * "treinta y cinco" -> "35", "doscientos diez" -> "210".
     * Dígitos sueltos dichos uno tras otro ("cinco cinco") no se suman.
     */
    public static String convertirNumeros(String texto) {

        if (texto.isEmpty()) return texto;

        String[] tokens = texto.split(" ");
        List<String> salida = new ArrayList<>();

        int i = 0;
        while (i < tokens.length) {

            String tk = tokens[i];

            // "5 mil" -> 5000
            if (tk.matches("\\d+") && i + 1 < tokens.length
                    && tokens[i + 1].equals("mil")) {
                salida.add(String.valueOf(Long.parseLong(tk) * 1000));
                i += 2;
                continue;
            }

            boolean porCiento = tk.equals("ciento") && !salida.isEmpty()
                    && salida.get(salida.size() - 1).equals("por");

            if (!NUMEROS.containsKey(tk) || porCiento) {
                salida.add(tk);
                i++;
                continue;
            }

            long total = 0;
            int actual = 0;
            // Valor máximo (exclusivo) que puede sumarse al bloque actual
            int permitido = Integer.MAX_VALUE;

            while (i < tokens.length) {

                String t = tokens[i];

                if (t.equals("y") && permitido == 10 && i + 1 < tokens.length) {
                    Integer sig = NUMEROS.get(tokens[i + 1]);
                    if (sig != null && sig >= 1 && sig <= 9) {
                        actual += sig;
                        permitido = 0;
                        i += 2;
                        continue;
                    }
                    break;
                }

                Integer v = NUMEROS.get(t);
                if (v == null) break;

                if (v == 1000) {
                    if (total > 0) break;
                    total = (actual == 0 ? 1 : actual) * 1000L;
                    actual = 0;
                    permitido = 1000;
                    i++;
                    continue;
                }

                if (v >= permitido) break;

                actual += v;
                if (v >= 100) {
                    permitido = 100;
                } else if (v >= 30 && v % 10 == 0) {
                    permitido = 10; // solo puede seguir "y" + unidad
                } else {
                    permitido = 0;
                }
                i++;

                if (permitido == 10 && (i >= tokens.length || !tokens[i].equals("y"))) {
                    permitido = 0;
                }
            }

            salida.add(String.valueOf(total + actual));
        }

        return String.join(" ", salida);
    }

    /** Extrae los dígitos de un número de teléfono dictado. */
    public static String extraerTelefono(String texto) {

        String t = texto.replace(" menos ", " ");
        if (!t.matches("[\\d .]+")) return null;

        String digitos = t.replaceAll("\\D", "");
        return digitos.length() >= 3 ? digitos : null;
    }

    /** "5512345678" -> "5 5 1 2 3 4 5 6 7 8" para que el sintetizador lo lea dígito a dígito. */
    public static String digitosParaVoz(String numero) {

        StringBuilder sb = new StringBuilder();
        for (char c : numero.toCharArray()) {
            if (Character.isDigit(c)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // --------------------------------
    // HORAS
    // --------------------------------

    private static final Pattern PATRON_HORA = Pattern.compile(
            "\\b(?:a|para|de) las? (\\d{1,2})"
                    + "(?:(?: y)? (\\d{1,2})(?! (?:minuto|hora|segundo)))?"
                    + "(?: (y media|y cuarto|menos cuarto|en punto))?"
                    + "(?: (de la manana|de la tarde|de la noche|de la madrugada"
                    + "|del mediodia|am|pm|a m|p m))?\\b");

    /**
     * Busca una hora en frases como "a las 7 y media de la tarde".
     *
     * @return {hora 0-23, minuto} o null si no hay hora válida.
     */
    public static int[] extraerHora(String comando, Calendar ahora) {

        String t = comando.replaceAll("\\b(la|las) una\\b", "$1 1");

        Matcher m = PATRON_HORA.matcher(t);
        if (!m.find()) return null;

        int hora = Integer.parseInt(m.group(1));
        int minuto = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        String ajuste = m.group(3);
        String periodo = m.group(4);

        if (ajuste != null) {
            switch (ajuste) {
                case "y media":
                    minuto = 30;
                    break;
                case "y cuarto":
                    minuto = 15;
                    break;
                case "menos cuarto":
                    minuto = 45;
                    hora = hora == 1 ? 12 : hora - 1;
                    break;
                default:
                    break;
            }
        }

        if (hora > 23 || minuto > 59) return null;

        if (periodo != null) {
            boolean tarde = periodo.contains("tarde")
                    || periodo.contains("noche")
                    || periodo.contains("mediodia")
                    || periodo.startsWith("p");

            if (tarde) {
                if (hora < 12) hora += 12;
                else if (hora == 12 && periodo.contains("noche")) hora = 0;
            } else if (hora == 12) {
                hora = 0;
            }

        } else if (hora <= 12) {
            // Sin "de la mañana/tarde": elegir la próxima vez que ocurra
            int base = hora % 12;
            int ahoraMin = ahora.get(Calendar.HOUR_OF_DAY) * 60
                    + ahora.get(Calendar.MINUTE);
            int manana = base * 60 + minuto;
            int tarde = (base + 12) * 60 + minuto;

            if (manana > ahoraMin) hora = base;
            else if (tarde > ahoraMin) hora = base + 12;
            else hora = base;
        }

        return new int[]{hora, minuto};
    }

    /** Devuelve "las 7 y 30 de la mañana", "la 1 de la tarde", etc. */
    public static String horaTexto(int hora, int minuto) {

        int h12 = hora % 12 == 0 ? 12 : hora % 12;

        StringBuilder sb = new StringBuilder();
        sb.append(h12 == 1 ? "la 1" : "las " + h12);

        if (minuto == 15) sb.append(" y cuarto");
        else if (minuto == 30) sb.append(" y media");
        else if (minuto > 0) sb.append(" y ").append(minuto);

        if (hora < 6) sb.append(" de la madrugada");
        else if (hora < 12) sb.append(" de la mañana");
        else if (hora < 20) sb.append(" de la tarde");
        else sb.append(" de la noche");

        return sb.toString();
    }

    /** "Es la 1 y 5 de la tarde" o "Son las 8 de la noche". */
    public static String horaParaVoz(int hora, int minuto) {

        String texto = horaTexto(hora, minuto);
        return (texto.startsWith("la ") ? "Es " : "Son ") + texto;
    }

    // --------------------------------
    // DURACIONES
    // --------------------------------

    private static final Pattern PATRON_DURACION = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?|un|una|media) (hora|minuto|segundo)s?"
                    + "(?: y (media|medio))?\\b");

    /** "1 hora y media", "5 minutos", "media hora" -> segundos (0 si no hay). */
    public static int extraerDuracionSegundos(String comando) {

        Matcher m = PATRON_DURACION.matcher(comando);
        double total = 0;

        while (m.find()) {
            String cant = m.group(1);
            double n;
            if (cant.equals("un") || cant.equals("una")) n = 1;
            else if (cant.equals("media")) n = 0.5;
            else n = Double.parseDouble(cant);

            if (m.group(3) != null) n += 0.5;

            switch (m.group(2)) {
                case "hora":
                    total += n * 3600;
                    break;
                case "minuto":
                    total += n * 60;
                    break;
                default:
                    total += n;
            }
        }

        return (int) Math.round(total);
    }

    /** 5400 -> "1 hora y 30 minutos". */
    public static String duracionParaVoz(int segundos) {

        int h = segundos / 3600;
        int m = (segundos % 3600) / 60;
        int s = segundos % 60;

        List<String> partes = new ArrayList<>();
        if (h > 0) partes.add(h + (h == 1 ? " hora" : " horas"));
        if (m > 0) partes.add(m + (m == 1 ? " minuto" : " minutos"));
        if (s > 0) partes.add(s + (s == 1 ? " segundo" : " segundos"));

        if (partes.isEmpty()) return "0 segundos";
        if (partes.size() == 1) return partes.get(0);

        return String.join(", ", partes.subList(0, partes.size() - 1))
                + " y " + partes.get(partes.size() - 1);
    }

    // --------------------------------
    // CÁLCULOS
    // --------------------------------

    private static final String NUM = "(-?\\d+(?:\\.\\d+)?)";

    /**
     * Evalúa operaciones dictadas: "cuanto es 5 mas 3 por 2",
     * "raiz cuadrada de 81", "15 por ciento de 200", "7 al cuadrado".
     *
     * @return el resultado, NaN si hay división entre cero, o null si no es una operación.
     */
    public static Double calcular(String comando) {

        String t = comando
                .replaceFirst("^(cuanto es|cuanto son|cuanto da|cuanto|calcula|calcular"
                        + "|resuelve|dime cuanto es|dime) ", "")
                .trim();

        Matcher m;

        m = Pattern.compile("^raiz cuadrada de " + NUM + "$").matcher(t);
        if (m.matches()) {
            double n = Double.parseDouble(m.group(1));
            return n < 0 ? Double.NaN : Math.sqrt(n);
        }

        m = Pattern.compile("^" + NUM + " al (cuadrado|cubo)$").matcher(t);
        if (m.matches()) {
            double n = Double.parseDouble(m.group(1));
            return Math.pow(n, m.group(2).equals("cuadrado") ? 2 : 3);
        }

        m = Pattern.compile("^(?:el )?" + NUM + " por ciento de " + NUM + "$").matcher(t);
        if (m.matches()) {
            return Double.parseDouble(m.group(1))
                    * Double.parseDouble(m.group(2)) / 100.0;
        }

        t = t.replace("multiplicado por", "por")
                .replace("dividido entre", "entre")
                .replace("dividido por", "entre")
                .replace("dividido", "entre")
                .replace("sobre", "entre")
                .replace("y luego", "")
                .replaceAll("\\s+", " ").trim();

        if (t.isEmpty()) return null;

        String[] tk = t.split(" ");
        List<Double> numeros = new ArrayList<>();
        List<Character> ops = new ArrayList<>();

        boolean esperaNumero = true;
        boolean negativo = false;

        for (String s : tk) {
            if (esperaNumero) {
                if (s.equals("menos") && !negativo) {
                    negativo = true;
                    continue;
                }
                if (!s.matches("-?\\d+(?:\\.\\d+)?")) return null;
                double n = Double.parseDouble(s);
                numeros.add(negativo ? -n : n);
                negativo = false;
                esperaNumero = false;
            } else {
                char op;
                switch (s) {
                    case "mas":
                        op = '+';
                        break;
                    case "menos":
                        op = '-';
                        break;
                    case "por":
                    case "x":
                    case "multiplicado":
                        op = '*';
                        break;
                    case "entre":
                        op = '/';
                        break;
                    default:
                        return null;
                }
                ops.add(op);
                esperaNumero = true;
            }
        }

        if (esperaNumero || ops.isEmpty()) return null;

        // Primero multiplicaciones y divisiones
        List<Double> nums2 = new ArrayList<>();
        List<Character> ops2 = new ArrayList<>();
        nums2.add(numeros.get(0));

        for (int i = 0; i < ops.size(); i++) {
            char op = ops.get(i);
            double b = numeros.get(i + 1);
            if (op == '*' || op == '/') {
                double a = nums2.remove(nums2.size() - 1);
                if (op == '/' && b == 0) return Double.NaN;
                nums2.add(op == '*' ? a * b : a / b);
            } else {
                ops2.add(op);
                nums2.add(b);
            }
        }

        double resultado = nums2.get(0);
        for (int i = 0; i < ops2.size(); i++) {
            double b = nums2.get(i + 1);
            resultado = ops2.get(i) == '+' ? resultado + b : resultado - b;
        }

        return resultado;
    }

    /** 8.0 -> "8" ; 2.33333 -> "2.3333" */
    public static String formatearNumero(double n) {

        if (Math.abs(n - Math.rint(n)) < 1e-9 && Math.abs(n) < 1e15) {
            return String.valueOf((long) Math.rint(n));
        }

        String s = String.format(Locale.US, "%.4f", n);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
