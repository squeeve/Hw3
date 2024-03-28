package com.squeeve.hw3

import com.google.android.gms.maps.model.Marker
import java.lang.invoke.TypeDescriptor

data class PostModel(
    var postKey: String,
    var uid: String,
    var description: String,
    var url: String,
    var date: String,
    var m: Marker
)