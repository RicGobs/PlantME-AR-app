from flask import Flask, request, jsonify, redirect, url_for
from flask_bcrypt import Bcrypt
import mysql.connector
import jwt
import datetime
import json

app = Flask(__name__)
bcrypt = Bcrypt(app)

# Secret key for encoding/decoding JWT
app.config['SECRET_KEY'] = 'your_secret_key_here'

# Function to generate a JWT token
def generate_token(username):
    expiration_time = datetime.datetime.utcnow() + datetime.timedelta(hours=1)
    payload = {'username': username, 'exp': expiration_time}
    token = jwt.encode(payload, app.config['SECRET_KEY'], algorithm='HS256')
    return token


@app.route('/')
def hello_world():
    app.logger.info("Ciao")
    return 'Hello from Flask sta funzionando!'

@app.route('/sign-in', methods=['POST'])
def login():
    # Connect to the MySQL database on PythonAnywhere
    db = mysql.connector.connect(
        host="",
        user="",
        passwd="",
        database=""  # Replace with your actual database name
    )
    app.logger.info(f"Received login google request. Request: {request.json}")

    cursor = db.cursor()

    if 'username' in request.json and 'password' in request.json:
        # Regular login with email and password
        username = request.json['username']
        entered_password = request.json['password']
        # Log information about the incoming request
        app.logger.info(f"Received login request. Username: {username}")

        # Retrieve user information from the database
        cursor.execute("SELECT * FROM table_users WHERE username = %s", (username,))
        user_data = cursor.fetchone()

        if user_data:
            # Extract the hashed password from the database
            hashed_password = user_data[2]  # Replace with the actual column index of the hashed password
            app.logger.info(f"Hashed_password: {hashed_password}")


            # Check if the entered password matches the hashed password
            if bcrypt.check_password_hash(hashed_password, entered_password):
                token = generate_token(username)
                app.logger.info(f"Login {username} success")
                cursor.close()
                db.close()
                return jsonify({'success': True, 'token': token, 'message': 'Login successful'})

    elif 'email' in request.json and 'displayname' in request.json and request.json['type'] == 'google':
        # Google login
        email = request.json['email']
        displayname = request.json['displayname']
        # Check if the email exists in the database
        cursor.execute("SELECT username FROM table_users WHERE email = %s", (email,))
        user_data = cursor.fetchone()
        if user_data:
            token = generate_token(user_data[0])  # Assuming email is unique
            app.logger.info(f"Google login with {email} success")
            cursor.close()
            db.close()
            return jsonify({'success': True, 'token': token, 'message': 'Login successful'})
        else:
            cursor.close()
            db.close()
            return jsonify({'success': False, 'message': 'User not registered'})

    cursor.close()
    db.close()

    # Login failed
    return jsonify({'success': False, 'message': 'Invalid credentials'})

@app.route('/sign-up', methods=['POST'])
def register():
    # Connect to the MySQL database on PythonAnywhere
    db = mysql.connector.connect(
        host="",
        user="",
        passwd="",
        database=""  # Replace with your actual database name
    )

    cursor = db.cursor()
    data = request.json
    app.logger.info(f"Received register request. data: {data}")
    username = data.get('username')
    password = data.get('password')
    email = data.get('email')
    name = data.get('name')
    surname = data.get('surname')

    # Check if the username already exists
    cursor.execute("SELECT * FROM table_users WHERE username=%s", (username,))
    existing_user = cursor.fetchone()

    if existing_user:
        cursor.close()
        db.close()
        return jsonify({'success': False, 'message': 'Username already exists'})

    # Check if the username already exists
    cursor.execute("SELECT * FROM table_users WHERE email=%s", (email,))
    existing_email = cursor.fetchone()

    if existing_email:
        cursor.close()
        db.close()
        return jsonify({'success': False, 'message': 'Email already exists'})


    # Hash the password before storing it
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')

    # Insert the new user into the database
    cursor.execute("INSERT INTO table_users (username, password_hash, email, name, surname) VALUES (%s, %s, %s, %s, %s)", (username, hashed_password, email, name, surname))
    db.commit()
    cursor.close()
    db.close()

    return jsonify({'success': True, 'message': 'Registration successful'})

