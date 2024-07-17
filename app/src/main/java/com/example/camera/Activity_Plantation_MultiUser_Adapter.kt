package com.example.camera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class Activity_projects_MultiUser_Adapter(private val clickListener: (String) -> Unit) : RecyclerView.Adapter<Activity_projects_MultiUser_Adapter.ProjectViewHolder>() {

    // Add your project data list
    private val projectsList = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_list_layout, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val projectName = projectsList[position]
        holder.bind(projectName)

        // Set click listener on the item
        holder.itemView.setOnClickListener {
            // Pass the clicked project name to the click listener
            clickListener(projectName)
        }
    }

    override fun getItemCount(): Int {
        return projectsList.size
    }

    fun updateData(newProjects: List<String>) {
        projectsList.clear()
        projectsList.addAll(newProjects)
        notifyDataSetChanged()
    }

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val projectNameTextView: TextView = itemView.findViewById(R.id.textViewProjectName)

        fun bind(projectName: String) {
            projectNameTextView.text = projectName
            // Bind other project details if needed
        }
    }
}
