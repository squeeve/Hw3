package com.squeeve.hw3

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.util.UUID

class PhotoPreview : AppCompatActivity() {
    private val REQUEST_FOR_LOCATION = 123
    private lateinit var uri: Uri
    private lateinit var description: EditText
    private lateinit var mAuth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_preview)

        uri = Uri.fromFile(File(intent.getStringExtra("uri")!!))
        val imageView: ImageView = findViewById(R.id.previewImage)
        imageView.setImageURI(uri)
        description = findViewById(R.id.description)
        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser!!
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_FOR_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FOR_LOCATION
            && ((grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED)
            || (grantResults.isNotEmpty() && grantResults[1] != PackageManager.PERMISSION_GRANTED))) {
                Toast.makeText(this, "We need to access your location to post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImage() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_FOR_LOCATION
            )
        }

        mFusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val storage = FirebaseStorage.getInstance()
                val fileNameInStorage = UUID.randomUUID().toString()
                val imageRef = storage.getReference("/images/$fileNameInStorage.jpg")
                val lat = location.latitude.toString()
                val lng = location.longitude.toString()
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpg")
                    .setCustomMetadata("photoLng", lng)
                    .setCustomMetadata("photoLat", lat)
                    .setCustomMetadata("uid", currentUser.uid)
                    .setCustomMetadata("alt-text", description.text.toString())
                    .build()
                imageRef.putFile(uri, metadata).addOnSuccessListener {
                    Log.d("PhotoPreview", "Image uploaded successfully; now uploading PostModel.")
                    val post = Post(
                        uid = currentUser.uid,
                        url = "images/$fileNameInStorage.jpg",
                        description = description.text.toString(),
                        lat = lat,
                        lng = lng)
                    val firestoreDb = FirebaseFirestore.getInstance()
                    firestoreDb.collection("ImagePosts").document(fileNameInStorage).set(post)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this@PhotoPreview,
                                "Post uploaded successfully. Your post will appear shortly.",
                                Toast.LENGTH_SHORT
                            ).show()
                            val geoFire = GeoFire(FirebaseDatabase.getInstance().getReference("/geofire"))
                            geoFire.setLocation(fileNameInStorage, GeoLocation(location.latitude, location.longitude))
                        }.addOnFailureListener { e ->
                            Toast.makeText(
                                this@PhotoPreview,
                                "Failed to upload post: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(
                    this@PhotoPreview,
                    "Failed to upload image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    fun Publish(view: View) {
        uploadImage()
        finish()
    }

    data class Post(
        var uid: String = "",
        var url: String = "",
        var description: String = "",
        var likeCount: Int = 0,
        var lat: String = "",
        var lng: String = "",
        var likes: MutableMap<String, Boolean> = HashMap(),
        @ServerTimestamp
        var timestamp: Timestamp? = null
    )
}
