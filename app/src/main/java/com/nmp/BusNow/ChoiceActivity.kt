package com.nmp.BusNow

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.nmp.BusNow.classes.OnItemClickListener
import com.nmp.BusNow.classes.rvAdapter
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.PolyUtil
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.Grafo
import com.nmp.BusNow.classes.Parada
import com.nmp.BusNow.classes.RouteBetween
import com.nmp.BusNow.classes.Ruta
import com.nmp.BusNow.classes.Tandil
import com.nmp.BusNow.classes.rvRouteViewHolder
import org.json.JSONObject

class ChoiceActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        val rutasList = mutableMapOf<String, Ruta>()
        lateinit var rutaSeleccionada: Ruta
    }

    private lateinit var mMap: GoogleMap
    private lateinit var rvRutas: RecyclerView
    private lateinit var back: ImageButton
    private lateinit var editTextOrigin: EditText
    private lateinit var editTextDest: EditText
    private var originLat: Double = -1.0
    private var originLong: Double = -1.0
    private var originAddress: String? = null
    private var destLat: Double = -1.0
    private var destLong: Double = -1.0
    private var destAddress: String? = null
    private lateinit var origin: LatLng
    private lateinit var destination: LatLng
    private var isOrigin = false
    private var shouldShowSuggestions = true
    private lateinit var placesClient: PlacesClient
    private lateinit var suggestionsContainer: CardView
    private lateinit var cardContainer: ConstraintLayout
    private lateinit var rvSugerencias: RecyclerView
    private lateinit var confirm: Button
    private var rutas: MutableList<String> = mutableListOf()
    private lateinit var grafo: Grafo
    private lateinit var paradas: List<Parada>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_choice)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Recibe las coordenadas y dirección pasadas desde el intent
        originLat = intent.getDoubleExtra("originLat", -1.0)
        originLong = intent.getDoubleExtra("originLong", -1.0)
        originAddress = intent.getStringExtra("originAddress")
        destLat = intent.getDoubleExtra("destLat", -1.0)
        destLong = intent.getDoubleExtra("destLong", -1.0)
        destAddress = intent.getStringExtra("destAddress")

        initComponents()
        initListeners()
    }

    private fun initComponents() {
        // Obtiene el SupportMapFragment y pide notificación cuando el mapa esté listo para ser usado.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializa lista de rutas en colectivo
        rvRutas = findViewById(R.id.rvRutas)

        // Obtiene ubicaciones origen y destino
        editTextOrigin = findViewById(R.id.origin)
        editTextDest = findViewById(R.id.destination)
        if (originLat != -1.0 && originLong != -1.0 && destLat != -1.0 && destLong != -1.0 ) {
            origin = LatLng(originLat,originLong)
            destination = LatLng(destLat,destLong)
            editTextOrigin.setText(originAddress)
            editTextDest.setText(destAddress)
            rutas.clear()
            traceAllRoutes()
        }

        // Inicializa el boton de volver hacia atras
        back = findViewById(R.id.back)

        // Inicializa Google Places
        if (!Places.isInitialized()) { Places.initialize(applicationContext, getString(R.string.google_maps_key)) }
        placesClient = Places.createClient(this)

        // Inicializa el contenedor de las tarjetas de mapa y rutas
        cardContainer = findViewById(R.id.cardContainer)

        // Inicializa el contenedor de sugerencias
        suggestionsContainer = findViewById(R.id.suggestionsContainer)
        rvSugerencias = findViewById(R.id.rvSugerencias)

        // Inicializa boton para confirmar ruta seleccionada
        confirm = findViewById(R.id.confirm)
    }

    private fun initListeners() {
        backListener()
        searchListener(editTextOrigin,true)
        searchListener(editTextDest,false)
        confirmListener()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setLatLngBoundsForCameraTarget(Tandil.bounds())
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Tandil.center(), 15f))
    }

    private fun traceAllRoutes() {
        traceRoute("walking", "Caminando", origin, destination)
        traceRoute("driving", "Automovil", origin, destination)
        for (colectivo in MapActivity.recorridos){
            grafo = Grafo()
            paradas = initParadas(colectivo.value.getParadas())
            addRoutesGrafo()
            val bestRoute = findBestRoute()
            val routeBetween = bestRoute.first
            val busDistance = bestRoute.second
            if (routeBetween!=null) {
                Log.i("print","Linea: ${colectivo.key}, P1: ${routeBetween.start.id}, P2: ${routeBetween.end.id}")
                val segment = getSegmentInPolyline(routeBetween.start.latLng,routeBetween.end.latLng,colectivo.value.getRecorrido().points)
                val polylineOptions = PolylineOptions()
                    .addAll(segment)
                    .width(10f)
                    .color(Color.BLUE)
                // Guardar la ruta y notificar que se ha actualizado el item
                val linea = colectivo.key.toString()
                val ruta = Ruta("${routeBetween.duration.toInt()} mins","${(routeBetween.distance*10).toInt()/10.0} km",polylineOptions,linea,busDistance,grafo.getListParadas(routeBetween.start,routeBetween.end))
                rutasList[linea] = ruta
                rutasSetup(linea)
            }
        }
    }

    private fun traceRoute(mode: String, key: String, ori: LatLng, dest: LatLng) {
        val googleMapsKey = getString(R.string.google_maps_key)
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${ori.latitude},${ori.longitude}&destination=${dest.latitude},${dest.longitude}&mode=${mode}&key=$googleMapsKey"

        // Usar Volley para hacer la solicitud
        val directionsRequest = StringRequest(Request.Method.GET, url, { response ->
            // Parsear la respuesta JSON
            val jsonResponse = JSONObject(response)
            val routes = jsonResponse.getJSONArray("routes")
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val overviewPolyline = route.getJSONObject("overview_polyline")
                val points = overviewPolyline.getString("points")

                // Obtener la información de duración y distancia del primer 'leg'
                val leg = route.getJSONArray("legs").getJSONObject(0)
                var duration = leg.getJSONObject("duration").getString("text")
                val distance = leg.getJSONObject("distance").getString("text")

                // Decodificar los puntos en una lista de LatLng
                val polylineOptions = PolylineOptions()
                    .addAll(PolyUtil.decode(points))
                    .width(10f)
                    .color(Color.BLUE)

                if(duration.contains("hour")){
                    val hours = duration.split(" ")[0].toInt()
                    val mins = duration.split(" ")[2].toInt()
                    val total = (60 * hours) + mins
                    duration = "$total mins"
                }

                rutasList[key] = Ruta(duration, distance, polylineOptions, key)
                rutasSetup(key)
            }
        }, { error ->
            Log.e("Error", "Error fetching directions: ${error.message}")
        })

        // Añadir la solicitud a la cola de Volley
        Volley.newRequestQueue(this).add(directionsRequest)
    }

    private fun backListener() {
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun searchListener(editText: EditText, bool: Boolean) {
        val context = this
        Funciones.searchListener(editText,suggestionsContainer,cardContainer,placesClient,
            rvSugerencias,context,object : OnItemClickListener {
                override fun onItemClickListener(item: String) {
                    shouldShowSuggestions = false
                    suggestionsContainer.visibility = android.view.View.GONE  // Ocultar sugerencias
                    cardContainer.visibility = android.view.View.VISIBLE  // Mostrar mapa
                    val latLng = Funciones.getLatLngSuggestion(item)
                    if(isOrigin){
                        editTextOrigin.setText(item)
                        origin = latLng
                        editTextOrigin.clearFocus()
                    } else {
                        editTextDest.setText(item)
                        destination = latLng
                        editTextDest.clearFocus()
                    }
                    rutas.clear()
                    traceAllRoutes()
                }}) {
            if (!shouldShowSuggestions) {
                shouldShowSuggestions = true // Reset the flag
                false
            } else {
                isOrigin = bool
                true
            }
        }
    }

    private fun rutasSetup(item: String) {
        rutas.add(item)
        if(rutas.containsAll(listOf("Caminando","500","501","502","503","504","505","Automovil"))) {
            val listaPulida = mutableListOf<String>()
            for(s in rutas) {
                if(s.startsWith("5")) {
                    if (rutasList[s]?.let { rutasList["Caminando"]?.getDuration()?.split(" ")?.get(0)?.let { it1 -> isRouteConsidered(it, it1.toDouble()) } } == true)
                        listaPulida.add(s)
                } else listaPulida.add(s)
            }
            val listaOrdenada = Funciones.ordenarRutas(listaPulida)

            rvRutas.adapter =
                rvAdapter(listaOrdenada, R.layout.item_rutas, object : OnItemClickListener {
                    override fun onItemClickListener(item: String) {
                        rutasList[item]?.let {
                            Funciones.drawRoute(mMap, origin, destination, it.getPoints())
                            rutaSeleccionada = it
                            for (latLng in rutaSeleccionada.getParadas()) {
                                mMap.addCircle(
                                    CircleOptions()
                                        .center(latLng)
                                        .radius(15.0) // Ajusta el radio según sea necesario
                                        .strokeColor(Color.BLUE)
                                        .strokeWidth(2F)
                                        .fillColor(Color.WHITE) // Color del círculo
                                )
                            }
                        }
                    }
                }) { view -> rvRouteViewHolder(view) }

            rvRutas.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

            // Forzar click en la opcion por defecto
            rvRutas.post { rvRutas.findViewHolderForAdapterPosition(0)?.itemView?.performClick() }
        }
    }

    private fun confirmListener() {
        confirm.setOnClickListener {
            val intent = Intent(this,TracingActivity::class.java).apply {
                putExtra("originLat", origin.latitude)
                putExtra("originLong", origin.longitude)
                putExtra("destLat", destination.latitude)
                putExtra("destLong", destination.longitude)
            }
            startActivity(intent)
        }
    }

    private fun initParadas(paradas: List<LatLng>): List<Parada> {
        val list: MutableList<Parada> = mutableListOf()
        var n = 1
        var id: String
        for(parada in paradas){
            id = "Parada${n}"
            list.add(Parada(id,parada))
            n++
        }
        return list
    }

    private fun addRoutesGrafo() {
        var distance: Double
        for (i in 0..<paradas.size-1) {
            distance = Funciones.calcDistance(paradas[i].latLng,paradas[i+1].latLng)
            grafo.addRoute(paradas[i],paradas[i+1],distance,Funciones.calcDuration(distance,20.0))
        }
        // Caso final para cerrar el circulo
        distance = Funciones.calcDistance(paradas[paradas.size-1].latLng,paradas[0].latLng)
        grafo.addRoute(paradas[paradas.size-1],paradas[0],distance,Funciones.calcDuration(distance,20.0))
    }

    // Función para obtener el segmento del recorrido entre dos paradas
    private fun getSegmentInPolyline(parada1: LatLng, parada2: LatLng, polyline: List<LatLng>): List<LatLng> {
        val index1 = Funciones.encontrarSegmentoMasCercano(parada1, polyline)
        val index2 = Funciones.encontrarSegmentoMasCercano(parada2, polyline)

        val list: MutableList<LatLng>
        // Extraer el segmento desde el índice 1 hasta el 2
        if (index1 < index2) {
            list = polyline.subList(index1, index2 + 1).toMutableList()
        } else {
            // Manejar el caso en que el 2 sea antes que el 1 (e.g., rutas circulares)
            val firstSegment = polyline.subList(index1, polyline.lastIndex) // Segmento desde index1 hasta el final
            val secondSegment = polyline.subList(0, index2 + 1) // Segmento desde el principio hasta index2
            list = (firstSegment + secondSegment).toMutableList() // Combinar ambos segmentos
        }
        list[0] = parada1
        list.add(parada2)
        return list
    }

    private fun findBestRoute(): Pair<RouteBetween?,Double> {
        var bestRoute: RouteBetween? = null
        var bestTotalTime = Double.MAX_VALUE
        var bestBusDistance = Double.MAX_VALUE

        for (parada1 in paradas) { // Parada de subida
            // Calcular tiempo y distancia desde el origen hasta la parada de subida
            val walkingToStop1Distance = Funciones.calcDistance(origin, parada1.latLng)
            val walkingToStop1Time = Funciones.calcDuration(walkingToStop1Distance,4.0)

            for (parada2 in paradas) { // Parada de bajada
                if (parada1.id != parada2.id) { // Asegurarse de que no sea la misma parada
                    // Encontrar la ruta entre las paradas
                    val route = grafo.calcTotalRoute(parada1, parada2)

                    // Calcular tiempo y distancia desde la parada de bajada hasta el destino
                    val walkingFromStop2Distance = Funciones.calcDistance(parada2.latLng, destination)
                    val walkingFromStop2Time = Funciones.calcDuration(walkingFromStop2Distance,4.0)

                    // Calcular el tiempo y distancia total
                    val totalTime = walkingToStop1Time + route.duration + walkingFromStop2Time
                    val totalDistance = walkingToStop1Distance + route.distance + walkingFromStop2Distance

                    // Actualizar mejor ruta si se encontró una mejor
                    if (totalTime < bestTotalTime) {
                        bestTotalTime = totalTime
                        bestBusDistance = route.distance
                        bestRoute = RouteBetween(parada1, parada2, totalDistance, totalTime)
                    }
                }
            }
        }
        return Pair(bestRoute, bestBusDistance)
    }

    private fun isRouteConsidered(ruta: Ruta, walkDuration: Double): Boolean {
        val totalDistance = ruta.getDistance().split(" ")[0].toDouble()
        val totalDuration = ruta.getDuration().split(" ")[0].toDouble()
        val busDistance = ruta.getBusDistance()

        // Evaluar criterio: el tiempo total debe ser menor a 1.5 veces el tiempo caminando
        val timeComparison = totalDuration < (1.5 * walkDuration)

        // Evaluar criterio: la distancia recorrida en colectivo debe ser al menos la mitad de la distancia total
        val distanceComparison = busDistance >= (totalDistance / 2)

        // La ruta es considerada válida si cumple ambos criterios
        return timeComparison && distanceComparison
    }
}