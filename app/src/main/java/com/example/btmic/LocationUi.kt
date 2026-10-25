package com.example.btmic

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable
fun LocationTab() {
    val ctx = LocalContext.current
    var sharing by remember { mutableStateOf(LocationState.sharing) }
    var room by remember { mutableStateOf(LocationState.room) }
    var host by remember { mutableStateOf(LocationState.serverHost) }
    var mode by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            sharing = LocationState.sharing
            delay(500)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            ctx.startForegroundService(Intent(ctx, LocationShareService::class.java))
        } else {
            Toast.makeText(ctx, "Location permission chahiye", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Location", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Dono taraf se raazi hoke live location share karo.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { mode = 0 }) {
                Text("Share karo", color = if (mode == 0) Cyan else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { mode = 1 }) {
                Text("Dekho", color = if (mode == 1) Cyan else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))

        Glass {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; LocationState.serverHost = it; LocationState.save(ctx) },
                label = { Text("Server URL (jaise walkie-relay.onrender.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Cyan
                )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = room,
                onValueChange = { v ->
                    val d = v.filter { it.isDigit() }.take(4)
                    room = d
                    LocationState.room = d
                    LocationState.save(ctx)
                },
                label = { Text("Room code (dono taraf same)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Cyan
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        if (mode == 0) {
            Glass {
                ToggleRow(
                    title = "Meri location share karo",
                    sub = "ON hote hi notification me hamesha dikhega, kabhi bhi band kar sakte ho",
                    checked = sharing
                ) { on ->
                    if (on) {
                        val missing = arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ).filter {
                            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isNotEmpty()) {
                            permLauncher.launch(missing.toTypedArray())
                        } else {
                            ctx.startForegroundService(Intent(ctx, LocationShareService::class.java))
                        }
                    } else {
                        ctx.startService(Intent(ctx, LocationShareService::class.java).setAction("STOP"))
                    }
                }
            }
        } else {
            LocationMap(host = host, room = room)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocationMap(host: String, room: String) {
    val base = if (host.startsWith("http")) host else "https://$host"
    val html = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
          html,body{margin:0;background:#0a0a16;font-family:sans-serif}
          #map{height:70%}
          #info{height:30%;color:#fff;padding:10px 14px;box-sizing:border-box;overflow:auto}
          #addr{font-size:14px;line-height:1.4}
          #coords{font-size:13px;color:#4fd1ff;margin-top:6px;font-weight:bold}
          #time{font-size:12px;color:#aaa;margin-top:4px}
        </style>
        </head><body>
        <div id="map"></div>
        <div id="info">
          <div id="addr">Location dhundi ja rahi hai...</div>
          <div id="coords"></div>
          <div id="time"></div>
        </div>
        <script>
        var map = L.map('map').setView([20.5937,78.9629], 4);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
        var marker = null;
        var lastKey = "";

        function reverseGeocode(lat, lng) {
            var key = lat.toFixed(4) + "," + lng.toFixed(4);
            if (key === lastKey) return;
            lastKey = key;
            fetch('https://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lng)
                .then(function(r){ return r.json(); })
                .then(function(d){
                    document.getElementById('addr').innerText = d.display_name || "Pata nahi mil paya";
                }).catch(function(){
                    document.getElementById('addr').innerText = "Pata load nahi ho paya";
                });
        }

        function poll() {
            fetch('$base/location/$room').then(function(r){ return r.json(); }).then(function(d){
                if (d && d.lat) {
                    var pos = [d.lat, d.lng];
                    if (!marker) {
                        marker = L.marker(pos).addTo(map);
                        map.setView(pos, 16);
                    } else {
                        marker.setLatLng(pos);
                    }
                    reverseGeocode(d.lat, d.lng);
                    document.getElementById('coords').innerText =
                        "GPS: " + d.lat.toFixed(6) + ", " + d.lng.toFixed(6);
                    var dt = new Date(d.time);
                    document.getElementById('time').innerText = "Last update: " + dt.toLocaleTimeString();
                }
            }).catch(function(){});
        }
        poll();
        setInterval(poll, 5000);
        </script>
        </body></html>
    """.trimIndent()

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(480.dp),
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}
