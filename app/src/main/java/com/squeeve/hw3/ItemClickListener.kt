package com.squeeve.hw3

import com.google.android.gms.maps.model.LatLng

interface ItemClickListener {
    fun onItemClick(latLng: LatLng): Unit
}
