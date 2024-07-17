
package com.example.camera

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Anchor
import com.google.ar.core.Anchor.CloudAnchorState
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.RotationsOrder
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.arcore.rotation
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.localRotation
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.CloudAnchorNode
import io.github.sceneview.ar.scene.destroy
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.gesture.MoveGestureDetector
import io.github.sceneview.gesture.RotateGestureDetector
import io.github.sceneview.gesture.ScaleGestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.math.quaternion
import io.github.sceneview.math.toQuaternion
import io.github.sceneview.model.isShadowCaster
import io.github.sceneview.model.renderableInstances
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

private var object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/small_bush.glb?alt=media&token=73266032-dd5f-4faf-9c41-0860a768ad59"
class Activity_Augmented_Reality : AppCompatActivity(R.layout.activity_augmented_reality) {
    lateinit var b : ImageButton
    lateinit var b1 : Button
    private lateinit var backToProfileButton: ImageButton
    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView
    lateinit var button_plants : ImageButton
    private val anchorsList = mutableListOf<Triple<AnchorNode?, String, Float3>>()

    var vis: Boolean = false

    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value // Hide or show loading view based on isLoading flag
        }

    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions() // Update UI instructions when anchorNode changes
            }
        }

    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions() // Update UI instructions when tracking failure reason changes
            }
        }

    // Function to update UI instructions based on tracking and anchor conditions
    fun updateInstructions() {
        val frame = sceneView.frame
        // Set instruction text based on tracking failure or anchor presence
        instructionText.text = (trackingFailureReason?.let {
            it.getDescription(this)
        } ?: if (anchorNode == null) {
            getString(R.string.tap) // Default instruction when no anchor
        } else {
            null // No change in instruction text if anchorNode is not null
        })

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set full screen flags and apply to rootView
        setFullScreen(
            findViewById(R.id.rootView),
            fullScreen = true,
            hideSystemBars = false,
            fitsSystemWindows = false
        )

        backToProfileButton = findViewById<ImageButton?>(R.id.btnBackToProfile)


        // Set up back to profile button click listener
        backToProfileButton.setOnClickListener {
            // Navigate back to Activity_Menu when "Back to Profile" button is clicked
            finish() // Close the ar activity
        }

        // Initialize views from layout
        instructionText = findViewById(R.id.instructionText)
        loadingView = findViewById(R.id.loadingView)
        sceneView = findViewById<ARSceneView?>(R.id.sceneView).apply {

            // Configure AR session settings
            planeRenderer.isEnabled = true // Enable plane rendering
            planeRenderer.isShadowReceiver
            configureSession { session, config ->
                // Set depth mode based on device support
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE // Use latest camera image for updates
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP // Set instant placement mode
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR // Enable environmental HDR light estimation
                config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED // Enable cloud anchor mode
                config.geospatialMode = Config.GeospatialMode.DISABLED // Disable geospatial mode
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL // Enable horizontal plane finding

                session.configure(config)
            }

            // Handle session updates
            onSessionUpdated = { _, frame ->
                frame.getUpdatedTrackables(Plane::class.java)

                // Evaluate feature map quality for visual data
                if (frame != null && anchorNode != null) {
                    val quality = sceneView.session?.estimateFeatureMapQualityForHosting(frame.camera.pose)
                    instructionText.text = when (quality) {
                        Session.FeatureMapQuality.INSUFFICIENT -> "Move around and put the plants!"
                        Session.FeatureMapQuality.SUFFICIENT -> "Start upload whenever you want!"
                        Session.FeatureMapQuality.GOOD -> "Start upload whenever you want!"
                        else -> instructionText.text // Keep the current instruction if quality is unknown
                    }
                }
            }

            // Handle tracking failure changes
            onTrackingFailureChanged = { reason ->
                this@Activity_Augmented_Reality.trackingFailureReason = reason
            }

            // Gesture listener for user interactions
            onGestureListener = object : GestureDetector.OnGestureListener {
                override fun onDown(e: MotionEvent, node: Node?) {
                }

                override fun onShowPress(e: MotionEvent, node: Node?) {
                }

                override fun onSingleTapUp(e: MotionEvent, node: Node?) {
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    node: Node?,
                    distance: Float2
                ) {
                }

                override fun onLongPress(e: MotionEvent, node: Node?) {
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    node: Node?,
                    velocity: Float2
                ) {
                }

                override fun onSingleTapConfirmed(e: MotionEvent, node: Node?) {
                    if (node == null) {
                        val hitResultList = frame?.hitTest(e.x, e.y)
                        hitResultList?.firstOrNull { hitResult ->
                            // Check if hit result is on a horizontal upward facing plane
                            hitResult.trackable is Plane && (hitResult.trackable as Plane).isPoseInPolygon(hitResult.hitPose) && (hitResult.trackable as Plane).type == Plane.Type.HORIZONTAL_UPWARD_FACING
                        }?.let { hitResult ->
                            // Create an anchor
                            val anchor = hitResult.createAnchor()

                            // Determine scale based on selected model
                            if (object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/small_bush.glb?alt=media&token=73266032-dd5f-4faf-9c41-0860a768ad59"){
                                addAnchorNode(anchor, Float3(0.8f, 0.8f, 0.8f))
                            }
                            else if(object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free__livistona_chinensis_-_fan_palm.glb?alt=media&token=99d32ea3-3310-479f-853c-93cdb02b4fac"){
                                addAnchorNode(anchor, Float3(0.4f, 0.4f, 0.4f))
                            }
                            else if(object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pilea_peperomioides_terracotta_pot.glb?alt=media&token=81dc6953-1d95-4393-baad-39fa2a17ecf6"){
                                addAnchorNode(anchor, Float3(0.7438163f, 0.7438163f, 0.7438163f))
                            }
                            else if(object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/cactus_verde.glb?alt=media&token=223b10a2-e7a7-4b36-ade9-8232cf537422"){
                                addAnchorNode(anchor, Float3(0.7438163f, 0.7438163f, 0.7438163f))
                            }
                            else if(object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/indoor_plant.glb?alt=media&token=d34307a2-e2e3-4efe-a516-4c5f89290804"){
                                addAnchorNode(anchor, Float3(0.1f, 0.1f, 0.1f))
                            }
                            else if(object_model=="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_plant.glb?alt=media&token=ed93bcab-666b-450a-9bde-6e0795922f9c"){
                                addAnchorNode(anchor, Float3(0.1438163f, 0.1438163f, 0.1438163f))
                            }
                            else if(object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_tree.glb?alt=media&token=f76dd719-2e22-4fa2-979b-74aefc95af24"){
                                addAnchorNode(anchor, Float3(0.11f, 0.11f, 0.11f))
                            }
                            else{
                                addAnchorNode(anchor, Float3(0.3438163f, 0.3438163f, 0.3438163f))
                            }
                        }
                    }
                }

                override fun onDoubleTap(e: MotionEvent, node: Node?) {
                }

                // Override method for handling double tap event
                override fun onDoubleTapEvent(e: MotionEvent, node: Node?) {
                    if(node!=null)
                    {
                        // Log information when double tap event happens on a node
                        Log.d("Pose", "Product pose: connected to gesture")
                        Log.d("Node", "Node= $node")
                        val dad: AnchorNode = node.parent as AnchorNode
                        val dadanchor : Anchor = dad.anchor
                        Log.d("DAD", "DAD= $dad")
                        Log.d("DAD", "DAD ANCHOR= $dadanchor")

                        // Remove the anchor node and associated data from the anchorsList
                        anchorsList.removeIf { (anchor, _, _) ->
                            anchor.toString() == dad.toString()
                        }
                        node.parent=null // Remove the node from its parent
                        node.destroy() // Destroy the node
                    }
                }

                override fun onContextClick(e: MotionEvent, node: Node?) {
                }

                override fun onMoveBegin(
                    detector: MoveGestureDetector,
                    e: MotionEvent,
                    node: Node?
                ) {
                }

                override fun onMove(detector: MoveGestureDetector, e: MotionEvent, node: Node?) {
                    if (node != null) {
                        val modelnode : ModelNode = node as ModelNode

                        modelnode.scale

                        Log.d("SCALE","Scale to units: ${modelnode?.scale}")

                    }
                }

                @SuppressLint("SuspiciousIndentation")
                override fun onMoveEnd(detector: MoveGestureDetector, e: MotionEvent, node: Node?) {
                    if (node != null) {
                        val modelnode : ModelNode = node as ModelNode

                        modelnode.scale

                        Log.d("SCALE","Scale to units: ${modelnode?.scale}")

                        if (node.parent is AnchorNode){
                            Log.d("ANCHOR NODE NEW (in teoria)", "${anchorNode?.anchor}")
                            Log.d("ANCHOR NODE NEW (in teoria)", "new: ${anchorNode?.anchor?.pose}")
                            for ((anchornode, model, scaling) in anchorsList){
                                if (anchornode.toString() == node.parent.toString()){
                                    // Update the entry with the new AnchorNode
                                    anchorsList.remove(Triple(anchornode, model, scaling))
                                    anchorsList.add(Triple(node.parent as AnchorNode, model, scaling))
                                    break // Exit the loop once the replacement is done
                                }
                            }
                        }

                    }
                }

                override fun onRotateBegin(detector: RotateGestureDetector, e: MotionEvent, node: Node?) {
                }

                override fun onRotate(
                    detector: RotateGestureDetector,
                    e: MotionEvent,
                    node: Node?
                ) {
                }

                override fun onRotateEnd(detector: RotateGestureDetector, e: MotionEvent, node: Node?) {
                }



                override fun onScaleBegin(
                    detector: ScaleGestureDetector,
                    e: MotionEvent,
                    node: Node?
                ) {

                }

                override fun onScale(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) {
                    if (node is ModelNode) {
                        scaleModelNode(node, detector)
                    } else if (node is AnchorNode) {
                        // Check if one of the children is a ModelNode
                        val modelNodeChild = node.childNodes.firstOrNull { it is ModelNode } as? ModelNode
                        modelNodeChild?.let { scaleModelNode(it, detector) }
                    }
                }

                override fun onScaleEnd(detector: ScaleGestureDetector, e: MotionEvent, node: Node?) {
                    if (node is ModelNode) {
                        scaleModelNode(node, detector)

                        if (node.parent is AnchorNode) {
                            updateAnchorList(node.parent as AnchorNode, node)
                        }
                    } else if (node is AnchorNode) {
                        // Check if one of the children is a ModelNode
                        val modelNodeChild = node.childNodes.firstOrNull { it is ModelNode } as? ModelNode
                        modelNodeChild?.let {
                            scaleModelNode(it, detector)
                            updateAnchorList(node, it)
                        }
                    }
                }

                private fun scaleModelNode(modelNode: ModelNode, detector: ScaleGestureDetector) {
                    val scaleFactor = detector.scaleFactor

                    // Adjust the scale based on the scaleFactor
                    val newScaleX = modelNode.scale.x * scaleFactor
                    val newScaleY = modelNode.scale.y * scaleFactor
                    val newScaleZ = modelNode.scale.z * scaleFactor

                    // Declare minScale with a default value
                    val minScale: Float = when {
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/small_bush.glb?alt=media&token=73266032-dd5f-4faf-9c41-0860a768ad59" -> 0.4f
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free__livistona_chinensis_-_fan_palm.glb?alt=media&token=99d32ea3-3310-479f-853c-93cdb02b4fac" -> 0.1f
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pilea_peperomioides_terracotta_pot.glb?alt=media&token=81dc6953-1d95-4393-baad-39fa2a17ecf6" -> 0.2f
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/cactus_verde.glb?alt=media&token=223b10a2-e7a7-4b36-ade9-8232cf537422" -> 0.3f
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/indoor_plant.glb?alt=media&token=d34307a2-e2e3-4efe-a516-4c5f89290804" -> 0.05f
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_plant.glb?alt=media&token=ed93bcab-666b-450a-9bde-6e0795922f9c" -> 0.05f
                        object_model == "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_tree.glb?alt=media&token=f76dd719-2e22-4fa2-979b-74aefc95af24" -> 0.05f
                        else -> 0.3f // Default value if none of the conditions match
                    }

                    // Calculate maxScale based on the assigned minScale
                    val maxScale = minScale + 0.7f

                    // Clamp the new scale values to stay within the limits
                    val clampedScaleX = newScaleX.coerceIn(minScale, maxScale)
                    val clampedScaleY = newScaleY.coerceIn(minScale, maxScale)
                    val clampedScaleZ = newScaleZ.coerceIn(minScale, maxScale)

                    // Set the clamped scale to the modelNode
                    modelNode.scale = Float3(
                        clampedScaleX.toFloat(),
                        clampedScaleY.toFloat(),
                        clampedScaleZ.toFloat()
                    )
                }

                private fun updateAnchorList(anchorNode: AnchorNode, modelNode: ModelNode) {
                    Log.d("SCALE", "Scale to units: ${modelNode?.scale}")

                    for ((anchornode, model, scaling) in anchorsList) {
                        if (anchornode.toString() == anchorNode.toString()) {
                            // Update the entry with the new AnchorNode
                            anchorsList.remove(Triple(anchornode, model, scaling))
                            anchorsList.add(Triple(anchornode, model, modelNode?.scale) as Triple<AnchorNode?, String, Float3>)
                            break // Exit the loop once the replacement is done
                        }
                    }
                }
            }
        }

        // Check if anchor ID is passed in the intent
        val projectTitle = intent.getStringExtra("projectTitle")
        val anchorIdList = intent.getSerializableExtra("anchor_id_list") as? ArrayList<HashMap<String, String>>


        if (!anchorIdList.isNullOrEmpty()) {
            // Anchor ID list is present, iterate over the list and resolve each anchor
            b1 = findViewById<Button?>(R.id.hostButton).apply {
                text = "h"
                setOnClickListener {
                    isClickable = false
                    isEnabled = false

                    val session = sceneView.session ?: return@setOnClickListener

                    lifecycleScope.launch {
                        isLoading = true
                        for (anchorData in anchorIdList) {
                            instructionText.text = "Resolving. . ."
                            val anchorId = anchorData["anchor_id"]
                            object_model = anchorData["model"].toString()
                            val scaling = anchorData["scaling"].toString()
                            Log.d("SCALE", "RESOLVED SCALE STRING $scaling")
                            if (!anchorId.isNullOrBlank()) {
                                // Resolve the anchor using the anchorId
                                val resolvedAnchor = session.resolveCloudAnchor(anchorId)
                                if (resolvedAnchor != null) {
                                    val resolvedpose = resolvedAnchor.pose
                                    Log.d("POSE RESOLVED", "Resolved pose = $resolvedpose")
                                    // Anchor resolved successfully, add anchor node
                                    val float3Object =
                                        scaling?.let { it1 -> parseFloat3FromString(it1) }
                                    addAnchorNode(resolvedAnchor, float3Object)
                                    Log.d(
                                        "Resolve",
                                        "Resolved $anchorId $object_model $float3Object"
                                    )
                                    runOnUiThread {
                                        isClickable = false
                                        isEnabled = false
                                    }
                                } else {
                                    // Handle anchor resolution failure
                                    val resolutionFailureToast = Toast.makeText(
                                        context,
                                        "Failed: $anchorId",
                                        Toast.LENGTH_LONG
                                    )
                                    resolutionFailureToast.show()
                                    Log.d("CloudAnchor", "Failed to resolve anchor: $anchorId")
                                    runOnUiThread {
                                        isClickable = true
                                        isEnabled = true

                                    }
                                }
                            }
                        }
                        instructionText.text = ""
                    }
                    isLoading = false
                }
            }
        } else {
            // No anchor ID passed, proceed with hosting logic
            b1 = findViewById<Button?>(R.id.hostButton).apply {
                setOnClickListener {

                    // Disable the button during the onClickListener execution
                    isClickable = false
                    isEnabled = false

                    val session = sceneView.session ?: return@setOnClickListener
                    val frame = sceneView.frame ?: return@setOnClickListener

                    if (sceneView.session?.estimateFeatureMapQualityForHosting(frame.camera.pose) == Session.FeatureMapQuality.INSUFFICIENT) {
                        val insufficientVisualDataToast = Toast.makeText(
                            context,
                            R.string.not,
                            Toast.LENGTH_LONG
                        )
                        insufficientVisualDataToast.show()
                        Log.d("CloudAnchor", "Move around the plant")
                        // Enable the button after showing the toast
                        isClickable = true
                        isEnabled = true

                        return@setOnClickListener
                    }

                    val anchorDataList = mutableListOf<JSONObject>()

                    // Iterate over anchorsList
                    for ((anchorNode, selectedModel, scaling) in anchorsList) {
                        val session = sceneView.session ?: continue

                        if (anchorNode != null) {
                            sceneView.addChildNode(CloudAnchorNode(sceneView.engine, anchorNode.anchor).apply {
                                isScaleEditable = false

                                isRotationEditable=false
                                host(session) { cloudAnchorId, state ->
                                    Log.d("CloudAnchor", "STATE: $state, CloudAnchorId: $cloudAnchorId")
                                    when (state) {
                                        CloudAnchorState.SUCCESS -> {
                                            Log.d("CloudAnchor", "Cloud anchor hosted successfully: $cloudAnchorId")

                                            // Create a JSON object for the anchor data
                                            val anchorData = JSONObject().apply {
                                                put("anchor_id", cloudAnchorId)
                                                put("model", selectedModel)
                                                put("scaling", scaling)
                                            }

                                            // Add the anchor data to the list
                                            anchorDataList.add(anchorData)

                                            Log.d("Actual Anchor Data List", "$anchorDataList")


                                            // Check if all anchors are hosted successfully
                                            if (anchorDataList.size == anchorsList.size) {
                                                // All anchors hosted, send the data to the server
                                                val projectTitle = intent.getStringExtra("projectTitle")

                                                // Create a JSON object to send to the server
                                                val requestBody = JSONObject().apply {
                                                    put("anchors", anchorDataList)
                                                    put("project_title", projectTitle)
                                                }

                                                val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                                                val authToken = sharedPreferences.getString("jwtToken", "")

                                                // Make a POST request to the Flask /anchors endpoint
                                                val client = OkHttpClient()
                                                val request = Request.Builder()
                                                    .url("https://RicGobs.pythonanywhere.com/post-plantation")
                                                    .header("Content-Type", "application/json")
                                                    .header("Authorization", "Bearer $authToken") // Include the JWT in the Authorization header
                                                    .post(RequestBody.create("application/json".toMediaTypeOrNull(), requestBody.toString()))
                                                    .build()

                                                client.newCall(request).enqueue(object : Callback {
                                                    override fun onFailure(call: Call, e: IOException) {
                                                        e.printStackTrace()
                                                        // Handle failure
                                                    }

                                                    override fun onResponse(call: Call, response: Response) {
                                                        // Handle the response from the server
                                                        val responseBody = response.body?.string()
                                                        Log.d("Response", responseBody ?: "Response body is null")

                                                        try {
                                                            val jsonResponse = JSONObject(responseBody)
                                                            val success = jsonResponse.optBoolean("success", false)

                                                            if (success) {
                                                                // Show a success Toast
                                                                runOnUiThread {
                                                                    Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                                                                }
                                                                runOnUiThread {
                                                                    isClickable = false
                                                                    isEnabled = false
                                                                }
                                                            } else {
                                                                // Show a failure Toast or handle the failure case as needed
                                                                runOnUiThread {
                                                                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                                                                }
                                                                runOnUiThread {
                                                                    isClickable = true
                                                                    isEnabled = true
                                                                }
                                                            }
                                                        } catch (e: JSONException) {
                                                            e.printStackTrace()
                                                            // Handle JSON parsing error
                                                        } finally {
                                                            // nothing to do
                                                        }
                                                    }
                                                })
                                            }
                                        }

                                        else -> {
                                            Log.d("CloudAnchor", "Cloud anchor hosting failed: $cloudAnchorId")
                                            val failureToast = Toast.makeText(
                                                context,
                                                "Cloud anchor hosting failed: $cloudAnchorId",
                                                Toast.LENGTH_LONG
                                            )
                                            failureToast.show()
                                            // Enable the button after showing the toast
                                            runOnUiThread {
                                                isClickable = true
                                                isEnabled = true
                                            }
                                        }
                                    }
                                }
                            })
                        }
                    }

                }
            }
        }

        button_plants = findViewById<ImageButton?>(R.id.btn).apply {
            setOnClickListener {
                showTitleInputDialog()
            }
        }
    }


    private fun showTitleInputDialog() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.activity_dialog_ar, null)

        // Initialize AlertDialog builder with the custom layout
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)

        // Set up the dialog appearance
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) // Transparent background

        dialogView.findViewById<ImageButton>(R.id.button1).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pilea_peperomioides_terracotta_pot.glb?alt=media&token=81dc6953-1d95-4393-baad-39fa2a17ecf6"
            dialog.dismiss() // Dismiss the dialog
        }
        dialogView.findViewById<ImageButton>(R.id.button2).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pothos_potted_plant_-_money_plant.glb?alt=media&token=396e3e3c-8bb8-43cb-8fec-5df578e800f2"
            dialog.dismiss() // Dismiss the dialog
        }
        dialogView.findViewById<ImageButton>(R.id.button3).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/small_bush.glb?alt=media&token=73266032-dd5f-4faf-9c41-0860a768ad59" //done
            dialog.dismiss() // Dismiss the dialog
        }
        dialogView.findViewById<ImageButton>(R.id.button4).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free__livistona_chinensis_-_fan_palm.glb?alt=media&token=99d32ea3-3310-479f-853c-93cdb02b4fac"
            dialog.dismiss() // Dismiss the dialog
        }
        dialogView.findViewById<ImageButton>(R.id.button5).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/indoor_plant.glb?alt=media&token=d34307a2-e2e3-4efe-a516-4c5f89290804"
            dialog.dismiss() // Dismiss the dialog
        }
        dialogView.findViewById<ImageButton>(R.id.button6).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_plant.glb?alt=media&token=ed93bcab-666b-450a-9bde-6e0795922f9c"
            dialog.dismiss() // Dismiss the dialog
        }
        dialogView.findViewById<ImageButton>(R.id.button7).setOnClickListener {
            object_model="https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_tree.glb?alt=media&token=f76dd719-2e22-4fa2-979b-74aefc95af24"
            dialog.dismiss() // Dismiss the dialog
        }

        dialog.show()
    }


    fun addAnchorNode(anchor: Anchor, scaling: Float3?) {
        val selectedModel = object_model  // Save the current selected model

        // Define the rotation variable
        val rotation: Float3 = when (selectedModel) {
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pothos_potted_plant_-_money_plant.glb?alt=media&token=396e3e3c-8bb8-43cb-8fec-5df578e800f2",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/cannabis_plant..glb?alt=media&token=b03218e1-8b4a-4b64-ba92-9d39251f38f5" -> {
                Float3(90.0f, 0.0f, 0.0f) // Example rotation for the specified models
            }
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free__livistona_chinensis_-_fan_palm.glb?alt=media&token=99d32ea3-3310-479f-853c-93cdb02b4fac",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pilea_peperomioides_terracotta_pot.glb?alt=media&token=81dc6953-1d95-4393-baad-39fa2a17ecf6",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/indoor_plant.glb?alt=media&token=d34307a2-e2e3-4efe-a516-4c5f89290804",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_tree.glb?alt=media&token=f76dd719-2e22-4fa2-979b-74aefc95af24" -> {
                Float3(270.0f, 0.0f, 0.0f) // Example rotation for the specified models
            }
            else -> {
                Float3(0.0f, 0.0f, 0.0f) // Default rotation
            }
        }

        // Define the rotation variable
        val newPosition: Float3 = when (selectedModel) {
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/small_bush.glb?alt=media&token=73266032-dd5f-4faf-9c41-0860a768ad59",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pothos_potted_plant_-_money_plant.glb?alt=media&token=396e3e3c-8bb8-43cb-8fec-5df578e800f2",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/cannabis_plant..glb?alt=media&token=b03218e1-8b4a-4b64-ba92-9d39251f38f5" -> {
                Float3(0.0f, 0.5f, 0.0f) // Example rotation for the specified models
            }
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free__livistona_chinensis_-_fan_palm.glb?alt=media&token=99d32ea3-3310-479f-853c-93cdb02b4fac",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/free_pilea_peperomioides_terracotta_pot.glb?alt=media&token=81dc6953-1d95-4393-baad-39fa2a17ecf6",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/indoor_plant.glb?alt=media&token=d34307a2-e2e3-4efe-a516-4c5f89290804",
            "https://firebasestorage.googleapis.com/v0/b/mobile-668e8.appspot.com/o/potted_tree.glb?alt=media&token=f76dd719-2e22-4fa2-979b-74aefc95af24" -> {
                Float3(0.0f, 0.0f, 0.0f) // Example rotation for the specified models
            }
            else -> {
                Float3(0.0f, 0.0f, 0.0f) // Default rotation
            }
        }

        // Add the anchor node with the model node attached
        sceneView.addChildNode(
            AnchorNode(sceneView.engine, anchor)
                .apply {
                    isScaleEditable = false
                    isEditable = true
                    isPositionEditable = true
                    isRotationEditable = false

                    lifecycleScope.launch {
                        isLoading = true

                        sceneView.modelLoader.loadModelInstance(selectedModel)?.let { modelInstance ->
                            modelInstance.isShadowCaster = true
                            val modelNode = ModelNode(
                                modelInstance = modelInstance,
                                scaleToUnits = null,
                                centerOrigin = null
                            ).apply {
                                isShadowCaster = true  // Enable casting shadows
                                isEditable = true
                                isRotationEditable = false
                                scaling?.let {
                                    this.scale = it
                                }
                                rotation?.let {
                                    this.rotation = rotation
                                }

                                // Retrieve current position and adjust it
                                val currentPosition = this.position

                                // Add the new position offset to the current position
                                val adjustedPosition = currentPosition + newPosition

                                // Adjust the position of the model node
                                // position = Position(x = 0.0f, y = 0.4f, z = 0.0f) // Adjust as needed

                            }

                            // Add the model node as a child of the anchor node
                            addChildNode(modelNode)
                        }
                        isLoading = false
                        isRotationEditable = false
                    }
                    anchorNode = this
                }
        )

        // Add the anchor and the selected model to the list
        val newAnchorTriple = Triple(anchorNode, selectedModel, scaling)
        anchorsList.add(newAnchorTriple as Triple<AnchorNode?, String, Float3>)

        // Log the contents of the anchorsList
        Log.d("AnchorsList", "Added new anchor: $newAnchorTriple. AnchorsList: $anchorsList")
        Log.d("AnchorsList", "new anchor pose: ${anchor.pose}")
    }

    fun parseFloat3FromString(input: String): Float3? {
        try {
            // Extract values from the string
            val regex = Regex("Float3\\(x=(-?\\d+\\.\\d+), y=(-?\\d+\\.\\d+), z=(-?\\d+\\.\\d+)\\)")
            val matchResult = regex.find(input)
            Log.d("MATCH","Result: $matchResult")
            if (matchResult != null) {
                val (x, y, z) = matchResult.destructured
                // Create a Float3 object
                val float3 = Float3(x.toFloat(), y.toFloat(), z.toFloat())

                // Log the successfully parsed Float3
                Log.d("parseFloat3", "Successfully parsed Float3: $float3")

                return float3
            }
        } catch (e: Exception) {
            // Log any exceptions that occurred during parsing
            Log.e("parseFloat3", "Error parsing Float3 from input: $input", e)
        }

        // Log that parsing failed and return null
        Log.d("parseFloat3", "Failed to parse Float3 from input: $input")
        return null
    }


    fun Fragment.setFullScreen(
        fullScreen: Boolean = true,
        hideSystemBars: Boolean = true,
        fitsSystemWindows: Boolean = true
    ) {
        requireActivity().setFullScreen(
            this.requireView(),
            fullScreen,
            hideSystemBars,
            fitsSystemWindows
        )
    }

    fun Activity.setFullScreen(
        rootView: View,
        fullScreen: Boolean = true,
        hideSystemBars: Boolean = true,
        fitsSystemWindows: Boolean = true
    ) {
        rootView.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                WindowCompat.setDecorFitsSystemWindows(window, fitsSystemWindows)
                WindowInsetsControllerCompat(window, rootView).apply {
                    if (hideSystemBars) {
                        if (fullScreen) {
                            hide(
                                WindowInsetsCompat.Type.statusBars() or
                                        WindowInsetsCompat.Type.navigationBars()
                            )
                        } else {
                            show(
                                WindowInsetsCompat.Type.statusBars() or
                                        WindowInsetsCompat.Type.navigationBars()
                            )
                        }
                        systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
        }
    }

    fun View.doOnApplyWindowInsets(action: (systemBarsInsets: Insets) -> Unit) {
        doOnAttach {
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                action(insets.getInsets(WindowInsetsCompat.Type.systemBars()))
                WindowInsetsCompat.CONSUMED
            }
        }
    }
}

