package com.nmp.BusNow.classes

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions

class Colectivo(private val recorrido: PolylineOptions, private val paradas: List<LatLng>, private val color: Int) {

    fun getRecorrido(): PolylineOptions {
        return recorrido
    }

    fun getParadas(): List<LatLng> {
        return paradas
    }

    fun getColor(): Int {
        return color
    }
}