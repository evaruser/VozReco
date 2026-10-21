package com.example.vozreco;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * Obtiene la ubicación actual y la convierte en una dirección legible.
 * Quien llama debe comprobar antes el permiso de ubicación.
 */
public final class Ubicacion {

    public interface Callback {
        /** direccion puede ser null si no se pudo traducir a calle. */
        void listo(Location ubicacion, String direccion);

        void error(String mensaje);
    }

    private static final long TIEMPO_MAXIMO_MS = 15000;
    private static final long ANTIGUEDAD_MAXIMA_MS = 2 * 60 * 1000;

    private Ubicacion() {}

    @SuppressLint("MissingPermission")
    public static void obtener(Context ctx, Callback callback) {

        Context app = ctx.getApplicationContext();
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        Handler main = new Handler(Looper.getMainLooper());

        if (lm == null) {
            callback.error("El servicio de ubicación no está disponible");
            return;
        }

        String proveedor;
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            proveedor = LocationManager.GPS_PROVIDER;
        } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            proveedor = LocationManager.NETWORK_PROVIDER;
        } else {
            callback.error("La ubicación del teléfono está desactivada. "
                    + "Di: abre los ajustes de ubicación, para activarla");
            return;
        }

        Location ultima = ultimaConocida(lm);
        if (ultima != null
                && System.currentTimeMillis() - ultima.getTime() < ANTIGUEDAD_MAXIMA_MS) {
            traducir(app, ultima, callback, main);
            return;
        }

        final boolean[] terminado = {false};

        Runnable sinUbicacionNueva = () -> {
            if (ultima != null) traducir(app, ultima, callback, main);
            else callback.error("No pude obtener tu ubicación. Intenta cerca de una ventana");
        };

        Runnable porTiempo = () -> {
            if (terminado[0]) return;
            terminado[0] = true;
            sinUbicacionNueva.run();
        };

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                CancellationSignal cancelar = new CancellationSignal();
                lm.getCurrentLocation(proveedor, cancelar, app.getMainExecutor(), loc -> {
                    if (terminado[0]) return;
                    terminado[0] = true;
                    if (loc != null) traducir(app, loc, callback, main);
                    else sinUbicacionNueva.run();
                });
                main.postDelayed(() -> {
                    if (!terminado[0]) cancelar.cancel();
                    porTiempo.run();
                }, TIEMPO_MAXIMO_MS);

            } else {

                LocationListener oyente = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        if (terminado[0]) return;
                        terminado[0] = true;
                        traducir(app, loc, callback, main);
                    }

                    @Override
                    public void onStatusChanged(String p, int s, Bundle b) {}

                    @Override
                    public void onProviderEnabled(String p) {}

                    @Override
                    public void onProviderDisabled(String p) {}
                };

                lm.requestSingleUpdate(proveedor, oyente, Looper.getMainLooper());
                main.postDelayed(() -> {
                    lm.removeUpdates(oyente);
                    porTiempo.run();
                }, TIEMPO_MAXIMO_MS);
            }

        } catch (SecurityException e) {
            terminado[0] = true;
            callback.error("No tengo permiso para conocer tu ubicación");
        }
    }

    @SuppressLint("MissingPermission")
    private static Location ultimaConocida(LocationManager lm) {

        Location mejor = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (mejor == null || l.getTime() > mejor.getTime())) mejor = l;
            } catch (SecurityException ignored) {
                // Sin permiso para este proveedor
            }
        }
        return mejor;
    }

    /** Convierte coordenadas en dirección en un hilo aparte. */
    @SuppressWarnings("deprecation")
    private static void traducir(Context app, Location loc, Callback callback, Handler main) {

        new Thread(() -> {
            String direccion = null;

            if (Geocoder.isPresent()) {
                try {
                    Geocoder g = new Geocoder(app, TextoUtil.IDIOMA);
                    List<Address> lista = g.getFromLocation(
                            loc.getLatitude(), loc.getLongitude(), 1);
                    if (lista != null && !lista.isEmpty()) {
                        direccion = describir(lista.get(0));
                    }
                } catch (Exception ignored) {
                    // Sin Internet o servicio no disponible: se devuelven solo coordenadas
                }
            }

            String resultado = direccion;
            main.post(() -> callback.listo(loc, resultado));
        }, "geocoder").start();
    }

    private static String describir(Address a) {

        StringBuilder sb = new StringBuilder();

        if (a.getThoroughfare() != null) {
            sb.append(a.getThoroughfare());
            if (a.getSubThoroughfare() != null) sb.append(" número ").append(a.getSubThoroughfare());
        }
        if (a.getSubLocality() != null) {
            if (sb.length() > 0) sb.append(", colonia ");
            sb.append(a.getSubLocality());
        }
        if (a.getLocality() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.getLocality());
        }

        if (sb.length() == 0 && a.getMaxAddressLineIndex() >= 0) {
            return a.getAddressLine(0);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
