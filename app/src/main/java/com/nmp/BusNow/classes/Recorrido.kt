package com.nmp.BusNow.classes

data class Recorrido (
    val linea: Long = 0,
    val color: String = "",
    val puntos: List<List<Double>> = emptyList(),
    val paradas: List<List<Double>> = emptyList()
)