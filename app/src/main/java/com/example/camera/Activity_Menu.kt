package com.example.camera
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class Activity_Menu : AppCompatActivity() {

    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var nameSurnameTextView: TextView
    private lateinit var newProjectButton: Button
    private lateinit var myProjectsButton: Button
    private lateinit var logoutButton: ImageButton
    private lateinit var projectsList: MutableList<String>
    private lateinit var username : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        // Retrieve user profile data from intent extras
        username = intent.getStringExtra("username").toString()
        // val email = intent.getStringExtra("email")
        // val name = intent.getStringExtra("name")
        // val surname = intent.getStringExtra("surname")

        Log.d("user -----------> ", "$username ")
        // Initialize views
        usernameTextView = findViewById(R.id.textViewUsername)
        logoutButton = findViewById<ImageButton?>(R.id.btnLogout)

        usernameTextView.text = "$username"

        newProjectButton = findViewById(R.id.btnNewProject)
        myProjectsButton = findViewById(R.id.btnMyProjects)

        // Set up new project button click listener
        newProjectButton.setOnClickListener {
            // Show a title input popup when "Start a New Project" button is clicked
            showTitleInputDialog()
        }

        // Set up my projects button click listener
        myProjectsButton.setOnClickListener {
            // Start ProjectsActivity when "My Projects" button is clicked
            // and pass to it the List of projects
            fetchUserProjectsandStart()
        }

        // Set up logout button click listener
        logoutButton.setOnClickListener {

            // Clear the token stored in SharedPreferences
            clearTokenFromSharedPreferences()

            // Handle logout logic, e.g., clear SharedPreferences and navigate to login screen
            val intent = Intent(applicationContext, Activity_SignIn::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            showToast("Logout done")
        }
    }

    private fun fetchUserProfile() {
        // Make a network request to Flask /profile
        // Use an HTTP client library like Retrofit or OkHttp for network requests

        // User data class with 'username', 'email', 'name', and 'surname' properties
        val profileUrl = "https://RicGobs.pythonanywhere.com/menu"
        // Get the JWT from SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("jwtToken", "")

        // Make a GET request to fetch user profile
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(profileUrl)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", responseBody ?: "Response body is null")

                // Parse the JSON response to get user profile information
                try {
                    val jsonResponse = JSONObject(responseBody)
                    val get_username = jsonResponse.getString("username")
                    val email = jsonResponse.getString("email")
                    val name = jsonResponse.getString("name")
                    val surname = jsonResponse.getString("surname")

                    username = get_username

                    // Update UI with user profile information
                    runOnUiThread {
                        usernameTextView.text = "$name"
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun showTitleInputDialog() {
        fetchUserProjects()
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.activity_dialog_menu, null)

        // Initialize AlertDialog builder with the custom layout
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)

        // Get reference to EditText in the custom layout
        val inputTitle = dialogView.findViewById<EditText>(R.id.editTextTitle)

        // Set up the dialog appearance
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Transparent background
        dialog.show()

        // Set up the "OK" button click listener
        dialogView.findViewById<Button>(R.id.buttonOK).setOnClickListener {
            val title = inputTitle.text.toString()
            if (projectsList.any { project -> project == title }) {
                // Show a warning Toast and return
                Toast.makeText(this, "Change Name", Toast.LENGTH_SHORT).show()
            } else {
                // Do something with the title, e.g., send it to the server or use it locally
                // Toast.makeText(this, "Project Title: $title", Toast.LENGTH_SHORT).show()
                Toast.makeText(this, "Plantation Time", Toast.LENGTH_SHORT).show()
                // Start Activity_Augmented_Reality for anchor resolution
                val intent = Intent(this, Activity_Augmented_Reality::class.java).apply {
                    putExtra("projectTitle", title) // Pass the project title to Activity_Augmented_Reality
                }
                startActivity(intent)
                dialog.dismiss() // Dismiss the dialog
            }
        }
    }

    private fun fetchUserProjects() {
        // Make a network request to Flask /get_projects
        // Use an HTTP client library like Retrofit or OkHttp for network requests

        val projectsUrl = "https://RicGobs.pythonanywhere.com/get-plantations"
        // Get the JWT from SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("jwtToken", "")

        // Make a GET request to fetch user projects
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(projectsUrl)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", responseBody ?: "Response body is null")

                // Parse the JSON response to get user projects information
                try {
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.getBoolean("success")) {
                        // If the "success" key is true, check if "projects" is a JSON array
                        if (jsonResponse.has("projects") && jsonResponse.get("projects") is JSONArray) {
                            val jsonArray = jsonResponse.getJSONArray("projects")

                            // Update RecyclerView adapter with projects data
                            projectsList = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                val projectName = jsonArray.getJSONObject(i).getString("project_name")
                                projectsList.add(projectName)
                            }
                        } else {
                            Log.e("ProjectsActivity", "Invalid format: 'projects' key not found or not a JSONArray")
                        }
                    } else {
                        Log.e("ProjectsActivity", "Request was not successful")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun fetchUserProjectsandStart() {
        // Make a network request to Flask /get_projects
        // Use an HTTP client library like Retrofit or OkHttp for network requests

        val projectsUrl = "https://RicGobs.pythonanywhere.com/get-plantations"
        // Get the JWT from SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("jwtToken", "")

        // Make a GET request to fetch user projects
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(projectsUrl)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", responseBody ?: "Response body is null")

                // Parse the JSON response to get user projects information
                try {
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.getBoolean("success")) {
                        // If the "success" key is true, check if "projects" is a JSON array
                        if (jsonResponse.has("projects") && jsonResponse.get("projects") is JSONArray) {
                            val jsonArray = jsonResponse.getJSONArray("projects")

                            // Update RecyclerView adapter with projects data
                            projectsList = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                val projectName = jsonArray.getJSONObject(i).getString("project_name")
                                if (jsonArray.getJSONObject(i).getString("shared_with") == "0"){
                                    projectsList.add(projectName)
                                }
                            }

                            startProjectsActivity(projectsList)


                        } else {
                            Log.e("ProjectsActivity", "Invalid format: 'projects' key not found or not a JSONArray")
                        }
                    } else {
                        Log.e("ProjectsActivity", "Request was not successful")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun startProjectsActivity(projectsList: MutableList<String>) {
        // Convert the list of triples to a format that can be easily serialized

        // Create an Intent to start Activity_Augmented_Reality
        val intent = Intent(this, Activity_projects::class.java)

        // Pass relevant data as extras to Activity_Augmented_Reality
        intent.putExtra("project_list", ArrayList(projectsList))
        intent.putExtra("username", username)

        // Start Activity_Augmented_Reality
        startActivity(intent)
    }

    private fun startSharedProjectsActivity(projectsList: MutableList<String>) {

        // Create an Intent to start Activity_Augmented_Reality
        val intent = Intent(this, Activity_projects_MultiUser::class.java)

        // Pass relevant data as extras to Activity_Augmented_Reality
        intent.putExtra("project_list", ArrayList(projectsList))

        // Start SharedProjectsActivity
        startActivity(intent)
    }
    private fun clearTokenFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("jwtToken")
        editor.apply()
    }
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

}
