package com.squeeve.hw3

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.CountDownTimer
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser


class MainActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser
    }

    override fun onResume() {
        super.onResume()
        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                if (currentUser == null) {
                    Toast.makeText(this@MainActivity, "No user found", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity, LoginSignup::class.java))
                    finish()
                } else {
                    if (currentUser!!.isEmailVerified) {
                        startActivity(Intent(this@MainActivity, UserHome::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@MainActivity, "Please verify your email and re-log in.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@MainActivity, LoginSignup::class.java))
                        finish()
                    }
                }
            }
        }.start()
    }
}
