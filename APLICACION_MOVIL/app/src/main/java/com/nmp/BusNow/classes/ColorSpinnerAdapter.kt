package com.nmp.BusNow.classes

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.nmp.BusNow.R

class ColorSpinnerAdapter(context: Context, private val items: List<Pair<String, Int>>) :
    ArrayAdapter<Pair<String, Int>>(context, R.layout.item_spinner, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_spinner, parent, false)
        val imageView = view.findViewById<ImageView>(R.id.color_icon)
        val textView = view.findViewById<TextView>(R.id.text)

        val (numero, color) = items[position]
        textView.text = numero
        val isDefault = (numero == "-")
        imageView.setImageDrawable(createCircleDrawable(color,isDefault))

        return view
    }

    private fun createCircleDrawable(color: Int, isDefault: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(30, 30) // Tamaño del círculo
            setStroke(2, if (isDefault) Color.WHITE else Color.BLACK) // Borde blanco si es "-", negro en otros casos
        }
    }
}