@app.route('/menu', methods=['GET'])
def profile():
    # Connect to the MySQL database on PythonAnywhere
    db = mysql.connector.connect(
        host="",
        user="",
        passwd="",
        database=""  # Replace with your actual database name
    )

    cursor = db.cursor()
    auth_header = request.headers.get('Authorization')

    if auth_header:
        parts = auth_header.split()

        if len(parts) == 2 and parts[0].lower() == 'bearer':
            token = parts[1]

            try:
                decoded_token = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
                username = decoded_token.get('username')

                # Fetch user details from the database based on the username
                cursor.execute("SELECT name, surname, email FROM table_users WHERE username = %s", (username,))
                user_data = cursor.fetchone()

                if user_data:
                    cursor.close()
                    db.close()

                    # Include user details in the JSON response
                    return jsonify({
                        'success': True,
                        'message': f'Profile for {username}',
                        'username': username,
                        'name': user_data[0],  # Replace with the actual column index of the name
                        'surname': user_data[1],  # Replace with the actual column index of the surname
                        'email': user_data[2]  # Replace with the actual column index of the email
                    })
                else:
                    cursor.close()
                    db.close()

                    return jsonify({'success': False, 'message': 'User not found'})

            except jwt.ExpiredSignatureError:
                cursor.close()
                db.close()

                return jsonify({'success': False, 'message': 'Token has expired'})
            except jwt.InvalidTokenError:
                cursor.close()
                db.close()
                return jsonify({'success': False, 'message': 'Invalid token'})
    cursor.close()
    db.close()

    return "Unauthorized", 401

@app.route('/get-plantations', methods=['GET'])
def get_user_projects():
    # Connect to the MySQL database on PythonAnywhere
    db = mysql.connector.connect(
        host="",
        user="",
        passwd="",
        database=""  # Replace with your actual database name
    )

    cursor = db.cursor()
    auth_header = request.headers.get('Authorization')

    if auth_header:
        parts = auth_header.split()

        if len(parts) == 2 and parts[0].lower() == 'bearer':
            token = parts[1]

            try:
                decoded_token = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
                user = decoded_token.get('username')
                app.logger.info(f"User {user} is requesting projects.")

                cursor.execute("SELECT id FROM table_users WHERE username = %s", (user,))
                username_row = cursor.fetchone()

                if username_row:
                    username = str(username_row[0])

                    # Fetch projects owned by the user
                    cursor.execute("SELECT * FROM table_plantation WHERE owned_by = %s", (username,))
                    owned_projects = cursor.fetchall()

                    # Fetch projects shared with the user
                    shared_with = "1"
                    cursor.execute("SELECT * FROM table_plantation WHERE owned_by != %s AND shared_with = %s", (username, shared_with))
                    shared_projects = cursor.fetchall()

                    projects_list = []

                    # Add owned projects to the list
                    for project in owned_projects:
                        projects_list.append({
                            'project_id': project[0],
                            'project_name': project[1],
                            'owned_by': project[2],
                            'shared_with': "0"
                        })

                    app.logger.info(f"Received request data: {owned_projects}")
                    # Add shared projects to the list
                    for project in shared_projects:

                        projects_list.append({
                            'project_id': project[0],
                            'project_name': project[1],
                            'owned_by': project[2],
                            'shared_with': "1"  # Assuming username is the current user
                        })

                    app.logger.info(f"Received request data: {shared_projects}")

                    cursor.close()
                    db.close()

                    app.logger.info(f"Received request data: {projects_list}")

                    return jsonify({'success': True, 'projects': projects_list})
                else:
                    return jsonify({'success': False, 'message': 'User not found'})
            except jwt.ExpiredSignatureError:
                cursor.close()
                db.close()
                app.logger.error("Token has expired.")
                return jsonify({'success': False, 'message': 'Token has expired'})
            except jwt.InvalidTokenError:
                cursor.close()
                db.close()
                app.logger.error("Invalid token.")
                return jsonify({'success': False, 'message': 'Invalid token'})

    cursor.close()
    db.close()

    app.logger.warning("Unauthorized access to /get_plantations.")
    return "Unauthorized", 401


