package com.nmp.BusNow.classes

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nmp.BusNow.R

open class rvViewHolder(view: View): RecyclerView.ViewHolder(view) {

    private val rvTextView: TextView = view.findViewById(R.id.rvTextView)

    open fun render(s: String) {
        rvTextView.text = s
    }

}