package com.example.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


class Activity_projects : AppCompatActivity() {

    private lateinit var myProjectsAdapter: Activity_projects_Adapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var SharedProjectsButton: ImageButton
    private lateinit var backToProfileButton: ImageButton
    private lateinit var username: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plantation)

        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewProjects)
        SharedProjectsButton = findViewById<ImageButton?>(R.id.btnSharedProjects)
        backToProfileButton = findViewById<ImageButton?>(R.id.btnBackToProfile)

        // Get user projects from Activity_Menu
        val projectsList = intent.getStringArrayListExtra("project_list") ?: emptyList<String>()
        username = intent.getStringExtra("username").toString()

        // Set up RecyclerView and Adapter
        myProjectsAdapter = Activity_projects_Adapter { projectName ->
            // Handle click event for the project
            Log.d("ProjectsActivity", "Clicked on project: $projectName")
            fetchProjectDetails(projectName)
        }

        recyclerView.adapter = myProjectsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        myProjectsAdapter.updateData(projectsList)

        // Set up my projects button click listener
        SharedProjectsButton.setOnClickListener {
            // Start ProjectsActivity when "My Projects" button is clicked
            // and pass to it the List of projects
            fetchSharedUserProjectsandStart()
        }
        // Set up back to profile button click listener
        backToProfileButton.setOnClickListener {
            // Navigate back to Activity_Menu when "Back to Profile" button is clicked
            // Create an intent to start Activity_Menu and put user profile data as extras
            val intent = Intent(applicationContext, Activity_Menu::class.java)
            intent.putExtra("username", username)
            startActivity(intent)
            finish() // Close the login activity
        }
    }

    private fun showTitleInputDialog(projectId: Int, projectTitle: String, anchorIdList: List<Triple<String, String, String>>) {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.activity_dialog_plantation, null)

        // Initialize AlertDialog builder with the custom layout
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)

        // Set up the dialog appearance
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Transparent background

        // Set the text of textViewTitle to the projectTitle
        val textViewTitle = dialogView.findViewById<TextView>(R.id.textViewTitle)
        textViewTitle.text = projectTitle


        // Set up the "OK" button click listener
        dialogView.findViewById<Button>(R.id.buttonEdit).setOnClickListener {
            // Start Activity_Augmented_Reality for anchor resolution
            startActivity_Augmented_Reality(projectId, projectTitle, anchorIdList)
            dialog.dismiss() // Dismiss the dialog
        }

        // Set up the "Share" button click listener
        dialogView.findViewById<Button>(R.id.buttonShared).setOnClickListener {
            // Show dialog for entering username
            shareProject(projectId, projectTitle)
            dialog.dismiss() // Dismiss the dialog
        }

        // Set up the "Delete" button click listener
        dialogView.findViewById<Button>(R.id.buttonDelete).setOnClickListener {
            // Call the deleteProject function passing the projectId and projectTitle
            deleteProject(projectId, projectTitle, myProjectsAdapter)
            dialog.dismiss() // Dismiss the dialog
        }

        dialog.show()
    }

    private fun shareProject(projectId: Int, projectTitle: String) {
        val url = "https://RicGobs.pythonanywhere.com/send-plantation"
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("jwtToken", "")

        val json = JSONObject().apply {
            put("project_name", projectTitle)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", responseBody ?: "Response body is null")

                // Parse the JSON response to get project details information
                try {
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.getBoolean("success")) {
                        // Handle successful response
                        // Show toast on completion
                        showCompletionToast("Plantation visible to the World")
                    }
                    else{
                        showCompletionToast("Error. ${jsonResponse.getString("message")}")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun deleteProject(projectId: Int, projectTitle: String, adapter: Activity_projects_Adapter) {
        val url = "https://RicGobs.pythonanywhere.com/delete-plantation"
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("jwtToken", "")

        val json = JSONObject().apply {
            put("project_name", projectTitle)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", responseBody ?: "Response body is null")

                // Parse the JSON response
                try {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.getBoolean("success")) {
                        runOnUiThread {
                            // Update adapter data after deletion
                            fetchUserProjects()
                            // Handle successful deletion
                            Toast.makeText(applicationContext, "Oh no, Plantation deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else{
                        showCompletionToast("Error. ${jsonResponse.getString("message")}")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }


    private fun showCompletionToast(message: String) {
        // This needs to be executed on the UI thread
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }




    private fun fetchProjectDetails(projectName: String) {
        // Make a network request to Flask /project/{project_name}
        // Use an HTTP client library like Retrofit or OkHttp for network requests

        val projectDetailsUrl = "https://RicGobs.pythonanywhere.com/plantation/$projectName"
        // Get the JWT from SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val authToken = sharedPreferences.getString("jwtToken", "")

        // Make a GET request to fetch project details
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(projectDetailsUrl)
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

                // Parse the JSON response to get project details information
                try {
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.getBoolean("success")) {
                        // Extract relevant information from the JSON response
                        val projectId = jsonResponse.getInt("project_id")
                        val projectTitle = jsonResponse.getString("project_title")

                        // Check if the JSON response contains anchor details
                        if (jsonResponse.has("anchors") && jsonResponse.get("anchors") is JSONArray) {
                            val anchorsArray = jsonResponse.getJSONArray("anchors")
                            val anchorsList = mutableListOf<Triple<String, String, String>>() // Pair of anchorId and model

                            // Iterate through the anchorsArray and add each anchor to the list
                            for (i in 0 until anchorsArray.length()) {
                                val anchorObject = anchorsArray.getJSONObject(i)
                                val anchorId = anchorObject.getString("anchor_id")
                                val model = anchorObject.getString("model")
                                val scaling = anchorObject.getString("scaling")
                                anchorsList.add(Triple(anchorId, model, scaling))
                            }

                            // Do showTitleInputDialog and pass the relevant data at the end
                            runOnUiThread {
                                showTitleInputDialog(projectId, projectTitle, anchorsList)
                            }

                        } else {
                            // Handle the case where no anchor details are present
                            Log.e("ProjectsActivity", "Invalid format: 'anchors' key not found or not a JSONArray")
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    // Handle JSON parsing exception
                }
            }

        })
    }

    private fun fetchSharedUserProjectsandStart() {
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
                            val projectsListShared = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                val projectName = jsonArray.getJSONObject(i).getString("project_name")
                                if (jsonArray.getJSONObject(i).getString("shared_with") == "1" && jsonArray.getJSONObject(i).getString("shared_with") != null){
                                    projectsListShared.add(projectName)
                                }
                            }

                            Log.d("user and projects", "$username and $projectsListShared")

                            startSharedProjectsActivity(projectsListShared)

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

    private fun startSharedProjectsActivity(projectsList: MutableList<String>) {

        // Create an Intent to start Activity_Augmented_Reality
        val intent = Intent(this, Activity_projects_MultiUser::class.java)

        // Pass relevant data as extras to Activity_Augmented_Reality
        intent.putExtra("project_list", ArrayList(projectsList))
        intent.putExtra("username", username)

        // Start SharedProjectsActivity
        startActivity(intent)
    }

    private fun startActivity_Augmented_Reality(projectId: Int, projectTitle: String, anchorIdList: List<Triple<String, String, String>>) {
        // Convert the list of pairs to a format that can be easily serialized
        val anchorIdArrayList = ArrayList<HashMap<String, Any>>()

        for (triple in anchorIdList) {
            val hashMap = hashMapOf<String, Any>()
            hashMap["anchor_id"] = triple.first
            hashMap["model"] = triple.second
            hashMap["scaling"] = triple.third
            anchorIdArrayList.add(hashMap)
        }

        // Create an Intent to start Activity_Augmented_Reality
        val arIntent = Intent(this, Activity_Augmented_Reality::class.java)

        // Pass relevant data as extras to Activity_Augmented_Reality
        arIntent.putExtra("project_id", projectId)
        arIntent.putExtra("project_title", projectTitle)
        arIntent.putExtra("anchor_id_list", anchorIdArrayList)

        // Start Activity_Augmented_Reality
        startActivity(arIntent)
    }


    // Not used now, because we do the fetchUserProjects in the Activity_Menu, But usefull for future improvement
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
                            val projectsList = mutableListOf<String>()
                            for (i in 0 until jsonArray.length()) {
                                if (jsonArray.getJSONObject(i).getString("shared_with") == "0") {
                                    val projectName =
                                        jsonArray.getJSONObject(i).getString("project_name")

                                    projectsList.add(projectName)
                                }
                            }
                            Log.d("updated projects", "$projectsList")
                            runOnUiThread {
                                // Ensure UI updates are done on the main thread
                                myProjectsAdapter.updateData(projectsList)
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
}