@app.route('/post-plantation', methods=['POST'])
def create_project():
    try:
        # Log the received request data
        app.logger.info(f"Received request data: {request.json}")

        # Extract project title and anchors from the request
        data = request.json
        project_title = data.get('project_title')

        # Parse the 'anchors' string into a list of dictionaries
        anchors_str = data.get('anchors', '[]')
        anchors = json.loads(anchors_str)

        # Log the extracted data
        app.logger.info(f"Extracted data - Project Title: {project_title}, Anchors: {anchors}")

        # Check if project title and anchors are provided
        if not project_title or not anchors:
            app.logger.error("Project title and anchors are required")
            return jsonify({'success': False, 'message': 'Project title and anchors are required'}), 400

        # Connect to the MySQL database
        db = mysql.connector.connect(
            host="",
            user="",
            passwd="",
            database=""
        )
        cursor = db.cursor()

        # Log the SQL query being executed
        select_project_query = "SELECT id FROM table_plantation WHERE project_name = %s AND owned_by = %s"
        insert_project_query = "INSERT INTO table_plantation (project_name, owned_by, shared_with) VALUES (%s, %s, %s)"
        delete_anchors_query = "DELETE FROM table_object_anchors WHERE project_id = %s"

        auth_header = request.headers.get('Authorization')

        if auth_header:
            parts = auth_header.split()

            if len(parts) == 2 and parts[0].lower() == 'bearer':
                token = parts[1]

                try:
                    decoded_token = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
                    user = decoded_token.get('username')
                    app.logger.info(f"User {user} is requesting projects.")

                    # Get the user ID
                    cursor.execute("SELECT id FROM table_users WHERE username = %s", (user,))
                    user_row = cursor.fetchone()

                    if user_row:
                        user_id = user_row[0]

                        # Check if the project with the same title exists for the user
                        cursor.execute(select_project_query, (project_title, user_id))
                        existing_project = cursor.fetchone()

                        if existing_project:
                            # Project with the same title already exists
                            app.logger.info(f"Plantation with the same title already exists for user {user}")
                            app.logger.info(f"{existing_project})")
                            app.logger.info(f"{existing_project[0]})")
                            project_id = existing_project[0]

                            # Delete existing anchors associated with the project
                            cursor.execute(delete_anchors_query, (existing_project[0],))
                            db.commit()
                        else:
                            # Log the SQL query being executed
                            shared_with = "0"
                            cursor.execute(insert_project_query, (project_title, user_id, shared_with))
                            db.commit()

                            app.logger.info("Executing SQL query: SELECT LAST INSERT ID)")

                            cursor.execute("SELECT LAST_INSERT_ID()")
                            project_id = cursor.fetchone()[0]
                            db.commit()

                        app.logger.info(f"{project_id})")

                        # Iterate over the anchors and insert them into the database
                        for anchor_data in anchors:
                            anchor_id = anchor_data.get('anchor_id')
                            model = anchor_data.get('model')
                            scaling = anchor_data.get('scaling')

                            # Log the SQL query being executed
                            insert_anchor_query = "INSERT INTO table_object_anchors (anchors_id, project_id, model, scaling) VALUES (%s, %s, %s, %s)"
                            app.logger.info(f"Executing SQL query: {insert_anchor_query}, Values: ({anchor_id}, {project_id}, {model}, {scaling})")

                            # Insert the new anchor into the anchors table
                            cursor.execute(insert_anchor_query, (anchor_id, project_id, model, scaling))
                            db.commit()
                            app.logger.info(f"Anchor inserted successfully - Anchor ID: {anchor_id}")

                        return jsonify({'success': True, 'project_id': project_id, 'message': 'Project created successfully'})

                except Exception as e:
                    db.rollback()
                    app.logger.error(f"Error creating project: {str(e)}")
                    return jsonify({'success': False, 'message': f'Error creating project: {str(e)}'}), 500

                finally:
                    cursor.close()
                    db.close()

        app.logger.error("Authorization header not provided or invalid")
        return jsonify({'success': False, 'message': 'Authorization header not provided or invalid'}), 401

    except Exception as e:
        app.logger.error(f"Error: {str(e)}")
        return jsonify({'success': False, 'message': f'Error: {str(e)}'}), 500


