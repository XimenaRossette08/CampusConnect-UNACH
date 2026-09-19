package com.example.campusconnect

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Referencias a los elementos de UI
        val etCorreo = findViewById<android.widget.EditText>(R.id.etCorreo)
        val etPassword = findViewById<android.widget.EditText>(R.id.etPassword)
        val btnLogin = findViewById<android.widget.Button>(R.id.btnLogin)
        val btnGoogle = findViewById<android.widget.Button>(R.id.btnGoogle)

        // Lógica del botón de correo tradicional
        btnLogin.setOnClickListener {
            val correo = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Regla estricta: texto + punto + texto + 2 números + @unach.mx
            val regexAlumno = "^[a-zA-Z]+\\.[a-zA-Z]+[0-9]{2}@unach\\.mx$".toRegex()

            if (correo.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(this, "Completa todos los campos", android.widget.Toast.LENGTH_SHORT).show()
            } else if (correo == "admin@unach.mx" && password == "unach123") {
                // Acceso único y estricto para el administrador
                val intent = android.content.Intent(this, AdminFeedActivity::class.java)
                startActivity(intent)
                finish()
            } else if (correo.matches(regexAlumno)) {
                // El correo cumple con la estructura oficial de estudiante UNACH
                // (En el MVP validamos el formato; en Fase 2 Firebase validará la contraseña real)
                val intent = android.content.Intent(this, AlumnoFeedActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                android.widget.Toast.makeText(this, "Formato de correo incorrecto o no institucional", android.widget.Toast.LENGTH_SHORT).show()
            }

        }

        // Lógica simulada para el botón de Google (MVP)
        btnGoogle.setOnClickListener {
            android.widget.Toast.makeText(this, "Conectando con Google Services...", android.widget.Toast.LENGTH_SHORT).show()
            // Aquí en la fase 2 se abrirá el selector de cuentas de Google
        }
        }
}
