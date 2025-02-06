package com.nmp.BusNow

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.nmp.BusNow.classes.Tandil

class PredictActivity : AppCompatActivity(), OnMapReadyCallback {

    private var linea: Long = -1L
    private var bus_id: String? = null
    private lateinit var mMap: GoogleMap
    private lateinit var back: ImageButton
    private lateinit var spinner: Spinner
    private lateinit var predictText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_predict)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Recibe los datos del colectivo pasados desde el intent
        linea = intent.getLongExtra("linea", -1L)
        bus_id = intent.getStringExtra("bus_id")

        initComponents()
        initListeners()
    }

    private fun initComponents(){
        // Obtiene el SupportMapFragment y pide notificación cuando el mapa esté listo para ser usado.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializa el boton de volver hacia atras
        back = findViewById(R.id.back)

        // Inicializa el Spinner de opciones de colectivos
        spinner = findViewById(R.id.spinner)
        var paradas : List<LatLng> = listOf()
        if (linea != -1L) {
            paradas = MapActivity.recorridos[linea]?.getParadas()!!
        }
        val adapter = ArrayAdapter(this,android.R.layout.simple_spinner_item, paradas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Inicializa texto de prediccion
        predictText = findViewById(R.id.predict)
    }

    private fun initListeners(){

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setLatLngBoundsForCameraTarget(Tandil.bounds())
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Tandil.center(), 15f))
    }

}