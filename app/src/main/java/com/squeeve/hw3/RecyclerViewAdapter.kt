package com.squeeve.hw3

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso

class RecyclerViewAdapter : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
    private lateinit var currentUser: FirebaseUser
    private lateinit var keyList: List<String>
    private lateinit var key_to_Post: HashMap<String, PostModel>
    private var currentMarker: Marker? = null
    private lateinit var itemClickListener: ItemClickListener

    constructor(key_to_Post: HashMap<String, PostModel>, keyList: List<String>, itemClickListener: ItemClickListener) {
        this.key_to_Post = key_to_Post
        this.keyList = keyList
        this.itemClickListener = itemClickListener
        val mAuth = FirebaseAuth.getInstance()
        this.currentUser = mAuth.currentUser!!
    }

    override fun getItemCount(): Int {
        return keyList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_view, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val db = FirebaseDatabase.getInstance()
        val firestoreDb = FirebaseFirestore.getInstance()
        val u: PostModel = key_to_Post[keyList[position]]!!
        val imagePostRef = firestoreDb.collection("ImagePosts").document(u.postKey)
        val uid = u.uid
        holder.uRef = db.getReference("Users").child(uid)
        holder.uRef!!.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                holder.fNameView.text =
                    holder.fNameView.context.getString(R.string.holder_first_name, user!!.displayName)
                holder.emailView.text = holder.fNameView.context.getString(R.string.holder_email, user.email)
                holder.phoneView.text =
                    holder.fNameView.context.getString(R.string.holder_phone_num, user.phone)
                holder.dateView.text = holder.fNameView.context.getString(R.string.holder_date_created, u.date)
                if (snapshot.child("profileImage").exists()) {
                    Picasso.get().load("profilePicture")
                        .transform(CircleTransform())
                        .into(holder.profileImage)
                    holder.profileImage.visibility = View.VISIBLE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("RecyclerViewAdapter", "Error: ${error.message}")
            }
        })

        imagePostRef.addSnapshotListener { value, error ->
            if (error != null) {
                Log.e("RecyclerViewAdapter", "Image_post_ref listener Error: ${error.message}")
                return@addSnapshotListener
            }
            val post = value?.toObject(PhotoPreview.Post::class.java)
            if (post != null) {
                val pathRef = FirebaseStorage.getInstance().getReference("images/${u.url}")
                pathRef.downloadUrl.addOnSuccessListener {
                    Picasso.get().load(it).into(holder.imageView)
                }
                holder.likeCount.text = String.format("%d Likes", post.likeCount)
                if (post.likes.getOrDefault(currentUser.uid, false)) {
                    holder.likeBtn.setImageDrawable(ContextCompat.getDrawable(holder.likeBtn.context, R.drawable.like_active))
                } else {
                    holder.likeBtn.setImageDrawable(ContextCompat.getDrawable(holder.likeBtn.context, R.drawable.like_disabled))
                }
            } else {
                Log.e("RecyclerViewAdapter", "Post is null")
            }
        }

        holder.likeBtn.setOnClickListener {
            firestoreDb.runTransaction { transaction ->
                val postSnapshot = transaction.get(imagePostRef)
                val post = postSnapshot.toObject(PhotoPreview.Post::class.java)
                if (post!!.likes.containsKey(currentUser.uid)) {
                    // Unlike the post and remove self from stars
                    post.likeCount -= 1
                    post.likes.remove(currentUser.uid)
                } else {
                    // Star the post and add self to stars
                    post.likeCount += 1
                    post.likes[currentUser.uid] = true
                }
                transaction.update(imagePostRef, "likeCount", post.likeCount)
                transaction.update(imagePostRef, "likes", post.likes)
                null
            }

            holder.descriptionView.text = u.description
            
            holder.imageView.setOnClickListener {
                if (currentMarker != null)  {
                    currentMarker!!.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_grey))
                }
                u.m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_red))
                currentMarker = u.m
                itemClickListener.onItemClick(currentMarker!!.position)
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var fNameView: TextView
        var emailView: TextView
        var phoneView: TextView
        var dateView: TextView
        var descriptionView: TextView
        var imageView: ImageView
        var likeBtn: ImageView
        var likeCount: TextView
        var uRef: DatabaseReference? = null
        var profileImage: ImageView

        init {
            fNameView = view.findViewById(R.id.fname_view)
            emailView = view.findViewById(R.id.email_view)
            phoneView = view.findViewById(R.id.phone_view)
            dateView = view.findViewById(R.id.date_view)
            descriptionView = view.findViewById(R.id.description)
            profileImage = view.findViewById(R.id.userImage)
            imageView = view.findViewById(R.id.postImg)
            likeBtn = view.findViewById(R.id.likeBtn)
            likeCount = view.findViewById(R.id.likeCount)
        }
    }
}