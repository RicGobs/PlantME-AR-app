package com.example.camera

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class Activity_SignUp : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val nameEditText: EditText = findViewById(R.id.editTextName)
        val surnameEditText: EditText = findViewById(R.id.editTextSurname)
        val emailEditText: EditText = findViewById(R.id.editTextEmail)
        val usernameEditText: EditText = findViewById(R.id.editTextUsername)
        val passwordEditText: EditText = findViewById(R.id.editTextPassword)
        val submitButton: Button = findViewById(R.id.buttonSubmit)

        // Check if extras are passed
        val emailExtra = intent.getStringExtra("email")
        val displaynameExtra = intent.getStringExtra("displayname")

        if (emailExtra != null && displaynameExtra != null) {
            // Extras are present, set email and display name EditText fields as unmodifiable with those values
            emailEditText.apply {
                setText(emailExtra)
                isEnabled = false
                isFocusable = false
            }

            // Split the display name into name and surname
            val spaceIndex = displaynameExtra.indexOf(' ')
            val name = displaynameExtra.substring(0, spaceIndex)
            val surname = displaynameExtra.substring(spaceIndex + 1)

            nameEditText.apply {
                setText(name)
                isEnabled = false
                isFocusable = false
            }
            surnameEditText.apply {
                setText(surname)
                isEnabled = false
                isFocusable = false
            }
        }



        submitButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val surname = surnameEditText.text.toString()
            val email = emailEditText.text.toString()
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            registerToServer(username, password, name, surname, email)

        }
    }

    private fun registerToServer(username: String, password: String, name: String, surname: String, email: String) {
        val json = JSONObject()
        json.put("username", username)
        json.put("password", password)
        json.put("name", name)
        json.put("surname", surname)
        json.put("email", email)

        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())

        val request = Request.Builder()
            .url("https://RicGobs.pythonanywhere.com/sign-up")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("Failed to connect to the server")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", responseBody ?: "Response body is null")

                try {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.getBoolean("success")

                    if (success) {
                        // Registration successful, navigate back to Activity_SignIn
                        runOnUiThread {
                            showToast("Signed Up")
                            val intent = Intent(applicationContext, Activity_SignIn::class.java)
                            startActivity(intent)
                            finish() // Close the registration activity
                        }
                    } else {
                        // Registration unsuccessful, show a toast message
                        showToast(jsonResponse.getString("message"))
                    }
                } catch (e: JSONException) {
                    // Handle the JSON parsing error
                    Log.e("JSON Parsing Error", e.message ?: "Unknown error")
                }
            }
        })
    }


    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
