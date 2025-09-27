package org.cubewhy.skidcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.cubewhy.skidcamera.ui.theme.SkidCameraTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private var currentPhotoUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            // share the image
            currentPhotoUri?.let { uri ->
                shareImage(uri)
            }
        } else {
            // user manually cancelled 
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Ok", Toast.LENGTH_SHORT).show()
             launchCamera()
        } else {
            Toast.makeText(this, "User Denied the camera permission", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkidCameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraShareScreen(
                        onTakePhotoClick = {
                            checkCameraPermissionAndLaunch()
                        }
                    )
                }
            }

        }
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val photoUri = createImageUri()
        if (photoUri != null) {
            currentPhotoUri = photoUri
            takePictureLauncher.launch(photoUri)
        } else {
            Toast.makeText(this, "Failed to create temp image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageUri(): Uri? {
        val imageFile = File(
            File(cacheDir, "images"),
            "temp_image_${System.currentTimeMillis()}.jpg"
        )
        val imagePath = imageFile.parentFile
        if (imagePath != null && !imagePath.exists()) {
            imagePath.mkdirs()
        }
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            imageFile
        )
    }

    private fun shareImage(imageUri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, imageUri)
            type = "image/jpeg"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share to...")
        startActivity(chooser)
    }
}

@Composable
fun CameraShareScreen(onTakePhotoClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onTakePhotoClick) {
            Text(text = "Take a photo", fontSize = 20.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SkidCameraTheme {
        CameraShareScreen(onTakePhotoClick = {})
    }
}