@app.route('/plantation/<string:project_name>', methods=['GET'])
def get_project(project_name):
    try:
        auth_header = request.headers.get('Authorization')

        if auth_header:
            parts = auth_header.split()

            if len(parts) == 2 and parts[0].lower() == 'bearer':
                token = parts[1]

                try:
                    decoded_token = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
                    user = decoded_token.get('username')
                    app.logger.info(f"User {user} is requesting projects.")

                    # Connect to the MySQL database
                    db = mysql.connector.connect(
                        host="",
                        user="",
                        passwd="",
                        database=""
                    )
                    cursor = db.cursor()

                    # Log the SQL query being executed
                    select_project_query = "SELECT p.id, p.project_name, a.anchors_id, a.model, a.scaling FROM table_plantation p " \
                                           "JOIN table_object_anchors a ON p.id = a.project_id " \
                                           "WHERE p.project_name = %s"

                    # Execute the SQL query to retrieve project details
                    cursor.execute(select_project_query, (project_name,))
                    project_details = cursor.fetchall()

                    if project_details:
                        project_id, project_title = project_details[0][:2]
                        anchor_details = [{'anchor_id': row[2], 'model': row[3], 'scaling': row[4]} for row in project_details]
                        app.logger.info(f"{project_details}\n{anchor_details}")

                        db.commit()

                        app.logger.info(f"Project details retrieved successfully - Project Name: {project_name}, "
                                        f"Project ID: {project_id}")

                        return jsonify({
                            'success': True,
                            'project_id': project_id,
                            'project_title': project_title,
                            'anchors': anchor_details,
                        })
                    else:
                        app.logger.error(f"Project not found with name: {project_name}")
                        return jsonify({'success': False, 'message': f'Project not found with name: {project_name}'}), 404

                except mysql.connector.Error as db_error:
                    app.logger.error(f"Database error: {db_error}")
                    return jsonify({'success': False, 'message': 'Database error'}), 500

                except Exception as e:
                    app.logger.error(f"Error retrieving project details: {str(e)}")
                    return jsonify({'success': False, 'message': f'Error retrieving project details: {str(e)}'}), 500

                finally:
                    cursor.close()
                    db.close()

    except Exception as e:
        app.logger.error(f"Error: {str(e)}")
        return jsonify({'success': False, 'message': f'Error: {str(e)}'}), 500



