package com.nmp.BusNow.classes

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.model.LocationBias
import com.google.android.libraries.places.api.model.RectangularBounds

class Tandil (){

    companion object {
        private val minLat = -37.36  // Latitud mínima (sur)
        private val maxLat = -37.26  // Latitud máxima (norte)
        private val minLng = -59.20  // Longitud mínima (oeste)
        private val maxLng = -59.04  // Longitud máxima (este)
        private val suroeste: LatLng = LatLng(minLat, minLng)
        private val noreste: LatLng = LatLng(maxLat, maxLng)
        val colectivos = listOf("500", "501", "502", "503", "504", "505")


        fun bounds(): LatLngBounds {
            return LatLngBounds(suroeste, noreste)
        }

        fun center(): LatLng {
            return LatLng(-37.328999, -59.137078)
        }

        fun locationBias(): LocationBias {
            return RectangularBounds.newInstance(suroeste, noreste)
        }

        fun isWithinBounds(latLng: LatLng): Boolean {
            return latLng.latitude in minLat..maxLat && latLng.longitude in minLng..maxLng
        }

        fun vectorMarconi(): Pair<Double, Double> {
            val marconiYbuzon = LatLng(-37.3159678426062,-59.120978925763666)
            val calvario = LatLng(-37.327785545306256,-59.15222555976733)
            return Funciones.calculateMovementVector(marconiYbuzon,calvario)
        }
    }

}