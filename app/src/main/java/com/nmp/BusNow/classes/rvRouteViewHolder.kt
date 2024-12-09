package com.nmp.BusNow.classes

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nmp.BusNow.ChoiceActivity.Companion.rutasList
import com.nmp.BusNow.R

class rvRouteViewHolder(view: View): rvViewHolder(view) {

    private val rvTextView: TextView = view.findViewById(R.id.rvTextView)
    private val rvTime: TextView = view.findViewById(R.id.rvTime)
    private val rvDistance: TextView = view.findViewById(R.id.rvDistance)
    private val rvImage: ImageView = view.findViewById(R.id.rvImage)

    override fun render(s: String) {
        rvTextView.text = s
        rvTime.text = rutasList[s]?.getDuration()
        rvDistance.text = rutasList[s]?.getDistance()
        if(s=="Caminando" || s=="Automovil") {
            if (s == "Caminando") {
                rvImage.setImageResource(R.drawable.walk)
            } else {
                rvImage.setImageResource(R.drawable.auto)
            }
        } else {
            // Resetea los valores predeterminados para los otros elementos
            rvImage.setImageResource(R.drawable.bus)
        }
    }

}