@app.route('/send-plantation', methods=['POST'])
def share_project():
    # Connect to the MySQL database on PythonAnywhere
    db = mysql.connector.connect(
        host="",
        user="",
        passwd="",
        database=""  # Replace with your actual database name
    )

    cursor = db.cursor()
    auth_header = request.headers.get('Authorization')

    if auth_header:
        parts = auth_header.split()

        if len(parts) == 2 and parts[0].lower() == 'bearer':
            token = parts[1]

            try:
                decoded_token = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
                user = decoded_token.get('username')
                app.logger.info(f"User {user} is sharing a project.")

                # Get request data
                data = request.json
                project_name = data.get('project_name')
                shared_with = "1" # data.get('shared_with')

                if not project_name or not shared_with:
                    return jsonify({'success': False, 'message': 'Project name and shared_with username are required'})

                # Check if the project exists and is owned by the user
                cursor.execute("SELECT id FROM table_users WHERE username = %s", (user,))
                user_row = cursor.fetchone()

                if not user_row:
                    return jsonify({'success': False, 'message': 'User not found'})

                cursor.execute("SELECT id FROM table_plantation WHERE project_name = %s AND owned_by = %s", (project_name, user_row[0]))
                project_row = cursor.fetchone()

                if not project_row:
                    return jsonify({'success': False, 'message': 'Project not found or not owned by the user'})


                # Check if the project is already shared with the user
                cursor.execute("SELECT id FROM table_plantation WHERE project_name = %s AND shared_with = %s", (project_name, shared_with))
                existing_share_row = cursor.fetchone()

                if existing_share_row:
                    return jsonify({'success': False, 'message': 'Project is already shared'})

                # Update the shared_with column in projects table
                cursor.execute("UPDATE table_plantation SET shared_with = 1 WHERE id = %s", (project_row[0],))
                db.commit()

                cursor.close()
                db.close()

                return jsonify({'success': True, 'message': 'Project shared successfully'})

            except jwt.ExpiredSignatureError:
                cursor.close()
                db.close()
                app.logger.error("Token has expired.")
                return jsonify({'success': False, 'message': 'Token has expired'})
            except jwt.InvalidTokenError:
                cursor.close()
                db.close()
                app.logger.error("Invalid token.")
                return jsonify({'success': False, 'message': 'Invalid token'})

    cursor.close()
    db.close()

    app.logger.warning("Unauthorized access to /share-project.")
    return "Unauthorized", 401

@app.route('/delete-plantation', methods=['POST'])
def delete_project():
    # Connect to the MySQL database on PythonAnywhere
    db = mysql.connector.connect(
        host="",
        user="",
        passwd="",
        database=""  # Replace with your actual database name
    )

    cursor = db.cursor()
    auth_header = request.headers.get('Authorization')

    if auth_header:
        parts = auth_header.split()

        if len(parts) == 2 and parts[0].lower() == 'bearer':
            token = parts[1]

            try:
                decoded_token = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
                user = decoded_token.get('username')
                app.logger.info(f"User {user} is deleting a project.")

                # Get request data
                data = request.json
                project_name = data.get('project_name')

                if not project_name:
                    cursor.close()
                    db.close()
                    return jsonify({'success': False, 'message': 'Project name is required'})

                # Check if the project exists and is owned by the user
                cursor.execute("SELECT id FROM table_users WHERE username = %s", (user,))
                user_row = cursor.fetchone()

                if not user_row:
                    cursor.close()
                    db.close()
                    return jsonify({'success': False, 'message': 'User not found'})

                cursor.execute("SELECT id FROM table_plantation WHERE project_name = %s AND owned_by = %s", (project_name, user_row[0]))
                project_row = cursor.fetchone()

                if not project_row:
                    cursor.close()
                    db.close()
                    return jsonify({'success': False, 'message': 'Project not found or not owned by the user'})

                # Delete associated anchors from the anchors table
                cursor.execute("DELETE FROM table_object_anchors WHERE project_id = %s", (project_row[0],))

                # Delete the project from the projects table
                cursor.execute("DELETE FROM table_plantation WHERE id = %s", (project_row[0],))

                db.commit()

                cursor.close()
                db.close()

                return jsonify({'success': True, 'message': 'Project deleted successfully'})

            except jwt.ExpiredSignatureError:
                cursor.close()
                db.close()
                app.logger.error("Token has expired.")
                return jsonify({'success': False, 'message': 'Token has expired'})
            except jwt.InvalidTokenError:
                cursor.close()
                db.close()
                app.logger.error("Invalid token.")
                return jsonify({'success': False, 'message': 'Invalid token'})

    cursor.close()
    db.close()

    app.logger.warning("Unauthorized access to /delete-project.")
    return "Unauthorized", 401





if __name__ == '__main__':
    app.run(debug=True)
