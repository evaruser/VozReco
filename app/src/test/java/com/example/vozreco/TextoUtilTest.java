package com.example.vozreco;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class TextoUtilTest {

    private static Calendar a(int hora, int minuto) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hora);
        c.set(Calendar.MINUTE, minuto);
        return c;
    }

    @Test
    public void normaliza_acentos_y_signos() {
        assertEquals("que hora es", TextoUtil.normalizar("¿Qué hora es?"));
        assertEquals("manana", TextoUtil.normalizar("Mañana"));
        assertEquals("5 mas 3", TextoUtil.normalizar("5+3"));
        assertEquals("8 por 7", TextoUtil.normalizar("8 x 7"));
        assertEquals("100 entre 4", TextoUtil.normalizar("100/4"));
        assertEquals("3.5 mas 1000", TextoUtil.normalizar("3,5 + 1,000"));
        assertEquals("15 por ciento de 200", TextoUtil.normalizar("15% de 200"));
        assertEquals("a las 7 30 a m", TextoUtil.normalizar("a las 7:30 a.m."));
    }

    @Test
    public void convierte_numeros_en_palabras() {
        assertEquals("35", TextoUtil.convertirNumeros("treinta y cinco"));
        assertEquals("210", TextoUtil.convertirNumeros("doscientos diez"));
        assertEquals("2500", TextoUtil.convertirNumeros("dos mil quinientos"));
        assertEquals("132", TextoUtil.convertirNumeros("ciento treinta y dos"));
        assertEquals("5 5 1 2", TextoUtil.convertirNumeros("cinco cinco uno dos"));
        assertEquals("30 y media", TextoUtil.convertirNumeros("treinta y media"));
        assertEquals("pon una alarma a las 7", TextoUtil.convertirNumeros("pon una alarma a las siete"));
        assertEquals("5000", TextoUtil.convertirNumeros("5 mil"));
    }

    @Test
    public void quita_muletillas() {
        assertEquals("que hora es", TextoUtil.quitarMuletillas("oye me puedes decir que hora es"
                .replace("decir ", "")));
        assertEquals("llama a mama", TextoUtil.quitarMuletillas("por favor llama a mama por favor"));
    }

    @Test
    public void extrae_hora_con_periodo() {
        assertArrayEquals(new int[]{19, 30},
                TextoUtil.extraerHora("pon una alarma a las 7 y media de la tarde", a(10, 0)));
        assertArrayEquals(new int[]{7, 0},
                TextoUtil.extraerHora("alarma a las 7 de la manana", a(22, 0)));
        assertArrayEquals(new int[]{0, 0},
                TextoUtil.extraerHora("alarma a las 12 de la noche", a(10, 0)));
        assertArrayEquals(new int[]{6, 45},
                TextoUtil.extraerHora("despiertame a las 7 menos cuarto", a(22, 0)));
        assertArrayEquals(new int[]{13, 15},
                TextoUtil.extraerHora("alarma a la una y cuarto de la tarde", a(9, 0)));
        assertArrayEquals(new int[]{7, 30},
                TextoUtil.extraerHora(TextoUtil.normalizar("alarma a las 7:30 a.m."), a(22, 0)));
        assertArrayEquals(new int[]{18, 0},
                TextoUtil.extraerHora("alarma a las 18", a(9, 0)));
    }

    @Test
    public void extrae_hora_sin_periodo_elige_la_proxima() {
        // Son las 10 de la mañana: "a las 3" debe ser 15:00
        assertArrayEquals(new int[]{15, 0},
                TextoUtil.extraerHora("alarma a las 3", a(10, 0)));
        // Son las 5 de la mañana: "a las 6" es 6:00
        assertArrayEquals(new int[]{6, 0},
                TextoUtil.extraerHora("alarma a las 6", a(5, 0)));
        // Son las 23:00: "a las 7" es mañana a las 7:00
        assertArrayEquals(new int[]{7, 0},
                TextoUtil.extraerHora("alarma a las 7", a(23, 0)));
        assertNull(TextoUtil.extraerHora("pon una alarma", a(10, 0)));
    }

    @Test
    public void hora_para_voz() {
        assertEquals("Es la 1 y 5 de la tarde", TextoUtil.horaParaVoz(13, 5));
        assertEquals("Son las 8 de la noche", TextoUtil.horaParaVoz(20, 0));
        assertEquals("Son las 12 de la madrugada", TextoUtil.horaParaVoz(0, 0));
        assertEquals("las 7 y media de la mañana", TextoUtil.horaTexto(7, 30));
    }

    @Test
    public void duraciones() {
        assertEquals(300, TextoUtil.extraerDuracionSegundos("temporizador de 5 minutos"));
        assertEquals(1800, TextoUtil.extraerDuracionSegundos("temporizador de media hora"));
        assertEquals(5400, TextoUtil.extraerDuracionSegundos("temporizador de 1 hora y media"));
        assertEquals(3600, TextoUtil.extraerDuracionSegundos("temporizador de una hora"));
        assertEquals(90, TextoUtil.extraerDuracionSegundos("1 minuto y 30 segundos"));
        assertEquals(0, TextoUtil.extraerDuracionSegundos("temporizador"));
        assertEquals("1 hora y 30 minutos", TextoUtil.duracionParaVoz(5400));
        assertEquals("1 minuto y 30 segundos", TextoUtil.duracionParaVoz(90));
    }

    @Test
    public void calculos() {
        assertEquals(42.0, TextoUtil.calcular("cuanto es 25 mas 17"), 1e-9);
        assertEquals(11.0, TextoUtil.calcular("cuanto es 5 mas 3 por 2"), 1e-9);
        assertEquals(25.0, TextoUtil.calcular("100 entre 4"), 1e-9);
        assertEquals(9.0, TextoUtil.calcular("raiz cuadrada de 81"), 1e-9);
        assertEquals(30.0, TextoUtil.calcular("15 por ciento de 200"), 1e-9);
        assertEquals(144.0, TextoUtil.calcular("12 al cuadrado"), 1e-9);
        assertEquals(-2.0, TextoUtil.calcular("menos 5 mas 3"), 1e-9);
        assertEquals(10.0, TextoUtil.calcular("cuanto es 20 dividido entre 2"), 1e-9);
        assertEquals(42.0, TextoUtil.calcular(TextoUtil.normalizar("¿Cuánto es veinticinco más diecisiete?")), 1e-9);
        assertTrue(TextoUtil.calcular("5 entre 0").isNaN());
        assertNull(TextoUtil.calcular("volumen al 50 por ciento"));
        assertNull(TextoUtil.calcular("llama a mama"));
        assertNull(TextoUtil.calcular("5"));
    }

    @Test
    public void formato_numeros() {
        assertEquals("8", TextoUtil.formatearNumero(8.0));
        assertEquals("2.3333", TextoUtil.formatearNumero(7.0 / 3));
        assertEquals("2.5", TextoUtil.formatearNumero(2.5));
        assertEquals("-3", TextoUtil.formatearNumero(-3.0));
    }

    @Test
    public void telefonos() {
        assertEquals("5512345678", TextoUtil.extraerTelefono("55 1234 5678"));
        assertEquals("5512345678", TextoUtil.extraerTelefono("55 menos 1234 menos 5678"));
        assertNull(TextoUtil.extraerTelefono("mama"));
        assertEquals("9 1 1", TextoUtil.digitosParaVoz("911"));
    }

    @Test
    public void extrae_texto_original_con_acentos() {
        assertEquals("mañana llego a las 3, ¿sí?",
                TextoUtil.extraerOriginal("Manda un mensaje a Juan diciendo mañana llego a las 3, ¿sí?",
                        "\\b(?:diciendo|que diga)[\\s,:]+(.+)$"));
        assertEquals("la cita es el miércoles",
                TextoUtil.extraerOriginal("Recuérdame que la cita es el miércoles",
                        "\\b(?:recuerdame|anota)(?: que)?[\\s,:]+(.+)$"));
        assertNull(TextoUtil.extraerOriginal("hola", "\\bdiciendo (.+)$"));
    }
}
