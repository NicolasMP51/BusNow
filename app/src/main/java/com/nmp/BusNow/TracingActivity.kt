package com.nmp.BusNow

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nmp.BusNow.classes.OnItemClickListener
import com.nmp.BusNow.classes.rvAdapter
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.rvViewHolder

class TracingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var back: ImageButton
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var rvParadas: RecyclerView
    private lateinit var btnSeguimiento: TextView
    private lateinit var cantParadas: TextView
    private lateinit var textTime: TextView
    private lateinit var rutaSel: TextView
    private var colectivo: String = ""
    private lateinit var origin: LatLng
    private lateinit var destination: LatLng

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tracing)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Recibe las coordenadas origen y destino pasadas desde el intent
        val originLat = intent.getDoubleExtra("originLat", -1.0)
        val originLong = intent.getDoubleExtra("originLong", -1.0)
        val destLat = intent.getDoubleExtra("destLat", -1.0)
        val destLong = intent.getDoubleExtra("destLong", -1.0)
        if (originLat != -1.0 && originLong != -1.0 && destLat != -1.0 && destLong != -1.0 ) {
            origin = LatLng(originLat,originLong)
            destination = LatLng(destLat,destLong)
        }

        initComponents()
        initListeners()
    }

    private fun initComponents() {
        // Obtiene el SupportMapFragment y pide notificación cuando el mapa esté listo para ser usado.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializa el boton de volver hacia atras
        back = findViewById(R.id.back)

        //Inicializa el boton de activar y desactivar notificaciones
        switchNotifications = findViewById(R.id.switch_notifications)

        // Inicializa el cartel de ruta seleccionada
        rutaSel = findViewById(R.id.ruta_seleccionada)
        colectivo = ChoiceActivity.rutaSeleccionada.getLinea()
        rutaSel.text = if(Funciones.isNotBus(colectivo)) colectivo else "Línea $colectivo"

        // Inicializa los textos de tiempo y cantidad de paradas
        cantParadas = findViewById(R.id.cantParadas)
        cantParadas.text = buildString { append("Bajar en ${ChoiceActivity.rutaSeleccionada.getParadas().size-1} paradas") }
        textTime = findViewById(R.id.textTime)
        textTime.text = ChoiceActivity.rutaSeleccionada.getDuration()

        // Inicializa lista de paradas siguientes
        rvParadas = findViewById(R.id.rvParadas)

        // Inicializa boton para informar que subio o bajo del colectivo
        btnSeguimiento = findViewById(R.id.seguimiento)
        btnSeguimiento.text = buildString { append("SUBÍ AL COLECTIVO") }

    }

    private fun initListeners() {
        backListener()
        notificationsListener()
        seguimientoListener()
        rvSetup()
    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        Funciones.drawRoute(mMap, origin, destination, ChoiceActivity.rutaSeleccionada.getPoints())
    }


    private fun backListener() {
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun notificationsListener() {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Activado
                Log.i("print","Activado")
            } else {
                // Desactivado
                Log.i("print","Desactivado")
            }
        }
    }

    private fun seguimientoListener() {
        if(!Funciones.isNotBus(colectivo)) {
            Funciones.seguimientoListener(btnSeguimiento, this)
            Funciones.setSelectedBus(colectivo.toLong(),btnSeguimiento)
        } else Funciones.setSelectedBus(0L,btnSeguimiento)
    }

    private fun rvSetup() {
        val paradas: MutableList<String> = mutableListOf()
        for (latLng in ChoiceActivity.rutaSeleccionada.getParadas()) {
            paradas.add(Funciones.getDireccion(latLng,this))
        }
        rvParadas.adapter = rvAdapter(paradas, R.layout.item_paradas, object : OnItemClickListener {
            override fun onItemClickListener(item: String) {
                // Hacer algo al clickear el elemento
            }}) {view -> rvViewHolder(view) }
        rvParadas.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }
}