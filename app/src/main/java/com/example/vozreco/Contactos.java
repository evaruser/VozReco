package com.example.vozreco;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.List;

/**
 * Consultas a contactos, registro de llamadas y SMS.
 * Quien llama debe comprobar antes los permisos correspondientes.
 */
public final class Contactos {

    public static final class Contacto {
        public final String nombre;
        public final String numero;

        Contacto(String nombre, String numero) {
            this.nombre = nombre;
            this.numero = numero;
        }
    }

    private Contactos() {}

    /**
     * Busca el contacto cuyo nombre se parezca más a lo dictado.
     * Requiere READ_CONTACTS.
     */
    public static Contacto buscar(Context ctx, String nombreDictado) {

        String buscado = TextoUtil.normalizar(nombreDictado)
                .replaceFirst("^(a |al )?(mi |mis )", "")
                .trim();

        if (buscado.isEmpty()) return null;

        String[] palabras = buscado.split(" ");

        Contacto mejor = null;
        int mejorPuntaje = 0;

        try (Cursor c = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {

            if (c == null) return null;

            while (c.moveToNext()) {

                String nombre = c.getString(0);
                String numero = c.getString(1);
                if (nombre == null || numero == null) continue;

                String n = TextoUtil.normalizar(nombre);
                int puntaje;

                if (n.equals(buscado)) {
                    puntaje = 4;
                } else if (n.startsWith(buscado + " ")) {
                    puntaje = 3;
                } else if (contieneTodas(n, palabras)) {
                    puntaje = 2;
                } else if (buscado.startsWith(n + " ")) {
                    puntaje = 1;
                } else {
                    continue;
                }

                if (puntaje > mejorPuntaje) {
                    mejorPuntaje = puntaje;
                    mejor = new Contacto(nombre, numero);
                }
            }
        }

        return mejor;
    }

    private static boolean contieneTodas(String nombre, String[] palabras) {

        String conEspacios = " " + nombre + " ";
        for (String p : palabras) {
            if (!conEspacios.contains(" " + p + " ")) return false;
        }
        return true;
    }

    /** Nombre del contacto con ese número, o null. Requiere READ_CONTACTS. */
    public static String nombreDeNumero(Context ctx, String numero) {

        if (numero == null || numero.isEmpty()) return null;

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(numero));

        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null)) {

            if (c != null && c.moveToFirst()) return c.getString(0);

        } catch (Exception ignored) {
            // Sin permiso o proveedor no disponible
        }
        return null;
    }

    /** Requiere READ_CALL_LOG (y READ_CONTACTS para nombres). */
    public static List<String> ultimasLlamadas(Context ctx, boolean soloPerdidas, int max) {

        List<String> lista = new ArrayList<>();
        ContentResolver cr = ctx.getContentResolver();

        String filtro = soloPerdidas
                ? CallLog.Calls.TYPE + " = " + CallLog.Calls.MISSED_TYPE
                : null;

        try (Cursor c = cr.query(CallLog.Calls.CONTENT_URI,
                new String[]{
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE},
                filtro, null,
                CallLog.Calls.DATE + " DESC")) {

            if (c == null) return lista;

            while (c.moveToNext() && lista.size() < max) {

                String nombre = c.getString(0);
                String numero = c.getString(1);
                int tipo = c.getInt(2);
                long fecha = c.getLong(3);

                String quien = nombre != null && !nombre.isEmpty()
                        ? nombre
                        : (numero == null || numero.isEmpty()
                        ? "un número privado"
                        : "el número " + TextoUtil.digitosParaVoz(numero));

                String accion;
                if (tipo == CallLog.Calls.MISSED_TYPE) accion = "Llamada perdida de ";
                else if (tipo == CallLog.Calls.OUTGOING_TYPE) accion = "Llamaste a ";
                else accion = "Te llamó ";

                lista.add(accion + quien + ", " + cuando(fecha));
            }
        }

        return lista;
    }

    /** Requiere READ_SMS (y READ_CONTACTS para nombres). */
    public static List<String> ultimosMensajes(Context ctx, int max) {

        List<String> lista = new ArrayList<>();

        try (Cursor c = ctx.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE},
                null, null,
                Telephony.Sms.DATE + " DESC")) {

            if (c == null) return lista;

            while (c.moveToNext() && lista.size() < max) {

                String numero = c.getString(0);
                String cuerpo = c.getString(1);
                long fecha = c.getLong(2);

                String nombre = nombreDeNumero(ctx, numero);
                String de = nombre != null
                        ? nombre
                        : (numero == null ? "desconocido" : TextoUtil.digitosParaVoz(numero));

                lista.add("Mensaje de " + de + ", " + cuando(fecha) + ": "
                        + (cuerpo == null ? "" : cuerpo));
            }
        }

        return lista;
    }

    private static String cuando(long fechaMillis) {

        long minutos = (System.currentTimeMillis() - fechaMillis) / 60000;

        if (minutos < 1) return "hace un momento";
        if (minutos < 60) return "hace " + minutos + (minutos == 1 ? " minuto" : " minutos");

        long horas = minutos / 60;
        if (horas < 24) return "hace " + horas + (horas == 1 ? " hora" : " horas");

        long dias = horas / 24;
        return "hace " + dias + (dias == 1 ? " día" : " días");
    }
}
