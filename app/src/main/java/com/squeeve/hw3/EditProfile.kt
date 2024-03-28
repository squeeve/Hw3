package com.squeeve.hw3

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
import java.io.File
import java.io.IOException
import java.util.UUID

class EditProfile : AppCompatActivity(), PopupMenu.OnMenuItemClickListener {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private lateinit var displayName: EditText
    private lateinit var phoneNumber: EditText
    private lateinit var usersRef: DatabaseReference
    private lateinit var profileImage: ImageView
    private var currentPhotoPath: String = ""
    /*
    private val REQUEST_FOR_CAMERA = 9 // 0011 in the original code
    private val OPEN_FILE = 10 // 0012 in the original code
     */
    private var imageUri: Uri? = null

    private val mTakePicture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ::onActivityResult)

    private val mGetContent = registerForActivityResult(
        ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            uploadImage()
        } else {
            Toast.makeText(this, "Couldn't find path to upload to.", Toast.LENGTH_SHORT).show()
        }}

    private fun onActivityResult(result: ActivityResult) {
        if (result.data == null) {
            Toast.makeText(this, "Couldn't capture this image. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        phoneNumber = findViewById(R.id.phoneNumberText)
        displayName = findViewById(R.id.displayNameText)
        profileImage = findViewById(R.id.profileImage)
        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser!!
        usersRef = FirebaseDatabase.getInstance().getReference("Users/${currentUser.uid}")
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                phoneNumber.setText(snapshot.child("phone").value.toString())
                displayName.setText(snapshot.child("displayName").value.toString())
                if (snapshot.child("profilePicture").exists()) {
                    Picasso.get().load(snapshot.child("profilePicture").value.toString())
                        .transform(CircleTransform())
                        .into(profileImage)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EditProfile, "Couldn't fetch user data.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun uploadImage() {
        val filenameInStorage = UUID.randomUUID().toString()
        val imageRef = FirebaseStorage.getInstance().getReference("images/$filenameInStorage.jpg")
        imageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    usersRef.child("profilePicture").setValue(uri.toString()).addOnSuccessListener {
                        Picasso.get().load(uri.toString())
                            .transform(CircleTransform())
                            .into(profileImage)
                    }
                }.addOnFailureListener { e ->
                    Toast.makeText(
                        this@EditProfile,
                        "Couldn't fetch the image URL: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this@EditProfile,
                    "Couldn't upload the image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    fun uploadProfilePhoto(view: View) {
        val popup = PopupMenu(this, view)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.popup, popup.menu)
        popup.setOnMenuItemClickListener(this)
        popup.show()
    }

    fun Save(view: View) {
        if (displayName.text.toString().isEmpty() || phoneNumber.text.toString().isEmpty()) {
            Toast.makeText(this, "Please fill out your display name and phone number.", Toast.LENGTH_SHORT).show()
            return
        }
        usersRef.child("phone").setValue(phoneNumber.text.toString())
        usersRef.child("displayName").setValue(displayName.text.toString())
        Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        var photoFile: File? = null
        try {
            photoFile = UserHome.createImageFile(this)
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
            mTakePicture.launch(takePictureIntent)
        } else {
            Toast.makeText(this, "Couldn't find a camera Activity; please try again!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when(itemId) {
            R.id.take_photo -> {
                takePhoto()
                return true
            }
            R.id.upload -> {
                mGetContent.launch("image/*")
                return true
            }
        }
        return false
    }
}
