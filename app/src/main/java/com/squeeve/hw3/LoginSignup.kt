package com.squeeve.hw3

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class LoginSignup : AppCompatActivity() {
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var displayName: EditText
    private lateinit var phoneNumber: EditText

    private lateinit var mAuth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private lateinit var signupButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_signup)

        email = findViewById(R.id.emailText)
        password = findViewById(R.id.passwordText)
        phoneNumber = findViewById(R.id.phoneNumberText)
        displayName = findViewById(R.id.displayNameText)
        signupButton = findViewById(R.id.signupBtn)

        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser
        updateUI()
    }

    private fun updateUI() {
        if (currentUser != null) {
            findViewById<TextInputLayout>(R.id.displayNameLayout).visibility = View.GONE
            findViewById<TextInputLayout>(R.id.phoneNumberLayout).visibility = View.GONE
            signupButton.setVisibility(View.GONE)
        }
    }

    private fun saveUserDataToDB() {
        val db = FirebaseDatabase.getInstance()
        val usersRef = db.getReference("Users")
        usersRef.child(currentUser!!.uid).setValue(
            User(displayName.text.toString(), email.text.toString(), phoneNumber.text.toString())
        )
    }

    fun ResetPassword(view: View) {
        if (email.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
        }

        mAuth.sendPasswordResetEmail(email.text.toString()).addOnFailureListener { exception ->
            Toast.makeText(
                this@LoginSignup,
                "Failed to send password reset email: $exception",
                Toast.LENGTH_SHORT
            ).show()
        }.addOnSuccessListener {
            Toast.makeText(this, "Email sent!", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmailVerification(view: View) {
        if (currentUser == null) {
            Toast.makeText(this, "No user found", Toast.LENGTH_SHORT).show()
            return
        }

        currentUser!!.sendEmailVerification().addOnFailureListener { exception ->
            Toast.makeText(
                this@LoginSignup,
                "Failed to send email verification: $exception",
                Toast.LENGTH_SHORT
            ).show()
        }.addOnSuccessListener {
            Toast.makeText(this, "Email sent!", Toast.LENGTH_SHORT).show()
        }
    }

    fun Login(view: View) {
        if (email.text.isNullOrEmpty() || password.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter your email and password", Toast.LENGTH_SHORT).show()
            return
        }

        mAuth.signInWithEmailAndPassword(email.text.toString(), password.text.toString())
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this@LoginSignup,
                    "Failed to log in: $exception",
                    Toast.LENGTH_SHORT
                ).show()
            }.addOnSuccessListener {
            if (currentUser == null) {
                Toast.makeText(this, "An impossible error.. Please try again!", Toast.LENGTH_SHORT)
                    .show()
                return@addOnSuccessListener
            }
            if (currentUser!!.isEmailVerified) {
                startActivity(Intent(this, UserHome::class.java))
                finish()
            } else {
                Toast.makeText(this, "Please verify your email and re-log in.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun Signup(view: View) {
        if (email.text.isNullOrEmpty() || password.text.isNullOrEmpty() || phoneNumber.text.isNullOrEmpty() || displayName.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
            return
        }
        mAuth.createUserWithEmailAndPassword(email.text.toString(), password.text.toString())
            .addOnSuccessListener { authResult ->
                currentUser = authResult.user
                if (currentUser == null) {
                    Toast.makeText(
                        this,
                        "An impossible error.. Please try again!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }
                currentUser!!.sendEmailVerification().addOnFailureListener { exception ->
                    Toast.makeText(
                        this@LoginSignup,
                        "Please try again. Failed to send email verification: $exception",
                        Toast.LENGTH_SHORT
                    ).show()
                }.addOnSuccessListener() {
                    saveUserDataToDB()
                    Toast.makeText(this, "Email sent!", Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }.addOnFailureListener { exception ->
                Toast.makeText(
                    this@LoginSignup,
                    "Signup failed: $exception",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}