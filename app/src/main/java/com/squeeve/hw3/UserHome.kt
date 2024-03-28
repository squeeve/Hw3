package com.squeeve.hw3

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryDataEventListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.Throws
import kotlin.toString

class UserHome : AppCompatActivity(), OnMapReadyCallback, ItemClickListener {
    private lateinit var locationCallback: LocationCallback
    private val REQUEST_FOR_LOCATION = 10 // Octal 0012 = 10, from original code
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mAuth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private var imageUri: Uri? = null
    private lateinit var myRecyclerAdapter : RecyclerViewAdapter
    private val db = FirebaseDatabase.getInstance()
    private val firestore_db = FirebaseFirestore.getInstance()
    private val geo_fire_ref = db.getReference("/geofire")
    private val geoFire = GeoFire(geo_fire_ref)
    private var geoQuery: GeoQuery? = null
    private lateinit var currentPhotoPath: String
    private lateinit var mMap: GoogleMap
    private var key_to_Post = HashMap<String, PostModel>()
    private var keyList = ArrayList<String>()
    private lateinit var recyclerView: RecyclerView

    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ::onActivityResult)

    override fun onItemClick(latLng: LatLng) {
        val cameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(12f)
            .build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    companion object {
        @Throws(IOException::class)
        fun createImageFile(context: Context): File {
            Log.d("UserHome", "I at least made it to this function...")
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_" + timeStamp + "_"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            return File.createTempFile(imageFileName, ".jpg", storageDir)
        }
    }

    fun newLocation(lastLocation: Location) {
        Log.d("UserHome", "newLocation: Am I making it here?")
        if (geoQuery != null) {
            geoQuery!!.center = com.firebase.geofire.GeoLocation(lastLocation.latitude, lastLocation.longitude)
        } else {
            Log.d("UserHome", "CONTRARY TO EXPECTATIONS, GEOQUERY IS NULL.")
            geoQuery = geoFire.queryAtLocation(
                com.firebase.geofire.GeoLocation(lastLocation.latitude, lastLocation.longitude),
                10.0
            )
            geoQuery!!.addGeoQueryDataEventListener(object : GeoQueryDataEventListener {
                override fun onDataEntered(snapshot: DataSnapshot, location: com.firebase.geofire.GeoLocation) {
                    val postKey: String = snapshot.key!!
                    if (key_to_Post.containsKey(postKey)) {
                        return
                    }
                    firestore_db.collection("ImagePosts").document(postKey).get().addOnSuccessListener { docSnap ->
                        Log.i("UserHome", "Got post with key: $docSnap")
                        val post = docSnap.toObject(PostModel::class.java)
                        val temp = mMap.addMarker(MarkerOptions()
                            .position(LatLng((docSnap.get("lat").toString().toDouble()), docSnap.get("lon").toString().toDouble()))
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_grey)))
                        val postModel = PostModel(
                            docSnap.get("uid").toString(),
                            docSnap.get("description").toString(),
                            docSnap.get("url").toString(),
                            docSnap.getDate("timestamp").toString(),
                            docSnap.id,
                            temp!!)
                        key_to_Post[docSnap.id] = postModel
                        keyList.add(docSnap.id)
                        temp.tag = docSnap.id
                        myRecyclerAdapter.notifyItemInserted(keyList.size - 1)
                        recyclerView.scrollToPosition(keyList.size - 1)
                    }.addOnFailureListener { e ->
                        Log.e("UserHome", "Error getting post ${e.message}")
                    }
                }

                override fun onDataExited(dSnap: DataSnapshot) {}
                override fun onDataMoved(dSnap: DataSnapshot, location: com.firebase.geofire.GeoLocation) {}
                override fun onDataChanged(dSnap: DataSnapshot, location: com.firebase.geofire.GeoLocation) {}
                override fun onGeoQueryReady() {}
                override fun onGeoQueryError(e: DatabaseError) {
                    Log.e("UserHome", "Error querying geofire: ${e.message}")
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_home)

        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser!!
        recyclerView = findViewById(R.id.recycler_view)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        layoutManager.scrollToPosition(0)
        recyclerView.layoutManager = layoutManager

        myRecyclerAdapter = RecyclerViewAdapter(key_to_Post, keyList, this)
        recyclerView.adapter = myRecyclerAdapter
        initializeLocationClient()
        val mapFrag = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFrag.getMapAsync(this)
    }

    private fun initializeLocationClient() {
        Log.d("UserHome", "initializeLocationClient: Am I making it here?")
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val MAX_UPDATE_DELAY_INTERVAL: Long = 1000*1000 // 20 seconds
        val UPDATE_INTERVAL: Long = 10*1000 // 10 seconds
        val FASTEST_INTERVAL: Long = 2000 // 2 seconds
        mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .setMaxUpdateAgeMillis(MAX_UPDATE_DELAY_INTERVAL)
            .build()
        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(mLocationRequest)
            .build()
        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation
                newLocation(lastLocation!!)
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        Log.d("UserHome", "initializeLocationClient: Requesting location updates...")
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper())
        Log.d("UserHome", "initializeLocationClient: Finished requesting location updates.")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.actionbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.signout) {
            mAuth.signOut()
            finish()
            return true
        } else if (itemId == R.id.edit_profile) {
            startActivity(Intent(this@UserHome, EditProfile::class.java))
            Log.d("UserHome", "onOptionsItemSelected: Successfully called EditProfile.")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun uploadNewPhoto(view: View) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, "This feature requires a camera!", Toast.LENGTH_SHORT).show()
            return
        }
        takePhoto()
    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        var photoFile: File? = null
        try {
            photoFile = createImageFile(this)
            currentPhotoPath = photoFile.absolutePath
        } catch (ex: IOException) {
            Log.e("UserHome", "Error creating image file: ${ex.message}")
        }

        if (photoFile == null) {
            Toast.makeText(this, "Couldn't capture the image; please try again!", Toast.LENGTH_SHORT).show()
            return
        }

        // Continue only if the File was successfully created
        val photoURI: Uri = FileProvider.getUriForFile(this, "com.squeeve.hw3.fileprovider", photoFile)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            cameraActivityResultLauncher.launch(takePictureIntent)
        } else {
            Toast.makeText(this, "Couldn't find a camera Activity; please try again!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onActivityResult(result: ActivityResult) {
        if (result.data == null) {
            Toast.makeText(this, "Couldn't capture the image; please try again!", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PhotoPreview::class.java)
        intent.putExtra("uri", currentPhotoPath)
        startActivity(intent)
        Log.d("UserHome", "onActivityResult: Successfully called PhotoPreview.")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FOR_LOCATION) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                    return
                }
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper())
            } else {
                Toast.makeText(this, "Location permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener { marker ->
            val key = marker.tag as String
            recyclerView.scrollToPosition(keyList.indexOf(key))
            //show the image
            Toast.makeText(this@UserHome, "Marker clicked", Toast.LENGTH_SHORT).show()
            false
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_FOR_LOCATION)
            return
        }
        mMap.isMyLocationEnabled = true
    }
}
