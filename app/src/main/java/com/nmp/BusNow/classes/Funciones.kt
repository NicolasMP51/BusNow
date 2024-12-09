package com.nmp.BusNow.classes

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.maps.android.SphericalUtil
import com.nmp.BusNow.MapActivity
import com.nmp.BusNow.R
import java.io.IOException
import java.util.Locale
import kotlin.math.*

class Funciones {

    companion object {
        private var suggestions = mutableListOf<String>()
        private var aux = mutableListOf<LatLng>()
        private var segActive: Boolean = false
        private var segBus: Long = 0L
        private var selectedBus: Long = 0L
        private var locationCallback: LocationCallback? = null
        private lateinit var fusedLocationClient: FusedLocationProviderClient
        private lateinit var database: DatabaseReference

        fun searchListener(editTextSearch: EditText, suggestionsContainer: CardView, cardContainer: ConstraintLayout,
                           placesClient: PlacesClient, rvSugerencias: RecyclerView, context: Context,
                           listener: OnItemClickListener, onSearch: () -> Boolean
        ) {
            editTextSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if(onSearch()) {
                        if (!s.isNullOrEmpty()) {
                            performSearch(
                                s.toString(),
                                suggestionsContainer,
                                cardContainer,
                                placesClient,
                                rvSugerencias,
                                context,
                                listener
                            )
                        } else {
                            suggestionsContainer.visibility = android.view.View.GONE  // Ocultar sugerencias
                            cardContainer.visibility = android.view.View.VISIBLE  // Mostrar el otro contenedor
                        }
                    }
                }
            })
        }

        private fun performSearch(query: String, suggestionsContainer: CardView, cardContainer: ConstraintLayout,
                                  placesClient: PlacesClient, rvSugerencias: RecyclerView, context: Context,
                                  listener: OnItemClickListener) {
            // Crea un token de sesión para autocompletar
            val token = AutocompleteSessionToken.newInstance()

            // Configura la solicitud de autocompletado
            val request = FindAutocompletePredictionsRequest.builder()
                .setLocationBias(Tandil.locationBias())  // Limitar la búsqueda a Tandil
                .setSessionToken(token)
                .setQuery(query)
                .build()

            // Ejecutar la solicitud
            placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                suggestionsContainer.visibility = android.view.View.VISIBLE  // Mostrar sugerencias
                cardContainer.visibility = android.view.View.GONE  // Ocultar atajos y recientes

                // Devuelve 5 predicciones
                suggestions = mutableListOf()
                aux = mutableListOf()
                for (prediction in response.autocompletePredictions) {
                    val placeId = prediction.placeId
                    val placeFields = listOf(com.google.android.libraries.places.api.model.Place.Field.LAT_LNG)

                    // Solicitar los detalles de latitud y longitud
                    val placeRequest = FetchPlaceRequest.builder(placeId, placeFields).build()
                    placesClient.fetchPlace(placeRequest).addOnSuccessListener { placeResponse ->
                        val place = placeResponse.place
                        val latLng = place.latLng

                        // Verificar si las coordenadas están dentro de los límites de Tandil
                        if (latLng != null && Tandil.isWithinBounds(latLng)) {
                            suggestions.add(prediction.getPrimaryText(null).toString())
                            aux.add(latLng)
                        }

                        // Aunque no sea lo mas optimo, es necesario hacerlo aca porque sino no espera que termine el request y no muestra nada
                        rvSugerencias.adapter = rvAdapter(suggestions, R.layout.item_suggestion, listener) { view -> rvViewHolder(view) }
                        rvSugerencias.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                    }
                }
            }.addOnFailureListener { exception ->
                if (exception is ApiException) {
                    Log.e("MapsApp", "Error: ${exception.statusCode}")
                    Toast.makeText(context, "Error en la búsqueda: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun getLatLngSuggestion(item: String): LatLng {
            return aux[suggestions.indexOf(item)]
        }

        fun isNotBus(s: String): Boolean {
            return !Tandil.colectivos.contains(s)
        }

        fun seguimientoListener(btnSeguimiento: TextView, context: Context) {
            database = FirebaseDatabase.getInstance().reference
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            btnSeguimiento.setOnClickListener {
                if(selectedBus != 0L) {
                    segActive = !segActive
                    if (segActive) {
                        btnSeguimiento.text = buildString { append("BAJÉ DEL COLECTIVO") }
                        btnSeguimiento.setBackgroundColor(ContextCompat.getColor(context, R.color.red))
                        segBus = selectedBus

                        // Empezar a rastrear la ubicación
                        startLocationUpdates(context)
                    } else {
                        btnSeguimiento.text = buildString { append("SUBÍ AL COLECTIVO") }
                        btnSeguimiento.setBackgroundColor(ContextCompat.getColor(context,R.color.green))
                        setSelectedBus(0L,btnSeguimiento)

                        // Dejar de rastrear la ubicación
                        stopLocationUpdates()
                    }
                }
            }
        }

        fun isSegActive(): Boolean {
            return segActive
        }

        fun setSelectedBus(linea: Long, btnSeguimiento: TextView) {
            selectedBus = linea
            if (linea == 0L)
                btnSeguimiento.visibility = android.view.View.GONE
            else btnSeguimiento.visibility = android.view.View.VISIBLE
        }

        private fun startLocationUpdates(context: Context) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000) // Intervalo más rápido
                .build()

            // Callback para recibir actualizaciones de ubicación
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val latitude = location.latitude
                        val longitude = location.longitude

                        // Verificar si la ubicación está dentro del recorrido del colectivo
                        if (MapActivity.recorridos[selectedBus]?.getRecorrido()?.let { isLocationInPolyline(LatLng(latitude, longitude), it) } == true) {
                            // Chequear si hay un bus cerca y actualizar su ubicacion
                            updateBusLocation(latitude, longitude)
                        } else {
                            // La ubicación no está en el recorrido
                            Log.i("print", "Ubicación fuera del recorrido")
                        }
                    }
                }
            }

            // Verificar si se tienen los permisos de ubicación
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                locationCallback?.let {fusedLocationClient.requestLocationUpdates(locationRequest, it, Looper.getMainLooper()) }
            } else {
                // Solicitar permisos en tiempo de ejecución si no se tienen
                ActivityCompat.requestPermissions((context as Activity),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }

        private fun stopLocationUpdates() {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            locationCallback = null
        }

        private fun updateLocationToFirebase(busId: String, latitude: Double, longitude: Double) {
            val busLocation = mapOf(
                busId to mapOf(
                    "lat" to latitude,
                    "lon" to longitude
                )
            )
            database.child("ubicaciones").child(selectedBus.toString()).updateChildren(busLocation)
        }

        private fun isLocationInPolyline(location: LatLng, polyline: PolylineOptions): Boolean {
            val index = encontrarSegmentoMasCercano(location,polyline.points)
            val distance = distanciaASegmento(location,polyline.points[index],polyline.points[index+1])

            // Verificar si la ubicación está en el recorrido con una tolerancia de 20 metros
            return (distance<20)
        }

        private fun updateBusLocation(latitude: Double, longitude: Double) {
            val currentLocation = LatLng(latitude, longitude)
            var nearbyBusId = ""
            var minDistance = Double.MAX_VALUE

            // Obtener las ubicaciones de los buses desde Firebase
            database.child("ubicaciones").child(selectedBus.toString()).get().addOnSuccessListener { snapshot ->

                // Iterar sobre los buses existentes y verificar si alguno está en el rango de 100 metros
                snapshot.children.forEach { busSnapshot ->
                    val busId = busSnapshot.key ?: return@forEach
                    val busLat = busSnapshot.child("lat").getValue(Double::class.java) ?: return@forEach
                    val busLon = busSnapshot.child("lon").getValue(Double::class.java) ?: return@forEach
                    val busLocation = LatLng(busLat, busLon)

                    // Calcular la distancia entre el usuario y el bus
                    val distance = SphericalUtil.computeDistanceBetween(currentLocation, busLocation)

                    // Si la distancia es menor, tomar el busId
                    if (distance < minDistance) {
                        minDistance = distance
                        nearbyBusId = busId
                        return@forEach
                    }
                }

                // Si se encontró un bus cerca, actualizar su ubicación, si no, crear uno nuevo
                if (minDistance < 100) {
                    // Llamar a la función para actualizar la ubicación del bus en la base de datos
                    updateLocationToFirebase(nearbyBusId, latitude, longitude)
                } else {
                    val newBusId = "bus" + (snapshot.childrenCount + 1)
                    // Llamar a la función para actualizar la ubicación del bus en la base de datos
                    updateLocationToFirebase(newBusId, latitude, longitude)
                }
            }
        }


        fun createMarkerBitmap(color: Int, context: Context): Bitmap {
            // Tamaño del bitmap y del círculo
            val size = 100 // Tamaño en píxeles (ajusta según sea necesario)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Dibuja el círculo blanco de fondo
            val paintWhite = Paint()
            paintWhite.color = Color.WHITE
            paintWhite.style = Paint.Style.FILL
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paintWhite)

            // Dibuja el borde negro
            val paintBlack = Paint()
            paintBlack.color = Color.BLACK
            paintBlack.style = Paint.Style.STROKE
            paintBlack.strokeWidth = 5f // Ancho del borde
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paintBlack)

            // Dibuja el borde negro al colectivo
            val busBorder = ContextCompat.getDrawable(context, R.drawable.bus_border)
            busBorder?.setTint(Color.BLACK)
            busBorder?.setBounds(18, 18, size - 18, size - 18) // Ajusta según el tamaño del drawable
            busBorder?.draw(canvas)

            // Inflar el drawable del colectivo
            val busDrawable = ContextCompat.getDrawable(context, R.drawable.bus)
            busDrawable?.setTint(color)
            busDrawable?.setBounds(25, 25, size - 25, size - 25) // Ajusta según el tamaño del drawable
            busDrawable?.draw(canvas)

            return bitmap
        }

        fun getDireccion(latLng: LatLng, context: Context): String {
            // Usar Geocoder para obtener la dirección
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                val addresses: MutableList<Address>? = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (addresses != null) {
                    if (addresses.isNotEmpty()) {
                        val address: Address = addresses[0]
                        return address.getAddressLine(0).split(",")[0] // Obtén la primera línea de la dirección
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return latLng.toString()
        }

        fun drawRoute(mMap: GoogleMap, origin: LatLng, destination: LatLng, polylineOptions: PolylineOptions) {
            // Dibujar la polilínea en el mapa
            mMap.clear()
            mMap.addPolyline(polylineOptions)
            mMap.addCircle(
                CircleOptions()
                    .center(origin)
                    .radius(20.0) // Ajusta el radio según sea necesario
                    .strokeColor(Color.WHITE)
                    .strokeWidth(2F)
                    .fillColor(Color.BLUE) // Color del círculo
            )
            mMap.addMarker(MarkerOptions().position(destination))
            val boundsBuilder = LatLngBounds.builder().include(origin).include(destination)
            val bounds = boundsBuilder.build()
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 300))
        }

        fun calcDistance(start: LatLng, end: LatLng): Double {
            var vectorRef = Tandil.vectorMarconi()
            val vector = calculateMovementVector(start,end)

            // Calcular angulo y asegurarse de que esté en el rango [0, 2π)
            var angulo = atan2(vector.second, vector.first) - atan2(vectorRef.second, vectorRef.first)
            if (angulo < 0) angulo += 2 * PI

            // Calcular la longitud de la hipotenusa (distancia entre puntos)
            val hipotenusa = sqrt(vector.first * vector.first + vector.second * vector.second)

            // Calcular los catetos
            val catetoY = abs(hipotenusa * sin(angulo))
            val catetoX = abs(hipotenusa * cos(angulo))

            val distance = (catetoX + catetoY)/1000 // Distancia en km

            return distance
        }

        fun calcDuration(distance: Double, velocity: Double): Double {
            return (distance / velocity) * 60.0 // Tiempo en minutos
        }

        fun calculateMovementVector(point1: LatLng, point2: LatLng): Pair<Double, Double> {
            val deltaLat = (point2.latitude - point1.latitude) * 111320 //pasar grados a metros
            val deltaLong = (point2.longitude - point1.longitude) * 111320 * cos(Math.toRadians(point1.latitude))
            return Pair(deltaLat, deltaLong) // Devuelve el vector de movimiento
        }

        // Función para proyectar una parada en un segmento (p1, p2) y calcular la distancia
        private fun distanciaASegmento(point: LatLng, p1: LatLng, p2: LatLng): Double {
            val latLngParada = doubleArrayOf(point.latitude, point.longitude)
            val latLngP1 = doubleArrayOf(p1.latitude, p1.longitude)
            val latLngP2 = doubleArrayOf(p2.latitude, p2.longitude)

            val v = doubleArrayOf(latLngP2[0] - latLngP1[0], latLngP2[1] - latLngP1[1]) // Vector del segmento
            val u = doubleArrayOf(latLngParada[0] - latLngP1[0], latLngParada[1] - latLngP1[1]) // Vector de la parada al primer punto

            val t = (u[0] * v[0] + u[1] * v[1]) / (v[0] * v[0] + v[1] * v[1]) // Escalar de la proyección
            val tClamped = max(0.0, min(1.0, t)) // Clampeamos para obtener el punto más cercano sobre el segmento

            val proyeccion = LatLng(latLngP1[0] + tClamped * v[0], latLngP1[1] + tClamped * v[1]) // Proyección de la parada en el segmento

            // Calcular la distancia entre la parada y la proyección usando Location.distanceBetween
            val distancia = FloatArray(1)
            android.location.Location.distanceBetween(point.latitude, point.longitude, proyeccion.latitude, proyeccion.longitude, distancia)

            return distancia[0].toDouble()
        }

        // Función para encontrar el índice del segmento más cercano a una parada
        fun encontrarSegmentoMasCercano(point: LatLng, polyline: List<LatLng>): Int {
            var nearestIndex = 0
            var minDistance = Double.MAX_VALUE
            var p1: LatLng
            var p2: LatLng
            var distancia: Double

            for (i in 0..<polyline.lastIndex) {
                p1 = polyline[i]
                p2 = polyline[i + 1]

                distancia = distanciaASegmento(point, p1, p2)
                if (distancia < minDistance) {
                    minDistance = distancia
                    nearestIndex = i
                }
            }
            p1 = polyline[polyline.lastIndex]
            p2 = polyline[0]
            distancia = distanciaASegmento(point, p1, p2)
            if (distancia < minDistance) {
                nearestIndex = polyline.lastIndex
            }

            return nearestIndex
        }

        fun ordenarRutas(rutas: MutableList<String>): List<String> {
            return rutas.sortedWith(
                compareBy(
                    { when {
                        it.startsWith("C") -> 0
                        it.startsWith("5") -> 1
                        it.startsWith("A") -> 2
                        else -> 3
                    }},
                    { it } // Ordena alfabéticamente
                )
            )
        }
    }
}