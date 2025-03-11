package com.nmp.BusNow.classes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class rvAdapter<T: rvViewHolder>(private val list: List<String>, private val item: Int, private val listener: OnItemClickListener, private val vhConstructor: (View) -> T): RecyclerView.Adapter<T>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T {
        val view = LayoutInflater.from(parent.context).inflate(item, parent, false)
        return vhConstructor(view)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: T, position: Int) {
        holder.render(list[position])

        // Establecer el listener para cada elemento
        holder.itemView.setOnClickListener {
            listener.onItemClickListener(list[position])
        }
    }
}