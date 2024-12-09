package com.nmp.BusNow.classes

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions

class Ruta (private val duration: String, private val distance: String, private val points: PolylineOptions,
            private val linea: String, private val busDistance: Double = 0.0, private val paradas: List<LatLng> = listOf()) {

    fun getDuration(): String {
        return duration
    }

    fun getDistance(): String {
        return distance
    }

    fun getPoints(): PolylineOptions {
        return points
    }

    fun getParadas(): List<LatLng> {
        return paradas
    }

    fun getLinea(): String {
        return linea
    }

    fun getBusDistance(): Double {
        return busDistance
    }
}