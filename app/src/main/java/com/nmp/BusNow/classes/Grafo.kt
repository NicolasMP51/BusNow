package com.nmp.BusNow.classes

import com.google.android.gms.maps.model.LatLng

// Data class para representar una parada de colectivo
data class Parada(val id: String, val latLng: LatLng)

// Data class para representar una ruta entre dos paradas
data class RouteBetween(val start: Parada, val end: Parada, val distance: Double, val duration: Double)

class Grafo {

    private val rutasEntreParadas: MutableMap<Parada,RouteBetween> = mutableMapOf()

    fun addRoute(start: Parada, end: Parada, distance: Double, duration: Double) {
        rutasEntreParadas[start] = RouteBetween(start,end,distance,duration)
    }

    private fun getRoute(parada: Parada): RouteBetween? {
        return rutasEntreParadas[parada]
    }

    fun calcTotalRoute(start: Parada, end: Parada): RouteBetween {
        var actual = start
        var aux: RouteBetween?
        var distance = 0.0
        var duration = 0.0
        while(actual.id!=end.id) {
            aux = getRoute(actual)
            if(aux!=null){
                distance += aux.distance
                duration += aux.duration
                actual = aux.end
            }
        }
        return RouteBetween(start,end,distance,duration)
    }

    fun getListParadas(start: Parada, end: Parada): List<LatLng> {
        val listParadas: MutableList<LatLng> = mutableListOf()
        var actual = start
        while(actual.id!=end.id) {
            listParadas.add(actual.latLng)
            actual = rutasEntreParadas[actual]?.end!!
        }
        listParadas.add(actual.latLng) // Agrego end
        return listParadas
    }

}
